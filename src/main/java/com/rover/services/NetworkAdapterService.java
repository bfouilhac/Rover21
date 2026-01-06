package com.rover.services;

import com.rover.model.NetworkInterfaceCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Service pour gérer les interfaces réseau
 * Scanne et liste toutes les interfaces réseau disponibles sur la machine
 */
public class NetworkAdapterService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAdapterService.class);

    /**
     * Récupère toutes les interfaces réseau avec une adresse IPv4 valide
     * @return Liste des NetworkInterfaceCustom disponibles
     */
    public List<NetworkInterfaceCustom> getAvailableAdapters() {
        List<NetworkInterfaceCustom> adapters = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = networkInterfaces.nextElement();
                if (!netInterface.isUp() || netInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = netInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address) {
                        String ipAddress = inetAddress.getHostAddress();
                        if (ipAddress.startsWith("127.") || ipAddress.startsWith("169.254.")) {
                            continue;
                        }
                        NetworkInterfaceCustom adapter = createAdapter(netInterface, (Inet4Address) inetAddress);
                        if (adapter != null) {
                            adapters.add(adapter);
                            logger.info("Found network adapter: {} - {}",
                                    adapter.getName(), adapter.getIpAddress());
                        }
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("Error scanning network interfaces", e);
        }
        return adapters;
    }

    /**
     * Crée un NetworkInterfaceCustom à partir d'une NetworkInterface Java
     */
    private NetworkInterfaceCustom createAdapter(NetworkInterface netInterface, Inet4Address ipAddress) {
        try {
            String name = netInterface.getName();
            String displayName = netInterface.getDisplayName();
            String ip = ipAddress.getHostAddress();

            NetworkInterfaceCustom adapter = new NetworkInterfaceCustom(name, displayName, ip);
            adapter.setUp(netInterface.isUp());
            adapter.setLoopback(netInterface.isLoopback());
            return adapter;
        } catch (Exception e) {
            logger.error("Error creating adapter for interface: {}", netInterface.getName(), e);
            return null;
        }
    }

    /**
     * Formate l'adresse MAC au format standard (AA:BB:CC:DD:EE:FF)
     */
    private String formatMacAddress(byte[] macBytes) {
        if (macBytes == null || macBytes.length == 0) {
            return "N/A";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < macBytes.length; i++) {
            sb.append(String.format("%02X", macBytes[i]));
            if (i < macBytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    /**
     * Calcule le masque de sous-réseau
     */
    private String calculateNetmask(NetworkInterface netInterface, Inet4Address ipAddress) {
        try {
            for (InterfaceAddress interfaceAddress : netInterface.getInterfaceAddresses()) {
                if (interfaceAddress.getAddress().equals(ipAddress)) {
                    short prefixLength = interfaceAddress.getNetworkPrefixLength();
                    return prefixLengthToNetmask(prefixLength);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not calculate netmask for {}", netInterface.getName(), e);
        }
        return "255.255.255.0"; // Valeur par défaut
    }

    /**
     * Convertit une longueur de préfixe en masque de sous-réseau
     * Exemple: 24 => 255.255.255.0
     */
    private String prefixLengthToNetmask(short prefixLength) {
        int mask = 0xffffffff << (32 - prefixLength);
        return String.format("%d.%d.%d.%d",
                (mask >> 24) & 0xff,
                (mask >> 16) & 0xff,
                (mask >> 8) & 0xff,
                mask & 0xff
        );
    }

    /**
     * Vérifie si une interface réseau existe et est valide
     */
    public boolean isAdapterValid(NetworkInterfaceCustom adapter) {
        if (adapter == null) {
            return false;
        }

        try {
            NetworkInterface netInterface = NetworkInterface.getByName(adapter.getName());
            return netInterface != null && netInterface.isUp() && !netInterface.isLoopback();
        } catch (SocketException e) {
            logger.error("Error checking adapter validity", e);
            return false;
        }
    }
}