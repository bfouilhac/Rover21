package rover.services;

import rover.model.PlanetSwitchDevice;
import rover.utils.PreferencesManager;
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
 * <p>
 * This service supports SNMPv1, SNMPv2c, and SNMPv3 with configurable community string
 * and timeout settings from user preferences. It creates a persistent SNMP session
 * that should be properly closed after use by calling {@link #close()}.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see PlanetSwitchDevice
 * @see <a href="https://www.snmp4j.org/">SNMP4J Library</a>
 */
public class SNMPService {

    private static final Logger logger = LoggerFactory.getLogger(SNMPService.class);

    private static final String DEFAULT_COMMUNITY = "public";

    private static final String OID_SYS_UPTIME = "1.3.6.1.2.1.1.3.0"; //** OID for system uptime (sysUpTime.0).
    private static final String OID_SYS_LOCATION = "1.3.6.1.2.1.1.6.0"; //** OID for system location (sysLocation.0).

    private static final int TRANSPORT_INIT_DELAY = 100;

    private static final String DEFAULT_UPTIME = "00:00:00:00";

    private static final String NOT_AVAILABLE = "N/A";

    private Snmp snmp;

    /**
     * Constructs a new SNMPService and initializes the SNMP transport.
     * <p>
     * This constructor creates a UDP transport mapping and starts listening
     * for SNMP responses. The transport remains active until {@link #close()} is called.
     * If SNMPv3 is configured, this method also initializes the USM security model.
     * </p>
     *
     * @see #close()
     */
    public SNMPService() {
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            this.snmp = new Snmp(transport);

            String version = PreferencesManager.getSnmpVersion();

            if ("v3".equals(version)) {
                initializeSnmpV3();
            } else {
                logger.info("SNMP service initialized with SNMP{}", version);
            }

            transport.listen();
            Thread.sleep(TRANSPORT_INIT_DELAY); // wait for transport to be ready

        } catch (IOException e) {
            logger.error("Failed to initialize SNMP service", e);
        } catch (InterruptedException e) {
            logger.warn("SNMP initialization interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enriches a device with SNMP data using the default community string.
     * <p>
     * This is a convenience method that calls {@link #enrichDeviceWithSNMP(PlanetSwitchDevice, String)}
     * with the default community string "public".
     * </p>
     *
     * @param device the device to enrich with SNMP data
     * @see #enrichDeviceWithSNMP(PlanetSwitchDevice, String)
     */
    public void enrichDeviceWithSNMP(PlanetSwitchDevice device) {enrichDeviceWithSNMP(device, DEFAULT_COMMUNITY);}

    /**
     * Enriches a device with SNMP data using a specific community string.
     * <p>
     * This method queries the device for system location and uptime using SNMP.
     * It supports SNMPv1, SNMPv2c, and SNMPv3 based on user preferences.
     * If SNMP queries fail, the device fields are set to "N/A".
     * </p>
     *
     * @param device the device to enrich
     * @param community the SNMP community string (used for v1/v2c only)
     * @return the enriched device, or original device with "N/A" values if SNMP fails
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
                addSnmpV3User();
            }

            Target<?> target = createTarget(device.getIpAddress(), community);

            enrichSystemLocation(device, target);
            enrichSystemUptime(device, target);

            logger.info("Device {} enriched successfully via SNMP", device.getIpAddress());

        } catch (Exception e) {
            logger.error("Failed to enrich device {} via SNMP: {}", device.getIpAddress(), e.getMessage());
            setDefaultSnmpValues(device);
        }

        return device;
    }

    /**
     * Tests SNMP connectivity to a device using the default community string.
     * <p>
     * This method attempts to query the system uptime OID to verify that
     * the device is reachable via SNMP. It's useful to check SNMP availability
     * before attempting to enrich device data.
     * </p>
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
     * <p>
     * This method supports all SNMP versions configured in preferences.
     * </p>
     *
     * @param ipAddress the IP address of the device to test
     * @param community the SNMP community string (used for v1/v2c only)
     * @return true if the device responds to SNMP queries, false otherwise
     */
    public boolean testSNMPConnectivity(String ipAddress, String community) {
        try {
            String version = PreferencesManager.getSnmpVersion();

            if ("v3".equals(version)) {
                addSnmpV3User();
            }

            Target<?> target = createTarget(ipAddress, community);
            String value = getSnmpValue(target, OID_SYS_UPTIME);
            return value != null && !value.isEmpty();
        } catch (Exception e) {
            logger.debug("SNMP connectivity test failed for {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Closes the SNMP service and releases all resources.
     * <p>
     * This method should be called when the SNMP service is no longer needed
     * to properly clean up the UDP transport and free system resources.
     * After calling this method, the service cannot be used anymore.
     * </p>
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

    /**
     * Initializes the USM security model for SNMPv3.
     */
    private void initializeSnmpV3() {
        USM usm = new USM(
                SecurityProtocols.getInstance(),
                new OctetString(MPv3.createLocalEngineID()),
                0
        );
        SecurityModels.getInstance().addSecurityModel(usm);
        logger.info("SNMP service initialized with SNMPv3");
    }

    /**
     * Adds SNMPv3 user credentials to the SNMP session.
     * <p>
     * This must be called before making SNMPv3 requests. The method configures
     * authentication (MD5) and privacy (DES) protocols using the username and
     * password from preferences.
     * </p>
     */
    private void addSnmpV3User() {
        String username = PreferencesManager.getSnmpV3Username();
        String password = PreferencesManager.getSnmpV3Password();

        if (username.isEmpty()) {
            logger.warn("SNMPv3 username is empty");
            return;
        }

        OctetString userName = new OctetString(username);
        OID authProtocol = AuthMD5.ID;
        OctetString authPassphrase = new OctetString(password);
        OID privProtocol = PrivDES.ID;
        OctetString privPassphrase = new OctetString(password);

        UsmUser user = new UsmUser(userName, authProtocol, authPassphrase, privProtocol, privPassphrase);
        snmp.getUSM().addUser(userName, user);

        logger.debug("SNMPv3 user added: {}", username);
    }

    /**
     * Creates an SNMP target based on the configured SNMP version.
     * <p>
     * This method returns either a CommunityTarget (v1/v2c) or UserTarget (v3)
     * depending on the version configured in preferences.
     * </p>
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
     * <p>
     * This method configures the target with the appropriate community string,
     * retry count, timeout, and SNMP version.
     * </p>
     *
     * @param ipAddress the target device IP address
     * @param community the community string
     * @return a configured CommunityTarget
     */
    private CommunityTarget createCommunityTarget(String ipAddress, String community) {
        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/" + PreferencesManager.getPortSNMP());
        CommunityTarget<Address> target = new CommunityTarget<>();

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
     * <p>
     * This method configures the target with the appropriate security level,
     * security name (username), retry count, and timeout.
     * </p>
     *
     * @param ipAddress the target device IP address
     * @return a configured UserTarget
     */
    private UserTarget createUserTarget(String ipAddress) {
        Address targetAddress = GenericAddress.parse("udp:" + ipAddress + "/" + PreferencesManager.getPortSNMP());
        UserTarget<Address> target = new UserTarget<>();

        target.setAddress(targetAddress);
        target.setRetries(PreferencesManager.getSNMPRetries());
        target.setTimeout(PreferencesManager.getSnmpTimeout());
        target.setVersion(SnmpConstants.version3);
        target.setSecurityLevel(SecurityLevel.AUTH_PRIV);

        String username = PreferencesManager.getSnmpV3Username();
        target.setSecurityName(new OctetString(username));

        return target;
    }

    /**
     * Performs an SNMP GET operation to retrieve a value for a specific OID.
     * <p>
     * This method supports both CommunityTarget (v1/v2c) and UserTarget (v3).
     * It creates the appropriate PDU type based on the SNMP version and sends
     * the request with the configured timeout.
     * </p>
     *
     * @param target the SNMP target (CommunityTarget or UserTarget)
     * @param oid the Object Identifier to query
     * @return the value as a string, or null if the query fails
     */
    private String getSnmpValue(Target<?> target, String oid) {
        try {
            PDU pdu = createPduForTarget(target);
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            long startTime = System.currentTimeMillis();
            ResponseEvent event = snmp.send(pdu, target);
            long elapsedTime = System.currentTimeMillis() - startTime;

            logger.debug("SNMP query for OID {} took {}ms", oid, elapsedTime);

            return extractValueFromResponse(event, oid, elapsedTime);

        } catch (IOException e) {
            logger.error("SNMP query failed for OID {}: {}", oid, e.getMessage());
            return null;
        }
    }

    /**
     * Creates the appropriate PDU type based on an SNMP version.
     *
     * @param target the SNMP target
     * @return a PDU or ScopedPDU depending on version
     */
    private PDU createPduForTarget(Target<?> target) {
        if (target.getVersion() == SnmpConstants.version3) {
            return new ScopedPDU();
        } else {
            return new PDU();
        }
    }

    /**
     * Extracts the value from an SNMP response event.
     *
     * @param event the response event
     * @param oid the queried OID (for logging)
     * @param elapsedTime the query time (for logging)
     * @return the value as a string, or null if extraction fails
     */
    private String extractValueFromResponse(ResponseEvent<Address> event, String oid, long elapsedTime) {
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

        return null;
    }

    /**
     * Enriches the device with system location from SNMP.
     *
     * @param device the device to enrich
     * @param target the SNMP target
     */
    private void enrichSystemLocation(PlanetSwitchDevice device, Target<?> target) {
        String sysLocation = getSnmpValue(target, OID_SYS_LOCATION);

        if (sysLocation != null && !sysLocation.isEmpty()) {
            device.setSystemLocation(sysLocation);
            logger.info("System Location: {}", sysLocation);
        } else {
            device.setSystemLocation(NOT_AVAILABLE);
            logger.debug("System Location not set");
        }
    }

    /**
     * Enriches the device with system uptime from SNMP.
     *
     * @param device the device to enrich
     * @param target the SNMP target
     */
    private void enrichSystemUptime(PlanetSwitchDevice device, Target<?> target) {
        String sysUptime = getSnmpValue(target, OID_SYS_UPTIME);

        if (sysUptime != null && !sysUptime.isEmpty()) {
            String formattedUptime = formatUptime(sysUptime);
            device.setSystemUptime(formattedUptime);
            logger.info("System Uptime: {} (raw: {})", formattedUptime, sysUptime);
        } else {
            device.setSystemUptime(NOT_AVAILABLE);
            logger.debug("System Uptime not available");
        }
    }

    /**
     * Sets default SNMP values when enrichment fails.
     *
     * @param device the device to set defaults on
     */
    private void setDefaultSnmpValues(PlanetSwitchDevice device) {
        if (device.getSystemLocation() == null) {
            device.setSystemLocation(NOT_AVAILABLE);
        }
        if (device.getSystemUptime() == null) {
            device.setSystemUptime(NOT_AVAILABLE);
        }
    }

    /**
     * Formats SNMP uptime to a standardized format: DD:HH:MM:SS.
     * <p>
     * This method parses various uptime formats returned by SNMP devices,
     * including formats with or without days and with decimal seconds.
     * </p>
     *
     * @param uptime the raw uptime string from SNMP
     * @return formatted uptime as "DD:HH:MM:SS", or "00:00:00:00" if parsing fails
     */
    private String formatUptime(String uptime) {
        try {
            int days = 0;
            String timePart = uptime;

            if (uptime.contains("day")) {
                days = extractDaysFromUptime(uptime);
                timePart = extractTimePartFromUptime(uptime);
            }

            return parseTimePart(timePart, days);

        } catch (Exception e) {
            logger.warn("Failed to parse uptime: {}", uptime, e);
            return DEFAULT_UPTIME;
        }
    }

    /**
     * Extracts the number of days from an uptime string.
     *
     * @param uptime the uptime string containing "day" or "days"
     * @return the number of days
     */
    private int extractDaysFromUptime(String uptime) {return Integer.parseInt(uptime.split("day")[0].trim().replace("\\D", ""));}

    /**
     * Extracts the time part (HH:MM:SS) from an uptime string.
     *
     * @param uptime the uptime string
     * @return the time part after "day(s)"
     */
    private String extractTimePartFromUptime(String uptime) {
        String[] parts = uptime.split("day");
        if (parts.length > 1) {
            return parts[1].replace("s,", "").replace(",", "").trim();
        }
        return uptime;
    }

    /**
     * Parses the time part and formats the complete uptime string.
     *
     * @param timePart the time part (HH:MM:SS or H:MM:SS.cc)
     * @param days the number of days
     * @return formatted uptime as "DD:HH:MM:SS"
     */
    private String parseTimePart(String timePart, int days) {
        String[] timeParts = timePart.split(":");

        if (timeParts.length >= 3) {
            int hours = Integer.parseInt(timeParts[0].trim());
            int minutes = Integer.parseInt(timeParts[1].trim());
            int seconds = parseSeconds(timeParts[2].trim());

            return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
        }

        return DEFAULT_UPTIME;
    }

    /**
     * Parses seconds from a string that may contain decimal places.
     *
     * @param secondsPart the seconds string (e.g., "45" or "45.67")
     * @return the integer seconds value
     */
    private int parseSeconds(String secondsPart) {
        if (secondsPart.contains(".")) {
            return Integer.parseInt(secondsPart.split("\\.")[0]);
        }
        return Integer.parseInt(secondsPart);
    }
}