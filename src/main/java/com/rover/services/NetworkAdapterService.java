package com.rover.services;

import com.rover.model.NetworkInterfaceCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Service for managing and discovering network interfaces on the local system.
 * This service provides methods to scan the system for available network interfaces,
 * filter them based on specific criteria (IPv4, active, non-loopback), and validate
 * their status for network discovery operations.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see NetworkInterfaceCustom
 * @see java.net.NetworkInterface
 */
public class NetworkAdapterService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAdapterService.class);

    /**
     * Retrieves all available network interfaces with valid IPv4 addresses.
     * This method scans the system for all network interfaces, filters out
     * loopback and inactive interfaces, and returns only those with valid
     * IPv4 addresses suitable for network discovery operations. APIPA addresses
     * (169.254.x.x) and localhost (127.x.x.x) are excluded.
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

                if (!netInterface.isUp() || netInterface.isLoopback()) { // skip interfaces that are down or loopback
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = netInterface.getInetAddresses(); // check all IP addresses assigned to this interface
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    if (inetAddress instanceof Inet4Address) { // only process IPv4 addresses
                        String ipAddress = inetAddress.getHostAddress();

                        if (ipAddress.startsWith("127.") || ipAddress.startsWith("169.254.")) { // skip localhost and APIPA addresses
                            continue;
                        }

                        NetworkInterfaceCustom adapter = createAdapter(netInterface, (Inet4Address) inetAddress);

                        if (adapter != null) {
                            adapters.add(adapter);
                            logger.info("Found network adapter: {} - {}", adapter.getName(), adapter.getIpAddress());
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
     * This method extracts relevant information from the system's NetworkInterface
     * and constructs a custom representation suitable for the application's needs.
     * It handles exceptions gracefully and returns null if the adapter cannot be created.
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
            logger.error("Error creating adapter for interface: {}", netInterface.getName(), e);
            return null;
        }
    }

    /**
     * Validates whether a network interface is currently active and usable.
     * This method checks if the specified adapter still exists in the system,
     * is currently active (up), and is not a loopback interface. This is useful
     * to verify that an adapter selected by the user is still valid before
     * attempting a network discovery operation.
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