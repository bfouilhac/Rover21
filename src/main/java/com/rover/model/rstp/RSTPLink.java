package com.rover.model.rstp;

/**
 * Represents a physical link between two switches in the RSTP topology.
 * The link state (active/blocked) is determined dynamically from port states.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public record RSTPLink(RSTPNode sourceNode, int sourcePort, RSTPNode destinationNode, int destinationPort) {

    /**
     * Determines if this link is active (forwarding traffic).
     * A link is considered active only if both ports are in the FORWARDING state.
     *
     * @return true if both ports are forwarding, false otherwise
     */
    public boolean isActive() {
        return getPortState(sourceNode, sourcePort) == RSTPPortState.FORWARDING && getPortState(destinationNode, destinationPort) == RSTPPortState.FORWARDING;
    }

    /**
     * Determines if this link has an unknown state (stale LLDP data).
     * This occurs when the port is not found in RSTP data, typically after
     * a cable is disconnected, but the LLDP cache still holds the entry.
     *
     * @return true if at least one port state is unknown, false otherwise
     */
    public boolean isUnknown() {
        return getPortState(sourceNode, sourcePort) == RSTPPortState.UNKNOWN || getPortState(destinationNode, destinationPort) == RSTPPortState.UNKNOWN;
    }

    /**
     * Gets the port state for a specific port on a node.
     * Returns UNKNOWN if the port is not found, which causes the link to be
     * displayed as blocked (conservative approach for stale LLDP data).
     */
    private RSTPPortState getPortState(RSTPNode node, int portNumber) {
        if (node == null || node.getPorts() == null) {
            return RSTPPortState.UNKNOWN;
        }

        for (RSTPPort port : node.getPorts()) {
            if (port.getPortNumber() == portNumber) {
                return port.getState();
            }
        }

        // Port isn't found in RSTP data - return UNKNOWN to display a link as blocked
        return RSTPPortState.UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("Link{%s[%d] : %s[%d], active=%b}",
                sourceNode.getDeviceName(), sourcePort,
                destinationNode.getDeviceName(), destinationPort, isActive());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RSTPLink rstpLink = (RSTPLink) o;
        return sourcePort == rstpLink.sourcePort &&
                destinationPort == rstpLink.destinationPort &&
                sourceNode.equals(rstpLink.sourceNode) &&
                destinationNode.equals(rstpLink.destinationNode);
    }

}