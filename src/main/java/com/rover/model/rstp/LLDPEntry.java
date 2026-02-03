package com.rover.model.rstp;

/**
 * Data Transfer Object representing a raw LLDP neighbor entry.
 * <p>
 * This class serves as an intermediate representation between the raw CLI output
 * and the formal RSTPLink model.
 * </p>
 * @author Benjamin FOUILHAC, ADM21 - February 2026
 */
public record LLDPEntry(String localPort, String remoteMac, String remotePort) {
    // Using Java Record for immutability and conciseness
}