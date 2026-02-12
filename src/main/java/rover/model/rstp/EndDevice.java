package rover.model.rstp;

/**
 * Represents an end device (PC, printer, etc.) discovered via MAC address table.
 * <p>
 * End devices are detected by finding MAC addresses on switch ports that don't have
 * LLDP neighbors (i.e., they are not other switches).
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public class EndDevice implements TopologyNode {

    private final String macAddress;
    private String name;
    private final RSTPNode connectedSwitch;
    private final int connectedPort;

    /**
     * Creates a new end device.
     *
     * @param macAddress the MAC address of the device (normalized, uppercase)
     * @param connectedSwitch the switch this device is connected to
     * @param connectedPort the port number on the switch
     */
    public EndDevice(String macAddress, RSTPNode connectedSwitch, int connectedPort) {
        this.macAddress = macAddress != null ? macAddress.toUpperCase() : "";
        this.connectedSwitch = connectedSwitch;
        this.connectedPort = connectedPort;
    }

    public void setName(String name) {this.name = name;}

    public String getMacAddress() {
        return macAddress;
    }

    public RSTPNode getConnectedSwitch() {
        return connectedSwitch;
    }

    public int getConnectedPort() {
        return connectedPort;
    }

    /**
     * Returns the display label for this end device.
     * Format: "ICMP\nMAC_ADDRESS"
     *
     * @return formatted display label
     */
    @Override
    public String getDisplayLabel() {
        return name + "\n" + macAddress;
    }

    /**
     * Returns a unique identifier for this end device.
     * Combination of MAC address, switch IP, and port.
     *
     * @return unique identifier string
     */
    @Override
    public String getUniqueId() {
        return macAddress + "@" + connectedSwitch.getIpAddress() + ":" + connectedPort;
    }

    @Override
    public boolean isSwitch() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("EndDevice{mac=%s, switch=%s, port=%d}",
                macAddress, connectedSwitch.getDeviceName(), connectedPort);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndDevice endDevice = (EndDevice) o;
        return macAddress.equals(endDevice.macAddress);
    }

    @Override
    public int hashCode() {
        return macAddress.hashCode();
    }

    public Object getName() { return name;}
}
