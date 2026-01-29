package com.rover.model.rstp;

/**
 * Represents a physical link between two switches in the RSTP topology.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPLink {

    private final RSTPNode sourceNode;
    private final int sourcePort;
    private final RSTPNode destinationNode;
    private final int destinationPort;

    private final boolean active;

    public RSTPLink(RSTPNode sourceNode, int sourcePort, RSTPNode destinationNode, int destinationPort) {
        this.sourceNode = sourceNode;
        this.sourcePort = sourcePort;
        this.destinationNode = destinationNode;
        this.destinationPort = destinationPort;
        this.active = true;
    }

    public RSTPNode getSourceNode() { return sourceNode; }
    public int getSourcePort() { return sourcePort; }
    public RSTPNode getDestinationNode() { return destinationNode; }
    public int getDestinationPort() { return destinationPort; }
    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return String.format("Link{%s[%d] : %s[%d], active=%b}",
                sourceNode.getDeviceName(), sourcePort,
                destinationNode.getDeviceName(), destinationPort, active);
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