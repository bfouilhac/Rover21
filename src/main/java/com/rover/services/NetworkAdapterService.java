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
 * <p>
 * This service provides methods to scan the system for available network interfaces,
 * filter them based on specific criteria (IPv4, active, non-loopback), and validate
 * their status for network discovery operations.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see NetworkInterfaceCustom
 * @see java.net.NetworkInterface
 */
public class NetworkAdapterService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAdapterService.class);

    private static final String LOCALHOST_PREFIX = "127.";
    private static final String APIPA_PREFIX = "169.254."; // Prefix for APIPA (Automatic Private IP Addressing) addresses.

    /**
     * Retrieves all available network interfaces with valid IPv4 addresses.
     * <p>
     * This method scans the system for all network interfaces, filters out
     * loopback and inactive interfaces, and returns only those with valid
     * IPv4 addresses suitable for network discovery operations.
     * </p>
     *
     * @return a list of NetworkInterfaceCustom objects representing valid adapters, or an empty list if no valid adapters are found
     * @see NetworkInterfaceCustom
     * @see #createAdapter(NetworkInterface, Inet4Address)
     */
    public List<NetworkInterfaceCustom> getAvailableAdapters() {
        List<NetworkInterfaceCustom> adapters = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                if (!isInterfaceValid(networkInterface)) {
                    continue; // skip interfaces that are down or loopback
                }

                processInterfaceAddresses(networkInterface, adapters);
            }
        } catch (SocketException e) {
            logger.error("Error scanning network interfaces", e);
        }

        return adapters;
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
            NetworkInterface networkInterface = NetworkInterface.getByName(adapter.getName());
            return networkInterface != null
                    && networkInterface.isUp()
                    && !networkInterface.isLoopback();
        } catch (SocketException e) {
            logger.error("Error checking adapter validity for: {}", adapter.getName(), e);
            return false;
        }
    }

    /**
     * Checks if a network interface is valid for scanning.
     * <p>
     * An interface is valid if it is up (active) and not a loopback interface.
     * </p>
     *
     * @param networkInterface the network interface to check
     * @return true if the interface is valid, false otherwise
     * @throws SocketException if an I/O error occurs
     */
    private boolean isInterfaceValid(NetworkInterface networkInterface) throws SocketException {
        return networkInterface.isUp() && !networkInterface.isLoopback();
    }

    /**
     * Processes all IP addresses assigned to a network interface.
     * <p>
     * This method iterates through all addresses on the interface, filters for
     * valid IPv4 addresses, and creates adapter objects for each valid address.
     * </p>
     *
     * @param networkInterface the network interface to process
     * @param adapters         the list to add valid adapters to
     */
    private void processInterfaceAddresses(NetworkInterface networkInterface,
                                           List<NetworkInterfaceCustom> adapters) {
        Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

        while (inetAddresses.hasMoreElements()) {
            InetAddress inetAddress = inetAddresses.nextElement();

            if (!(inetAddress instanceof Inet4Address)) {
                continue; // only process IPv4 addresses
            }

            String ipAddress = inetAddress.getHostAddress();

            if (isAddressExcluded(ipAddress)) {
                continue; // skip localhost and APIPA addresses
            }

            NetworkInterfaceCustom adapter = createAdapter(networkInterface, (Inet4Address) inetAddress);

            if (adapter != null) {
                adapters.add(adapter);
                logger.info("Found network adapter: {} - {}", adapter.getName(), adapter.getIpAddress());
            }
        }
    }

    /**
     * Checks if an IP address should be excluded from the adapter list.
     * <p>
     * Excluded addresses include localhost (127.x.x.x) and APIPA (169.254.x.x).
     * </p>
     *
     * @param ipAddress the IP address to check
     * @return true if the address should be excluded, false otherwise
     */
    private boolean isAddressExcluded(String ipAddress) {
        return ipAddress.startsWith(LOCALHOST_PREFIX) || ipAddress.startsWith(APIPA_PREFIX);
    }

    /**
     * Creates a NetworkInterfaceCustom object from a system NetworkInterface.
     * <p>
     * This method extracts relevant information from the system's NetworkInterface
     * and constructs a custom representation suitable for the application's needs.
     * It handles exceptions gracefully and returns null if the adapter cannot be created.
     * </p>
     *
     * @param networkInterface the system NetworkInterface to convert
     * @param ipAddress the IPv4 address assigned to this interface
     * @return a new NetworkInterfaceCustom instance, or null if creation fails
     * @see NetworkInterfaceCustom
     */
    private NetworkInterfaceCustom createAdapter(NetworkInterface networkInterface, Inet4Address ipAddress) {
        try {
            String name = networkInterface.getName();
            String displayName = networkInterface.getDisplayName();
            String ip = ipAddress.getHostAddress();

            NetworkInterfaceCustom adapter = new NetworkInterfaceCustom(name, displayName, ip);
            adapter.setUp(networkInterface.isUp());
            adapter.setLoopback(networkInterface.isLoopback());

            return adapter;
        } catch (Exception e) {
            logger.error("Error creating adapter for interface: {}", networkInterface.getName(), e);
            return null;
        }
    }
}