package com.rover.services;

import com.rover.model.NetworkInterfaceCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Service for managing and discovering network interfaces on the local system.
 * <p>
 * This service provides methods to scan the system for available network interfaces,
 * filter them based on specific criteria (IPv4, active, non-loopback), and validate
 * their status for network discovery operations.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see NetworkInterfaceCustom
 * @see java.net.NetworkInterface
 */
public class NetworkAdapterService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAdapterService.class);

    /**
     * Retrieves all available network interfaces with valid IPv4 addresses.
     * <p>
     * This method scans the system for all network interfaces, filters out
     * loopback and inactive interfaces, and returns only those with valid
     * IPv4 addresses suitable for network discovery operations.
     * </p>
     *
     * @return a list of NetworkInterfaceCustom objects representing valid adapters
     * @see NetworkInterfaceCustom
     * @see #createAdapter(NetworkInterface, Inet4Address)
     */
    public List<NetworkInterfaceCustom> getAvailableAdapters() {
        List<NetworkInterfaceCustom> adapters = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = networkInterfaces.nextElement();

                // Skip interfaces that are down or loopback
                if (!netInterface.isUp() || netInterface.isLoopback()) {
                    continue;
                }

                // Check all IP addresses assigned to this interface
                Enumeration<InetAddress> inetAddresses = netInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // Only process IPv4 addresses
                    if (inetAddress instanceof Inet4Address) {
                        String ipAddress = inetAddress.getHostAddress();

                        // Skip localhost and APIPA addresses
                        if (ipAddress.startsWith("127.") || ipAddress.startsWith("169.254.")) {
                            continue;
                        }

                        NetworkInterfaceCustom adapter = createAdapter(
                                netInterface,
                                (Inet4Address) inetAddress
                        );

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
     * Creates a NetworkInterfaceCustom object from a system NetworkInterface.
     * <p>
     * This method extracts relevant information from the system's NetworkInterface
     * and constructs a custom representation suitable for the application's needs.
     * It handles exceptions gracefully and returns null if the adapter cannot be created.
     * </p>
     *
     * @param netInterface the system NetworkInterface to convert
     * @param ipAddress the IPv4 address assigned to this interface
     * @return a new NetworkInterfaceCustom instance, or null if creation fails
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
            logger.error("Error creating adapter for interface: {}",
                    netInterface.getName(), e);
            return null;
        }
    }

    /**
     * Validates whether a network interface is currently active and usable.
     * <p>
     * This method checks if the specified adapter still exists in the system,
     * is currently active (up), and is not a loopback interface. This is useful
     * to verify that an adapter selected by the user is still valid before
     * attempting a network discovery operation.
     * </p>
     *
     * @param adapter the NetworkInterfaceCustom to validate
     * @return true if the adapter exists and is valid for use, false otherwise
     * @see NetworkInterfaceCustom#isValidForScanning()
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