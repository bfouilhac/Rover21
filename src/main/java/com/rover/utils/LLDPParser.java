package com.rover.utils;

import com.rover.model.rstp.LLDPEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing LLDP neighbor discovery outputs from PLANET switches.
 * <p>
 * Handles two distinct formats:
 * 1. Tabular format (e.g., gi1 | MAC | PortID)
 * 2. Detailed block format (e.g., Local Interface: ..., Chassis ID: ...)
 * </p>
 * @author Benjamin FOUILHAC ADM21 - January/February 2026
 */
public class LLDPParser {

    // Pattern for Table Format: Capture Group 1: Port, 2: MAC, 3: Remote Port
    private static final Pattern TABLE_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\S+)\\s*\\|\\s*([0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2})\\s*\\|\\s*(\\S+)"
    );

    // Patterns for Block Format
    private static final Pattern BLOCK_LOCAL_PORT = Pattern.compile("Local Interface\\s*:\\s*(.*)");
    private static final Pattern BLOCK_CHASSIS_ID = Pattern.compile("Chassis ID\\s*:\\s*([0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2})");
    private static final Pattern BLOCK_REMOTE_PORT = Pattern.compile("Port ID\\s*:\\s*(.*)");

    private LLDPParser(){}

    /**
     * Main entry point for parsing. Detects the format and returns a list of LLDP entries.
     * @param input The raw string response from the switch
     * @return List of discovered neighbors, never null
     */
    public static List<LLDPEntry> parse(String input) {
        if (input == null || input.isBlank()) return List.of();

        // Heuristic to detect format: Table format contains separators "|"
        if (input.contains("|")) {
            return parseTableFormat(input);
        } else {
            return parseBlockFormat(input);
        }
    }

    private static List<LLDPEntry> parseTableFormat(String input) {
        List<LLDPEntry> entries = new ArrayList<>();
        String[] lines = input.split("\\R"); // Split by any newline sequence

        for (String line : lines) {
            Matcher m = TABLE_LINE_PATTERN.matcher(line);
            if (m.find()) {
                entries.add(new LLDPEntry(
                        m.group(1).trim(),
                        normalizeMac(m.group(2)),
                        m.group(3).trim()
                ));
            }
        }
        return entries;
    }

    private static List<LLDPEntry> parseBlockFormat(String input) {
        List<LLDPEntry> entries = new ArrayList<>();
        String[] lines = input.split("\\R");

        String currentLocal = null;
        String currentMac = null;
        String currentRemote;

        for (String line : lines) {
            Matcher mLocal = BLOCK_LOCAL_PORT.matcher(line);
            Matcher mMac = BLOCK_CHASSIS_ID.matcher(line);
            Matcher mRemote = BLOCK_REMOTE_PORT.matcher(line);

            if (mLocal.find()) {
                // When we find a new "Local Interface", it might be the start of a new block.
                // If we already had data, we save it (though in the LLDP block, the order is usually Local -> Chassis -> Port)
                currentLocal = mLocal.group(1).trim();
            } else if (mMac.find()) {
                currentMac = normalizeMac(mMac.group(1));
            } else if (mRemote.find()) {
                currentRemote = mRemote.group(1).trim();

                // Once we have all 3 for a block, we record it
                if (currentLocal != null && currentMac != null) {
                    entries.add(new LLDPEntry(currentLocal, currentMac, currentRemote));
                    // Reset for next block, keeping Local Port if multiple neighbors are on the same port (rare but possible)
                    currentMac = null;
                }
            }
        }
        return entries;
    }

    /**
     * Normalizes MAC address to UPCASE with Colons (e.g., A1:B2:C3:D4:E5:F6).
     * Necessary for reliable Map lookups.
     * @param mac raw MAC string
     * @return standardized MAC string
     */
    public static String normalizeMac(String mac) {
        if (mac == null) return "";
        return mac.toUpperCase().replace("-", ":").trim();
    }
}