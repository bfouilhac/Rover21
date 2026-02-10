package com.rover.model.rstp;

/**
 * Spanning Tree Protocol version.
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public enum RSTPVersion {
    UNKNOWN("Unknown"),
    STP("STP"),
    RSTP("RSTP"),
    MSTP("MSTP");

    private final String displayName;

    RSTPVersion(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}