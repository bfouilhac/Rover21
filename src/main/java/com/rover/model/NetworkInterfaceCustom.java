package com.rover.model;

import java.util.Objects;

/**
 * Customized class representing a network interface.
 * Allows you to manage the various pieces of information needed to process network interfaces in the application.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class NetworkInterfaceCustom {

    private final String name; // Interface name
    private final String displayName; // Interface name that will be displayed (ex: Realtek PCIe Family Controller)
    private final String ipAddress; // IPv4 Address linked to the specific interface
    private boolean isUp; // Shows whether its enabled or disabled
    private boolean isLoopback; // Shows whether its enabled or not

    // Getters
    public String getName() {return name;}
    public String getIpAddress() {return ipAddress;}

    // Setters
    public void setUp(boolean up) {this.isUp = up;}
    public void setLoopback(boolean loopback) {this.isLoopback = loopback;}

    /**
     * Constructor, initialize attributs by the parameters, sets isUp true and isLoopback false by default.
     * @param name Interface name.
     * @param displayName Interface name that will be displayed (ex: Realtek PCIe Family Controller).
     * @param ipAddress IPv4 Address linked to the specific interface.
     */
    public NetworkInterfaceCustom(String name, String displayName, String ipAddress) {
        this.name = name;
        this.displayName = displayName;
        this.ipAddress = ipAddress;
        this.isUp = true;
        this.isLoopback = false;
    }

    /**
     * Build a formatted string that contains : IPv4 Address and DisplayName for this interface.
     * @return The string that was created and validated.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();

        if (ipAddress != null) {
            sb.append(ipAddress);
        }

        if (displayName != null && !displayName.equals(name)) {
            sb.append(" - ").append(displayName);
        }
        return sb.toString();
    }

    /**
     * Checks if this interface is valid for a discovery.
     * Basically : active, IPv4 that is valid and not loopback.
     * @return whether the conditions are true or not.
     */
    public boolean isValidForScanning() {
        return isUp &&
                !isLoopback &&
                ipAddress != null &&
                !ipAddress.isEmpty() &&
                !ipAddress.startsWith("127.") &&
                validateIPv4Address(ipAddress);
    }

    /**
     * Small method that checks if a specific IPv4 Address is correct.
     * @param ipAddress the address you want to check.
     * @return True if it matches the pattern, false either.
     * @see String#matches(String)
     */
    private boolean validateIPv4Address(String ipAddress) {
        return ipAddress.matches("^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$");
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkInterfaceCustom that = (NetworkInterfaceCustom) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(ipAddress, that.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ipAddress);
    }
}
