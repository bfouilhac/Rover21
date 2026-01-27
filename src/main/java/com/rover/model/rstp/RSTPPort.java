package com.rover.model.rstp;

/**
 * Represents a single port on a switch with its RSTP state and role.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class RSTPPort {

    private final int portNumber;
    private RSTPPortState state;
    private RSTPPortRole role;
    private int pathCost;

    public RSTPPort(int portNumber) {
        this.portNumber = portNumber;
        this.state = RSTPPortState.UNKNOWN;
        this.role = RSTPPortRole.UNKNOWN;
    }

    public void setState(RSTPPortState state) {
        this.state = state;
    }

    public void setRole(RSTPPortRole role) {this.role = role;}

    public void setPathCost(int pathCost) {
        this.pathCost = pathCost;
    }

    @Override
    public String toString() {
        return String.format("Port %d: %s (%s) - Cost: %d",
                portNumber, state, role, pathCost);
    }
}