package com.rover.model.rstp;

/**
 * RSTP port roles as defined in IEEE 802.1w.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public enum RSTPPortRole {
    UNKNOWN("Unknown"),
    ROOT("Root"),
    DESIGNATED("Designated"),
    ALTERNATE("Alternate"),
    BACKUP("Backup"),
    DISABLED("Disabled");

    private final String displayName;

    RSTPPortRole(String displayName) {this.displayName = displayName;}

    @Override
    public String toString() {
        return displayName;
    }
}
