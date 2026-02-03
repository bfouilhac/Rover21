package com.rover.model.rstp;

/**
 * Represents a physical link between two switches in the RSTP topology.
 * The link state (active/blocked) is determined dynamically from port states.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class RSTPLink {

    private final RSTPNode sourceNode;
    private final int sourcePort;
    private final RSTPNode destinationNode;
    private final int destinationPort;

    public RSTPLink(RSTPNode sourceNode, int sourcePort, RSTPNode destinationNode, int destinationPort) {
        this.sourceNode = sourceNode;
        this.sourcePort = sourcePort;
        this.destinationNode = destinationNode;
        this.destinationPort = destinationPort;
    }

    public RSTPNode getSourceNode() { return sourceNode; }
    public int getSourcePort() { return sourcePort; }
    public RSTPNode getDestinationNode() { return destinationNode; }
    public int getDestinationPort() { return destinationPort; }

    /**
     * Determines if this link is active (forwarding traffic).
     * A link is considered blocked if either port is in a non-forwarding state.
     *
     * @return true if both ports are forwarding, false if blocked
     */
    public boolean isActive() {
        RSTPPortState srcState = getPortState(sourceNode, sourcePort);
        RSTPPortState dstState = getPortState(destinationNode, destinationPort);

        // Link is active only if both ports are forwarding
        return srcState == RSTPPortState.FORWARDING && dstState == RSTPPortState.FORWARDING;
    }

    /**
     * Checks if this link is blocked by RSTP.
     *
     * @return true if at least one port is blocking/discarding
     */
    public boolean isBlocked() {
        RSTPPortState srcState = getPortState(sourceNode, sourcePort);
        RSTPPortState dstState = getPortState(destinationNode, destinationPort);

        return isBlockedState(srcState) || isBlockedState(dstState);
    }

    /**
     * Gets the port state for a specific port on a node.
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

        // Port not found in RSTP data - assume forwarding (LLDP detected the link)
        return RSTPPortState.FORWARDING;
    }

    /**
     * Checks if a port state represents a blocked/non-forwarding state.
     */
    private boolean isBlockedState(RSTPPortState state) {
        return state == RSTPPortState.BLOCKING
                || state == RSTPPortState.DISCARDING
                || state == RSTPPortState.DISABLED
                || state == RSTPPortState.LISTENING
                || state == RSTPPortState.LEARNING;
    }

    /**
     * Gets a short name for the source node (for display).
     */
    public String getSourceShortName() {
        return getShortName(sourceNode);
    }

    /**
     * Gets a short name for the destination node (for display).
     */
    public String getDestinationShortName() {
        return getShortName(destinationNode);
    }

    private String getShortName(RSTPNode node) {
        if (node == null) return "?";
        String name = node.getDeviceName();
        if (name == null || name.isEmpty()) return "?";
        // Return first 8 chars max
        return name.length() > 8 ? name.substring(0, 8) : name;
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

    @Override
    public int hashCode() {
        int result = sourceNode.hashCode();
        result = 31 * result + sourcePort;
        result = 31 * result + destinationNode.hashCode();
        result = 31 * result + destinationPort;
        return result;
    }
}