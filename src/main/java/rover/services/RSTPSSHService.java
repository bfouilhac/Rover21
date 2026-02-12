package rover.services;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import rover.model.PlanetSwitchDevice;
import rover.utils.PreferencesManager;
import rover.utils.SSHConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rover.model.rstp.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import rover.model.rstp.MacAddressEntry;

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

    // Supports gi1, fa1, ge1, te1, etc. and also formats like "Gi 1/0/1"
    private static final Pattern PORT_LINE_ALT_PATTERN = Pattern.compile("^\\s*([A-Za-z]++\\d++(?:/\\d++)*+)\\s+(\\w+)\\s+(\\d+)\\.(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+)\\s+.*$");

    // MAC Address Table patterns - Format 1: "Dynamic 1    08:8f:c3:48:64:f5 GigabitEthernet 1/15"
    private static final Pattern MAC_TABLE_FORMAT1_PATTERN = Pattern.compile(
            "^(Dynamic|Static)\\s+(\\d+)\\s+([0-9a-fA-F:]+)\\s+(?:GigabitEthernet|2\\.5GigabitEthernet|FastEthernet|TenGigabitEthernet)\\s+(\\d+)/(\\d+).*$",
            Pattern.CASE_INSENSITIVE);

    // MAC Address Table patterns - Format 2: "    1 | A8:F7:E0:DE:4E:BE |    Management     | CPU"
    private static final Pattern MAC_TABLE_FORMAT2_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\s*\\|\\s*([0-9a-fA-F:]+)\\s*\\|\\s*(\\w+)\\s*\\|\\s*(\\S+).*$",
            Pattern.CASE_INSENSITIVE);

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
     * @throws IOException if the connection fails
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
     * Uses a persistent connection that will be reused for later operations.
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
        // NOTE: Connection kept open for later operations (getRSTPInfo, getLLDPNeighborInfo)
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
     */
    public String getLLDPNeighborInfo(PlanetSwitchDevice device){
        String ip = device.getIpAddress();
        Object lock = deviceLocks.computeIfAbsent(ip, _ -> new Object());

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
                logger.error("LLDP communication error with {}: {}", ip, e.getMessage());
                closeConnection(ip);
                return null;
            }
        }
    }

    /**
     * Executes a command in an already established SSH session.
     * Handles paginated output by detecting "--More--" and sending newlines to continue.
     */
    private String executeCommandInSession(InputStream in, OutputStream out, String command) throws IOException, InterruptedException {

        sendLine(out, command);
        Thread.sleep(AFTER_COMMAND_WAIT_MS);

        StringBuilder output = new StringBuilder();
        readCommandOutputWithPagination(in, out, output);

        String result = cleanOutput(output.toString(), command);
        logger.info("Command '{}' executed, output length: {} chars", command, result.length());

        return result;
    }

    /**
     * Reads command output with pagination support.
     * Detects "--More--" prompts and sends newlines to retrieve the full output.
     */
    private void readCommandOutputWithPagination(InputStream in, OutputStream out, StringBuilder output) throws IOException, InterruptedException {

        long lastReadTime = System.currentTimeMillis();
        int consecutiveEmptyReads = 0;
        int maxEmptyReads = 15;

        while (true) {
            if (in.available() > 0) {
                byte[] buffer = new byte[READ_BUFFER_SIZE];
                int bytesRead = in.read(buffer);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead);
                    output.append(chunk);
                    lastReadTime = System.currentTimeMillis();
                    consecutiveEmptyReads = 0;

                    // Check for pagination prompt and send a newline to continue
                    if (chunk.contains("--More--")) {
                        logger.debug("Detected --More-- prompt, sending newline to continue");
                        sendLine(out, "");
                        Thread.sleep(PROMPT_WAIT_MS);
                    }
                }
            } else {
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

                Thread.sleep(50);
            }
        }
    }

    /**
     * Cleans SSH output by removing command echo, prompts, and pagination markers.
     */
    private String cleanOutput(String output, String command) {
        // Remove --More-- pagination markers and associated escape sequences
        String cleaned = output.replace("--More--", "")
                .replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "") // Remove ANSI escape codes
                .replaceAll("\\x08+\\s*\\x08*", ""); // Remove backspace sequences

        String[] lines = cleaned.split("\r?\n");
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

            // Skip empty lines that may result from pagination cleanup
            if (trimmed.isEmpty() && result.isEmpty() && result.toString().endsWith("\n\n")) {
                continue;
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
            // Detect a version from output
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
                logger.debug("Format 1: Bridge ID set to {}", mac);
            }

            Matcher rootMatcher = ROOT_ID_PATTERN.matcher(line);
            if (rootMatcher.find()) {
                String priority = rootMatcher.group(1);
                String mac = rootMatcher.group(2).replace("-", ":");
                node.setRootBridgeId(priority + "." + mac);
                logger.debug("Format 1: Root ID set to {}", mac);
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

            // Extract the port number from the name (e.g., "Gi 1/2" -> 2)
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
     * Parses a single port line from Format 2 output.
     * Format: "gi1 enabled 128.1 20000 Frw Desg No P2P (RSTP)"
     *         "gi1 enabled 128.1 20000 Blk Altn No P2P (RSTP)"
     */
    private RSTPPort parsePortLineFormat2(String line) {
        Matcher matcher = PORT_LINE_ALT_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            logger.info("Failed to parse port line: {}", line);
            return null;
        }

        try {
            String portName = matcher.group(1); // "gi1"
            String portNum = matcher.group(4); // "1" from "128.1"
            int pathCost = Integer.parseInt(matcher.group(5)); // "20000"
            String stateShort = matcher.group(6); // "Frw"/"Blk"
            String role = matcher.group(7); // "Root"/"Desg"/"Altn"

            int portNumber = Integer.parseInt(portNum);

            RSTPPort port = new RSTPPort(portNumber);
            port.setPathCost(pathCost);
            port.setState(mapStateShort(stateShort));
            port.setRole(mapRoleShort(role));

            logger.info("Parsed port {}: number={}, state={}, role={}", portName, portNumber, port.getState(), port.getRole());
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
     * - Format 1: "0d 00:23:10" (days and time)
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

                // Convert excess hours to days (e.g., 65:00:00 -> 2d 17:00:00)
                if (hours >= 24) {
                    days += hours / 24;
                    hours = hours % 24;
                }

                return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
            }

        } catch (Exception e) {
            logger.warn("Failed to parse topology change time '{}': {}", tcTime, e.getMessage());
        }

        // Return original if parsing fails
        return tcTime;
    }

    /**
     * Retrieves the MAC address table from a device.
     * Thread-safety is guaranteed by internal locking on the device IP.
     *
     * @param device the device to query
     * @return list of MAC address entries
     */
    public List<MacAddressEntry> getMacAddressTable(PlanetSwitchDevice device) {
        String ip = device.getIpAddress();

        if (device.getUsername() == null || device.getPassword() == null) {
            logger.warn("No SSH credentials for {} - skipping MAC table query", ip);
            return new ArrayList<>();
        }

        Object lock = deviceLocks.computeIfAbsent(ip, _ -> new Object());

        synchronized (lock) {
            try {
                SSHConnection conn = getOrCreateConnection(device);

                if (conn == null || !conn.isValid()) {
                    logger.warn("Invalid SSH connection for {} - skipping MAC table query", ip);
                    return new ArrayList<>();
                }

                InputStream in = conn.channel().getInputStream();
                OutputStream out = conn.channel().getOutputStream();

                if (in == null || out == null) {
                    logger.warn("Invalid SSH streams for {} - skipping MAC table query", ip);
                    closeConnection(ip);
                    return new ArrayList<>();
                }

                while (in.available() > 0) in.read();

                String response = executeCommandInSession(in, out, "show mac address-table");

                if (isInvalidResponse(response)) {
                    logger.debug("Falling back to 'show mac-address-table' for {}", ip);
                    response = executeCommandInSession(in, out, "show mac-address-table");
                }

                return parseMacAddressTable(response);

            } catch (IOException e) {
                logger.error("MAC table I/O error for {}: {}", ip, e.getMessage());
                closeConnection(ip);
                return new ArrayList<>();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("MAC table query interrupted for {}", ip);
                return new ArrayList<>();
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                logger.error("MAC table query failed for {}: {} - {}", ip, e.getClass().getSimpleName(), errorMsg);
                closeConnection(ip);
                return new ArrayList<>();
            }
        }
    }

    /**
     * Parses the MAC address table output.
     * Supports both Format 1 (space-separated) and Format 2 (pipe-separated).
     *
     * @param output the command output
     * @return list of parsed MAC address entries
     */
    private List<MacAddressEntry> parseMacAddressTable(String output) {
        List<MacAddressEntry> entries = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return entries;
        }

        boolean isFormat2 = output.contains("|");
        String[] lines = output.split("\n");

        for (String line : lines) {
            MacAddressEntry entry = isFormat2
                    ? parseMacEntryFormat2(line)
                    : parseMacEntryFormat1(line);

            if (entry != null) {
                entries.add(entry);
                logger.debug("Parsed MAC entry: {}", entry);
            }
        }

        logger.info("Parsed {} MAC address entries", entries.size());
        return entries;
    }

    /**
     * Parses a MAC address entry from Format 1 (space-separated).
     * Example: "Dynamic 1    08:8f:c3:48:64:f5 GigabitEthernet 1/15"
     *
     * @param line the line to parse
     * @return parsed entry or null if not a valid entry
     */
    private MacAddressEntry parseMacEntryFormat1(String line) {
        Matcher matcher = MAC_TABLE_FORMAT1_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String type = matcher.group(1);
            int vlanId = Integer.parseInt(matcher.group(2));
            String mac = normalizeMacAddress(matcher.group(3));
            int port = Integer.parseInt(matcher.group(5));

            return new MacAddressEntry(type, vlanId, mac, port);
        } catch (Exception e) {
            logger.warn("Failed to parse MAC entry Format 1: {}", line);
            return null;
        }
    }

    /**
     * Parses a MAC address entry from Format 2 (pipe-separated).
     * Example: "    1 | A8:F7:E0:DE:4E:BE |      Dynamic      | gi2"
     *
     * @param line the line to parse
     * @return parsed entry or null if not a valid entry
     */
    private MacAddressEntry parseMacEntryFormat2(String line) {
        Matcher matcher = MAC_TABLE_FORMAT2_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            int vlanId = Integer.parseInt(matcher.group(1));
            String mac = normalizeMacAddress(matcher.group(2));
            String type = matcher.group(3).trim();
            String portStr = matcher.group(4).trim();

            // Skip CPU/management entries
            if ("cpu".equalsIgnoreCase(portStr)) {
                return null;
            }

            // Extract port number from "gi2", "fa1", etc.
            int port = extractPortFromString(portStr);
            if (port <= 0) {
                return null;
            }

            return new MacAddressEntry(type, vlanId, mac, port);
        } catch (Exception e) {
            logger.warn("Failed to parse MAC entry Format 2: {}", line);
            return null;
        }
    }

    /**
     * Extracts port number from a port string like "gi2", "fa1", "ge1/0/5".
     *
     * @param portStr the port string
     * @return the port number, or 0 if unable to extract
     */
    private int extractPortFromString(String portStr) {
        if (portStr == null || portStr.isEmpty()) return 0;

        Matcher matcher = Pattern.compile("(\\d+)$").matcher(portStr);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * Normalizes a MAC address to uppercase with colons.
     *
     * @param mac the MAC address to normalize
     * @return normalized MAC address
     */
    private String normalizeMacAddress(String mac) {
        if (mac == null) return "";
        return mac.toUpperCase().replace("-", ":");
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