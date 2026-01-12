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
 * <p>
 * This service implements the proprietary PLANET discovery protocol, which uses
 * a specific magic packet broadcasted over UDP to detect PLANET switches on the
 * local network. The switches respond with their configuration information including
 * MAC address, IP settings, firmware version, and device details.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see PlanetSwitchDevice
 * @see SNMPService
 */
public class PlanetDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PlanetDiscoveryService.class);
    private static final LanguageManager lang = LanguageManager.getInstance();

    private static final int MAX_PACKET_SIZE = 1024;

    // Magic discovery packet for PLANET switches.
    private static final byte[] DISCOVERY_PACKET = {
            0x50, 0x4c, 0x41, 0x4e, 0x45, 0x54, 0x75, 0x74,  // "PLANETut"
            0x00, 0x16,                                       // Length/Type
            (byte) 0xff, (byte) 0xff, (byte) 0xff,           // Broadcast MAC
            (byte) 0xff, (byte) 0xff, (byte) 0xff            // Broadcast MAC
    };

    /**
     * Discovers PLANET switches on the network using UDP broadcast.
     * <p>
     * This method performs a complete discovery cycle by sending the magic packet,
     * listening for responses within the configured timeout period, parsing device
     * information from responses, and optionally enriching devices with SNMP data.
     * </p>
     *
     * @param localAddress the IPv4 address of the network interface to use for discovery
     * @return a list of discovered PlanetSwitchDevice objects may be empty if no devices found
     * @see #sendDiscoveryPacket(DatagramSocket, int)
     * @see #parseResponse(DatagramPacket)
     * @see PreferencesManager#getDiscoveryTimeout()
     */
    public List<PlanetSwitchDevice> discoverDevices(String localAddress, DiscoveryCallback callback) {
        // Load configuration from preferences
        int socketTimeout = PreferencesManager.getDiscoveryTimeout() * 1000;
        int responsePort = PreferencesManager.getDiscoveryListenPort();
        int discoveryPort = PreferencesManager.getDiscoverySendPort();

        logger.info("Starting PLANET device discovery on interface: {}", localAddress);

        List<PlanetSwitchDevice> devices = new ArrayList<>();
        Set<String> discoveredMacs = new HashSet<>(); // To avoid duplicates
        List<String> discoveredIps = new ArrayList<>(); // checking if duplicates for SNMP
        DatagramSocket sendSocket = null;
        DatagramSocket receiveSocket = null;

        try {
            // Set up receive socket
            try {
                receiveSocket = new DatagramSocket(responsePort);
                receiveSocket.setSoTimeout(socketTimeout); // timeout convert to ms
                receiveSocket.setBroadcast(true);
                logger.info("Receive socket bound to port: {}", responsePort);
            } catch (SocketException e) {
                logger.error("Could not bind to port {}: {}", responsePort, e.getMessage());
                return devices; // Return an empty list if socket binding fails
            }

            // Set up send socket bound to a specific interface
            sendSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(localAddress), discoveryPort));
            sendSocket.setBroadcast(true);
            logger.info("Send socket created on port: {}", sendSocket.getLocalPort());

            sendDiscoveryPacket(sendSocket, discoveryPort); // Send the discovery packet

            // Listen for responses
            long startTime = System.currentTimeMillis();
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
                        Objects.requireNonNull(callback).onDeviceDiscovered(device);
                        if (PreferencesManager.getAutoEnrichSnmp() && !discoveredIps.contains(device.getIpAddress())) {
                            discoveredIps.add(device.getIpAddress());
                            enrichDeviceAsync(device, callback);
                        } else if (discoveredIps.contains(device.getIpAddress())) {
                            device.setSystemLocation(lang.get("error.snmp_duplicate"));
                            device.setSystemUptime(lang.get("error.snmp_duplicate"));
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return devices;

        } catch (IOException e) {
            logger.error("Error during device discovery", e);
        } finally { // Clean up sockets
            if (sendSocket != null && !sendSocket.isClosed()) {
                sendSocket.close();
                logger.debug("Send socket closed.");
            }
            if (receiveSocket != null && !receiveSocket.isClosed()) {
                receiveSocket.close();
                logger.debug("Receive socket closed.");
            }
        }
        logger.info("Discovery complete. Found {} PLANET device(s)", devices.size());
        return devices;
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
     *
     * @param packet the DatagramPacket received from a switch
     * @return a PlanetSwitchDevice object with parsed information, or null if parsing fails
     * @see #parseDeviceInfo(byte[], int, PlanetSwitchDevice)
     */
    private PlanetSwitchDevice parseResponse(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            logger.debug("Parsing response packet: {} bytes from {}",
                    length, packet.getAddress().getHostAddress());

            // Validate packet length
            if (length < 32) {
                logger.warn("Received packet too short: {} bytes (minimum 32 required)", length);
                return null;
            }

            // Validate magic header
            String header = new String(data, 0, 6);
            if (!header.equals("PLANET")) {
                logger.warn("Invalid magic header: '{}' (expected 'PLANET')", header);
                return null;
            }

            logger.debug("Valid PLANET packet detected, parsing fields...");
            PlanetSwitchDevice device = new PlanetSwitchDevice();

            // Extract MAC address (bytes 10-15)
            String macAddress = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    data[10] & 0xFF, data[11] & 0xFF, data[12] & 0xFF,
                    data[13] & 0xFF, data[14] & 0xFF, data[15] & 0xFF);
            device.setMacAddress(macAddress);
            logger.debug("MAC Address: {}", macAddress);

            // Extract IP address (bytes 20-23)
            String ipAddress = String.format("%d.%d.%d.%d",
                    data[20] & 0xFF, data[21] & 0xFF, data[22] & 0xFF, data[23] & 0xFF);
            device.setIpAddress(ipAddress);
            logger.debug("IP Address: {}", ipAddress);

            // Extract netmask (bytes 24-27)
            String netmask = String.format("%d.%d.%d.%d",
                    data[24] & 0xFF, data[25] & 0xFF, data[26] & 0xFF, data[27] & 0xFF);
            device.setNetMask(netmask);
            logger.debug("Netmask: {}", netmask);

            // Extract gateway (bytes 28-31)
            String gateway = String.format("%d.%d.%d.%d",
                    data[28] & 0xFF, data[29] & 0xFF, data[30] & 0xFF, data[31] & 0xFF);
            device.setGateway(gateway);
            logger.debug("Gateway: {}", gateway);

            // Extract textual device information (bytes 32+)
            if (length > 32) {
                parseDeviceInfo(data, length, device);
            } else {
                // Set default values if no extended data available
                device.setDeviceName("PLANET Switch");
                device.setVersion("Unknown");
                device.setDescription("PLANET Network Device");
            }

            device.setOnline(true);
            device.setSystemLocation(lang.get("info.loading_snmp"));
            device.setSystemUptime(lang.get("info.loading_snmp"));
            return device;

        } catch (Exception e) {
            logger.error("Error parsing device response", e);
            return null;
        }
    }

    /**
     * Parses textual device information from the response packet.
     * <p>
     *
     * @param data the raw packet data
     * @param length the total length of the packet
     * @param device the PlanetSwitchDevice to populate with parsed information
     * @see #extractNullTerminatedStrings(byte[], int)
     */
    private void parseDeviceInfo(byte[] data, int length, PlanetSwitchDevice device) {
        try {
            List<String> strings = extractNullTerminatedStrings(data, length);

            // Generally, the order is: DeviceName, Version, Description
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
            }
        } catch (Exception e) {
            logger.warn("Error parsing device info strings", e);
            device.setDeviceName("PLANET Switch");
            device.setVersion("Unknown");
            device.setDescription("PLANET Network Device");
        }
    }

    /**
     * Extracts all null-terminated ASCII strings from a byte buffer.
     * <p>
     * This method scans through the buffer starting at byte 32, extracting
     * printable ASCII characters (0x20-0x7E) until it encounters a null byte (0x00).
     * Each complete string is added to the result list.
     * </p>
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

            if (b == 0) {
                // End of string
                if (!currentString.isEmpty()) {
                    strings.add(currentString.toString().trim());
                    currentString = new StringBuilder();
                }
            } else if (b >= 0x20 && b <= 0x7E) {
                currentString.append((char) b);
            }
        }

        if (!currentString.isEmpty()) {
            strings.add(currentString.toString().trim());
        }

        return strings;
    }

    /**
     * Enriches a device with SNMP data and tests protocols in a separate thread.
     * Sets "Timeout" values if SNMP doesn't respond within timeout period.
     *
     * @param device the device to enrich
     * @param callback the callback to notify (can be null)
     */
    private void enrichDeviceAsync(PlanetSwitchDevice device, DiscoveryCallback callback) throws InterruptedException {
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
                if (callback != null) {
                    callback.onProtocolsTestedCallback(device, protocolResult); // Notify callback with protocol results
                }
            } catch (Exception e) {
                logger.error("Failed to enrich {} with SNMP: {}", device.getIpAddress(), e.getMessage());
                device.setSystemLocation("Error");
                device.setSystemUptime("Error");
            } finally {
                if (snmpService != null) {
                    snmpService.close();
                }
                if (!enriched) {
                    if (device.getSystemLocation() == null || device.getSystemLocation().equals("Loading...")) {
                        device.setSystemLocation("Timeout");
                    }
                    if (device.getSystemUptime() == null || device.getSystemUptime().equals("Loading...")) {
                        device.setSystemUptime("Timeout");
                    }
                }
                if (callback != null) {
                    callback.onDeviceEnriched(device); // Notify callback for SNMP enrichment
                }
            }
        });
        enrichmentThread.setName("Enrichment-" + device.getIpAddress());
        enrichmentThread.setDaemon(true);
        enrichmentThread.start();
        enrichmentThread.join(1000); // 1000ms to terminate
    }
}