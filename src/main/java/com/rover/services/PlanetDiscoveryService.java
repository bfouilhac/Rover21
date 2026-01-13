package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.LanguageManager;
import com.rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Service for discovering PLANET network switches using UDP broadcast protocol.
 * This service implements the proprietary PLANET discovery protocol, which uses
 * a specific magic packet broadcasted over UDP to detect PLANET switches on the
 * local network. The switches respond with their configuration information, including
 * MAC address, IP settings, firmware version, and device details.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see PlanetSwitchDevice
 * @see SNMPService
 */
public class PlanetDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PlanetDiscoveryService.class);
    private static final LanguageManager lang = LanguageManager.getInstance();
    private static final int MAX_PACKET_SIZE = 1024;

    // magic discovery packet for PLANET switches
    private static final byte[] DISCOVERY_PACKET = {
            0x50, 0x4c, 0x41, 0x4e, 0x45, 0x54, 0x75, 0x74,  // "PLANETut"
            0x00, 0x16,                                       // length/type
            (byte) 0xff, (byte) 0xff, (byte) 0xff,           // broadcast MAC
            (byte) 0xff, (byte) 0xff, (byte) 0xff            // broadcast MAC
    };

    /**
     * Discovers PLANET switches on the network using UDP broadcast.
     * This method performs a complete discovery cycle by sending the magic packet,
     * listening for responses within the configured timeout period, parsing device
     * information from responses, and optionally enriching devices with SNMP data
     * and protocol tests.
     *
     * @param localAddress the IPv4 address of the network interface to use for discovery
     * @param callback the callback to notify when devices are discovered or enriched
     * @return a list of discovered PlanetSwitchDevice objects (maybe empty if no devices found)
     * @throws NullPointerException if callback is null
     * @see #sendDiscoveryPacket(DatagramSocket, int)
     * @see #parseResponse(DatagramPacket)
     * @see PreferencesManager#getDiscoveryTimeout()
     */
    public List<PlanetSwitchDevice> discoverDevices(String localAddress, DiscoveryCallback callback) {
        int socketTimeout = PreferencesManager.getDiscoveryTimeout() * 1000; // load configuration from preferences
        int responsePort = PreferencesManager.getDiscoveryListenPort();
        int discoveryPort = PreferencesManager.getDiscoverySendPort();

        logger.info("Starting PLANET device discovery on interface: {}", localAddress);

        List<PlanetSwitchDevice> devices = new ArrayList<>();
        Set<String> discoveredMacs = new HashSet<>(); // to avoid duplicates
        Set<String> discoveredIps = new HashSet<>(); // to track IPs for SNMP (avoid duplicates)

        DatagramSocket sendSocket = null;
        DatagramSocket receiveSocket = null;

        try {
            receiveSocket = createReceiveSocket(responsePort, socketTimeout); // set up receive socket
            if (receiveSocket == null) {
                return devices; // return an empty list if socket binding fails
            }

            sendSocket = createSendSocket(localAddress, discoveryPort); // set up send socket bound to a specific interface
            sendDiscoveryPacket(sendSocket, discoveryPort); // send the discovery packet

            long startTime = System.currentTimeMillis(); // listen for responses
            logger.info("Listening for responses on port {} for {} ms...", responsePort, socketTimeout);

            while (System.currentTimeMillis() - startTime < socketTimeout) {
                try {
                    byte[] buffer = new byte[MAX_PACKET_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    receiveSocket.receive(packet);

                    PlanetSwitchDevice device = parseResponse(packet);

                    if (device != null && !discoveredMacs.contains(device.getMacAddress())) {
                        devices.add(device);
                        discoveredMacs.add(device.getMacAddress());
                        logger.info("Discovered PLANET device: {} at {}", device.getDeviceName(), device.getIpAddress());

                        Objects.requireNonNull(callback, "Callback cannot be null").onDeviceDiscovered(device);

                        if (PreferencesManager.getAutoEnrichSnmp()) { // check if auto-enrich is enabled
                            if (!discoveredIps.contains(device.getIpAddress())) {
                                discoveredIps.add(device.getIpAddress());
                                enrichDeviceAsync(device, callback); // enrich in a separate thread
                            } else {
                                device.setSystemLocation(lang.get("error.snmp_duplicate")); // duplicate IP detected
                                device.setSystemUptime(lang.get("error.snmp_duplicate"));
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break; // timeout reached, stop listening
                }
            }

            logger.info("Discovery complete. Found {} PLANET device(s)", devices.size());
            return devices;

        } catch (IOException e) {
            logger.error("Error during device discovery", e);
            return devices;
        } finally {
            closeSocket(sendSocket, "Send socket");
            closeSocket(receiveSocket, "Receive socket");
        }
    }

    /**
     * Creates and configures the receiver socket for listening to discovery responses.
     * This method binds the socket to the specified port and configures it for
     * broadcast reception with the appropriate timeout.
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
     * This method binds the socket to a specific network interface to ensure
     * the broadcast is sent on the correct network.
     *
     * @param localAddress the local interface IP address
     * @param port the port to bind to
     * @return the configured DatagramSocket
     * @throws SocketException if socket creation fails
     */
    private DatagramSocket createSendSocket(String localAddress, int port) throws SocketException {
        try {
            DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(localAddress), port));
            socket.setBroadcast(true);
            logger.info("Send socket created on port: {}", socket.getLocalPort());
            return socket;
        } catch (UnknownHostException e) {
            throw new SocketException("Invalid local address: " + localAddress);
        }
    }

    /**
     * Sends the magic discovery packet via UDP broadcast.
     * The packet is broadcasted to 255.255.255.255 on the configured discovery port,
     * allowing all PLANET switches on the local network to receive and respond to it.
     *
     * @param socket the DatagramSocket to use for sending
     * @param discoveryPort the UDP port to send the packet to (default: 62630)
     * @throws IOException if the packet cannot be sent
     * @see #DISCOVERY_PACKET
     */
    private void sendDiscoveryPacket(DatagramSocket socket, int discoveryPort) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                DISCOVERY_PACKET,
                DISCOVERY_PACKET.length,
                InetAddress.getByName("255.255.255.255"),
                discoveryPort
        );
        socket.send(packet);
        logger.debug("Discovery packet sent to broadcast address on port: {}", discoveryPort);
    }

    /**
     * Parses a UDP response packet from a PLANET switch.
     * This method extracts device information from the binary response packet,
     * including MAC address, IP configuration, and device details.
     *
     * @param packet the DatagramPacket received from a switch
     * @return a PlanetSwitchDevice object with parsed information, or null if parsing fails
     * @see #parseDeviceInfo(byte[], int, PlanetSwitchDevice)
     */
    private PlanetSwitchDevice parseResponse(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            logger.debug("Parsing response packet: {} bytes from {}", length, packet.getAddress().getHostAddress());

            if (length < 32) { // validate packet length
                logger.warn("Response packet too short: {} bytes", length);
                return null;
            }

            if (!isValidPlanetPacket(data)) { // verify PLANET magic header
                logger.warn("Invalid PLANET packet header");
                return null;
            }

            PlanetSwitchDevice device = new PlanetSwitchDevice();

            String macAddress = extractMacAddress(data); // extract MAC address (bytes 10-15)
            device.setMacAddress(macAddress);
            logger.debug("MAC Address: {}", macAddress);

            String ipAddress = extractIpAddress(data, 20); // extract IP address (bytes 20-23)
            device.setIpAddress(ipAddress);
            logger.debug("IP Address: {}", ipAddress);

            String netmask = extractIpAddress(data, 24); // extract netmask (bytes 24-27)
            device.setNetMask(netmask);
            logger.debug("Netmask: {}", netmask);

            String gateway = extractIpAddress(data, 28); // extract gateway (bytes 28-31)
            device.setGateway(gateway);
            logger.debug("Gateway: {}", gateway);

            if (length > 32) { // extract textual device information (bytes 32+)
                parseDeviceInfo(data, length, device);
            } else {
                setDefaultDeviceInfo(device); // set default values if no extended data available
            }

            device.setOnline(true);
            device.setSystemLocation(lang.get("info.loading_snmp")); // default value before SNMP enrichment
            device.setSystemUptime(lang.get("info.loading_snmp"));

            return device;
        } catch (Exception e) {
            logger.error("Error parsing device response", e);
            return null;
        }
    }

    /**
     * Validates if a packet has the correct PLANET magic header.
     * The first 8 bytes should spell "PLANETut" in ASCII.
     *
     * @param data the packet data
     * @return true if the header is valid, false otherwise
     */
    private boolean isValidPlanetPacket(byte[] data) {
        if (data.length < 8) {
            return false;
        }
        String header = new String(data, 0, 8);
        return header.equals("PLANETut");
    }

    /**
     * Extracts a MAC address from the packet data.
     * The MAC address is stored in bytes 10-15 in the format XX:XX:XX:XX:XX:XX.
     *
     * @param data the packet data
     * @return the MAC address as a formatted string
     */
    private String extractMacAddress(byte[] data) {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                data[10] & 0xFF, data[11] & 0xFF, data[12] & 0xFF,
                data[13] & 0xFF, data[14] & 0xFF, data[15] & 0xFF);
    }

    /**
     * Extracts an IPv4 address from the packet data at the specified offset.
     * The address is stored as 4 consecutive bytes representing the octets.
     *
     * @param data the packet data
     * @param offset the starting byte position
     * @return the IP address as a dotted decimal string
     */
    private String extractIpAddress(byte[] data, int offset) {
        return String.format("%d.%d.%d.%d",
                data[offset] & 0xFF, data[offset + 1] & 0xFF,
                data[offset + 2] & 0xFF, data[offset + 3] & 0xFF);
    }

    /**
     * Parses textual device information from the response packet.
     * The device information is stored as null-terminated strings starting at byte 32.
     * Generally, the order is: DeviceName, Version, Description.
     *
     * @param data the raw packet data
     * @param length the total length of the packet
     * @param device the PlanetSwitchDevice to populate with parsed information
     * @see #extractNullTerminatedStrings(byte[], int)
     */
    private void parseDeviceInfo(byte[] data, int length, PlanetSwitchDevice device) {
        try {
            List<String> strings = extractNullTerminatedStrings(data, length);

            if (!strings.isEmpty()) { // generally, the order is: DeviceName, Version, Description
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
     * Sets default device information when parsing fails or data is unavailable.
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
     * This method scans through the buffer starting at byte 32, extracting
     * printable ASCII characters (0x20-0x7E) until it encounters a null byte (0x00).
     * Each complete string is added to the result list.
     *
     * @param data the byte array containing the strings
     * @param endOffset the end position to stop reading
     * @return a list of extracted strings
     */
    private List<String> extractNullTerminatedStrings(byte[] data, int endOffset) {
        List<String> strings = new ArrayList<>();
        StringBuilder currentString = new StringBuilder();

        for (int i = 32; i < endOffset && i < data.length; i++) {
            byte b = data[i];

            if (b == 0) { // end of string
                if (!currentString.isEmpty()) {
                    strings.add(currentString.toString().trim());
                    currentString = new StringBuilder();
                }
            } else if (b >= 0x20 && b <= 0x7E) { // printable ASCII character
                currentString.append((char) b);
            }
        }

        if (!currentString.isEmpty()) { // add last string if not empty
            strings.add(currentString.toString().trim());
        }

        return strings;
    }

    /**
     * Enriches a device with SNMP data and tests protocols in a separate daemon thread.
     * This method creates a dedicated thread for each device to perform SNMP queries
     * and protocol tests without blocking the main discovery process. The thread has
     * a maximum execution time of 1 second before being forcibly terminated.
     *
     * @param device the device to enrich
     * @param callback the callback to notify when enrichment completes
     */
    private void enrichDeviceAsync(PlanetSwitchDevice device, DiscoveryCallback callback) {
        Thread enrichmentThread = new Thread(() -> {
            SNMPService snmpService = null;
            ProtocolTestService protocolService = new ProtocolTestService();
            boolean enriched = false;

            try {
                snmpService = new SNMPService();
                if (snmpService.testSNMPConnectivity(device.getIpAddress())) {
                    snmpService.enrichDeviceWithSNMP(device);
                    enriched = true;
                    logger.info("Device {} enriched with SNMP data", device.getIpAddress());
                } else {
                    logger.warn("SNMP not accessible for {}", device.getIpAddress());
                    device.setSystemLocation(lang.get("error.snmp_available"));
                    device.setSystemUptime(lang.get("error.snmp_available"));
                }

                logger.info("Testing protocols for device: {}", device.getIpAddress());
                ProtocolTestService.ProtocolTestResult protocolResult = protocolService.testAllProtocols(device.getIpAddress());
                callback.onProtocolsTestedCallback(device, protocolResult);

            } catch (Exception e) {
                logger.error("Failed to enrich {} with SNMP: {}", device.getIpAddress(), e.getMessage());
                device.setSystemLocation("Error");
                device.setSystemUptime("Error");
            } finally {
                if (snmpService != null) {
                    snmpService.close();
                }

                if (!enriched) { // set timeout values if enrichment failed
                    if (device.getSystemLocation() == null || device.getSystemLocation().equals("Loading...")) {
                        device.setSystemLocation("Timeout");
                    }
                    if (device.getSystemUptime() == null || device.getSystemUptime().equals("Loading...")) {
                        device.setSystemUptime("Timeout");
                    }
                }

                callback.onDeviceEnriched(device);
            }
        });

        enrichmentThread.setName("Enrichment-" + device.getIpAddress());
        enrichmentThread.setDaemon(true); // daemon thread will not prevent JVM shutdown
        enrichmentThread.start();

        try {
            enrichmentThread.join(1000); // wait up to 1 second for a thread to complete
        } catch (InterruptedException e) {
            logger.warn("Enrichment thread interrupted for {}", device.getIpAddress());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes a DatagramSocket safely and logs the operation.
     * This method handles null sockets and ensures proper cleanup.
     *
     * @param socket the socket to close
     * @param socketName the name of the socket for logging
     */
    private void closeSocket(DatagramSocket socket, String socketName) {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("{} closed", socketName);
        }
    }
}