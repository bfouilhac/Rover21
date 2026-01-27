package com.rover.services;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.PreferencesManager;
import com.rover.utils.SSHConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Service for downloading switch configurations via SSH.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class SwitchConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SwitchConfigService.class);

    private static final int READ_BUFFER_SIZE = 8192;
    private static final int LOGIN_TIMEOUT_MS = 10000;
    private static final int INITIAL_WAIT_MS = 1000;
    private static final int PROMPT_WAIT_MS = 500;
    private static final int AFTER_COMMAND_WAIT_MS = 2000;
    private static final int NO_DATA_TIMEOUT_MS = 3000;

    private final SSHConnectionManager sshManager;

    /**
     * Configuration type to download with fallback commands.
     */
    public enum ConfigType {
        RUNNING(new String[]{"show running-config"}),
        STARTUP(new String[]{"more flash:startup-config", "show startup-config"});

        private final String[] commands;

        ConfigType(String[] commands) {
            this.commands = commands;
        }

        public String[] getCommands() {
            return commands;
        }
    }

    public SwitchConfigService() {
        this.sshManager = new SSHConnectionManager();
    }

    /**
     * Downloads configuration from a PlanetSwitch device.
     *
     * @param device source device
     * @param configType configuration type
     * @param destination destination file
     * @throws IOException on network or I/O error
     */
    public void downloadConfig(PlanetSwitchDevice device, ConfigType configType, File destination) throws IOException {
        if (!device.isSSHUp()) {
            throw new IllegalStateException("SSH is not available on device " + device.getIpAddress());
        }

        String username = device.getUsername();
        String password = device.getPassword();

        if (username == null || password == null) {
            throw new IllegalStateException("No SSH credentials available for device " + device.getIpAddress());
        }

        downloadConfigViaSsh(device.getIpAddress(), username, password, configType, destination);
    }

    /**
     * Downloads configuration via SSH.
     *
     * @param switchIp switch IP address
     * @param username username
     * @param password password
     * @param configType configuration type
     * @param destination destination file
     * @throws IOException on network or I/O error
     */
    public void downloadConfigViaSsh(String switchIp, String username, String password, ConfigType configType, File destination) throws IOException {
        logger.info("Downloading {} config from {} via SSH", configType, switchIp);

        Session session = null;
        ChannelShell channel = null;

        try {
            session = sshManager.createSession(switchIp, PreferencesManager.getSshPort(), username, password);
            session.connect();

            if (!session.isConnected()) {
                throw new IOException("SSH session failed to connect to " + switchIp);
            }

            channel = sshManager.openShellChannel(session);

            if (!channel.isConnected()) {
                throw new IOException("SSH channel failed to connect to " + switchIp);
            }

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            Thread.sleep(INITIAL_WAIT_MS);

            boolean loggedIn = performAdaptiveLogin(in, out, username, password);

            if (!loggedIn) {
                throw new IOException("Failed to login to switch");
            }

            logger.info("Login successful, disabling pagination");

            disablePagination(in, out);

            String cleanedConfig = null;
            String successfulCommand = null;

            for (String command : configType.getCommands()) {
                logger.info("Attempting command: {}", command);

                String rawOutput = executeCommand(in, out, command);

                if (isCommandFailed(rawOutput)) {
                    logger.warn("Command '{}' failed or returned error, trying next fallback", command);
                    continue;
                }

                if (rawOutput.isEmpty()) {
                    logger.warn("Command '{}' returned empty output, trying next fallback", command);
                    continue;
                }

                cleanedConfig = extractConfig(rawOutput, command);

                if (!cleanedConfig.isEmpty()) {
                    successfulCommand = command;
                    logger.info("Successfully retrieved config using command: {}", command);
                    break;
                }
            }

            if (cleanedConfig == null || cleanedConfig.isEmpty()) {
                throw new IOException("Failed to retrieve configuration with any available command");
            }

            saveConfigToFile(cleanedConfig, destination);

            logger.info("Config saved to {} ({} lines) using command '{}'",
                    destination.getAbsolutePath(),
                    cleanedConfig.split("\n").length,
                    successfulCommand);

        } catch (JSchException e) {
            throw new IOException("SSH connection failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operation interrupted", e);
        } finally {
            sshManager.disconnect(channel, session);
        }
    }

    /**
     * Performs adaptive login on the switch.
     *
     * @param in SSH input stream
     * @param out SSH output stream
     * @param username username
     * @param password password
     * @return true if login succeeded
     * @throws IOException on I/O error
     * @throws InterruptedException if the operation is interrupted
     */
    private boolean performAdaptiveLogin(InputStream in, OutputStream out, String username, String password) throws IOException, InterruptedException {
        StringBuilder buffer = new StringBuilder();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < LOGIN_TIMEOUT_MS) {
            readAvailableData(in, buffer);
            String text = buffer.toString().toLowerCase();

            if (text.contains("#") || text.contains(">")) {
                logger.info("Login successful");
                return true;
            }

            if (text.contains("login:") || text.contains("username:")) {
                sendLine(out, username);
                buffer.setLength(0);
                Thread.sleep(PROMPT_WAIT_MS);
                continue;
            }

            if (text.contains("password:")) {
                sendLine(out, password);
                buffer.setLength(0);
                Thread.sleep(PROMPT_WAIT_MS);
                continue;
            }

            if (text.contains("denied") || text.contains("incorrect")) {
                return false;
            }

            Thread.sleep(100);
        }

        return false;
    }

    /**
     * Disables pagination to retrieve the entire output.
     *
     * @param in SSH input stream
     * @param out SSH output stream
     * @throws IOException on I/O error
     * @throws InterruptedException if the operation is interrupted
     */
    private void disablePagination(InputStream in, OutputStream out) throws IOException, InterruptedException {
        sendLine(out, "terminal length 0");
        Thread.sleep(PROMPT_WAIT_MS);

        StringBuilder buffer = new StringBuilder();
        readAvailableData(in, buffer);

        logger.debug("Pagination disabled");
    }

    /**
     * Executes a command and retrieves the entire output.
     *
     * @param in SSH input stream
     * @param out SSH output stream
     * @param command command to execute
     * @return complete command output
     * @throws IOException on I/O error
     * @throws InterruptedException if the operation is interrupted
     */
    private String executeCommand(InputStream in, OutputStream out, String command) throws IOException, InterruptedException {
        sendLine(out, command);
        Thread.sleep(AFTER_COMMAND_WAIT_MS);

        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        long lastReadTime = System.currentTimeMillis();
        int consecutiveEmptyReads = 0;

        while (true) {
            if (in.available() > 0) {
                int bytesRead = in.read(buffer);
                if (bytesRead > 0) {
                    output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                    lastReadTime = System.currentTimeMillis();
                    consecutiveEmptyReads = 0;

                    String currentOutput = output.toString();
                    if (currentOutput.contains("end") && (currentOutput.contains("#") || currentOutput.contains(">"))) {
                        logger.debug("Detected end of configuration");
                        break;
                    }
                }
            } else {
                if (System.currentTimeMillis() - lastReadTime > NO_DATA_TIMEOUT_MS) {
                    logger.debug("No data received for {} ms, stopping read", NO_DATA_TIMEOUT_MS);
                    break;
                }

                consecutiveEmptyReads++;
                if (consecutiveEmptyReads > 30) {
                    logger.debug("Too many consecutive empty reads, stopping");
                    break;
                }

                Thread.sleep(100);
            }
        }

        logger.info("Read complete, received {} characters", output.length());
        return output.toString();
    }

    /**
     * Checks if a command failed based on output content.
     *
     * @param output command output
     * @return true if the command failed
     */
    private boolean isCommandFailed(String output) {
        String lowerOutput = output.toLowerCase();
        return lowerOutput.contains("unknown command") ||
                lowerOutput.contains("invalid command") ||
                lowerOutput.contains("command not found") ||
                lowerOutput.contains("unrecognized command") ||
                lowerOutput.contains("% invalid") ||
                lowerOutput.contains("error:");
    }

    /**
     * Extracts clean configuration from raw SSH output.
     *
     * @param rawOutput raw SSH output
     * @param command executed command
     * @return cleaned configuration
     */
    private String extractConfig(String rawOutput, String command) {
        String[] lines = rawOutput.split("\r?\n");
        StringBuilder result = new StringBuilder();
        boolean capturing = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!capturing) {
                if (trimmed.contains(command)) {
                    capturing = true;
                }
                continue;
            }

            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }

            if (trimmed.toLowerCase().contains("building configuration")) {
                continue;
            }

            if (trimmed.startsWith("Current configuration")) {
                continue;
            }

            if (trimmed.matches("^[a-zA-Z0-9_-]+[>#]$")) {
                break;
            }

            if (trimmed.equals("end")) {
                result.append(line).append("\n");
                break;
            }

            result.append(line).append("\n");
        }

        return result.toString().trim();
    }

    /**
     * Saves configuration to a file.
     *
     * @param config configuration content
     * @param destination destination file
     * @throws IOException on write error
     */
    private void saveConfigToFile(String config, File destination) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(destination, StandardCharsets.UTF_8))) {
            writer.write(config);
        }
    }

    /**
     * Reads available data from the input stream.
     *
     * @param in input stream
     * @param buffer destination buffer
     * @throws IOException on read error
     */
    private void readAvailableData(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[READ_BUFFER_SIZE];
            int bytesRead = in.read(tmp);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead, StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Sends a line of text followed by a newline.
     *
     * @param out output stream
     * @param text text to send
     * @throws IOException on write error
     */
    private void sendLine(OutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}