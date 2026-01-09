package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * Service for querying PLANET switches via SNMP protocol.
 * Retrieves additional device information that isn't available via UDP discovery.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class SNMPService {

    private static final Logger logger = LoggerFactory.getLogger(SNMPService.class);

    // SNMP configuration
    private static final String DEFAULT_COMMUNITY = "public";

    // Standard OIDs (Object Identifiers)
    private static final String OID_SYS_UPTIME = "1.3.6.1.2.1.1.3.0"; // System uptime
    private static final String OID_SYS_LOCATION = "1.3.6.1.2.1.1.6.0"; // System location
    // 250ms
    private int retries = 2;
    private int portSNMP = 161;

    private Snmp snmp;

    /**
     * Constructor initializes SNMP transport.
     */
    public SNMPService() {
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            this.snmp = new Snmp(transport);
            transport.listen();
            logger.info("SNMP service initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize SNMP service", e);
        }
    }

    /**
     * Enriches a device with SNMP data.
     * Queries the device via SNMP and adds additional information
     * @param device the device to enrich
     * @return the enriched device, or original device if SNMP fails
     */
    public PlanetSwitchDevice enrichDeviceWithSNMP(PlanetSwitchDevice device) {
        return enrichDeviceWithSNMP(device, DEFAULT_COMMUNITY);
    }

    /**
     * Enriches a device with SNMP data using a specific community string.
     * @param device the device to enrich
     * @param community the SNMP community string (default: "public")
     * @return the enriched device, or original device if SNMP fails
     */
    public PlanetSwitchDevice enrichDeviceWithSNMP(PlanetSwitchDevice device, String community) {
        if (device == null || device.getIpAddress() == null) {
            logger.warn("Cannot enrich null device or device without IP");
            return device;
        }
        logger.info("Enriching device {} via SNMP", device.getIpAddress());
        try {
            CommunityTarget target = createTarget(device.getIpAddress(), community); // Create an SNMP target
            String sysLocation = getSNMPValue(target, OID_SYS_LOCATION); // Query SystemLocation
            if (sysLocation != null && !sysLocation.isEmpty()) {
                device.setSystemLocation(sysLocation);
                logger.info("System Location: {}", sysLocation);
            } else {
                device.setSystemLocation("N/A");
                logger.debug("System Location not set");
            }
            String sysUptime = getSNMPValue(target, OID_SYS_UPTIME); // Query SystemUptime
            if (sysUptime != null && !sysUptime.isEmpty()) {
                String formattedUptime = formatUptime(sysUptime); // Convert centiseconds to human-readable format (better)
                device.setSystemUptime(formattedUptime);
                logger.info("System Uptime: {} ({})", sysUptime, formattedUptime);
            } else {
                device.setSystemUptime("N/A");
                logger.debug("System Uptime not available");
            }
            logger.info("Device {} enriched successfully via SNMP", device.getIpAddress());
        } catch (Exception e) { // Set default values on error or exception
            logger.error("Failed to enrich device {} via SNMP: {}", device.getIpAddress(), e.getMessage());
            if (device.getSystemLocation() == null) {
                device.setSystemLocation("N/A");
            }
            if (device.getSystemUptime() == null) {
                device.setSystemUptime("N/A");
            }
        }
        return device;
    }

    /**
     * Formats SNMP uptime to format: days:hours:minutes:seconds
     * @param uptime the uptime string from SNMP
     * @return formatted uptime string (e.g., "01:22:19:08")
     */
    private String formatUptime(String uptime) {
        try {
            int days = 0;
            String timePart = uptime;
            if (uptime.contains("day")) { // Check if it contains "day" or "days"
                String[] parts = uptime.split("day"); // Extract days number
                String daysPart = parts[0].trim();
                days = Integer.parseInt(daysPart.replaceAll("[^0-9]", "")); // Extract the number from the beginning
                if (parts.length > 1) { // Get the time part after "day(s),"
                    timePart = parts[1].replace("s,", "").replace(",", "").trim();
                }
            }

            String[] timeParts = timePart.split(":"); // Parse the time part (HH:MM:SS.cc or H:MM:SS.cc)

            if (timeParts.length >= 3) {
                int hours = Integer.parseInt(timeParts[0].trim());
                int minutes = Integer.parseInt(timeParts[1].trim());
                String secondsPart = timeParts[2].trim();
                int seconds;
                if (secondsPart.contains(".")) {
                    seconds = Integer.parseInt(secondsPart.split("\\.")[0]);
                } else {
                    seconds = Integer.parseInt(secondsPart);
                }

                return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse uptime: {}", uptime, e);
        }

        return "00:00:00:00";
    }

    /**
     * Tests SNMP connectivity to a device.
     * @param ipAddress the IP address to test
     * @return true if SNMP is accessible, false otherwise
     */
    public boolean testSNMPConnectivity(String ipAddress) {
        return testSNMPConnectivity(ipAddress, DEFAULT_COMMUNITY);
    }

    /**
     * Tests SNMP connectivity to a device.
     * @param ipAddress the IP address to test
     * @param community the SNMP community string
     * @return true if SNMP is accessible, false otherwise
     */
    public boolean testSNMPConnectivity(String ipAddress, String community) {
        try {
            CommunityTarget target = createTarget(ipAddress, community);
            String value = getSNMPValue(target, OID_SYS_UPTIME);
            return value != null && !value.isEmpty();
        } catch (Exception e) {
            logger.debug("SNMP connectivity test failed for {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Creates an SNMP target for a specific IP address.
     * @param ipAddress the target IP address
     * @param community the SNMP community string
     * @return the configured SNMP target
     */
    private CommunityTarget createTarget(String ipAddress, String community) {
        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/" + PreferencesManager.getPortSNMP());
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setTimeout(PreferencesManager.getSNMPRetries());
        target.setTimeout(PreferencesManager.getSnmpTimeout());
        target.setVersion(SnmpConstants.version2c); // SNMPv2c
        return target;
    }

    /**
     * Performs an SNMP GET operation.
     * @param target the SNMP target
     * @param oid the OID to query
     * @return the value as a string, or null if a query fails
     */
    private String getSNMPValue(CommunityTarget target, String oid) {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);
            ResponseEvent event = snmp.send(pdu, target);

            if (event != null && event.getResponse() != null) {
                PDU response = event.getResponse();

                if (response.getErrorStatus() == PDU.noError) {
                    VariableBinding vb = response.get(0);
                    return vb.getVariable().toString();
                } else {
                    logger.warn("SNMP error for OID {}: {}", oid, response.getErrorStatusText());
                }
            } else {
                logger.warn("SNMP timeout for OID {}", oid);
            }
        } catch (IOException e) {
            logger.error("SNMP query failed for OID {}: {}", oid, e.getMessage());
        }

        return null;
    }

    /**
     * Closes the SNMP service and releases resources.
     */
    public void close() {
        try {
            if (snmp != null) {
                snmp.close();
                logger.info("SNMP service closed");
            }
        } catch (IOException e) {
            logger.error("Error closing SNMP service", e);
        }
    }
}