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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int AFTER_COMMAND_WAIT_MS = 1250;
    private static final int PROMPT_WAIT_MS = 200;
    private static final int NO_DATA_TIMEOUT_MS = 1500;
    private static final int READ_BUFFER_SIZE = 8192;

    private final SSHConnectionManager sshManager;

    // Regex patterns for parsing - Format 1 (IGS-6325)
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
    private static final Pattern PORT_LINE_ALT_PATTERN = Pattern.compile("^\\s*(gi\\d+)\\s+(\\w+)\\s+(\\d+)\\.(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+).*");

    private Map<String, ChannelShell> channels = new HashMap<>();

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

        boolean enabled = false;
        try {

            Session session = sshManager.createSession(
                    device.getIpAddress(),
                    PreferencesManager.getSshPort(),
                    device.getUsername(),
                    device.getPassword()
            );
            session.connect();
            logger.info("SSH connected to: {}", device.getIpAddress());

            if (!session.isConnected()) {
                logger.error("SSH session failed to connect while checking RSTP status");
                return false;
            }

            ChannelShell channel = sshManager.openShellChannel(session);
            if (!channel.isConnected()) {
                logger.error("SSH channel failed to connect while checking RSTP status");
                return false;
            }
            channels.put(device.getMacAddress(), channel); // Saving this channel in our map

            if (sshManager.handleSshAuthentication(channel, device.getIpAddress(), device.getUsername(), device.getPassword())){
                logger.info("SSH authentication successful for {}", device.getIpAddress());
                InputStream in = channel.getInputStream();
                OutputStream out = channel.getOutputStream();
                String output = executeCommandInSession(in, out, "show spanning-tree summary");
                if (output.toLowerCase().contains("unknown command")){
                    logger.info("Device {} does not support the summary spanning-tree command", device.getIpAddress());
                    output = executeCommandInSession(in, out, "show spanning-tree");
                    enabled = output.contains("Spanning tree enabled mode RSTP");
                } else {
                    enabled = output.contains("Protocol Version: RSTP") || output.contains("Protocol Version: Compatible");
                }
            }
            logger.info("Device {} RSTP enabled ? {}", device.getIpAddress(), enabled);
            return enabled;
        } catch (Exception e) {
            logger.error("Failed to check RSTP status for {} - {}: {}", device.getIpAddress(), e.getClass().getSimpleName(), e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }

    }

    public RSTPNode getRSTPInfo(PlanetSwitchDevice device) throws IOException {
        if (!device.isSSHUp() || device.getUsername() == null || device.getPassword() == null) {
            throw new IOException("SSH not available or credentials missing for " + device.getIpAddress());
        }

        logger.info("Getting RSTP info for {} ({})", device.getDeviceName(), device.getIpAddress());
        RSTPNode node = new RSTPNode(device);

        Session session = null;
        ChannelShell channel = null;

        try {
            // Create a new SSH connection for this operation
            session = sshManager.createSession(
                    device.getIpAddress(),
                    PreferencesManager.getSshPort(),
                    device.getUsername(),
                    device.getPassword()
            );
            session.connect();
            logger.debug("SSH session connected for getRSTPInfo: {}", device.getIpAddress());

            if (!session.isConnected()) {
                logger.error("SSH session failed to connect during getRSTPInfo");
                return null;
            }

            channel = sshManager.openShellChannel(session);
            if (!channel.isConnected()) {
                logger.error("SSH channel failed to connect during getRSTPInfo");
                return null;
            }

            // Authenticate
            if (!sshManager.handleSshAuthentication(channel, device.getIpAddress(), device.getUsername(), device.getPassword())) {
                logger.error("SSH authentication failed during getRSTPInfo for {}", device.getIpAddress());
                return null;
            }

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            Thread.sleep(INITIAL_WAIT_MS); // 500ms

            sendLine(out, "terminal length 0");
            Thread.sleep(PROMPT_WAIT_MS); // 200ms
            readAvailableData(in, new StringBuilder()); // Clear the buffer by reading all data

            // Always execute 'show spanning-tree' for details
            logger.debug("Executing 'show spanning-tree' on {}", device.getIpAddress());
            String detailOutput = executeCommandInSession(in, out, "show spanning-tree");

            // Always parse details from 'show spanning-tree'
            parseSpanningTreeInfo(detailOutput, node);

            logger.info("Successfully retrieved RSTP info for {}: version={}, cost={}, ports={}", device.getDeviceName(), node.getVersion(), node.getRootCost(), node.getPorts().size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Operation interrupted for {}", device.getIpAddress());
            throw new IOException("Operation interrupted", e);
        } catch (Exception e) {
            logger.error("Failed to get RSTP info for {}: {}", device.getIpAddress(), e.getMessage());
            throw new IOException("Failed to get RSTP info", e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
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
        logger.info("Command '{}' executed, output length: {} chars", command, result.length());

        return result;
    }

    /**
     * Executes a command and returns the output.
     *
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
     * @param command the command to execute
     * @return command output
     * @throws IOException if SSH fails
     * @throws InterruptedException if interrupted
     */
    private String executeSshCommand(PlanetSwitchDevice device, String command) throws IOException, InterruptedException {

        ChannelShell channel = channels.get(device.getMacAddress());
        try {
            if (!channel.isConnected()) {
                throw new IOException("SSH channel failed to connect");
            }

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();

            Thread.sleep(INITIAL_WAIT_MS);

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
            logger.info("Command '{}' executed successfully, output length: {} chars",command, result.length());

            return result;

        } finally {
            channel.disconnect();
        }
    }

    /**
     * Reads command output from the stream.
     * Stops as soon as no more data is available (non-blocking).
     */
    private void readCommandOutput(InputStream in, StringBuilder output) throws IOException, InterruptedException {

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
     * Parses spanning tree detailed information.
     * Supports both Format 1 (IGS-6325) and Format 2.
     */
    private void parseSpanningTreeInfo(String output, RSTPNode node) {
        // Detect a format by checking for colon-based syntax
        boolean isFormat1 = output.contains("CIST Bridge STP Status");

        if (isFormat1) {
            logger.info("RSTP response format 1 detected");
            parseFormat1(output, node);
            node.setVersion(RSTPVersion.RSTP);
        } else {
            logger.info("RSTP response format 2 detected");
            parseFormat2(output, node);
            // Detect version from output
            if (output.contains("Spanning tree enabled mode RSTP")) {
                node.setVersion(RSTPVersion.RSTP);
            } else if (output.contains("Spanning tree enabled mode MSTP")) {
                node.setVersion(RSTPVersion.MSTP);
            } else if (output.contains("Spanning tree enabled mode STP")) {
                node.setVersion(RSTPVersion.STP);
            }
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
                logger.debug("Format 1: Bridge ID set to {}", priority + "." + mac);
            }

            Matcher rootMatcher = ROOT_ID_PATTERN.matcher(line);
            if (rootMatcher.find()) {
                String priority = rootMatcher.group(1);
                String mac = rootMatcher.group(2).replace("-", ":");
                node.setRootBridgeId(priority + "." + mac);
                logger.debug("Format 1: Root ID set to {}", priority + "." + mac);
            }

            Matcher rootCostMatcher = ROOT_COST_PATTERN.matcher(line);
            if (rootCostMatcher.find()) {
                node.setRootCost(Integer.parseInt(rootCostMatcher.group(1)));
                logger.debug("Format 1: Root Cost set to {}", rootCostMatcher.group(1));
            }

            Matcher maxHopsMatcher = MAX_HOPS_PATTERN.matcher(line);
            if (maxHopsMatcher.find()) {
                node.setHopCount(Integer.parseInt(maxHopsMatcher.group(1)));
                logger.debug("Format 1: Hop Count set to {}", maxHopsMatcher.group(1));
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
                    logger.debug("Format 2: Root Cost set to {}", costMatcher.group(1));
                }
            }

            // Parse Max Age (hop count in Format 2 uses Max Age)
            if (line.contains("Max Age") && line.contains("sec")) {
                Matcher hopMatcher = Pattern.compile("Max Age\\s+(\\d+)").matcher(line);
                if (hopMatcher.find()) {
                    node.setHopCount(Integer.parseInt(hopMatcher.group(1)));
                    logger.debug("Format 2: Hop Count set to {}", hopMatcher.group(1));
                }
            }
        }

        // Set Bridge ID and Root ID
        if (bridgePriority != null && bridgeAddress != null) {
            node.setBridgeId(bridgePriority + "." + bridgeAddress);
            logger.debug("Format 2: Bridge ID set to {}", bridgePriority + "." + bridgeAddress);
        }
        if (rootPriority != null && rootAddress != null) {
            node.setRootBridgeId(rootPriority + "." + rootAddress);
            logger.debug("Format 2: Root ID set to {}", rootPriority + "." + rootAddress);
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
     * Format: "     gi1  enabled   128.1     20000   Frw   Desg       No P2P (RSTP)"
     */
    private RSTPPort parsePortLineFormat2(String line) {
        Matcher matcher = PORT_LINE_ALT_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String portName = matcher.group(1).trim(); // "gi1"
            String state = matcher.group(2); // "enabled"/"disabled"
            String priority = matcher.group(3); // "128"
            String portNum = matcher.group(4); // "1"
            int pathCost = Integer.parseInt(matcher.group(5)); // "20000"
            String stateShort = matcher.group(6); // "Frw"/"Dis"
            String role = matcher.group(7); // "Root"/"Desg"

            // Use the port number from priority.number format
            int portNumber = Integer.parseInt(portNum);

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
     *
     * @param in the input stream to read from
     * @param buffer the buffer to append data to
     * @return true if data was read, false otherwise
     * @throws IOException if I/O errors occur
     */
    private boolean readAvailableData(InputStream in, StringBuilder buffer) throws IOException {
        if (in.available() > 0) {
            byte[] tmp = new byte[READ_BUFFER_SIZE];
            int bytesRead = in.read(tmp);
            if (bytesRead > 0) {
                buffer.append(new String(tmp, 0, bytesRead));
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a line followed by a newline.
     */
    private void sendLine(OutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes());
        out.flush();
    }

    /**
     * Closes all active SSH channels and clears the channel map.
     * Should be called when the RSTP topology dialog is closed.
     */
    public void shutdown() {
        logger.info("Shutting down RSTPSSHService, closing {} active channels", channels.size());

        for (Map.Entry<String, ChannelShell> entry : channels.entrySet()) {
            try {
                ChannelShell channel = entry.getValue();
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                    logger.debug("Closed SSH channel for device: {}", entry.getKey());
                }
            } catch (Exception e) {
                logger.error("Error closing SSH channel for device {}: {}", entry.getKey(), e.getMessage());
            }
        }

        channels.clear();
        logger.info("RSTPSSHService shutdown complete");
    }
}