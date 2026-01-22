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
}