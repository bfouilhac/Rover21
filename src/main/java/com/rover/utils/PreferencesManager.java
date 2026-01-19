package com.rover.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Manager for handling persistent user preferences across application sessions.
 * This utility class uses Java's Preferences API to store and retrieve application
 * settings in the operating system's user preference store. On Windows, preferences
 * are stored in the registry; on Unix/Linux systems, they are stored in XML files
 * in the user's home directory. All methods are static, and preferences are persisted
 * immediately when saved using the flush method to ensure data integrity.
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see Preferences
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/preferences/overview.html">Java Preferences API</a>
 */
public class PreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);
    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesManager.class);

    // Language preference key
    private static final String LANGUAGE_KEY = "language";

    // Window state preference keys
    private static final String WINDOW_WIDTH_KEY = "window.width";
    private static final String WINDOW_HEIGHT_KEY = "window.height";

    // SNMP configuration preference keys
    private static final String SNMP_COMMUNITY_KEY = "snmp.community";
    private static final String SNMP_TIMEOUT_KEY = "snmp.timeout";
    private static final String AUTO_ENRICH_SNMP_KEY = "auto.enrich.snmp";
    private static final String PORT_SNMP_KEY = "port.snmp.key";
    private static final String SNMP_RETRIES = "snmp.retries";
    private static final String SNMP_VERSION_KEY = "snmp.version";
    private static final String SNMP_V3_USERNAME_KEY = "snmp.v3.username";
    private static final String SNMP_V3_PASSWORD_KEY = "snmp.v3.password";

    // Network discovery preference keys
    private static final String DISCOVERY_SEND_PORT_KEY = "discovery.send.port";
    private static final String DISCOVERY_LISTEN_PORT_KEY = "discovery.listen.port";
    private static final String DISCOVERY_TIMEOUT_KEY = "discovery.timeout";

    // Protocol port preference keys
    private static final String SSH_PORT_KEY = "protocol.ssh.port";
    private static final String TELNET_PORT_KEY = "protocol.telnet.port";

    // Session-only list: no need to save it between runs.
    private static final List<String>  customAccountMap = new ArrayList<>();

    public static List<String> getCustomAccountMap(){return customAccountMap;} // encoded : username:pwd

    /**
     * Retrieves the SNMP version.
     *
     * @return the SNMP version ("v1", "v2c", or "v3"), default: "v2c"
     */
    public static String getSnmpVersion() {
        return prefs.get(SNMP_VERSION_KEY, "v2c");
    }

    /**
     * Saves the SNMP version.
     *
     * @param version the SNMP version ("v1", "v2c", or "v3")
     */
    public static void saveSnmpVersion(String version) {
        try {
            prefs.put(SNMP_VERSION_KEY, version);
            prefs.flush();
            logger.info("SNMP version saved: {}", version);
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP version", e);
        }
    }

    /**
     * Retrieves the SNMPv3 username.
     *
     * @return the SNMPv3 username, default: empty string
     */
    public static String getSnmpV3Username() {
        return prefs.get(SNMP_V3_USERNAME_KEY, "");
    }

    /**
     * Saves the SNMPv3 username.
     *
     * @param username the SNMPv3 username
     */
    public static void saveSnmpV3Username(String username) {
        try {
            prefs.put(SNMP_V3_USERNAME_KEY, username);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMPv3 username", e);
        }
    }

    /**
     * Retrieves the SNMPv3 password.
     *
     * @return the SNMPv3 password, default: empty string
     */
    public static String getSnmpV3Password() {
        return prefs.get(SNMP_V3_PASSWORD_KEY, "");
    }

    /**
     * Saves the SNMPv3 password.
     *
     * @param password the SNMPv3 password
     */
    public static void saveSnmpV3Password(String password) {
        try {
            prefs.put(SNMP_V3_PASSWORD_KEY, password);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMPv3 password", e);
        }
    }

    /**
     * Retrieves the SSH port.
     *
     * @return the SSH port (default: 22)
     */
    public static int getSshPort() {
        return prefs.getInt(SSH_PORT_KEY, 22);
    }

    /**
     * Saves the SSH port.
     *
     * @param port the SSH port
     */
    public static void saveSshPort(int port) {
        try {
            prefs.putInt(SSH_PORT_KEY, port);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save SSH port", e);
        }
    }

    /**
     * Retrieves the Telnet port.
     *
     * @return the Telnet port (default: 23)
     */
    public static int getTelnetPort() {
        return prefs.getInt(TELNET_PORT_KEY, 23);
    }

    /**
     * Saves the Telnet port.
     *
     * @param port the Telnet port
     */
    public static void saveTelnetPort(int port) {
        try {
            prefs.putInt(TELNET_PORT_KEY, port);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save Telnet port", e);
        }
    }

    /**
     * Saves the user's preferred language.
     * The language code is persisted immediately and will be loaded on the next
     * application startup. This method is typically called when the user
     * changes the language in the preferences' dialog.
     *
     * @param languageCode the ISO 639-1 language code (e.g., "en", "fr")
     * @see LanguageManager#setLanguage(String)
     */
    public static void saveLanguage(String languageCode) {
        try {
            prefs.put(LANGUAGE_KEY, languageCode);
            prefs.flush();
            logger.info("Language preference saved: {}", languageCode);
        } catch (BackingStoreException e) {
            logger.error("Failed to save language preference", e);
        }
    }

    /**
     * Retrieves the user's preferred language.
     *
     * @return the saved language code, or "en" (English) as default
     */
    public static String getLanguage() {
        return prefs.get(LANGUAGE_KEY, "en");
    }

    /**
     * Saves the main window's size.
     * This method is typically called when the window is closed or resized,
     * allowing the application to restore the window to the same size on the next startup.
     *
     * @param width the window width in pixels
     * @param height the window height in pixels
     */
    public static void saveWindowSize(int width, int height) {
        try {
            prefs.putInt(WINDOW_WIDTH_KEY, width);
            prefs.putInt(WINDOW_HEIGHT_KEY, height);
            prefs.flush();
            logger.debug("Window size saved: {}x{}", width, height);
        } catch (BackingStoreException e) {
            logger.error("Failed to save window size", e);
        }
    }

    /**
     * Retrieves the saved window width.
     *
     * @param defaultWidth the default width to return if no preference is saved
     * @return the saved width, or defaultWidth if not found
     */
    public static int getWindowWidth(int defaultWidth) {
        return prefs.getInt(WINDOW_WIDTH_KEY, defaultWidth);
    }

    /**
     * Retrieves the saved window height.
     *
     * @param defaultHeight the default height to return if no preference is saved
     * @return the saved height, or defaultHeight if not found
     */
    public static int getWindowHeight(int defaultHeight) {
        return prefs.getInt(WINDOW_HEIGHT_KEY, defaultHeight);
    }

    /**
     * Retrieves the SNMP timeout value in milliseconds.
     * This timeout determines how long to wait for SNMP responses before
     * considering the query failed.
     *
     * @return the SNMP timeout in milliseconds (default: 1000 ms)
     */
    public static int getSnmpTimeout() {
        return prefs.getInt(SNMP_TIMEOUT_KEY, 1000);
    }

    /**
     * Retrieves the SNMP community string.
     * The community string acts as a password for SNMP access. Most devices
     * use "public" for read-only access by default.
     *
     * @return the SNMP community string (default: "public")
     */
    public static String getSnmpCommunity() {
        return prefs.get(SNMP_COMMUNITY_KEY, "public");
    }

    /**
     * Retrieves whether automatic SNMP enrichment is enabled.
     * When enabled, devices discovered via UDP are automatically queried
     * via SNMP to retrieve additional information (location, uptime).
     *
     * @return true if auto-enrichment is enabled (default: true)
     */
    public static boolean getAutoEnrichSnmp() {
        return prefs.getBoolean(AUTO_ENRICH_SNMP_KEY, true);
    }

    /**
     * Retrieves the number of SNMP retry attempts.
     * If an SNMP query fails, it will be retried this many times before
     * giving up.
     *
     * @return the number of retries (default: 3)
     */
    public static int getSNMPRetries() {
        return prefs.getInt(SNMP_RETRIES, 3);
    }

    /**
     * Retrieves the SNMP UDP port.
     *
     * @return the SNMP port (default: 161)
     */
    public static int getPortSNMP() {
        return prefs.getInt(PORT_SNMP_KEY, 161);
    }

    /**
     * Saves the SNMP community string.
     *
     * @param community the community string (e.g., "public", "private")
     */
    public static void saveSnmpCommunity(String community) {
        try {
            prefs.put(SNMP_COMMUNITY_KEY, community);
            prefs.flush();
            logger.info("SNMP community saved");
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP community", e);
        }
    }

    /**
     * Saves the SNMP timeout value.
     *
     * @param timeout the timeout in milliseconds
     */
    public static void saveSnmpTimeout(int timeout) {
        try {
            prefs.putInt(SNMP_TIMEOUT_KEY, timeout);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP timeout", e);
        }
    }

    /**
     * Saves the auto-enrich SNMP preference.
     *
     * @param autoEnrich true to enable automatic SNMP enrichment
     */
    public static void saveAutoEnrichSnmp(boolean autoEnrich) {
        try {
            prefs.putBoolean(AUTO_ENRICH_SNMP_KEY, autoEnrich);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save auto enrich SNMP", e);
        }
    }

    /**
     * Retrieves the network discovery timeout in milliseconds.
     * This is the duration for which the application listens for UDP
     * responses from PLANET switches during network discovery.
     *
     * @return the discovery timeout in milliseconds (default: 5000 ms)
     */
    public static int getDiscoveryTimeout() {
        return prefs.getInt(DISCOVERY_TIMEOUT_KEY, 5000);
    }

    /**
     * Retrieves the UDP port used for sending discovery packets.
     *
     * @return the sending port (default: 62630)
     */
    public static int getDiscoverySendPort() {
        return prefs.getInt(DISCOVERY_SEND_PORT_KEY, 62630);
    }

    /**
     * Retrieves the UDP port used for listening to discovery responses.
     *
     * @return the listen port (default: 62631)
     */
    public static int getDiscoveryListenPort() {
        return prefs.getInt(DISCOVERY_LISTEN_PORT_KEY, 62631);
    }

    /**
     * Saves the network discovery timeout.
     *
     * @param timeout the timeout in milliseconds
     */
    public static void saveDiscoveryTimeout(int timeout) {
        try {
            prefs.putInt(DISCOVERY_TIMEOUT_KEY, timeout);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save discovery timeout", e);
        }
    }

    /**
     * Saves the UDP port for sending discovery packets.
     *
     * @param port the port number (1-65535)
     */
    public static void saveDiscoverySendPort(int port) {
        try {
            prefs.putInt(DISCOVERY_SEND_PORT_KEY, port);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save discovery send port", e);
        }
    }

    /**
     * Saves the UDP port for listening to discovery responses.
     *
     * @param port the port number (1-65535)
     */
    public static void saveDiscoveryListenPort(int port) {
        try {
            prefs.putInt(DISCOVERY_LISTEN_PORT_KEY, port);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save discovery listen port", e);
        }
    }
}