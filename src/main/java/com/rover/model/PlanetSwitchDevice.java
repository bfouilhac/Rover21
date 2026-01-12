package com.rover.model;

import java.util.Objects;

/**
 * Represents a PLANET network switch discovered on the local network.
 * <p>
 * This class encapsulates all information about a PLANET switch that can be retrieved
 * through multiple discovery methods:
 * <ul>
 *   <li>UDP broadcast discovery (MAC, IP, gateway, firmware version)</li>
 *   <li>SNMP queries (system location, uptime)</li>
 * </ul>
 * </p>
 * <p>
 * Each device is uniquely identified by its MAC address, which is used for
 * equality comparison and deduplication during network scans.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see com.rover.services.PlanetDiscoveryService
 * @see com.rover.services.SNMPService
 */
public class PlanetSwitchDevice {

    private String macAddress;
    private String deviceName;
    private String version;
    private String ipAddress;
    private String netMask;
    private String gateway;
    private String description;
    private boolean isOnline;
    private String systemLocation;
    private String systemUptime;

    /**
     * Constructs a new empty PlanetSwitchDevice.
     * <p>
     * By default, the device is considered offline (isOnline = false).
     * All other fields are initialized to null and should be populated
     * during the discovery process.
     * </p>
     */
    public PlanetSwitchDevice() {
        this.isOnline = false;
    }

    /**
     * Returns the MAC address of the switch.
     *
     * @return the MAC address in format "XX:XX:XX:XX:XX:XX"
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * Returns the device model name.
     *
     * @return the device name (e.g., "IGS-6325-16P4S")
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Returns the firmware version of the device.
     *
     * @return the firmware version string (e.g., "v1.440b300826")
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the IPv4 address of the device.
     *
     * @return the IP address as a string (e.g., "192.168.1.100")
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Returns the network mask of the device.
     *
     * @return the netmask (e.g., "255.255.255.0")
     */
    public String getNetMask() {
        return netMask;
    }

    /**
     * Returns the default gateway configured on the device.
     *
     * @return the gateway IP address
     */
    public String getGateway() {
        return gateway;
    }

    /**
     * Returns the device description.
     *
     * @return additional information about the device
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the system location retrieved via SNMP.
     *
     * @return the location string (e.g., "Building A, Floor 2") or "N/A" if not available
     */
    public String getSystemLocation() {
        return systemLocation;
    }

    /**
     * Returns the system uptime retrieved via SNMP.
     *
     * @return the uptime in format "DD:HH:MM:SS" or "N/A" if not available
     */
    public String getSystemUptime() {return systemUptime;}

    /**
     * Sets the MAC address of the switch.
     *
     * @param macAddress the MAC address in format "XX:XX:XX:XX:XX:XX"
     */
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    /**
     * Sets the device model name.
     *
     * @param deviceName the device name (e.g., "IGS-6325-16P4S")
     */
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    /**
     * Sets the firmware version.
     *
     * @param version the firmware version string (e.g., "v1.440b300826")
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Sets the IPv4 address of the device.
     *
     * @param ipAddress the IP address (e.g., "192.168.1.100")
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Sets the network mask.
     *
     * @param netMask the netmask (e.g., "255.255.255.0")
     */
    public void setNetMask(String netMask) {
        this.netMask = netMask;
    }

    /**
     * Sets the default gateway.
     *
     * @param gateway the gateway IP address
     */
    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    /**
     * Sets the device description.
     *
     * @param description additional information about the device
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the online status of the device.
     *
     * @param online true if the device is online and responding, false otherwise
     */
    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    /**
     * Sets the system location retrieved via SNMP.
     *
     * @param systemLocation the location string (e.g., "Building A, Floor 2")
     */
    public void setSystemLocation(String systemLocation) {
        this.systemLocation = systemLocation;
    }

    /**
     * Sets the system uptime retrieved via SNMP.
     *
     * @param systemUptime the uptime in format "DD:HH:MM:SS"
     */
    public void setSystemUptime(String systemUptime) {
        this.systemUptime = systemUptime;
    }

    /**
     * Returns a string representation of this device with key information.
     *
     * @return a formatted string containing MAC, device name, IP, location, uptime, and online status
     */
    @Override
    public String toString() {
        return "NetworkDevice{" +
                "macAddress='" + macAddress + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", systemLocation='" + systemLocation + '\'' +
                ", systemUptime='" + systemUptime + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }

    /**
     * Compares this device with another object for equality.
     * <p>
     * Two PlanetSwitchDevice objects are considered equal if they have the same MAC address.
     * This ensures that duplicate devices discovered through multiple methods are properly
     * identified and deduplicated.
     * </p>
     *
     * @param o the object to compare with
     * @return true if both devices have the same MAC address, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanetSwitchDevice that = (PlanetSwitchDevice) o;
        return Objects.equals(macAddress, that.macAddress);
    }

    /**
     * Returns a hash code value for this device.
     * <p>
     * The hash code is computed based solely on the MAC address,
     * ensuring consistency with the {@link #equals(Object)} method.
     * </p>
     *
     * @return a hash code value for this device
     * @see #equals(Object)
     */
    @Override
    public int hashCode() {
        return Objects.hash(macAddress);
    }
}