package com.rover.utils;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Centralized SSH connection manager for PLANET switches.
 * <p>
 * This class provides low-level SSH connectivity operations that are reused
 * across different services (protocol testing, config download, etc.).
 * It handles session creation, channel management, and resource cleanup.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class SSHConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(SSHConnectionManager.class);

    private static final int DEFAULT_TIMEOUT_MS = 2000;
    private static final int DEFAULT_CHANNEL_TIMEOUT_MS = 2000;
    private static final int SSH_READ_TIMEOUT = 3000;

    private final JSch jsch;

    public SSHConnectionManager() {
        this.jsch = new JSch();
    }

    /**
     * Creates and configures an SSH session.
     *
     * @param ipAddress the target IP address
     * @param port the SSH port
     * @param username the SSH username
     * @param password the SSH password
     * @return configured Session (not yet connected)
     * @throws JSchException if session creation fails
     */
    public Session createSession(String ipAddress, int port, String username, String password) throws JSchException {
        Session session = jsch.getSession(username, ipAddress, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(DEFAULT_TIMEOUT_MS);
        return session;
    }

    /**
     * Opens and connects a shell channel on the given session.
     *
     * @param session the SSH session (must be connected)
     * @return connected ChannelShell
     * @throws JSchException if channel creation or connection fails
     */
    public ChannelShell openShellChannel(Session session) throws JSchException {
        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        channel.connect(DEFAULT_CHANNEL_TIMEOUT_MS);
        return channel;
    }

    /**
     * Executes a command on the shell channel and reads the response.
     * <p>
     * This method handles paginated output by automatically sending ENTER
     * when it detects "More" prompts, ensuring all configuration is retrieved.
     * </p>
     *
     * @param channel the connected shell channel
     * @param command the command to execute
     * @param maxWaitTimeMs maximum time to wait for complete output
     * @return the command output as string
     * @throws IOException if I/O errors occur
     * @throws InterruptedException if the thread is interrupted
     */
    @SuppressWarnings("unused")
    public String executeCommand(ChannelShell channel, String command, int maxWaitTimeMs) throws IOException, InterruptedException {

        InputStream inputStream = channel.getInputStream();
        OutputStream outputStream = channel.getOutputStream();

        outputStream.write((command + "\n").getBytes());
        outputStream.flush();

        Thread.sleep(500);

        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[4096];
        long startTime = System.currentTimeMillis();
        int noDataCount = 0;

        while (System.currentTimeMillis() - startTime < maxWaitTimeMs) {
            if (inputStream.available() > 0) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead);
                    output.append(chunk);
                    noDataCount = 0;

                    if (containsMorePrompt(chunk)) {
                        logger.debug("Detected '-- more --' prompt, sending 'g' to continue");
                        outputStream.write("g".getBytes());
                        outputStream.flush();
                    }
                }
            } else {
                noDataCount++;
                if (noDataCount > 5) {
                    break;
                }
                Thread.sleep(200);
            }
        }

        return output.toString();
    }

    /**
     * Checks if the output contains a "More" or pagination prompt.
     *
     * @param text the text to check
     * @return true if pagination prompt detected
     */
    private boolean containsMorePrompt(String text) {
        return text.contains("-- more --") ||
                text.contains("--more--") ||
                text.contains("(more)") ||
                text.contains("more");
    }

    /**
     * Safely disconnects channel and session.
     *
     * @param channel the channel to disconnect (can be null)
     * @param session the session to disconnect (can be null)
     */
    public void disconnect(ChannelShell channel, Session session) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }


    /**
     * Handles SSH authentication with improved stability for devices with complex login flows.
     * <p>
     * This method handles various authentication scenarios:
     * - Welcome screens requiring <enter> to continue
     * - Multiple username/password prompts
     * - "% Authentication Failed" informational messages before re-prompting
     * - Shell prompt detection for successful login
     * </p>
     *
     * @param channel the SSH channel
     * @param ipAddress the IP address (for logging)
     * @param username the username
     * @param password the password
     * @return true if authentication succeeded, false otherwise
     * @throws Exception if errors occur
     */
    public boolean handleSshAuthentication(ChannelShell channel, String ipAddress, String username, String password) throws Exception {
        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();

        long startTime = System.currentTimeMillis();
        long lastActivityTime = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();

        int usernamePromptCount = 0;
        int passwordPromptCount = 0;
        boolean welcomeScreenPassed = false;
        boolean authFailedMessageSeen = false;

        logger.info("SSH: Starting authentication for {}", ipAddress);

        while (System.currentTimeMillis() - startTime < SSH_READ_TIMEOUT) {

            if (readAvailableDataBoolean(in, fullResponse)) {
                lastActivityTime = System.currentTimeMillis();
            }

            String currentResponse = fullResponse.toString().toLowerCase();
            String recentResponse = getRecentResponse(fullResponse, 200);

            // Handle welcome screen
            if (!welcomeScreenPassed && isWelcomeScreen(recentResponse)) {
                sendNewline(out);
                logger.info("SSH: welcome screen on {}", ipAddress);
                welcomeScreenPassed = true;
                fullResponse.setLength(0);
                Thread.sleep(200);
                continue;
            }

            // Handle username prompt
            if (isUsernamePrompt(recentResponse)) {
                usernamePromptCount++;
                logger.debug("SSH: Username prompt #{} detected on {}", usernamePromptCount, ipAddress);

                if (usernamePromptCount > 3) {
                    logger.warn("SSH: Too many username prompts ({}) on {} - authentication likely failed", usernamePromptCount, ipAddress);
                    return false;
                }

                sendCredential(out, username);
                fullResponse.setLength(0);
                authFailedMessageSeen = false; // Reset flag
                Thread.sleep(200);
                continue;
            }

            // Detect "% Authentication Failed" informational message
            if (detectAuthFailedMessage(recentResponse)) {
                if (!authFailedMessageSeen) {
                    logger.info("SSH: 'Authentication Failed' message detected on {} - device will re-prompt", ipAddress);
                    authFailedMessageSeen = true;
                    Thread.sleep(500);
                    sendCredential(out, username);
                }
                // Clear old buffer but keep recent content
                if (fullResponse.length() > 500) {
                    fullResponse.delete(0, fullResponse.length() - 200);
                }
                continue;
            }

            // Handle password prompt
            if (recentResponse.contains("password:")) {
                passwordPromptCount++;
                logger.info("SSH: Password prompt #{} detected on {}", passwordPromptCount, ipAddress);

                if (passwordPromptCount > 3) {
                    logger.warn("SSH: Too many password prompts ({}) on {} - authentication likely failed", passwordPromptCount, ipAddress);
                    return false;
                }

                sendCredential(out, password);
                fullResponse.setLength(0);
                Thread.sleep(200);
                continue;
            }

            // Handle username prompt
            if (isUsernamePrompt(recentResponse)) {
                if (!authFailedMessageSeen) {
                    usernamePromptCount++;
                }
                logger.info("SSH: Second Username prompt #{} detected on {}", usernamePromptCount, ipAddress);

                if (usernamePromptCount > 3) {
                    logger.warn("SSH: Too many username prompts ({}) on {} - authentication likely failed", usernamePromptCount, ipAddress);
                    return false;
                }

                sendCredential(out, username);
                fullResponse.setLength(0);
                authFailedMessageSeen = false; // Reset flag
                Thread.sleep(200);
                continue;
            }

            // Handle password prompt
            if (recentResponse.contains("password:")) {
                passwordPromptCount++;
                logger.info("SSH: Password prompt #{} detected on {}", passwordPromptCount, ipAddress);

                if (passwordPromptCount > 3) {
                    logger.warn("SSH: Too many password prompts ({}) on {} - authentication likely failed", passwordPromptCount, ipAddress);
                    return false;
                }

                sendCredential(out, password);
                fullResponse.setLength(0);
                Thread.sleep(200);
                continue;
            }

            // Check for shell prompt (success)
            if (isShellPrompt(recentResponse)) {
                logger.info("SSH: Authentication successful for {} - got shell prompt", ipAddress);
                return true;
            }
            // Check for terminal authentication failure (permanent rejection)
            if (isTerminalAuthFailure(currentResponse, usernamePromptCount, passwordPromptCount)) {
                logger.info("SSH: Authentication permanently failed for {}", ipAddress);
                return false;
            }

            // Check for inactivity timeout
            if (System.currentTimeMillis() - lastActivityTime > 2000 && usernamePromptCount > 0) {
                logger.debug("SSH: No activity for 2 seconds after sending credentials on {}", ipAddress);
                if (fullResponse.length() < 20) {
                    // No meaningful data received
                    return false;
                }
            }

            Thread.sleep(100);
        }

        return analyzeFinalSshState(fullResponse.toString(), ipAddress, usernamePromptCount, passwordPromptCount);
    }

    /**
     * Gets the most recent response text to avoid false positives from old buffer content.
     *
     * @param buffer the full response buffer
     * @param maxChars maximum number of recent characters to return
     * @return the recent response text
     */
    private String getRecentResponse(StringBuilder buffer, int maxChars) {
        int length = buffer.length();
        if (length <= maxChars) {
            return buffer.toString().toLowerCase();
        }
        return buffer.substring(length - maxChars).toLowerCase();
    }

    /**
     * Detects the "% Authentication Failed" informational message.
     *
     * @param text the text to check
     * @return true if authentication failed message detected
     */
    public boolean detectAuthFailedMessage(String text) {
        return text.toLowerCase().contains("% authentication failed") || text.toLowerCase().contains("%authentication failed") || text.toLowerCase().contains("authentication failed");
    }

    /**
     * Detects terminal authentication failure (permanent rejection).
     *
     * @param text the full text buffer
     * @param usernamePromptCount number of username prompts seen
     * @param passwordPromptCount number of password prompts seen
     * @return true if authentication has permanently failed
     */
    public  boolean isTerminalAuthFailure(String text, int usernamePromptCount, int passwordPromptCount) {
        // Only consider terminal failure if we've made attempts
        if (usernamePromptCount == 0 && passwordPromptCount == 0) {
            return false;
        }

        // Look for definitive rejection messages
        return text.contains("access denied") ||
                text.contains("incorrect") ||
                text.contains("invalid credentials") ||
                text.contains("login incorrect");
    }

    /**
     * Reads available data from the input stream and returns whether data was read.
     *
     * @param in the input stream
     * @param buffer the buffer to append to
     * @return true if data was read, false otherwise
     * @throws IOException if I/O errors occur
     */
    public boolean readAvailableDataBoolean(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[1024];
            int bytesRead = in.read(tmp, 0, 1024);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead));
                return true;
            }
        }
        return false;
    }

    private boolean analyzeFinalSshState(String finalResponse, String ipAddress, int usernamePromptCount, int passwordPromptCount) {
        String response = finalResponse.toLowerCase();

        logger.info("SSH timeout reached for {} after {}ms", ipAddress, SSH_READ_TIMEOUT);

        if (isUsernamePrompt(response) || response.contains("password:")) {
            logger.warn("SSH authentication incomplete on {} - still asking for credentials: {}", ipAddress, finalResponse);
        }

        if (usernamePromptCount > 0 || passwordPromptCount > 0) {
            logger.warn("SSH response unclear on {} after sending credentials: {}", ipAddress, finalResponse);
        }

        logger.warn("SSH response unclear on {} - no prompts detected: {}", ipAddress, finalResponse);
        return false;
    }

    public boolean isWelcomeScreen(String text) {
        return text.contains("press <enter>") ||
                text.contains("press enter") ||
                text.contains("continue...");
    }

    public void sendNewline(OutputStream out) throws IOException {
        out.write("\n".getBytes());
        out.flush();
    }

    public void sendCredential(OutputStream out, String credential) throws IOException {
        out.write((credential + "\n").getBytes());
        out.flush();
    }

    /**
     * Checks if the response text contains a shell prompt.
     *
     * @param text the text to check
     * @return either true of false
     */
    public boolean isUsernamePrompt(String text) {
        return text.contains("login") ||
                text.contains("username:") ||
                text.contains("login as") ||
                text.contains("user name:");
    }

    public boolean isShellPrompt(String text) {
        return text.contains("#") || text.contains(">") || text.contains("$") || text.contains("IGS");
    }
}