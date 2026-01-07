package com.rover.services;

import com.rover.model.NetworkInterfaceCustom;
import com.rover.model.PlanetSwitchDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service used to perform the discovery on a specific network using UDP broadcast and a magic discovery packet to find PLANET switches.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class PlanetDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PlanetDiscoveryService.class);

    private static final int DISCOVERY_PORT = 62630; // The port that the switch listens on for discovery.
    private static final int RESPONSE_PORT = 62631; // The port to which the switch sends its response.
    private static final int SOCKET_TIMEOUT = 5000; // 5s of discovery, allowing time for all devices to answer.
    private static final int MAX_PACKET_SIZE = 1024;

    // Magic bytes for the discovery (16 bytes)
    // "PLANETut" + 0x0016 + broadcast MAC (FF FF FF FF FF FF = 255.255.255.255)
    private static final byte[] DISCOVERY_PACKET = {
            0x50, 0x4c, 0x41, 0x4e, 0x45, 0x54, 0x75, 0x74,  // "PLANETut"
            0x00, 0x16,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, // Broadcast MAC
            (byte) 0xff, (byte) 0xff, (byte) 0xff // Broadcast MAC
    };

    /**
     * Getter
     * @return the current limit time used for the discovery.
     * @see com.rover.controller.MainController#onDiscoverClicked(NetworkInterfaceCustom)
     */
    public int getTimeout() {return SOCKET_TIMEOUT;}

    /**
     * Sends the magic packet by broadcast and waits for the device's response.
     * @param localAddress IPv4 Address of the network interface from which the magic packet will be sent.
     * @return a list of all the PlanetSwitchDevices found.
     * @see PlanetSwitchDevice
     */
    public List<PlanetSwitchDevice> discoverDevices(String localAddress) {

        logger.info("Starting PLANET device discovery on interface : {}", localAddress);
        List<PlanetSwitchDevice> devices = new ArrayList<>();
        Set<String> discoveredMacs = new HashSet<>(); // To avoid duplicates
        DatagramSocket sendSocket = null; // init
        DatagramSocket receiveSocket = null; // init
        try {
            try {
                receiveSocket = new DatagramSocket(RESPONSE_PORT); // Socket listening to port 62631, where the switches will send their answer
                receiveSocket.setSoTimeout(SOCKET_TIMEOUT); // Timeout so we can avoid waiting too long for answers
                logger.info("Receive socket setup to port : {}", RESPONSE_PORT);
            } catch (SocketException e) {
                logger.error("Could not bind to port {} : {}", RESPONSE_PORT, e.getMessage());

                receiveSocket = new DatagramSocket(null); // Retrying without mentioning any port
                receiveSocket.setReuseAddress(true);
                receiveSocket.bind(new InetSocketAddress(RESPONSE_PORT));
                receiveSocket.setSoTimeout(SOCKET_TIMEOUT); // Setting the timeout so we can catch SocketTimeoutException
                logger.info("Receive socket bound to port {} with SO_REUSEADDR", RESPONSE_PORT);
            }

            sendSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(localAddress), DISCOVERY_PORT));
            sendSocket.setBroadcast(true); // Setting broadcast ON so it will be sent to every device on the same network
            logger.info("Send socket created on port : {}", sendSocket.getLocalPort());

            sendDiscoveryPacket(sendSocket); // Sending the magic packet by the sending socket

            long startTime = System.currentTimeMillis(); // Starting the timer for the timeout
            logger.info("Listening for responses on port {} for {} ms...", RESPONSE_PORT, SOCKET_TIMEOUT);
            while (System.currentTimeMillis() - startTime < SOCKET_TIMEOUT) {
                try {
                    byte[] buffer = new byte[MAX_PACKET_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    receiveSocket.receive(packet);
                    PlanetSwitchDevice device = parseResponse(packet); // Parser la réponse
                    if (device != null) {
                        if (!discoveredMacs.contains(device.getMacAddress())) {
                            devices.add(device);
                            discoveredMacs.add(device.getMacAddress());
                            logger.info("Discovered PLANET device: {} at {}",
                                    device.getDeviceName(), device.getIpAddress());
                        } else {
                            logger.debug("Device already discovered (duplicate): {}", device.getMacAddress());
                        }
                    } else {
                        logger.warn("Failed to parse device from packet.");
                    }
                } catch (SocketTimeoutException e) {
                    logger.error("Socket timeout reached after {} ms, ending discovery", System.currentTimeMillis() - startTime); // Timeout atteint, sortir de la boucle
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error during device discovery", e);
        } finally { // We can finally close connections
            if (sendSocket != null && !sendSocket.isClosed()) {
                sendSocket.close();
                logger.debug("Send socket closed.");
            }
            if (receiveSocket != null && !receiveSocket.isClosed()) {
                receiveSocket.close();
                logger.info("Receive socket closed.");
            }
        }
        logger.info("Discovery COMPLETE. Found {} PLANET device(s)", devices.size());
        return devices;
    }

    /**
     * Send the magic packet by broadcast.
     * @param socket the sending Datagram Socket.
     * @see DatagramSocket
     * @see DatagramPacket
     */
    private void sendDiscoveryPacket(DatagramSocket socket) throws IOException {
        socket.send(new DatagramPacket(DISCOVERY_PACKET, DISCOVERY_PACKET.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
        logger.debug("Discovery packet sent : {}", DISCOVERY_PORT);
    }

    /**
     * Parse the response to get information about a product.
     * Checking several conditions before analyzing the bits such as the length (>32, the header...).
     * @param packet the DatagramPacket containing the response from a PLANET switch.
     * @return an instance of a PlanetSwitchDevice, created with the data found in the packets.
     * @see DatagramPacket
     * @see PlanetSwitchDevice
     */
    private PlanetSwitchDevice parseResponse(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            logger.debug("Parsing response packet: {} bytes from {}",
                    length, packet.getAddress().getHostAddress());

            if (length < 32) { // Check the size
                logger.warn("Received packet too short: {} bytes (minimum 32 required)", length);
                return null;
            }

            String header = new String(data, 0, 6);
            if (!header.equals("PLANET")) {
                logger.warn("Invalid magic header: '{}' (expected 'PLANET')", header);
                return null;
            }
            logger.debug("Valid PLANET packet detected, parsing in progress ...");
            PlanetSwitchDevice device = new PlanetSwitchDevice();

            String macAddress = String.format("%02X:%02X:%02X:%02X:%02X:%02X", data[10] & 0xFF, data[11] & 0xFF, data[12] & 0xFF, data[13] & 0xFF, data[14] & 0xFF, data[15] & 0xFF); // Pattern pour les adresses MAC
            device.setMacAddress(macAddress);
            logger.debug("MAC Address: {}", macAddress);

            String ipAddress = String.format("%d.%d.%d.%d", data[20] & 0xFF, data[21] & 0xFF, data[22] & 0xFF, data[23] & 0xFF);
            device.setIpAddress(ipAddress);
            device.setDeviceIp(ipAddress);
            logger.debug("IP Address: {}", ipAddress);

            String netmask = String.format("%d.%d.%d.%d", data[24] & 0xFF, data[25] & 0xFF, data[26] & 0xFF, data[27] & 0xFF);
            device.setNetMask(netmask);
            logger.debug("Netmask: {}", netmask);

            String gateway = String.format("%d.%d.%d.%d", data[28] & 0xFF, data[29] & 0xFF, data[30] & 0xFF, data[31] & 0xFF);
            device.setGateway(gateway);
            logger.debug("Gateway: {}", gateway);

            // Extract context data (Device Name, firmware version, description)
            if (length > 32) {
                parseDeviceInfo(data, length, device);
            } else { // Default values if not found, may be blank or invalid
                device.setDeviceName("PLANET Switch");
                device.setVersion("Unknown");
                device.setDescription("PLANET Network Device");
            }
            device.setOnline(true); // If the switch is found, then its online obviously
            return device;
        } catch (Exception e) {
            logger.error("Error parsing device response", e);
            return null;
        }
    }

    /**
     * Parse les informations textuelles du device (nom, version, description)
     * Ces données sont des chaînes null-terminated après l'offset 32
     */
    private void parseDeviceInfo(byte[] data, int length, PlanetSwitchDevice device) {
        try {
            List<String> strings = extractNullTerminatedStrings(data, length);

            // Généralement, l'ordre est : DeviceName, Version, Description
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
     * Extrait toutes les chaînes null-terminated d'un buffer
     */
    private List<String> extractNullTerminatedStrings(byte[] data, int endOffset) {
        List<String> strings = new ArrayList<>();
        StringBuilder currentString = new StringBuilder();
        for (int i = 32; i < endOffset && i < data.length; i++) {
            byte b = data[i];

            if (b == 0) {
                if (!currentString.isEmpty()) {
                    strings.add(currentString.toString().trim()); // Fin de chaîne
                    currentString = new StringBuilder();
                }
            } else if (b >= 0x20 && b <= 0x7E) {
                currentString.append((char) b); // Caractère ASCII imprimable
            }
        }
        if (!currentString.isEmpty()) {
            strings.add(currentString.toString().trim()); // Ajouter la dernière chaîne si elle n'est pas vide
        }

        return strings;
    }
}