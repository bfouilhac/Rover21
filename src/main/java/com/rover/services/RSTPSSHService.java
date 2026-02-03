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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for retrieving RSTP information via SSH.
 * Manages persistent SSH connections per device for efficient command execution.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPSSHService {

    private static final Logger logger = LoggerFactory.getLogger(RSTPSSHService.class);

    private static final int INITIAL_WAIT_MS = 500;
    private static final int AFTER_COMMAND_WAIT_MS = 1250;
    private static final int PROMPT_WAIT_MS = 200;
    private static final int NO_DATA_TIMEOUT_MS = 1500;
    private static final int READ_BUFFER_SIZE = 8192;

    /**
     * Holds an SSH session and its associated shell channel for a device.
     */
    private record SSHConnection(Session session, ChannelShell channel) {
        public boolean isValid() {
            return session != null && session.isConnected()
                    && channel != null && channel.isConnected();
        }

        public void close() {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private final SSHConnectionManager connectionManager;

    // Regex patterns for parsing - Format 1 (IGS-6325)
    private static final Pattern BRIDGE_ID_PATTERN = Pattern.compile("Bridge ID\\s*:\\s*(\\d+)\\.(\\S+)");
    private static final Pattern ROOT_ID_PATTERN = Pattern.compile("Root ID\\s*:\\s*(\\d+)\\.(\\S+)");
    private static final Pattern ROOT_COST_PATTERN = Pattern.compile("Root PathCost:\\s*(\\d+)");
    private static final Pattern MAX_HOPS_PATTERN = Pattern.compile("Max Hops\\s*:\\s*(\\d+)");
    private static final Pattern TC_LAST_PATTERN = Pattern.compile("TC Last\\s*:\\s*(.+)");
    private static final Pattern PORT_LINE_PATTERN = Pattern.compile("^(\\S+\\s+\\d+/\\d+)\\s+(\\w+)\\s+(\\w+)\\s+(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+).*");

    // Regex patterns for Format 2 - multiline bridge/root info
    private static final Pattern BRIDGE_PRIORITY_PATTERN = Pattern.compile("Bridge ID\\s+Priority\\s+(\\d+)");
    private static final Pattern BRIDGE_ADDRESS_PATTERN = Pattern.compile("Address\\s+([a-f0-9:]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT_PRIORITY_PATTERN = Pattern.compile("Root ID\\s+Priority\\s+(\\d+)");
    private static final Pattern ROOT_COST_ALT_PATTERN = Pattern.compile("Cost\\s+(\\d+)");
    private static final Pattern TOPOLOGY_CHANGE_PATTERN = Pattern.compile("last change occurred\\s+(.+?)\\s+ago");
    private static final Pattern PORT_LINE_ALT_PATTERN = Pattern.compile("^\\s*(gi\\d+)\\s+(\\w+)\\s+(\\d+)\\.(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+).*");

    // Thread-safe map for persistent SSH connections (key = IP address)
    private final Map<String, SSHConnection> connections = new ConcurrentHashMap<>();

    // Lock map to ensure only one thread uses a specific connection at a time
    private final Map<String, Object> deviceLocks = new ConcurrentHashMap<>();

    public RSTPSSHService() {
        this.connectionManager = new SSHConnectionManager();
    }

    /**
     * Gets or creates a persistent SSH connection for a device.
     * Uses IP address as the unique key.
     *
     * @param device the device to connect to
     * @return a valid SSHConnection
     * @throws IOException if connection fails
     */
    private SSHConnection getOrCreateConnection(PlanetSwitchDevice device) throws IOException {
        String key = device.getIpAddress();

        SSHConnection existing = connections.get(key);
        if (existing != null && existing.isValid()) {
            logger.debug("Reusing existing SSH connection for {}", key);
            return existing;
        }

        // Clean up invalid connection if exists
        if (existing != null) {
            logger.debug("Closing invalid SSH connection for {}", key);
            existing.close();
            connections.remove(key);
        }

        try {
            Session session = connectionManager.createSession(
                    device.getIpAddress(),
                    PreferencesManager.getSshPort(),
                    device.getUsername(),
                    device.getPassword()
            );
            session.connect();
            logger.info("SSH session connected for {}", device.getIpAddress());

            ChannelShell channel = connectionManager.openShellChannel(session);
            if (!channel.isConnected()) {
                connectionManager.disconnect(channel, session);
                throw new IOException("SSH channel failed to connect for " + device.getIpAddress());
            }

            // Authenticate
            if (!connectionManager.handleSshAuthentication(channel, device.getIpAddress(),
                    device.getUsername(), device.getPassword())) {
                connectionManager.disconnect(channel, session);
                throw new IOException("SSH authentication failed for " + device.getIpAddress());
            }

            SSHConnection conn = new SSHConnection(session, channel);
            connections.put(key, conn);
            logger.info("Created new SSH connection for {}", device.getIpAddress());
            return conn;

        } catch (JSchException e) {
            throw new IOException("Failed to establish SSH connection to " + device.getIpAddress(), e);
        } catch (Exception e) {
            throw new IOException("SSH connection error for " + device.getIpAddress() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Closes the SSH connection for a specific device.
     *
     * @param ipAddress the IP address of the device
     */
    public void closeConnection(String ipAddress) {
        SSHConnection conn = connections.remove(ipAddress);
        if (conn != null) {
            conn.close();
            logger.debug("Closed SSH connection for {}", ipAddress);
        }
    }

    /**
     * Checks if RSTP/STP is enabled on the device.
     * Uses a persistent connection that will be reused for subsequent operations.
     *
     * @param device the device to check
     * @return true if RSTP/STP is enabled
     */
    public boolean isRSTPEnabled(PlanetSwitchDevice device) {
        if (!device.isSSHUp()) {
            logger.debug("Device {} SSH not available", device.getIpAddress());
            return false;
        }

        if (device.getUsername() == null || device.getPassword() == null) {
            logger.debug("Device {} has no SSH credentials", device.getIpAddress());
            return false;
        }

        try {
            SSHConnection conn = getOrCreateConnection(device);
            InputStream in = conn.channel().getInputStream();
            OutputStream out = conn.channel().getOutputStream();

            // Clear any pending data
            while (in.available() > 0) in.read();

            String output = executeCommandInSession(in, out, "show spanning-tree summary");

            if (output.toLowerCase().contains("unknown command")) {
                logger.debug("Device {} does not support summary command, trying full command", device.getIpAddress());
                output = executeCommandInSession(in, out, "show spanning-tree");
                boolean enabled = output.contains("Spanning tree enabled mode RSTP")
                        || output.contains("Spanning tree enabled mode STP")
                        || output.contains("Spanning tree enabled mode MSTP");
                logger.info("Device {} RSTP/STP enabled: {}", device.getIpAddress(), enabled);
                return enabled;
            }

            boolean enabled = output.contains("Protocol Version: RSTP")
                    || output.contains("Protocol Version: Compatible")
                    || output.contains("Protocol Version: STP");
            logger.info("Device {} RSTP/STP enabled: {}", device.getIpAddress(), enabled);
            return enabled;

        } catch (Exception e) {
            logger.error("Failed to check RSTP status for {}: {}", device.getIpAddress(), e.getMessage());
            closeConnection(device.getIpAddress());
            return false;
        }
        // NOTE: Connection kept open for subsequent operations (getRSTPInfo, getLLDPNeighborInfo)
    }

    /**
     * Retrieves RSTP information for a device.
     * Uses the persistent connection created by isRSTPEnabled() or creates a new one.
     *
     * @param device the device to query
     * @return RSTPNode with populated RSTP data
     * @throws IOException if SSH communication fails
     */
    public RSTPNode getRSTPInfo(PlanetSwitchDevice device) throws IOException {
        if (!device.isSSHUp() || device.getUsername() == null || device.getPassword() == null) {
            throw new IOException("SSH not available or credentials missing for " + device.getIpAddress());
        }

        logger.info("Getting RSTP info for {} ({})", device.getDeviceName(), device.getIpAddress());
        RSTPNode node = new RSTPNode(device);

        try {
            SSHConnection conn = getOrCreateConnection(device);
            InputStream in = conn.channel().getInputStream();
            OutputStream out = conn.channel().getOutputStream();

            // Clear any pending data
            while (in.available() > 0) in.read();

            Thread.sleep(INITIAL_WAIT_MS);

            sendLine(out, "terminal length 0");
            Thread.sleep(PROMPT_WAIT_MS);
            readAvailableData(in, new StringBuilder());

            logger.debug("Executing 'show spanning-tree' on {}", device.getIpAddress());
            String detailOutput = executeCommandInSession(in, out, "show spanning-tree");

            parseSpanningTreeInfo(detailOutput, node);

            logger.info("Successfully retrieved RSTP info for {}: version={}, cost={}, ports={}",
                    device.getDeviceName(), node.getVersion(), node.getRootCost(), node.getPorts().size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operation interrupted", e);
        } catch (IOException e) {
            closeConnection(device.getIpAddress());
            throw e;
        } catch (Exception e) {
            closeConnection(device.getIpAddress());
            throw new IOException("Failed to get RSTP info: " + e.getMessage(), e);
        }
        // NOTE: Connection kept open for LLDP discovery

        return node;
    }

    /**
     * Executes the LLDP discovery logic with fallback command support.
     * Thread-safety is guaranteed by internal locking on the device IP.
     *
     * @param device the device to query
     * @return LLDP neighbor information output
     * @throws IOException if SSH communication fails
     */
    public String getLLDPNeighborInfo(PlanetSwitchDevice device) throws IOException {
        String ip = device.getIpAddress();
        Object lock = deviceLocks.computeIfAbsent(ip, k -> new Object());

        synchronized (lock) {
            try {
                SSHConnection conn = getOrCreateConnection(device);
                InputStream in = conn.channel().getInputStream();
                OutputStream out = conn.channel().getOutputStream();

                // Clear any pending data
                while (in.available() > 0) in.read();

                String response = executeCommandInSession(in, out, "show lldp neighbor");

                // Fallback to plural form if the first one failed or returned nothing useful
                if (isInvalidResponse(response)) {
                    logger.debug("Falling back to 'show lldp neighbors' for {}", ip);
                    response = executeCommandInSession(in, out, "show lldp neighbors");
                }

                return response;

            } catch (Exception e) {
                closeConnection(ip);
                throw new IOException("LLDP communication error with " + ip + ": " + e.getMessage(), e);
            }
        }
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
     * Executes a MAC address table command and returns the output.
     *
     * @param device the device to query
     * @return the MAC address table output
     * @throws IOException if SSH fails
     */
    public String executeMacTableCommand(PlanetSwitchDevice device) throws IOException {
        String ip = device.getIpAddress();
        Object lock = deviceLocks.computeIfAbsent(ip, k -> new Object());

        synchronized (lock) {
            try {
                SSHConnection conn = getOrCreateConnection(device);
                InputStream in = conn.channel().getInputStream();
                OutputStream out = conn.channel().getOutputStream();

                // Clear any pending data
                while (in.available() > 0) in.read();

                return executeCommandInSession(in, out, "show mac address-table");

            } catch (Exception e) {
                closeConnection(ip);
                throw new IOException("MAC table command error for " + ip + ": " + e.getMessage(), e);
            }
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

            Matcher tcLastMatcher = TC_LAST_PATTERN.matcher(line);
            if (tcLastMatcher.find()) {
                String rawTime = tcLastMatcher.group(1).trim();
                String formattedTime = formatTopologyChangeTime(rawTime);
                node.setTopologyChangeTime(formattedTime);
                logger.debug("Format 1: TC Last set to {} (raw: {})", formattedTime, rawTime);
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

            // Parse topology change time
            Matcher tcMatcher = TOPOLOGY_CHANGE_PATTERN.matcher(line);
            if (tcMatcher.find()) {
                String rawTime = tcMatcher.group(1).trim();
                String formattedTime = formatTopologyChangeTime(rawTime);
                node.setTopologyChangeTime(formattedTime);
                logger.debug("Format 2: Topology Change Time set to {} (raw: {})", formattedTime, rawTime);
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
     * Formats a topology change time string to standardized format DD:HH:MM:SS.
     * <p>
     * Supports two input formats:
     * - Format 1: "0d 00:23:10" (days + time)
     * - Format 2: "00:23:18" (time only, assumes 0 days)
     * </p>
     *
     * @param tcTime the raw topology change time string
     * @return formatted time as "DD:HH:MM:SS", or the original string if parsing fails
     */
    private String formatTopologyChangeTime(String tcTime) {
        if (tcTime == null || tcTime.trim().isEmpty()) {
            return "00:00:00:00";
        }

        try {
            String trimmed = tcTime.trim();
            int days = 0;
            String timePart;

            // Format 1: "0d 00:23:10" or "4d 14:35:02"
            if (trimmed.matches("\\d+d\\s+.*")) {
                String[] parts = trimmed.split("d\\s+");
                days = Integer.parseInt(parts[0]);
                timePart = parts[1];
            }
            // Format 2: "00:23:18" (time only)
            else {
                timePart = trimmed;
            }

            // Parse time part (HH:MM:SS)
            String[] timeParts = timePart.split(":");
            if (timeParts.length >= 3) {
                int hours = Integer.parseInt(timeParts[0].trim());
                int minutes = Integer.parseInt(timeParts[1].trim());
                int seconds = Integer.parseInt(timeParts[2].trim());

                return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
            }

        } catch (Exception e) {
            logger.warn("Failed to parse topology change time '{}': {}", tcTime, e.getMessage());
        }

        // Return original if parsing fails
        return tcTime;
    }

    /**
     * Closes all active SSH connections and clears resources.
     * Should be called when the RSTP topology dialog is closed.
     */
    public void shutdown() {
        logger.info("Shutting down RSTPSSHService, closing {} active connections", connections.size());

        for (Map.Entry<String, SSHConnection> entry : connections.entrySet()) {
            try {
                SSHConnection conn = entry.getValue();
                if (conn != null) {
                    conn.close();
                    logger.debug("Closed SSH connection for device: {}", entry.getKey());
                }
            } catch (Exception e) {
                logger.error("Error closing SSH connection for device {}: {}", entry.getKey(), e.getMessage());
            }
        }

        connections.clear();
        deviceLocks.clear();
        logger.info("RSTPSSHService shutdown complete");
    }

    private boolean isInvalidResponse(String resp) {
        if (resp == null || resp.isBlank()) return true;
        String lower = resp.toLowerCase();
        return lower.contains("invalid") || lower.contains("incomplete") || lower.contains("unknown");
    }

}