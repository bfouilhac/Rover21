package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;

/**
 * Service for querying PLANET switches via SNMP (Simple Network Management Protocol).
 * This service supports SNMPv1, SNMPv2c, and SNMPv3 with configurable community string
 * (default: "public") and timeout settings from user preferences. It creates a persistent
 * SNMP session that should be properly closed after use by calling close().
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see PlanetSwitchDevice
 * @see <a href="https://www.snmp4j.org/">SNMP4J Library</a>
 */
public class SNMPService {

    private static final Logger logger = LoggerFactory.getLogger(SNMPService.class);
    private static final String DEFAULT_COMMUNITY = "public";
    private static final String OID_SYS_UPTIME = "1.3.6.1.2.1.1.3.0";
    private static final String OID_SYS_LOCATION = "1.3.6.1.2.1.1.6.0";

    private Snmp snmp;

    /**
     * Constructs a new SNMPService and initializes the SNMP transport.
     * This constructor creates a UDP transport mapping and starts listening
     * for SNMP responses. The transport remains active until close() is called.
     * If SNMPv3 is configured, this method also initializes the USM security model.
     *
     * @see #close()
     */
    public SNMPService() {
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            this.snmp = new Snmp(transport);

            String version = PreferencesManager.getSnmpVersion();

            if ("v3".equals(version)) {
                USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0); // configure USM for SNMPv3
                SecurityModels.getInstance().addSecurityModel(usm);
                logger.info("SNMP service initialized with SNMPv3");
            } else {
                logger.info("SNMP service initialized with SNMPv{}", version);
            }

