package rover.model.rstp;

/**
 * Represents a link between a switch and an end device in the topology.
 * <p>
 * Unlike RSTPLink (switch-to-switch), this link connects a switch port to an end device
 * discovered via the MAC address table.
 * </p>
 *
 * @param switchNode the switch node
 * @param switchPort the port number on the switch
 * @param endDevice the connected end device
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public record EndDeviceLink(RSTPNode switchNode, int switchPort, EndDevice endDevice) implements TopologyLink {

    @Override
    public TopologyNode getSourceNode() {
        return switchNode;
    }

    @Override
    public TopologyNode getDestinationNode() {
        return endDevice;
    }

    @Override
    public int getSourcePort() {
        return switchPort;
    }

    @Override
    public int getDestinationPort() {
        return 0;
    }

    @Override
    public boolean isSwitchLink() {
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String toString() {
        return String.format("EndDeviceLink{%s[%d] -> %s}",
                switchNode.getDeviceName(), switchPort, endDevice.getMacAddress());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndDeviceLink that = (EndDeviceLink) o;
        return switchPort == that.switchPort &&
                switchNode.equals(that.switchNode) &&
                endDevice.equals(that.endDevice);
    }
}
