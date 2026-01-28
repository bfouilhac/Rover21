package com.rover.services;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.rover.model.PlanetSwitchDevice;
import com.rover.model.rstp.*;
import com.rover.utils.PreferencesManager;
import com.rover.utils.SSHConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for retrieving RSTP information via SSH.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPSSHService {

    private static final Logger logger = LoggerFactory.getLogger(RSTPSSHService.class);

    private static final int INITIAL_WAIT_MS = 500;
    private static final int AFTER_COMMAND_WAIT_MS = 800;
    private static final int LOGIN_TIMEOUT_MS = 10000;
    private static final int PROMPT_WAIT_MS = 200;
    private static final int NO_DATA_TIMEOUT_MS = 1500;
    private static final int READ_BUFFER_SIZE = 8192;
    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final SSHConnectionManager sshManager;

    // Regex patterns for parsing - Format 1 (IGS-6325)
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("Protocol Version:\\s*(\\w+)");
    private static final Pattern BRIDGE_ID_PATTERN = Pattern.compile("Bridge ID\\s*:\\s*(\\d+)\\.(\\S+)");
    private static final Pattern ROOT_ID_PATTERN = Pattern.compile("Root ID\\s*:\\s*(\\d+)\\.(\\S+)");
    private static final Pattern ROOT_COST_PATTERN = Pattern.compile("Root PathCost:\\s*(\\d+)");
    private static final Pattern MAX_HOPS_PATTERN = Pattern.compile("Max Hops\\s*:\\s*(\\d+)");
    private static final Pattern PORT_LINE_PATTERN = Pattern.compile("^(\\S+\\s+\\d+/\\d+)\\s+(\\w+)\\s+(\\w+)\\s+(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+).*");

    // Regex patterns for Format 2 - multiline bridge/root info
    private static final Pattern BRIDGE_PRIORITY_PATTERN = Pattern.compile("Bridge ID\\s+Priority\\s+(\\d+)");
    private static final Pattern BRIDGE_ADDRESS_PATTERN = Pattern.compile("Address\\s+([a-f0-9:]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT_PRIORITY_PATTERN = Pattern.compile("Root ID\\s+Priority\\s+(\\d+)");
    private static final Pattern ROOT_COST_ALT_PATTERN = Pattern.compile("Cost\\s+(\\d+)");
    private static final Pattern PORT_LINE_ALT_PATTERN = Pattern.compile("^\\s*(gi\\d+)\\s+(\\w+)\\s+\\d+\\.(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+).*");

    public RSTPSSHService() {
        this.sshManager = new SSHConnectionManager();
    }

    /**
     * Checks if RSTP/STP is enabled on the device.
     *
     * @param device the device to check
     * @return true if RSTP/STP is enabled
     */
    public boolean isRSTPEnabled(PlanetSwitchDevice device) {
        if (!device.isSSHUp()) {
            logger.debug("Device {} SSH not available", device.getIpAddress());
            return false;
        }

        try {
            logger.debug("Checking RSTP status for {}", device.getIpAddress());
            String output = executeSshCommand(device, "show spanning-tree summary");

            boolean enabled = output.contains("Protocol Version: RSTP") || output.contains("Protocol Version: Compatible");

            logger.info("Device {} RSTP enabled: {}", device.getIpAddress(), enabled);
            return enabled;
        } catch (Exception e) {
            logger.error("Failed to check RSTP status for {} - {}: {}", device.getIpAddress(), e.getClass().getSimpleName(), e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Retrieves complete RSTP information for a device.
     *
     * @param device the device to query
     * @return RSTPNodeNode with all information
     * @throws IOException if SSH query fails
     */
    public RSTPNode getRSTPInfo(PlanetSwitchDevice device) throws IOException {
        if (!device.isSSHUp() || device.getUsername() == null || device.getPassword() == null) {
            throw new IOException("SSH not available or credentials missing for " + device.getIpAddress());
        }

        logger.info("Getting RSTP info for {} ({})", device.getDeviceName(), device.getIpAddress());
        RSTPNode node = new RSTPNode(device);

        Session session = null;
        ChannelShell channel = null;

        try {
            // Open a single SSH connection for both commands
            session = sshManager.createSession(
                    device.getIpAddress(),
                    PreferencesManager.getSshPort(),
                    device.getUsername(),
                    device.getPassword()
            );
            session.connect();

            if (!session.isConnected()) {
                throw new IOException("SSH session failed to connect");
            }

            channel = sshManager.openShellChannel(session);
            if (!channel.isConnected()) {
                throw new IOException("SSH channel failed to connect");
            }

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            Thread.sleep(INITIAL_WAIT_MS);

            // Perform login
            if (!performAdaptiveLogin(in, out, device.getUsername(), device.getPassword())) {
                throw new IOException("Failed to login");
            }

            // Disable pagination
            sendLine(out, "terminal length 0");
            Thread.sleep(PROMPT_WAIT_MS);
            readAvailableData(in, new StringBuilder());

            // Execute both commands in the same session
            logger.debug("Executing 'show spanning-tree summary' on {}", device.getIpAddress());
            String summaryOutput = executeCommandInSession(in, out, "show spanning-tree summary");

            // Check if a summary command is supported
            boolean summarySupported = !summaryOutput.toLowerCase().contains("unknown command");

            logger.debug("Executing 'show spanning-tree' on {}", device.getIpAddress());
            String detailOutput = executeCommandInSession(in, out, "show spanning-tree");

            // Parse based on available output
            if (summarySupported) {
                parseRSTPVersion(summaryOutput, node);
            } else {
                logger.debug("'show spanning-tree summary' not supported, parsing from detail output");
                parseRSTPVersionFromDetail(detailOutput, node);
            }

            parseSpanningTreeInfo(detailOutput, node);

            logger.info("Successfully retrieved RSTP info for {}: version={}, cost={}, ports={}", device.getDeviceName(), node.getVersion(), node.getRootCost(), node.getPorts().size());

        } catch (JSchException e) {
            logger.error("SSH connection failed for {}: {}", device.getIpAddress(), e.getMessage());
            Thread.currentThread().interrupt();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Operation interrupted for {}", device.getIpAddress());
            throw new IOException("Operation interrupted", e);
        } finally {
            sshManager.disconnect(channel, session);
        }

        return node;
    }

    /**
     * Executes a command in an already established SSH session.
     */
    private String executeCommandInSession(InputStream in, OutputStream out, String command) throws IOException, InterruptedException {

        sendLine(out, command);
        Thread.sleep(AFTER_COMMAND_WAIT_MS);

        StringBuilder output = new StringBuilder();
        readCommandOutput(in, output);

        String result = cleanOutput(output.toString(), command);
        logger.debug("Command '{}' executed, output length: {} chars", command, result.length());

        return result;
    }

    /**
     * Executes a command and returns the output.
     *
     * @param device the current device to execute the command on
     * @return the mac address table output
     * @throws IOException if ssh fails
     * @throws JSchException if the connection fails
     * @throws InterruptedException if interrupted
     */
    public String executeMacTableCommand(PlanetSwitchDevice device) throws IOException, JSchException, InterruptedException {
        return executeSshCommand(device, "show mac address-table");
    }

    /**
     * Executes an SSH command and returns the output.
     *
     * @param device the device to connect to
     * @param command the command to execute
     * @return command output
     * @throws IOException if SSH fails
     * @throws JSchException if the connection fails
     * @throws InterruptedException if interrupted
     */
    private String executeSshCommand(PlanetSwitchDevice device, String command) throws IOException, JSchException, InterruptedException {

        Session session = null;
        ChannelShell channel = null;

        try {
            session = sshManager.createSession(
                    device.getIpAddress(),
                    PreferencesManager.getSshPort(),
                    device.getUsername(),
                    device.getPassword()
            );
            session.connect();

            if (!session.isConnected()) {
                throw new IOException("SSH session failed to connect");
            }

            channel = sshManager.openShellChannel(session);
            if (!channel.isConnected()) {
                throw new IOException("SSH channel failed to connect");
            }

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            Thread.sleep(INITIAL_WAIT_MS);

            // Perform login
            if (!performAdaptiveLogin(in, out, device.getUsername(), device.getPassword())) {
                throw new IOException("Failed to login");
            }

            // Disable pagination
            sendLine(out, "terminal length 0");
            Thread.sleep(PROMPT_WAIT_MS);
            readAvailableData(in, new StringBuilder());

            // Execute command
            sendLine(out, command);
            Thread.sleep(AFTER_COMMAND_WAIT_MS);

            // Read output
            StringBuilder output = new StringBuilder();
            readCommandOutput(in, output);

            String result = cleanOutput(output.toString(), command);
            logger.debug("Command '{}' executed successfully, output length: {} chars",
                    command, result.length());

            return result;

        } finally {
            sshManager.disconnect(channel, session);
        }
    }

    /**
     * Performs adaptive login on the switch.
     * Handles devices that ask for login/password multiple times.
     */
    private boolean performAdaptiveLogin(InputStream in, OutputStream out,
                                         String username, String password)
            throws IOException, InterruptedException {

        StringBuilder buffer = new StringBuilder();
        long startTime = System.currentTimeMillis();
        int loginsSent = 0;
        int passwordsSent = 0;

        while (System.currentTimeMillis() - startTime < LOGIN_TIMEOUT_MS) {
            readAvailableData(in, buffer);
            String text = buffer.toString().toLowerCase();

            // Check if we're logged in successfully
            if (text.contains("#") || text.contains(">")) {
                logger.debug("Login successful (prompt detected)");
                return true;
            }

            // Handle login prompt
            if ((text.contains("login:") || text.contains("username:")) && loginsSent < MAX_LOGIN_ATTEMPTS) {
                logger.debug("Login prompt detected (attempt {})", loginsSent + 1);
                sendLine(out, username);
                loginsSent++;
                buffer.setLength(0);
                Thread.sleep(PROMPT_WAIT_MS);
                continue;
            }

            // Handle password prompt
            if (text.contains("password:") && passwordsSent < MAX_LOGIN_ATTEMPTS) {
                logger.debug("Password prompt detected (attempt {})", passwordsSent + 1);
                sendLine(out, password);
                passwordsSent++;
                buffer.setLength(0);
                Thread.sleep(PROMPT_WAIT_MS);
                continue;
            }

            // Check for login failure
            if (text.contains("denied") || text.contains("incorrect") || text.contains("failed")) {
                logger.error("Login failed - credentials rejected");
                return false;
            }

            Thread.sleep(100);
        }

        logger.error("Login timeout - no prompt detected");
        return false;
    }

    /**
     * Reads command output from the stream.
     * Stops as soon as no more data is available (non-blocking).
     */
    private void readCommandOutput(InputStream in, StringBuilder output)
            throws IOException, InterruptedException {

        long lastReadTime = System.currentTimeMillis();
        int consecutiveEmptyReads = 0;
        int maxEmptyReads = 15; // Reduced from 30

        while (true) {
            if (in.available() > 0) {
                byte[] buffer = new byte[READ_BUFFER_SIZE];
                int bytesRead = in.read(buffer);
                if (bytesRead > 0) {
                    output.append(new String(buffer, 0, bytesRead));
                    lastReadTime = System.currentTimeMillis();
                    consecutiveEmptyReads = 0;
                }
            } else {
                // Check if we've waited long enough without data
                long timeSinceLastRead = System.currentTimeMillis() - lastReadTime;

                if (timeSinceLastRead > NO_DATA_TIMEOUT_MS) {
                    logger.debug("No data timeout reached, stopping read");
                    break;
                }

                consecutiveEmptyReads++;
                if (consecutiveEmptyReads > maxEmptyReads) {
                    logger.debug("Max empty reads reached, stopping");
                    break;
                }

                Thread.sleep(50); // Reduced from 100ms
            }
        }
    }

    /**
     * Cleans SSH output by removing command echo and prompts.
     */
    private String cleanOutput(String output, String command) {
        String[] lines = output.split("\r?\n");
        StringBuilder result = new StringBuilder();
        boolean capturing = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!capturing && trimmed.contains(command)) {
                capturing = true;
                continue;
            }

            if (!capturing) {
                continue;
            }

            // Stop at prompt
            if (trimmed.matches("^[a-zA-Z0-9_-]+[>#]$")) {
                break;
            }

            result.append(line).append("\n");
        }

        return result.toString().trim();
    }

    /**
     * Parses RSTP version from summary output.
     */
    private void parseRSTPVersion(String output, RSTPNode node) {
        Matcher matcher = PROTOCOL_PATTERN.matcher(output);
        if (matcher.find()) {
            String version = matcher.group(1);
            if ("RSTP".equalsIgnoreCase(version)) {
                node.setVersion(RSTPVersion.RSTP);
            } else if ("Compatible".equalsIgnoreCase(version)) {
                node.setVersion(RSTPVersion.STP);
            }
        }
    }

    /**
     * Parses RSTP version from detail output (for switches without a summary command).
     * Format: "Spanning tree enabled mode RSTP"
     */
    private void parseRSTPVersionFromDetail(String output, RSTPNode node) {
        if (output.contains("enabled mode RSTP")) {
            node.setVersion(RSTPVersion.RSTP);
            logger.debug("Detected RSTP from detail output");
        } else if (output.contains("enabled mode STP")) {
            node.setVersion(RSTPVersion.STP);
            logger.debug("Detected STP from detail output");
        } else if (output.contains("enabled mode MSTP")) {
            node.setVersion(RSTPVersion.MSTP);
            logger.debug("Detected MSTP from detail output");
        }
    }

    /**
     * Parses spanning tree detailed information.
     * Supports both Format 1 (IGS-6325) and Format 2.
     */
    private void parseSpanningTreeInfo(String output, RSTPNode node) {
        // Detect a format by checking for colon-based syntax
        boolean isFormat1 = output.contains("Bridge ID    :");

        if (isFormat1) {
            parseFormat1(output, node);
        } else {
            parseFormat2(output, node);
        }
    }

    /**
     * Parses Format 1 (IGS-6325): "Bridge ID    : 32768.A8-F7-E0-C5-10-5D"
     */
    private void parseFormat1(String output, RSTPNode node) {
        String[] lines = output.split("\n");

        // Parse bridge and root information
        for (String line : lines) {
            Matcher bridgeMatcher = BRIDGE_ID_PATTERN.matcher(line);
            if (bridgeMatcher.find()) {
                String priority = bridgeMatcher.group(1);
                String mac = bridgeMatcher.group(2).replace("-", ":");
                node.setBridgeId(priority + "." + mac);
            }

            Matcher rootMatcher = ROOT_ID_PATTERN.matcher(line);
            if (rootMatcher.find()) {
                String priority = rootMatcher.group(1);
                String mac = rootMatcher.group(2).replace("-", ":");
                node.setRootBridgeId(priority + "." + mac);
            }

            Matcher rootCostMatcher = ROOT_COST_PATTERN.matcher(line);
            if (rootCostMatcher.find()) {
                node.setRootCost(Integer.parseInt(rootCostMatcher.group(1)));
            }

            Matcher maxHopsMatcher = MAX_HOPS_PATTERN.matcher(line);
            if (maxHopsMatcher.find()) {
                node.setHopCount(Integer.parseInt(maxHopsMatcher.group(1)));
            }
        }

        // Parse port table
        List<RSTPPort> ports = new ArrayList<>();
        boolean inPortTable = false;

        for (String line : lines) {
            if (line.contains("Port") && line.contains("Role") && line.contains("State")) {
                inPortTable = true;
                continue;
            }

            if (inPortTable && line.contains("------")) {
                continue;
            }

            if (inPortTable && line.trim().isEmpty()) {
                break;
            }

            if (inPortTable) {
                RSTPPort port = parsePortLine(line);
                if (port != null) {
                    ports.add(port);
                }
            }
        }

        node.setPorts(ports);
    }

    /**
     * Parses multiline format
     */
    private void parseFormat2(String output, RSTPNode node) {
        String[] lines = output.split("\n");

        String bridgePriority = null;
        String bridgeAddress = null;
        String rootPriority = null;
        String rootAddress = null;
        boolean inBridgeSection = false;
        boolean inRootSection = false;

        // Parse bridge and root information (multiline)
        for (String line : lines) {
            // Bridge ID section
            if (line.contains("Bridge ID")) {
                inBridgeSection = true;
                inRootSection = false;
                Matcher prioMatcher = BRIDGE_PRIORITY_PATTERN.matcher(line);
                if (prioMatcher.find()) {
                    bridgePriority = prioMatcher.group(1);
                }
            }

            // Root ID section
            if (line.contains("Root ID")) {
                inRootSection = true;
                inBridgeSection = false;
                Matcher prioMatcher = ROOT_PRIORITY_PATTERN.matcher(line);
                if (prioMatcher.find()) {
                    rootPriority = prioMatcher.group(1);
                }
            }

            // Parse Address lines
            if (line.trim().startsWith("Address")) {
                Matcher addrMatcher = BRIDGE_ADDRESS_PATTERN.matcher(line);
                if (addrMatcher.find()) {
                    String addr = addrMatcher.group(1).replace("-", ":");
                    if (inBridgeSection) {
                        bridgeAddress = addr;
                    } else if (inRootSection) {
                        rootAddress = addr;
                    }
                }
            }

            // Parse Cost and Port (in a Root section)
            if (inRootSection) {
                Matcher costMatcher = ROOT_COST_ALT_PATTERN.matcher(line);
                if (costMatcher.find()) {
                    node.setRootCost(Integer.parseInt(costMatcher.group(1)));
                }
            }

            // Parse Max Age (hop count in Format 2 uses Max Age)
            if (line.contains("Max Age") && line.contains("sec")) {
                Matcher hopMatcher = Pattern.compile("Max Age\\s+(\\d+)").matcher(line);
                if (hopMatcher.find()) {
                    node.setHopCount(Integer.parseInt(hopMatcher.group(1)));
                }
            }
        }

        // Set Bridge ID and Root ID
        if (bridgePriority != null && bridgeAddress != null) {
            node.setBridgeId(bridgePriority + "." + bridgeAddress);
        }
        if (rootPriority != null && rootAddress != null) {
            node.setRootBridgeId(rootPriority + "." + rootAddress);
        }

        // Parse port table (Format 2)
        List<RSTPPort> ports = new ArrayList<>();
        boolean inPortTable = false;

        for (String line : lines) {
            if (line.contains("Name") && line.contains("State") && line.contains("Role")) {
                inPortTable = true;
                continue;
            }

            if (inPortTable && line.contains("------")) {
                continue;
            }

            if (inPortTable && line.trim().isEmpty()) {
                break;
            }

            if (inPortTable) {
                RSTPPort port = parsePortLineFormat2(line);
                if (port != null) {
                    ports.add(port);
                }
            }
        }

        node.setPorts(ports);
    }

    /**
     * Parses a single port line from the spanning tree output.
     */
    private RSTPPort parsePortLine(String line) {
        Matcher matcher = PORT_LINE_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String portName = matcher.group(1).trim();
            String role = matcher.group(2);
            String state = matcher.group(3);
            int pathCost = Integer.parseInt(matcher.group(5));

            // Extract port number from name (e.g., "Gi 1/2" -> 2)
            int portNumber = extractPortNumber(portName);

            RSTPPort port = new RSTPPort(portNumber);
            port.setPathCost(pathCost);
            port.setState(mapState(state));
            port.setRole(mapRole(role));

            return port;

        } catch (Exception e) {
            logger.warn("Failed to parse port line: {}", line, e);
            return null;
        }
    }

    /**
     * Parses a single port line from Format 2 output
     */
    private RSTPPort parsePortLineFormat2(String line) {
        Matcher matcher = PORT_LINE_ALT_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String portName = matcher.group(1).trim(); // "gi2"
            int pathCost = Integer.parseInt(matcher.group(4)); // "20000"
            String stateShort = matcher.group(5); // "Frw"/"Dis"
            String role = matcher.group(6); // "Root"/"Desg"

            // Extract port number from "gi2" -> 2
            int portNumber = Integer.parseInt(portName.replace("\\D", ""));

            RSTPPort port = new RSTPPort(portNumber);
            port.setPathCost(pathCost);
            port.setState(mapStateShort(stateShort));
            port.setRole(mapRoleShort(role));

            return port;

        } catch (Exception e) {
            logger.warn("Failed to parse Format 2 port line: {}", line, e);
            return null;
        }
    }

    /**
     * Extracts port number from the port name.
     */
    private int extractPortNumber(String portName) {
        // Extract from "Gi 1/2" or "Fa 0/1" format
        Pattern pattern = Pattern.compile("(\\d+)/(\\d+)");
        Matcher matcher = pattern.matcher(portName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return 0;
    }

    /**
     * Maps SSH state string to RSTPPortState enum.
     */
    private RSTPPortState mapState(String state) {
        return switch (state.toLowerCase()) {
            case "forwarding" -> RSTPPortState.FORWARDING;
            case "discarding" -> RSTPPortState.DISCARDING;
            case "learning" -> RSTPPortState.LEARNING;
            case "listening" -> RSTPPortState.LISTENING;
            case "blocking" -> RSTPPortState.BLOCKING;
            case "disabled" -> RSTPPortState.DISABLED;
            default -> RSTPPortState.UNKNOWN;
        };
    }

    /**
     * Maps SSH role string to RSTPPortRole enum.
     */
    private RSTPPortRole mapRole(String role) {
        return switch (role.toLowerCase()) {
            case "rootport" -> RSTPPortRole.ROOT;
            case "designatedport" -> RSTPPortRole.DESIGNATED;
            case "alternateport" -> RSTPPortRole.ALTERNATE;
            case "backupport" -> RSTPPortRole.BACKUP;
            case "disabled" -> RSTPPortRole.DISABLED;
            default -> RSTPPortRole.UNKNOWN;
        };
    }

    /**
     * Maps short state string (Format 2) to RSTPPortState enum.
     * "Frw" -> FORWARDING, "Dis" -> DISCARDING, etc.
     */
    private RSTPPortState mapStateShort(String stateShort) {
        return switch (stateShort.toLowerCase()) {
            case "frw" -> RSTPPortState.FORWARDING;
            case "dis" -> RSTPPortState.DISCARDING;
            case "lrn" -> RSTPPortState.LEARNING;
            case "lis" -> RSTPPortState.LISTENING;
            case "blk" -> RSTPPortState.BLOCKING;
            default -> RSTPPortState.UNKNOWN;
        };
    }

    /**
     * Maps a short role string (Format 2) to RSTPPortRole enum.
     * "Root" -> ROOT, "Desg" -> DESIGNATED, etc.
     */
    private RSTPPortRole mapRoleShort(String role) {
        return switch (role.toLowerCase()) {
            case "root" -> RSTPPortRole.ROOT;
            case "desg" -> RSTPPortRole.DESIGNATED;
            case "altn" -> RSTPPortRole.ALTERNATE;
            case "back" -> RSTPPortRole.BACKUP;
            default -> RSTPPortRole.UNKNOWN;
        };
    }

    /**
     * Reads available data from input stream.
     */
    private void readAvailableData(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[READ_BUFFER_SIZE];
            int bytesRead = in.read(tmp);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead));
            }
        }
    }

    /**
     * Sends a line followed by a newline.
     */
    private void sendLine(OutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes());
        out.flush();
    }
}