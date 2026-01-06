package com.rover.services;

import com.rover.model.NetworkInterfaceCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Service for managing network interfaces.
 * Scans and lists all network interfaces available on the PC.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class NetworkAdapterService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAdapterService.class);

    /**
     * Get all the network interfaces with a valid IPv4 address.
     * @return the list of NetworkInterfaceCustom adapters built from the scan.
     * @see NetworkInterfaceCustom
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
                            logger.info("Found network adapter : {} - {}", adapter.getName(), adapter.getIpAddress());
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
     * Build a NetworkInterfaceCustom from a NetworkInterface.
     * @param netInterface the NetworkInterface.
     * @param ipAddress the IPv4 Address linked to the specific interface.
     * @return the instance of NetworkInterfaceCustom created.
     * @see NetworkInterfaceCustom
     * @see NetworkInterface
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
     * Check if a specific interface exists and is valid.
     * @param adapter the network interface you want to check.
     * @return whether the conditions are true or false.
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