package rover.model.rstp;

/**
 * Represents a single entry from the MAC address table.
 * <p>
 * Parsed from the "show mac address-table" command output.
 * Only dynamic entries on physical ports are relevant for end device discovery.
 * </p>
 *
 * @param type the entry type (Dynamic, Static, Management)
 * @param vlanId the VLAN ID
 * @param macAddress the MAC address (normalized to uppercase with colons)
 * @param port the port number
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public record MacAddressEntry(String type, int vlanId, String macAddress, int port) {

    /**
     * Checks if this entry represents a dynamic (learned) MAC address.
     *
     * @return true if the entry is dynamic
     */
    public boolean isDynamic() {
        return "dynamic".equalsIgnoreCase(type);
    }

    /**
     * Checks if this entry is on a valid physical port (not CPU or management).
     *
     * @return true if on a physical port
     */
    public boolean isOnPhysicalPort() {
        return port > 0;
    }

    @Override
    public String toString() {
        return String.format("MacEntry{type=%s, vlan=%d, mac=%s, port=%d}",
                type, vlanId, macAddress, port);
    }
}
