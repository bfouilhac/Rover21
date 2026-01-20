package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for discovering PLANET network switches using UDP broadcast protocol.
 * <p>
 * This service implements the proprietary PLANET discovery protocol, which uses
 * a specific magic packet broadcasted over UDP to detect PLANET switches on the
 * local network. The switches respond with their configuration information, including
 * MAC address, IP settings, firmware version, and device details.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PlanetSwitchDevice
 * @see SNMPService
 * @see ProtocolTestService
 */
public class PlanetDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PlanetDiscoveryService.class);

    private static final int MAX_PACKET_SIZE = 1024;
    private static final int MIN_PACKET_LENGTH = 32;

    private static final String PLANET_MAGIC_HEADER = "PLANETut";

    private static final int OFFSET_MAC_ADDRESS = 10;
    private static final int OFFSET_IP_ADDRESS = 20;
    private static final int OFFSET_NETMASK = 24;
    private static final int OFFSET_GATEWAY = 28;
    private static final int OFFSET_DEVICE_INFO = 32;

    private static final String BROADCAST_ADDRESS = "255.255.255.255";

    private static final long THREAD_KEEP_ALIVE_SECONDS = 60L;

    private static final byte[] DISCOVERY_PACKET = { // Magic discovery packet for PLANET switches.
            0x50, 0x4c, 0x41, 0x4e, 0x45, 0x54, 0x75, 0x74, // "PLANETut"
            0x00, 0x16,                                       // packet type
            (byte) 0xff, (byte) 0xff, (byte) 0xff,           // target MAC (broadcast)
            (byte) 0xff, (byte) 0xff, (byte) 0xff
    };

    private final LanguageManager lang = LanguageManager.getInstance();

    /**
     * Discovers PLANET switches on the network using UDP broadcast.
     * <p>
     *
     * @param localAddress the IPv4 address of the network interface to use for discovery
     * @param callback the callback to notify when devices are discovered or enriched
     * @return a list of discovered PlanetSwitchDevice objects (maybe empty if no devices found)
     * @throws NullPointerException if callback is null
     * @see DiscoveryCallback
     * @see PreferencesManager#getDiscoveryTimeout()
     */
    public List<PlanetSwitchDevice> discoverDevices(String localAddress, DiscoveryCallback callback) {
        int socketTimeout = PreferencesManager.getDiscoveryTimeout() * 1000;
        int responsePort = PreferencesManager.getDiscoveryListenPort();
        int discoveryPort = PreferencesManager.getDiscoverySendPort();

        logger.info("Starting PLANET device discovery on interface: {}", localAddress);

        List<PlanetSwitchDevice> devices = new ArrayList<>();
        Set<String> discoveredMacs = new HashSet<>();
        Set<String> discoveredIps = new HashSet<>();

        ExecutorService protocolExecutor = createAdaptiveExecutor("Protocol");
        ExecutorService snmpExecutor = createAdaptiveExecutor("SNMP");

        DatagramSocket sendSocket = null;
        DatagramSocket receiveSocket = null;

        try {
            receiveSocket = createReceiveSocket(responsePort, socketTimeout);
            if (receiveSocket == null) {
                return devices;
            }

            sendSocket = createSendSocket(localAddress, discoveryPort);
            sendDiscoveryPacket(sendSocket, discoveryPort);

            long startTime = System.currentTimeMillis();
            logger.info("Listening for responses on port {} for {} ms", responsePort, socketTimeout);

            listenForResponses(receiveSocket, socketTimeout, startTime, devices, discoveredMacs,
                    discoveredIps, callback, snmpExecutor, protocolExecutor);

            logger.info("Discovery complete. Found {} PLANET device(s)", devices.size());

        } catch (IOException e) {
            logger.error("Error during device discovery", e);
        } finally {
            closeSocket(sendSocket, "Send socket");
            closeSocket(receiveSocket, "Receive socket");
        }

        return devices;
    }

    /**
     * Creates an adaptive thread pool executor.
     * <p>
     * The pool size adapts to the available processors, with a minimum equal to
     * the processor count and a maximum of 3 times that value.
     * </p>
     *
     * @param name the name prefix for threads in this pool
     * @return a configured ExecutorService
     */
    private ExecutorService createAdaptiveExecutor(String name) {
        int minThreads = Runtime.getRuntime().availableProcessors();
        int maxThreads = minThreads * 3;

        logger.info("Creating {} executor: min={}, max={}", name, minThreads, maxThreads);

        return new ThreadPoolExecutor(
                minThreads,
                maxThreads,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                createThreadFactory(name)
        );
    }

    /**
     * Creates a thread factory with custom naming.
     *
     * @param namePrefix the prefix for thread names
     * @return a ThreadFactory that creates named daemon threads
     */
    private ThreadFactory createThreadFactory(String namePrefix) {
        return new ThreadFactory() {
            private int counter = 0;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName(namePrefix + "-" + (++counter));
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /**
     * Creates and configures the receiver socket for listening to discovery responses.
     * <p>
     * This method binds the socket to the specified port and configures it for
     * broadcast reception with the appropriate timeout.
     * </p>
     *
     * @param port the port to bind to
     * @param timeout the socket timeout in milliseconds
     * @return the configured DatagramSocket, or null if binding fails
     */
    private DatagramSocket createReceiveSocket(int port, int timeout) {
        try {
            DatagramSocket socket = new DatagramSocket(port);
            socket.setSoTimeout(timeout);
            socket.setBroadcast(true);
            logger.info("Receive socket bound to port: {}", port);
            return socket;
        } catch (SocketException e) {
            logger.error("Could not bind to port {}: {}", port, e.getMessage());
            return null;
        }
    }

    /**
     * Creates and configures the sending socket for broadcasting discovery packets.
     * <p>
     * This method binds the socket to a specific network interface to ensure
     * the broadcast is sent on the correct network.
     * </p>
     *
     * @param localAddress the local interface IP address
     * @param port the port to bind to
     * @return the configured DatagramSocket
     * @throws SocketException if socket creation fails
     */
    private DatagramSocket createSendSocket(String localAddress, int port) throws SocketException {
        try {
            InetAddress address = InetAddress.getByName(localAddress);
            DatagramSocket socket = new DatagramSocket(new InetSocketAddress(address, port));
            socket.setBroadcast(true);
            logger.info("Send socket created on port: {}", socket.getLocalPort());
            return socket;
        } catch (UnknownHostException e) {
            throw new SocketException("Invalid local address: " + localAddress);
        }
    }

    /**
     * Closes a DatagramSocket safely and logs the operation.
     *
     * @param socket the socket to close (maybe null)
     * @param socketName the name of the socket for logging
     */
    private void closeSocket(DatagramSocket socket, String socketName) {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("{} closed", socketName);
        }
    }

    /**
     * Sends the magic discovery packet via UDP broadcast.
     * <p>
     * The packet is broadcasted to 255.255.255.255 on the configured discovery port,
     * allowing all PLANET switches on the local network to receive and respond to it.
     * </p>
     *
     * @param socket the DatagramSocket to use for sending
     * @param discoveryPort the UDP port to send the packet to (default: 62630)
     * @throws IOException if the packet cannot be sent
     */
    private void sendDiscoveryPacket(DatagramSocket socket, int discoveryPort) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                DISCOVERY_PACKET,
                DISCOVERY_PACKET.length,
                InetAddress.getByName(BROADCAST_ADDRESS),
                discoveryPort
        );
        socket.send(packet);
        logger.debug("Discovery packet sent to broadcast");
    }

    /**
     * Listens for response packets and processes discovered devices.
     *
     * @param receiveSocket the socket to receive packets on
     * @param socketTimeout the total timeout in milliseconds
     * @param startTime the start time of the discovery
     * @param devices the list to add discovered devices to
     * @param discoveredMacs set of already discovered MAC addresses
     * @param discoveredIps set of already discovered IP addresses
     * @param callback the callback for device notifications
     * @param snmpExecutor executor for SNMP enrichment tasks
     * @param protocolExecutor executor for protocol test tasks
     */
    private void listenForResponses(DatagramSocket receiveSocket, int socketTimeout, long startTime,
                                    List<PlanetSwitchDevice> devices, Set<String> discoveredMacs,
                                    Set<String> discoveredIps, DiscoveryCallback callback,
                                    ExecutorService snmpExecutor, ExecutorService protocolExecutor) {

        while (System.currentTimeMillis() - startTime < socketTimeout) {
            try {
                byte[] buffer = new byte[MAX_PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);

                processReceivedPacket(packet, devices, discoveredMacs, discoveredIps, callback, snmpExecutor, protocolExecutor);

            } catch (SocketTimeoutException e) {
                break; // timeout reached, exit loop
            } catch (IOException e) {
                logger.warn("Error receiving packet: {}", e.getMessage());
            }
        }
    }

    /**
     * Processes a received UDP packet and handles device discovery.
     *
     * @param packet the received packet
     * @param devices the list to add the device to
     * @param discoveredMacs set of already discovered MAC addresses
     * @param discoveredIps set of already discovered IP addresses
     * @param callback the callback for device notifications
     * @param snmpExecutor executor for SNMP enrichment tasks
     * @param protocolExecutor executor for protocol test tasks
     */
    private void processReceivedPacket(DatagramPacket packet, List<PlanetSwitchDevice> devices,
                                       Set<String> discoveredMacs, Set<String> discoveredIps,
                                       DiscoveryCallback callback, ExecutorService snmpExecutor,
                                       ExecutorService protocolExecutor) {

        PlanetSwitchDevice device = parseResponse(packet);

        if (device != null && !discoveredMacs.contains(device.getMacAddress())) {
            devices.add(device);
            discoveredMacs.add(device.getMacAddress());
            logger.info("Discovered PLANET device: {} at {}", device.getDeviceName(), device.getIpAddress());

            Objects.requireNonNull(callback, "Callback cannot be null").onDeviceDiscovered(device);

            if (PreferencesManager.getAutoEnrichSnmp()) {
                handleSnmpEnrichment(device, discoveredIps, callback, snmpExecutor, protocolExecutor);
            }
        }
    }

    /**
     * Handles SNMP enrichment for a discovered device.
     *
     * @param device the device to enrich
     * @param discoveredIps set of already discovered IP addresses
     * @param callback the callback for device notifications
     * @param snmpExecutor executor for SNMP tasks
     * @param protocolExecutor executor for protocol tests
     */
    private void handleSnmpEnrichment(PlanetSwitchDevice device, Set<String> discoveredIps,
                                      DiscoveryCallback callback, ExecutorService snmpExecutor,
                                      ExecutorService protocolExecutor) {

        if (!discoveredIps.contains(device.getIpAddress())) {
            discoveredIps.add(device.getIpAddress());
            enrichDeviceAsync(device, callback, snmpExecutor, protocolExecutor);
        } else {
            device.setSystemLocation(lang.get("error.snmp_duplicate"));
            device.setSystemUptime(lang.get("error.snmp_duplicate"));
        }
    }

    /**
     * Asynchronously enriches a device with SNMP data and tests protocols.
     *
     * @param device the device to enrich
     * @param callback the callback for notifications
     * @param snmpExecutor executor for SNMP tasks
     * @param protocolExecutor executor for protocol tests
     */
    private void enrichDeviceAsync(PlanetSwitchDevice device, DiscoveryCallback callback,
                                   ExecutorService snmpExecutor, ExecutorService protocolExecutor) {

        submitProtocolTests(device, callback, protocolExecutor);
        submitSnmpEnrichment(device, callback, snmpExecutor);
    }

    /**
     * Submits protocol tests for a device.
     *
     * @param device the device to test
     * @param callback the callback for notifications
     * @param executor the executor to run tests on
     */
    private void submitProtocolTests(PlanetSwitchDevice device, DiscoveryCallback callback,
                                     ExecutorService executor) {
        executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            ProtocolTestService protocolService = new ProtocolTestService();

            try {
                logger.info("Protocol tests starting for {}", device.getIpAddress());

                ProtocolTestService.ProtocolTestResult result = protocolService.testAllProtocols(device.getIpAddress(), device.getMacAddress());

                long elapsed = System.currentTimeMillis() - startTime;
                logger.info("Protocol tests for {} completed in {}ms", device.getIpAddress(), elapsed);

                if (callback != null) {
                    callback.onProtocolsTestedCallback(device, result);
                }

            } catch (Exception e) {
                logger.error("Failed to test protocols for {}: {}", device.getIpAddress(), e.getMessage());
            }
        });
    }

    /**
     * Submits SNMP enrichment for a device.
     *
     * @param device the device to enrich
     * @param callback the callback for notifications
     * @param executor the executor to run enrichment on
     */
    private void submitSnmpEnrichment(PlanetSwitchDevice device, DiscoveryCallback callback,
                                      ExecutorService executor) {
        executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            SNMPService snmpService = null;
            boolean enriched = false;

            try {
                snmpService = new SNMPService();

                if (snmpService.testSNMPConnectivity(device.getIpAddress())) {
                    snmpService.enrichDeviceWithSNMP(device);
                    enriched = true;

                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.info("SNMP enrichment for {} completed in {}ms", device.getIpAddress(), elapsed);
                } else {
                    logger.warn("SNMP not accessible for {}", device.getIpAddress());
                    device.setSystemLocation("SNMP not available");
                    device.setSystemUptime("N/A");
                }

            } catch (Exception e) {
                logger.error("Failed to enrich {} with SNMP: {}", device.getIpAddress(), e.getMessage());
                device.setSystemLocation("Error");
                device.setSystemUptime("Error");

            } finally {
                if (snmpService != null) {
                    snmpService.close();
                }

                handleSnmpEnrichmentComplete(device, enriched, callback);
            }
        });
    }

    /**
     * Handles completion of SNMP enrichment.
     *
     * @param device the enriched device
     * @param enriched true if enrichment succeeded
     * @param callback the callback to notify
     */
    private void handleSnmpEnrichmentComplete(PlanetSwitchDevice device, boolean enriched,
                                              DiscoveryCallback callback) {
        if (!enriched) {
            if (device.getSystemLocation() == null || device.getSystemLocation().equals("Loading...")) {
                device.setSystemLocation("Timeout");
            }
            if (device.getSystemUptime() == null || device.getSystemUptime().equals("Loading...")) {
                device.setSystemUptime("Timeout");
            }
        }

        if (callback != null) {
            callback.onDeviceEnriched(device);
        }
    }

    /**
     * Parses a UDP response packet from a PLANET switch.
     * <p>
     * This method extracts device information from the binary response packet,
     * including MAC address, IP configuration, and device details.
     * </p>
     *
     * @param packet the DatagramPacket received from a switch
     * @return a PlanetSwitchDevice object with parsed information, or null if parsing fails
     */
    private PlanetSwitchDevice parseResponse(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            logger.debug("Parsing response packet: {} bytes from {}", length, packet.getAddress().getHostAddress());

            if (length < MIN_PACKET_LENGTH) {
                logger.warn("Response packet too short: {} bytes", length);
                return null;
            }

            if (!isValidPlanetPacket(data)) {
                logger.warn("Invalid PLANET packet header");
                return null;
            }

            return buildDeviceFromPacket(data, length);

        } catch (Exception e) {
            logger.warn("Error parsing device response", e);
            return null;
        }
    }

    /**
     * Validates if a packet has the correct PLANET magic header.
     *
     * @param data the packet data
     * @return true if the header is valid, false otherwise
     */
    private boolean isValidPlanetPacket(byte[] data) {
        if (data.length < 8) {
            return false;
        }
        String header = new String(data, 0, 8);
        return header.equals(PLANET_MAGIC_HEADER);
    }

    /**
     * Builds a PlanetSwitchDevice from parsed packet data.
     *
     * @param data the packet data
     * @param length the packet length
     * @return a configured PlanetSwitchDevice
     */
    private PlanetSwitchDevice buildDeviceFromPacket(byte[] data, int length) {
        PlanetSwitchDevice device = new PlanetSwitchDevice();

        String macAddress = extractMacAddress(data);
        device.setMacAddress(macAddress);
        logger.debug("MAC Address: {}", macAddress);

        String ipAddress = extractIpAddress(data, OFFSET_IP_ADDRESS);
        device.setIpAddress(ipAddress);
        logger.debug("IP Address: {}", ipAddress);

        String netmask = extractIpAddress(data, OFFSET_NETMASK);
        device.setNetMask(netmask);
        logger.debug("Netmask: {}", netmask);

        String gateway = extractIpAddress(data, OFFSET_GATEWAY);
        device.setGateway(gateway);
        logger.debug("Gateway: {}", gateway);

        if (length > MIN_PACKET_LENGTH) {
            parseDeviceInfo(data, length, device);
        } else {
            setDefaultDeviceInfo(device);
        }

        device.setOnline(true);
        device.setSystemLocation(lang.get("info.loading_snmp"));
        device.setSystemUptime(lang.get("info.loading_snmp"));

        return device;
    }

    /**
     * Extracts a MAC address from the packet data.
     *
     * @param data the packet data
     * @return the MAC address as a formatted string (XX:XX:XX:XX:XX:XX)
     */
    private String extractMacAddress(byte[] data) {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                data[OFFSET_MAC_ADDRESS] & 0xFF,
                data[OFFSET_MAC_ADDRESS + 1] & 0xFF,
                data[OFFSET_MAC_ADDRESS + 2] & 0xFF,
                data[OFFSET_MAC_ADDRESS + 3] & 0xFF,
                data[OFFSET_MAC_ADDRESS + 4] & 0xFF,
                data[OFFSET_MAC_ADDRESS + 5] & 0xFF);
    }

    /**
     * Extracts an IPv4 address from the packet data at the specified offset.
     *
     * @param data the packet data
     * @param offset the starting byte position
     * @return the IP address as a dotted decimal string
     */
    private String extractIpAddress(byte[] data, int offset) {
        return String.format("%d.%d.%d.%d",
                data[offset] & 0xFF,
                data[offset + 1] & 0xFF,
                data[offset + 2] & 0xFF,
                data[offset + 3] & 0xFF);
    }

    /**
     * Parses textual device information from the response packet.
     * <p>
     * The device information is stored as null-terminated strings starting at byte 32.
     * Generally, the order is: DeviceName, Version, Description.
     * </p>
     *
     * @param data the raw packet data
     * @param length the total length of the packet
     * @param device the PlanetSwitchDevice to populate
     */
    private void parseDeviceInfo(byte[] data, int length, PlanetSwitchDevice device) {
        try {
            List<String> strings = extractNullTerminatedStrings(data, length);

            if (!strings.isEmpty()) {
                device.setDeviceName(strings.get(0));
            }
            if (strings.size() >= 2) {
                device.setVersion(strings.get(1));
            }
            if (strings.size() >= 3) {
                device.setDescription(strings.get(2));
            }

            if (device.getDeviceName() == null || device.getDeviceName().isEmpty()) {
                logger.warn("No device name provided for {}", device.getIpAddress());
                setDefaultDeviceInfo(device);
            }

        } catch (Exception e) {
            logger.warn("Error parsing device info strings", e);
            setDefaultDeviceInfo(device);
        }
    }

    /**
     * Sets default device information when parsing fails.
     *
     * @param device the device to set default values for
     */
    private void setDefaultDeviceInfo(PlanetSwitchDevice device) {
        device.setDeviceName("PLANET Switch");
        device.setVersion("Unknown");
        device.setDescription("PLANET Network Device");
    }

    /**
     * Extracts all null-terminated ASCII strings from a byte buffer.
     * <p>
     * This method scans through the buffer starting at the device info offset,
     * extracting printable ASCII characters (0x20-0x7E) until it encounters
     * a null byte (0x00).
     * </p>
     *
     * @param data the byte array containing the strings
     * @param endOffset the end position to stop reading
     * @return a list of extracted strings
     */
    private List<String> extractNullTerminatedStrings(byte[] data, int endOffset) {
        List<String> strings = new ArrayList<>();
        StringBuilder currentString = new StringBuilder();

        for (int i = OFFSET_DEVICE_INFO; i < endOffset && i < data.length; i++) {
            byte b = data[i];

            if (b == 0) { // end of string
                if (!currentString.isEmpty()) {
                    strings.add(currentString.toString().trim());
                    currentString = new StringBuilder();
                }
            } else if (b >= 0x20 && b <= 0x7E) { // printable ASCII
                currentString.append((char) b);
            }
        }

        if (!currentString.isEmpty()) {
            strings.add(currentString.toString().trim());
        }

        return strings;
    }
}