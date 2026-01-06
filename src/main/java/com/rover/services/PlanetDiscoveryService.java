package com.rover.services;

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
 * Service de découverte des switchs PLANET via UDP
 * Utilise le protocole reverse-engineered du logiciel PLANET Smart Discovery
 */
public class PlanetDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PlanetDiscoveryService.class);

    // Constantes du protocole PLANET
    private static final int DISCOVERY_PORT = 62630; // Port pour envoyer la requête
    private static final int RESPONSE_PORT = 62631; // Port pour recevoir les réponses
    private static final int SOCKET_TIMEOUT = 5000; // Timeout de 5 secondes
    private static final int MAX_PACKET_SIZE = 1024; // Taille max d'un paquet UDP

    // Magic bytes pour la découverte (16 bytes)
    // "PLANETut" + 0x0016 + broadcast MAC (FF FF FF FF FF FF)
    private static final byte[] DISCOVERY_PACKET = {
            0x50, 0x4c, 0x41, 0x4e, 0x45, 0x54, 0x75, 0x74,  // "PLANETut"
            0x00, 0x16,                                        // Length/Type
            (byte) 0xff, (byte) 0xff, (byte) 0xff,            // Broadcast MAC
            (byte) 0xff, (byte) 0xff, (byte) 0xff             // Broadcast MAC
    };

    /**
     * Découvre tous les switchs PLANET sur le réseau
     *
     * @param localAddress L'adresse IP locale de l'interface réseau à utiliser
     * @return Liste des switchs PLANET découverts
     */
    public List<PlanetSwitchDevice> discoverDevices(String localAddress) {
        logger.info("Starting PLANET device discovery on interface: {}", localAddress);

        List<PlanetSwitchDevice> devices = new ArrayList<>();
        Set<String> discoveredMacs = new HashSet<>(); // Pour éviter les doublons

        DatagramSocket sendSocket = null;
        DatagramSocket receiveSocket = null;

        try {
            try {
                receiveSocket = new DatagramSocket(RESPONSE_PORT); // Créer un socket pour recevoir les réponses sur le port 62631
                receiveSocket.setSoTimeout(SOCKET_TIMEOUT); // On met un timeout également pour éviter d'attendre indéfiniment les réponses
                logger.info("Receive socket setup to port : {}", RESPONSE_PORT);
            } catch (SocketException e) {
                logger.error("Could not bind to port {} : {}", RESPONSE_PORT, e.getMessage());

                receiveSocket = new DatagramSocket(null);
                receiveSocket.setReuseAddress(true); // On essaye de réattribuer en cas d'échec
                receiveSocket.bind(new InetSocketAddress(RESPONSE_PORT));
                receiveSocket.setSoTimeout(SOCKET_TIMEOUT);
                logger.info("Receive socket bound to port {} with SO_REUSEADDR", RESPONSE_PORT);
            }

            // Créer un socket pour ENVOYER la commande (port libre)
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
            logger.info("Send socket created on port: {}", sendSocket.getLocalPort());

            sendDiscoveryPacket(sendSocket); // Envoyer le paquet de découverte en broadcast : on l'envoie sur le port 62630 des appareils

            long startTime = System.currentTimeMillis(); // Écouter les réponses pendant le timeout

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
                        logger.warn("✗ Failed to parse device from packet");
                    }

                } catch (SocketTimeoutException e) {
                    logger.error("Socket timeout reached after {} ms, ending discovery", System.currentTimeMillis() - startTime); // Timeout atteint, sortir de la boucle
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error during device discovery", e);
        } finally { // Enfin, on coupe les connexions
            if (sendSocket != null && !sendSocket.isClosed()) {
                sendSocket.close();
                logger.debug("Send socket closed");
            }
            if (receiveSocket != null && !receiveSocket.isClosed()) {
                receiveSocket.close();
                logger.info("Receive socket closed");
            }
        }

        logger.info("Discovery COMPLETE. Found {} PLANET device(s)", devices.size());
        return devices;
    }

    /**
     * Envoie le paquet de découverte en broadcast
     */
    private void sendDiscoveryPacket(DatagramSocket socket) throws IOException {
        socket.send(new DatagramPacket(DISCOVERY_PACKET, DISCOVERY_PACKET.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
        logger.debug("Discovery packet sent to 255.255.255.255 : {}", DISCOVERY_PORT);
    }

    /**
     * Parse la réponse d'un switch PLANET pour récupérer les informations essentielles
     */
    private PlanetSwitchDevice parseResponse(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            logger.debug("Parsing response packet: {} bytes from {}",
                    length, packet.getAddress().getHostAddress());

            if (length < 32) { // Vérifier la taille minimale
                logger.warn("Received packet too short: {} bytes (minimum 32 required)", length);
                return null;
            }

            if (length < 8) { // Vérifier le magic header "PLANETut"
                logger.warn("Packet too small to contain magic header");
                return null;
            }

            String header = new String(data, 0, 6);
            if (!header.equals("PLANET")) {
                logger.warn("Invalid magic header: '{}' (expected 'PLANET')", header);
                return null;
            }

            logger.debug("Valid PLANET packet detected, parsing in progress ...");

            PlanetSwitchDevice device = new PlanetSwitchDevice();

            if (length < 16) { // Extraire la MAC address (offset 10-15)
                logger.warn("Packet too small to contain MAC address");
                return null;
            }

            String macAddress = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    data[10] & 0xFF, data[11] & 0xFF, data[12] & 0xFF,
                    data[13] & 0xFF, data[14] & 0xFF, data[15] & 0xFF
            ); // Pattern pour les adresses MAC
            device.setMacAddress(macAddress);

            logger.debug("MAC Address: {}", macAddress);

            if (length < 24) { // Extraire l'adresse IP (offset 20-23)
                logger.warn("Packet too small to contain IP address");
                return null;
            }

            String ipAddress = String.format("%d.%d.%d.%d",
                    data[20] & 0xFF, data[21] & 0xFF, data[22] & 0xFF, data[23] & 0xFF
            );
            device.setIpAddress(ipAddress);
            device.setDeviceIp(ipAddress);

            logger.debug("IP Address: {}", ipAddress);

            if (length >= 28) { // Extraire le netmask (offset 24-27)
                String netmask = String.format("%d.%d.%d.%d",
                        data[24] & 0xFF, data[25] & 0xFF, data[26] & 0xFF, data[27] & 0xFF
                );
                device.setNetMask(netmask);
                logger.debug("Netmask: {}", netmask);
            }

            if (length >= 32) { // Extraire la gateway (offset 28-31)
                String gateway = String.format("%d.%d.%d.%d",
                        data[28] & 0xFF, data[29] & 0xFF, data[30] & 0xFF, data[31] & 0xFF
                );
                device.setGateway(gateway);
                logger.debug("Gateway: {}", gateway);
            }

            // Extraire les informations textuelles (Device Name, Version, Description) souvent après 32 bits
            if (length > 32) {
                parseDeviceInfo(data, length, device);
            } else { // Valeurs par défaut si pas assez de données
                device.setDeviceName("PLANET Switch");
                device.setVersion("Unknown");
                device.setDescription("PLANET Network Device");
            }
            device.setOnline(true);
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