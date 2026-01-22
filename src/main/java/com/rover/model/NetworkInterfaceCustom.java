package com.rover.model;

import java.util.Objects;

/**
 * Represents a custom network interface with relevant information for network discovery.
 * <p>
 * This class encapsulates the essential properties of a network interface, including
 * its system name, display name, IPv4 address, and operational status. It provides
 * validation methods to determine if the interface is suitable for PLANET switch
 * discovery operations.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see java.net.NetworkInterface
 * @see com.rover.services.NetworkAdapterService
 */
public class NetworkInterfaceCustom {

    /** Regular expression pattern for validating IPv4 addresses. */
    public static final String IPV4_PATTERN =
            "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}" +
                    "(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";

    /** Prefix for loopback addresses (localhost). */
    private static final String LOOPBACK_PREFIX = "127.";

    private final String name;
    private final String displayName;
    private final String ipAddress;
    private boolean isUp;
    private boolean isLoopback;

    /**
     * Constructs a new NetworkInterfaceCustom with the specified properties.
     * <p>
     * By default, the interface is considered active (up) and not a loopback interface.
     * These values can be modified using the setter methods.
     * </p>
     *
     * @param name        the system name of the interface (e.g., "eth0", "wlan0")
     * @param displayName the human-readable display name (e.g., "Realtek PCIe Family Controller")
     * @param ipAddress   the IPv4 address assigned to this interface (e.g., "192.168.1.100")
     */
    public NetworkInterfaceCustom(String name, String displayName, String ipAddress) {
        this.name = name;
        this.displayName = displayName;
        this.ipAddress = ipAddress;
        this.isUp = true;
        this.isLoopback = false;
    }

    /**
     * Returns the system name of the network interface.
     *
     * @return the interface system name (e.g., "eth0", "wlan0")
     */
    public String getName() {return name;}

    /**
     * Returns the IPv4 address assigned to this interface.
     *
     * @return the IPv4 address as a string (e.g., "192.168.1.100")
     */
    public String getIpAddress() {return ipAddress;}

    /**
     * Sets the operational status of the interface.
     *
     * @param up true if the interface is active, false otherwise
     */
    public void setUp(boolean up) {this.isUp = up;}

    /**
     * Sets whether this interface is a loopback interface.
     *
     * @param loopback true if this is a loopback interface, false otherwise
     */
    public void setLoopback(boolean loopback) {this.isLoopback = loopback;}

    /**
     * Builds a formatted display string containing the IP address and display name.
     * <p>
     * The format is: "IPv4Address - DisplayName". If the display name is identical
     * to the system name or is null, only the IP address is returned.
     * </p>
     *
     * @return a formatted string representation suitable for UI display
     */
    public String toDisplayString() {
        StringBuilder builder = new StringBuilder();

        if (ipAddress != null) {
            builder.append(ipAddress);
        }

        if (displayName != null && !displayName.equals(name)) {
            builder.append(" - ").append(displayName);
        }

        return builder.toString();
    }

    /**
     * Determines whether this interface is valid for network discovery operations.
     *
     * @return true if the interface is valid for scanning, false otherwise
     * @see #validateIpv4Address(String)
     */
    public boolean isValidForScanning() {
        return isUp
                && !isLoopback
                && ipAddress != null
                && !ipAddress.isEmpty()
                && !ipAddress.startsWith(LOOPBACK_PREFIX)
                && validateIpv4Address(ipAddress);
    }

    /**
     * Validates an IPv4 address format using a regular expression.
     * <p>
     * This method checks if the provided string matches the standard IPv4 format:
     * four octets separated by dots, with each octet ranging from 0 to 255.
     * </p>
     *
     * @param ipAddress the IP address string to validate
     * @return true if the address matches the IPv4 format, false otherwise
     */
    private boolean validateIpv4Address(String ipAddress) {return ipAddress.matches(IPV4_PATTERN);}

    /**
     * Returns a string representation of this network interface.
     * <p>
     * This method delegates to {@link #toDisplayString()} to provide a formatted
     * representation suitable for display in UI components like ComboBox.
     * </p>
     *
     * @return a formatted string representation of the interface
     * @see #toDisplayString()
     */
    @Override
    public String toString() {return toDisplayString();}

    /**
     * Compares this network interface with another object for equality.
     * <p>
     * Two NetworkInterfaceCustom objects are considered equal if they have
     * the same system name and IP address. The display name and status flags
     * are not considered in the equality comparison.
     * </p>
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NetworkInterfaceCustom other = (NetworkInterfaceCustom) obj;
        return Objects.equals(name, other.name) && Objects.equals(ipAddress, other.ipAddress);
    }

    /**
     * Returns a hash code value for this network interface.
     * <p>
     * The hash code is computed based on the system name and IP address,
     * ensuring consistency with the {@link #equals(Object)} method.
     * </p>
     *
     * @return a hash code value for this object
     * @see #equals(Object)
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, ipAddress);
    }
}