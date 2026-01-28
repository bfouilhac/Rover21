package com.rover.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for MAC address table from PLANET switches.
 * Supports multiple output formats.
 *
 * @author Benjamin FOUILHAC, ADM21 - January 2026
 */
public class MACTableParser {

    private static final Logger logger = LoggerFactory.getLogger(MACTableParser.class);

    // Format 1: "1 | A8:F7:E0:DE:4E:BE | Dynamic | gi8"
    private static final Pattern FORMAT1_PATTERN = Pattern.compile(
            "\\s*(\\d+)\\s*\\|\\s*([A-F0-9:]{17})\\s*\\|\\s*(\\w+)\\s*\\|\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE
    );

    // Format 2: "Dynamic 1 a8:f7:e0:b7:e0:ad GigabitEthernet 1/2"
    private static final Pattern FORMAT2_PATTERN = Pattern.compile(
            "^\\s*(\\w+)\\s+(\\d+)\\s+([a-f0-9:]{17})\\s+(\\S+.*?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses MAC address table output.
     *
     * @param output raw command output
     * @return list of MAC table entries
     */
    public List<MACTableEntry> parse(String output) {
        List<MACTableEntry> entries = new ArrayList<>();

        if (output == null || output.trim().isEmpty()) {
            return entries;
        }

        String[] lines = output.split("\n");
        boolean format1Detected = output.contains("|");

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip headers and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("VID") ||
                    trimmed.startsWith("Type") || trimmed.contains("------") ||
                    trimmed.toLowerCase().contains("total number")) {
                continue;
            }

            MACTableEntry entry = format1Detected ?
                    parseFormat1(trimmed) : parseFormat2(trimmed);

            if (entry != null && entry.getType().equalsIgnoreCase("dynamic")) {
                entries.add(entry);
                logger.debug("Parsed MAC entry: {} on port {}", entry.getMacAddress(), entry.getPort());
            }
        }

        logger.info("Parsed {} dynamic MAC entries", entries.size());
        return entries;
    }

    /**
     * Parses Format 1: pipe-separated values.
     */
    private MACTableEntry parseFormat1(String line) {
        Matcher matcher = FORMAT1_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        try {
            int vlan = Integer.parseInt(matcher.group(1));
            String mac = matcher.group(2).toUpperCase();
            String type = matcher.group(3);
            String port = normalizePort(matcher.group(4));

            return new MACTableEntry(vlan, mac, type, port);
        } catch (Exception e) {
            logger.warn("Failed to parse Format1 line: {}", line);
            return null;
        }
    }

    /**
     * Parses Format 2: space-separated values.
     */
    private MACTableEntry parseFormat2(String line) {
        Matcher matcher = FORMAT2_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        try {
            String type = matcher.group(1);
            int vlan = Integer.parseInt(matcher.group(2));
            String mac = matcher.group(3).toUpperCase().replace("-", ":");
            String port = normalizePort(matcher.group(4));

            return new MACTableEntry(vlan, mac, type, port);
        } catch (Exception e) {
            logger.warn("Failed to parse Format2 line: {}", line);
            return null;
        }
    }

    /**
     * Normalizes port names to a common format.
     * "gi8" -> "gi8", "GigabitEthernet 1/2" -> "gi1/2"
     */
    private String normalizePort(String port) {
        if (port == null) return null;

        port = port.trim();

        // Skip CPU, multi-port, or invalid entries
        if (port.equalsIgnoreCase("CPU") || port.contains("-") || port.contains(" ")) {
            // "GigabitEthernet 1/2" -> "gi1/2"
            if (port.toLowerCase().startsWith("gigabitethernet")) {
                String portNum = port.replaceAll("(?i)gigabitethernet\\s*", "");
                return "gi" + portNum;
            }
            return null;
        }

        return port.toLowerCase();
    }

    /**
     * Represents a single MAC table entry.
     */
    public static class MACTableEntry {
        private final int vlan;
        private final String macAddress;
        private final String type;
        private final String port;

        public MACTableEntry(int vlan, String macAddress, String type, String port) {
            this.vlan = vlan;
            this.macAddress = macAddress;
            this.type = type;
            this.port = port;
        }

        public int getVlan() { return vlan; }
        public String getMacAddress() { return macAddress; }
        public String getType() { return type; }
        public String getPort() { return port; }

        @Override
        public String toString() {
            return String.format("MAC{%s on %s (VLAN %d)}", macAddress, port, vlan);
        }
    }
}