package com.rover.model;

import java.util.Objects;

/**
 * Represents a PLANET network switch discovered on the local network.
 * <p>
 * This class encapsulates all information about a PLANET switch that can be retrieved
 * through multiple discovery methods
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see com.rover.services.PlanetDiscoveryService
 * @see com.rover.services.SNMPService
 */
public class PlanetSwitchDevice {

    private String macAddress;
    private String deviceName; // Device model name (e.g., "IGS-6325-16P4S").
    private String version; // Firmware version string (e.g., "v1.440b300826").
    private String ipAddress;
    private String netMask; // Network mask (e.g., "255.255.255.0").
    private String gateway;
    private String description; //** Additional device description.
    private String username;
    private String password;

    private boolean isOnline;

    private boolean isTelnetUp = false;
    private boolean isSSHUp = false;
    private boolean isHTTPUp = false;
    private boolean isHTTPSUp = false;
    private boolean isSNMPUp = false;

    // SNMP Data
    private String systemLocation; // System location retrieved via SNMP (e.g., "Building A, Floor 2").
    private String systemUptime; // System uptime in format "DD:HH:MM:SS".

    /**
     * Constructs a new empty PlanetSwitchDevice.
     * <p>
     * By default, the device is considered offline. All other fields are initialized
     * to null and should be filled during the discovery process.
     * </p>
     */
    public PlanetSwitchDevice() {this.isOnline = false;}

    /**
     * Returns the username of the admin account for this device
     *
     * @return the username as a String
     */
    public String getUsername(){return this.username;}

    /**
     * Returns the password of the admin account for this device
     *
     * @return the password as a String
     */
    public String getPassword(){return this.password;}


    /**
     * Returns the MAC address of the switch.
     *
     * @return the MAC address in format "XX:XX:XX:XX:XX:XX", or null if not set
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * Returns the device model name.
     *
     * @return the device name (e.g., "IGS-6325-16P4S"), or null if not set
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Returns the firmware version of the device.
     *
     * @return the firmware version string (e.g., "v1.440b300826"), or null if not set
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the IPv4 address of the device.
     *
     * @return the IP address as a string (e.g., "192.168.1.100"), or null if not set
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Returns the network mask of the device.
     *
     * @return the netmask (e.g., "255.255.255.0"), or null if not set
     */
    public String getNetMask() {
        return netMask;
    }

    /**
     * Returns the default gateway configured on the device.
     *
     * @return the gateway IP address, or null if not set
     */
    public String getGateway() {
        return gateway;
    }

    /**
     * Returns the device description.
     *
     * @return additional information about the device, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the system location retrieved via SNMP.
     *
     * @return the location string (e.g., "Building A, Floor 2"), or "N/A" if not available
     */
    public String getSystemLocation() {
        return systemLocation;
    }

    /**
     * Returns the system uptime retrieved via SNMP.
     *
     * @return the uptime in format "DD:HH:MM:SS", or "N/A" if not available
     */
    public String getSystemUptime() {
        return systemUptime;
    }

    /**
     * Returns if Telnet is enabled on this device.
     *
     * @return either true or false
     */
    public boolean isTelnetUp() {return isTelnetUp;}

    /**
     * Returns if SSH is enabled on this device.
     *
     * @return either true or false
     */
    public boolean isSSHUp() {return isSSHUp;}

    /**
     * Returns if HTTP is enabled on this device.
     *
     * @return either true or false
     */
    public boolean isHTTPUp() {return isHTTPUp;}

    /**
     * Returns if HTTPS is enabled on this device.
     *
     * @return either true or false
     */
    public boolean isHTTPSUp() {return isHTTPSUp;}

    /**
     * Returns if SNMP is enabled on this device.
     *
     * @return either true or false
     */
    public boolean isSNMPUp() {return isSNMPUp;}

    /**
     * Sets the username of the admin account for this device
     */
    public void setUsername(String username){this.username = username;}

    /**
     * Sets the password of the admin account for this device
     */
    public void setPassword(String password){this.password = password;}

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
     * Sets to true if Telnet is enabled on this device
     *
     * @param isTelnetUp the result of the protocol test
     */
    public void setTelnetUp(boolean isTelnetUp) {this.isTelnetUp = isTelnetUp;}

    /**
     * Sets to true if SSH is enabled on this device
     *
     * @param isSSHUp the result of the protocol test
     */
    public void setSSHUp(boolean isSSHUp) {this.isSSHUp = isSSHUp;}

    /**
     * Sets to true if HTTP is enabled on this device
     *
     * @param isHTTPUp the result of the protocol test
     */
    public void setHTTPUp(boolean isHTTPUp) {this.isHTTPUp = isHTTPUp;}

    /**
     * Sets to true if HTTPS is enabled on this device
     *
     * @param isHTTPSUp the result of the protocol test
     */
    public void setHTTPSUp(boolean isHTTPSUp) {this.isHTTPSUp = isHTTPSUp;}

    /**
     * Sets to true if SNMP is enabled on this device
     *
     * @param isSNMPUp the result of the protocol test
     */
    public void setSNMPUp(boolean isSNMPUp) {this.isSNMPUp = isSNMPUp;}




    /**
     * Returns a string representation of this device with key information.
     * <p>
     * The string includes MAC address, device name, IP address, system location,
     * system uptime, and online status.
     * </p>
     *
     * @return a formatted string containing the main device properties
     */
    @Override
    public String toString() {
        return "PlanetSwitchDevice{" +
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
     * @param obj the object to compare with
     * @return true if both devices have the same MAC address, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PlanetSwitchDevice other = (PlanetSwitchDevice) obj;
        return Objects.equals(macAddress, other.macAddress);
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