            transport.listen();
            Thread.sleep(100); // wait for transport to be ready

        } catch (IOException e) {
            logger.error("Failed to initialize SNMP service", e);
        } catch (InterruptedException e) {
            logger.warn("SNMP initialization interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enriches a device with SNMP data using the default community string.
     * This is a convenience method that calls enrichDeviceWithSNMP(device, "public").
     *
     * @param device the device to enrich with SNMP data
     * @see #enrichDeviceWithSNMP(PlanetSwitchDevice, String)
     */
    public void enrichDeviceWithSNMP(PlanetSwitchDevice device) {
        enrichDeviceWithSNMP(device, DEFAULT_COMMUNITY);
    }

    /**
     * Enriches a device with SNMP data using a specific community string.
     * This method queries the device for system location and uptime using SNMP.
     * It supports SNMPv1, SNMPv2c, and SNMPv3 based on user preferences.
     *
     * @param device the device to enrich
     * @param community the SNMP community string (used for v1/v2c only)
     * @return the enriched device, or original device if SNMP fails
     */
    public PlanetSwitchDevice enrichDeviceWithSNMP(PlanetSwitchDevice device, String community) {
        if (device == null || device.getIpAddress() == null) {
            logger.warn("Cannot enrich null device or device without IP");
            return device;
        }

        logger.info("Enriching device {} via SNMP", device.getIpAddress());

        try {
            String version = PreferencesManager.getSnmpVersion();
            if ("v3".equals(version)) {
                addSNMPv3User();
            }

            Target<?> target = createTarget(device.getIpAddress(), community);

            String sysLocation = getSNMPValue(target, OID_SYS_LOCATION); // query system location
            if (sysLocation != null && !sysLocation.isEmpty()) {
                device.setSystemLocation(sysLocation);
                logger.info("System Location: {}", sysLocation);
            } else {
                device.setSystemLocation("N/A");
                logger.debug("System Location not set");
            }

            String sysUptime = getSNMPValue(target, OID_SYS_UPTIME); // query system uptime
            if (sysUptime != null && !sysUptime.isEmpty()) {
                String formattedUptime = formatUptime(sysUptime);
                device.setSystemUptime(formattedUptime);
                logger.info("System Uptime: {} (raw: {})", formattedUptime, sysUptime);
            } else {
                device.setSystemUptime("N/A");
                logger.debug("System Uptime not available");
            }

            logger.info("Device {} enriched successfully via SNMP", device.getIpAddress());

        } catch (Exception e) {
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
     * Adds SNMPv3 user credentials to the SNMP session.
     * This must be called before making SNMPv3 requests. The method configures
     * authentication (MD5) and privacy (DES) protocols using the username and
     * password from preferences.
     */
    private void addSNMPv3User() {
        String username = PreferencesManager.getSnmpV3Username();
        String password = PreferencesManager.getSnmpV3Password();

        if (username.isEmpty()) {
            logger.warn("SNMPv3 username is empty");
            return;
        }

        OctetString userName = new OctetString(username);
        OID authProtocol = AuthMD5.ID; // authentication protocol: MD5
        OctetString authPassphrase = new OctetString(password);
        OID privProtocol = PrivDES.ID; // privacy protocol: DES
        OctetString privPassphrase = new OctetString(password);

        UsmUser user = new UsmUser(userName, authProtocol, authPassphrase, privProtocol, privPassphrase);
        snmp.getUSM().addUser(userName, user); // add user to USM

        logger.debug("SNMPv3 user added: {}", username);
    }

    /**
     * Formats SNMP uptime to a standardized format: DD:HH:MM:SS.
     * This method parses various uptime formats returned by SNMP devices,
     * including formats with or without days and with decimal seconds.
     *
     * @param uptime the raw uptime string from SNMP
     * @return formatted uptime as "DD:HH:MM:SS", or "00:00:00:00" if parsing fails
     */
    private String formatUptime(String uptime) {
        try {
            int days = 0;
            String timePart = uptime;

            if (uptime.contains("day")) { // check if the format contains "day" or "days"
                String[] parts = uptime.split("day");
                String daysPart = parts[0].trim();
                days = Integer.parseInt(daysPart.replaceAll("[^0-9]", "")); // extract the number of days

                if (parts.length > 1) { // get the time part after "day(s),"
                    timePart = parts[1].replace("s,", "").replace(",", "").trim();
                }
            }

            String[] timeParts = timePart.split(":"); // parse the time part (HH:MM:SS.cc or H:MM:SS.cc)

            if (timeParts.length >= 3) {
                int hours = Integer.parseInt(timeParts[0].trim());
                int minutes = Integer.parseInt(timeParts[1].trim());

                String secondsPart = timeParts[2].trim(); // extract seconds (before the decimal point)
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
     * Tests SNMP connectivity to a device using the default community string.
     * This method attempts to query the system uptime OID to verify that
     * the device is reachable via SNMP. It's useful to check SNMP availability
     * before attempting to enrich device data.
     *
     * @param ipAddress the IP address of the device to test
     * @return true if the device responds to SNMP queries, false otherwise
     * @see #testSNMPConnectivity(String, String)
     */
    public boolean testSNMPConnectivity(String ipAddress) {
        return testSNMPConnectivity(ipAddress, DEFAULT_COMMUNITY);
    }

    /**
     * Tests SNMP connectivity to a device using a specific community string.
     * This method supports all SNMP versions configured in preferences.
     *
     * @param ipAddress the IP address of the device to test
     * @param community the SNMP community string (used for v1/v2c only)
     * @return true if the device responds to SNMP queries, false otherwise
     */
    public boolean testSNMPConnectivity(String ipAddress, String community) {
        try {
            String version = PreferencesManager.getSnmpVersion();

            if ("v3".equals(version)) { // add SNMPv3 user if needed
                addSNMPv3User();
            }

            Target<?> target = createTarget(ipAddress, community);
            String value = getSNMPValue(target, OID_SYS_UPTIME);
            return value != null && !value.isEmpty();
        } catch (Exception e) {
            logger.debug("SNMP connectivity test failed for {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Creates an SNMP target based on the configured SNMP version.
     * This method returns either a CommunityTarget (v1/v2c) or UserTarget (v3)
     * depending on the version configured in preferences.
     *
     * @param ipAddress the target device IP address
     * @param community the community string (used for v1/v2c only)
     * @return a Target object (CommunityTarget or UserTarget)
     */
    private Target<?> createTarget(String ipAddress, String community) {
        String version = PreferencesManager.getSnmpVersion();

        if ("v3".equals(version)) {
            return createUserTarget(ipAddress);
        } else {
            return createCommunityTarget(ipAddress, community);
        }
    }

    /**
     * Creates a CommunityTarget for SNMPv1 or SNMPv2c.
     * This method configures the target with the appropriate community string,
     * retry count, timeout, and SNMP version.
     *
     * @param ipAddress the target device IP address
     * @param community the community string
     * @return a configured CommunityTarget
     */
    private CommunityTarget createCommunityTarget(String ipAddress, String community) {
        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/" + PreferencesManager.getPortSNMP());
        CommunityTarget target = new CommunityTarget();

        String communityToUse = community.equals(DEFAULT_COMMUNITY)
                ? PreferencesManager.getSnmpCommunity()
                : community;

        target.setCommunity(new OctetString(communityToUse));
        target.setAddress(targetAddress);
        target.setRetries(PreferencesManager.getSNMPRetries());
        target.setTimeout(PreferencesManager.getSnmpTimeout());

        String version = PreferencesManager.getSnmpVersion();
        if ("v1".equals(version)) {
            target.setVersion(SnmpConstants.version1);
        } else {
            target.setVersion(SnmpConstants.version2c);
        }

        return target;
    }

    /**
     * Creates a UserTarget for SNMPv3 with authentication and privacy.
     * This method configures the target with the appropriate security level,
     * security name (username), retry count, and timeout.
     *
     * @param ipAddress the target device IP address
     * @return a configured UserTarget
     */
    private UserTarget createUserTarget(String ipAddress) {
        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/" + PreferencesManager.getPortSNMP());
        UserTarget target = new UserTarget();

        target.setAddress(targetAddress);
        target.setRetries(PreferencesManager.getSNMPRetries());
        target.setTimeout(PreferencesManager.getSnmpTimeout());
        target.setVersion(SnmpConstants.version3);
        target.setSecurityLevel(SecurityLevel.AUTH_PRIV); // auth + privacy

        String username = PreferencesManager.getSnmpV3Username(); // set a security name (username)
        target.setSecurityName(new OctetString(username));

        return target;
    }

    /**
     * Performs an SNMP GET operation to retrieve a value for a specific OID.
     * This method supports both CommunityTarget (v1/v2c) and UserTarget (v3).
     * It creates the appropriate PDU type based on the SNMP version and sends
     * the request with the configured timeout.
     *
     * @param target the SNMP target (CommunityTarget or UserTarget)
     * @param oid the Object Identifier to query
     * @return the value as a string, or null if the query fails
     */
    private String getSNMPValue(Target<?> target, String oid) {
        try {
            PDU pdu;

            if (target.getVersion() == SnmpConstants.version3) { // create the appropriate PDU based on an SNMP version
                pdu = new ScopedPDU();
            } else {
                pdu = new PDU();
            }

            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            long startTime = System.currentTimeMillis();
            ResponseEvent event = snmp.send(pdu, target);
            long elapsedTime = System.currentTimeMillis() - startTime;

            logger.debug("SNMP query for OID {} took {}ms", oid, elapsedTime);

            if (event != null && event.getResponse() != null) {
                PDU response = event.getResponse();

                if (response.getErrorStatus() == PDU.noError) {
                    VariableBinding vb = response.get(0);
                    return vb.getVariable().toString();
                } else {
                    logger.warn("SNMP error for OID {}: {}", oid, response.getErrorStatusText());
                }
            } else {
                logger.warn("SNMP timeout for OID {} after {}ms", oid, elapsedTime);
            }
        } catch (IOException e) {
            logger.error("SNMP query failed for OID {}: {}", oid, e.getMessage());
        }

        return null;
    }

    /**
     * Closes the SNMP service and releases all resources.
     * This method should be called when the SNMP service is no longer needed
     * to properly clean up the UDP transport and free system resources.
     * After calling this method, the service cannot be used anymore.
     *
     * @see #SNMPService()
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