package com.rover.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Manager for handling persistent user preferences across application sessions.
 * <p>
 * All methods are static, and preferences are persisted immediately when saved
 * using the flush method to ensure data integrity via Java Preferences API
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see Preferences
 */
public class PreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);

    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesManager.class);

    // Language preference
    private static final String KEY_LANGUAGE = "language";

    // Window state preferences
    private static final String KEY_WINDOW_WIDTH = "window.width";
    private static final String KEY_WINDOW_HEIGHT = "window.height";

    // SNMP configuration preferences
    private static final String KEY_SNMP_COMMUNITY = "snmp.community";
    private static final String KEY_SNMP_TIMEOUT = "snmp.timeout";
    private static final String KEY_SNMP_PORT = "port.snmp.key";
    private static final String KEY_SNMP_RETRIES = "snmp.retries";
    private static final String KEY_SNMP_VERSION = "snmp.version";
    private static final String KEY_SNMP_V3_USERNAME = "snmp.v3.username";
    private static final String KEY_SNMP_V3_PASSWORD = "snmp.v3.password";
    private static final String KEY_AUTO_ENRICH_SNMP = "auto.enrich.snmp";

    // Network discovery preferences
    private static final String KEY_DISCOVERY_SEND_PORT = "discovery.send.port";
    private static final String KEY_DISCOVERY_LISTEN_PORT = "discovery.listen.port";
    private static final String KEY_DISCOVERY_TIMEOUT = "discovery.timeout";

    // Protocol port preferences
    private static final String KEY_SSH_PORT = "protocol.ssh.port";
    private static final String KEY_TELNET_PORT = "protocol.telnet.port";

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String DEFAULT_SNMP_COMMUNITY = "public";
    private static final int DEFAULT_SNMP_TIMEOUT = 1000;
    private static final int DEFAULT_SNMP_PORT = 161;
    private static final int DEFAULT_SNMP_RETRIES = 3;
    private static final String DEFAULT_SNMP_VERSION = "v2c";
    private static final int DEFAULT_DISCOVERY_TIMEOUT = 5;
    private static final int DEFAULT_DISCOVERY_SEND_PORT = 62630;
    private static final int DEFAULT_DISCOVERY_LISTEN_PORT = 62631;
    private static final int DEFAULT_SSH_PORT = 22;
    private static final int DEFAULT_TELNET_PORT = 23;

    private static final String KEY_LAST_EXPORT_PATH = "last.export.path";

    public static String getLastExportPath() {
        return prefs.get(KEY_LAST_EXPORT_PATH, "");
    }

    public static void saveLastExportPath(String path) {
        prefs.put(KEY_LAST_EXPORT_PATH, path);
    }


    /**
     * Session-only list of custom credentials for security testing.
     * <p>
     * This list is not persisted between application sessions for security reasons.
     * Format: "username:password"
     * </p>
     */
    private static final List<String> customAccountList = new ArrayList<>();

    /**
     * Returns the list of custom account credentials for security testing.
     * <p>
     * This list is session-only and is not persisted between application restarts.
     * Each entry is in the format "username:password".
     * </p>
     *
     * @return the list of custom credentials
     */
    public static List<String> getCustomAccountMap() {return customAccountList;}

    /**
     * Saves the user's preferred language.
     * <p>
     * The language code is persisted immediately and will be loaded on the next
     * application startup. This method is typically called when the user
     * changes the language in the preferences' dialog.
     * </p>
     *
     * @param languageCode the ISO 639-1 language code (e.g., "en", "fr")
     * @see LanguageManager#setLanguage(String)
     */
    public static void saveLanguage(String languageCode) {saveString(KEY_LANGUAGE, languageCode, "Language preference");}

    /**
     * Retrieves the user's preferred language.
     *
     * @return the saved language code, or "en" (English) as default
     */
    public static String getLanguage() {return prefs.get(KEY_LANGUAGE, DEFAULT_LANGUAGE);}

    /**
     * Saves the main window's size.
     * <p>
     * This method is typically called when the window is closed or resized,
     * allowing the application to restore the window to the same size on the next startup.
     * </p>
     *
     * @param width  the window width in pixels
     * @param height the window height in pixels
     */
    public static void saveWindowSize(int width, int height) {
        try {
            prefs.putInt(KEY_WINDOW_WIDTH, width);
            prefs.putInt(KEY_WINDOW_HEIGHT, height);
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
    public static int getWindowWidth(int defaultWidth) {return prefs.getInt(KEY_WINDOW_WIDTH, defaultWidth);}

    /**
     * Retrieves the saved window height.
     *
     * @param defaultHeight the default height to return if no preference is saved
     * @return the saved height, or defaultHeight if not found
     */
    public static int getWindowHeight(int defaultHeight) {return prefs.getInt(KEY_WINDOW_HEIGHT, defaultHeight);}

    /**
     * Retrieves the SNMP community string.
     * <p>
     * The community string acts as a password for SNMP access. Most devices
     * use "public" for read-only access by default.
     * </p>
     *
     * @return the SNMP community string (default: "public")
     */
    public static String getSnmpCommunity() {return prefs.get(KEY_SNMP_COMMUNITY, DEFAULT_SNMP_COMMUNITY);}

    /**
     * Saves the SNMP community string.
     *
     * @param community the community string (e.g., "public", "private")
     */
    public static void saveSnmpCommunity(String community) {saveString(KEY_SNMP_COMMUNITY, community, "SNMP community");}

    /**
     * Retrieves the SNMP timeout value in milliseconds.
     * <p>
     * This timeout determines how long to wait for SNMP responses before
     * considering the query failed.
     * </p>
     *
     * @return the SNMP timeout in milliseconds (default: 1000 ms)
     */
    public static int getSnmpTimeout() {return prefs.getInt(KEY_SNMP_TIMEOUT, DEFAULT_SNMP_TIMEOUT);}

    /**
     * Saves the SNMP timeout value.
     *
     * @param timeout the timeout in milliseconds
     */
    public static void saveSnmpTimeout(int timeout) {saveInt(KEY_SNMP_TIMEOUT, timeout, "SNMP timeout");}

    /**
     * Retrieves the SNMP UDP port.
     *
     * @return the SNMP port (default: 161)
     */
    public static int getPortSNMP() {return prefs.getInt(KEY_SNMP_PORT, DEFAULT_SNMP_PORT);}

    /**
     * Retrieves the number of SNMP retry attempts.
     * <p>
     * If an SNMP query fails, it will be retried this many times before giving up.
     * </p>
     *
     * @return the number of retries (default: 3)
     */
    public static int getSNMPRetries() {return prefs.getInt(KEY_SNMP_RETRIES, DEFAULT_SNMP_RETRIES);}

    /**
     * Retrieves the SNMP version.
     *
     * @return the SNMP version ("v1", "v2c", or "v3"), default: "v2c"
     */
    public static String getSnmpVersion() {return prefs.get(KEY_SNMP_VERSION, DEFAULT_SNMP_VERSION);}

    /**
     * Saves the SNMP version.
     *
     * @param version the SNMP version ("v1", "v2c", or "v3")
     */
    public static void saveSnmpVersion(String version) {saveString(KEY_SNMP_VERSION, version, "SNMP version");}

    /**
     * Retrieves the SNMPv3 username.
     *
     * @return the SNMPv3 username, or empty string if not set
     */
    public static String getSnmpV3Username() {return prefs.get(KEY_SNMP_V3_USERNAME, "");}

    /**
     * Saves the SNMPv3 username.
     *
     * @param username the SNMPv3 username
     */
    public static void saveSnmpV3Username(String username) {saveString(KEY_SNMP_V3_USERNAME, username, "SNMPv3 username");}

    /**
     * Retrieves the SNMPv3 password.
     *
     * @return the SNMPv3 password, or empty string if not set
     */
    public static String getSnmpV3Password() {return prefs.get(KEY_SNMP_V3_PASSWORD, "");}

    /**
     * Saves the SNMPv3 password.
     *
     * @param password the SNMPv3 password
     */
    public static void saveSnmpV3Password(String password) {saveString(KEY_SNMP_V3_PASSWORD, password, "SNMPv3 password");}

    /**
     * Retrieves whether automatic SNMP enrichment is enabled.
     * <p>
     * When enabled, devices discovered via UDP are automatically queried
     * via SNMP to retrieve additional information (location, uptime).
     * </p>
     *
     * @return true if auto-enrichment is enabled (default: true)
     */
    public static boolean getAutoEnrichSnmp() {return prefs.getBoolean(KEY_AUTO_ENRICH_SNMP, true);}

    /**
     * Saves the auto-enrich SNMP preference.
     *
     * @param autoEnrich true to enable automatic SNMP enrichment
     */
    public static void saveAutoEnrichSnmp(boolean autoEnrich) {saveBoolean(KEY_AUTO_ENRICH_SNMP, autoEnrich, "Auto enrich SNMP");}

    /**
     * Retrieves the network discovery timeout in seconds.
     * <p>
     * This is the duration for which the application listens for UDP
     * responses from PLANET switches during network discovery.
     * </p>
     *
     * @return the discovery timeout in seconds (default: 5 seconds)
     */
    public static int getDiscoveryTimeout() {return prefs.getInt(KEY_DISCOVERY_TIMEOUT, DEFAULT_DISCOVERY_TIMEOUT);}

    /**
     * Saves the network discovery timeout.
     *
     * @param timeout the timeout in seconds
     */
    public static void saveDiscoveryTimeout(int timeout) {saveInt(KEY_DISCOVERY_TIMEOUT, timeout, "Discovery timeout");}

    /**
     * Retrieves the UDP port used for sending discovery packets.
     *
     * @return the sending port (default: 62630)
     */
    public static int getDiscoverySendPort() {return prefs.getInt(KEY_DISCOVERY_SEND_PORT, DEFAULT_DISCOVERY_SEND_PORT);}

    /**
     * Saves the UDP port for sending discovery packets.
     *
     * @param port the port number (1-65535)
     */
    public static void saveDiscoverySendPort(int port) {saveInt(KEY_DISCOVERY_SEND_PORT, port, "Discovery send port");}

    /**
     * Retrieves the UDP port used for listening to discovery responses.
     *
     * @return the listen port (default: 62631)
     */
    public static int getDiscoveryListenPort() {return prefs.getInt(KEY_DISCOVERY_LISTEN_PORT, DEFAULT_DISCOVERY_LISTEN_PORT);}

    /**
     * Saves the UDP port for listening to discovery responses.
     *
     * @param port the port number (1-65535)
     */
    public static void saveDiscoveryListenPort(int port) {saveInt(KEY_DISCOVERY_LISTEN_PORT, port, "Discovery listen port");}

    /**
     * Retrieves the SSH port.
     *
     * @return the SSH port (default: 22)
     */
    public static int getSshPort() {return prefs.getInt(KEY_SSH_PORT, DEFAULT_SSH_PORT);}

    /**
     * Saves the SSH port.
     *
     * @param port the SSH port
     */
    public static void saveSshPort(int port) {saveInt(KEY_SSH_PORT, port, "SSH port");}

    /**
     * Retrieves the Telnet port.
     *
     * @return the Telnet port (default: 23)
     */
    public static int getTelnetPort() {return prefs.getInt(KEY_TELNET_PORT, DEFAULT_TELNET_PORT);}

    /**
     * Saves the Telnet port.
     *
     * @param port the Telnet port
     */
    public static void saveTelnetPort(int port) {saveInt(KEY_TELNET_PORT, port, "Telnet port");}

    /**
     * Saves a string preference and flushes to persistent storage.
     *
     * @param key the preference key
     * @param value the value to save
     * @param description description for logging
     */
    private static void saveString(String key, String value, String description) {
        try {
            prefs.put(key, value);
            prefs.flush();
            logger.info("String {} saved: {}", description, value);
        } catch (BackingStoreException e) {
            logger.error("Failed to save {}", description, e);
        }
    }

    /**
     * Saves an integer preference and flushes to persistent storage.
     *
     * @param key the preference key
     * @param value the value to save
     * @param description description for logging
     */
    private static void saveInt(String key, int value, String description) {
        try {
            prefs.putInt(key, value);
            prefs.flush();
            logger.info("Int {} saved: {}", description, value);
        } catch (BackingStoreException e) {
            logger.error("Failed to save {}", description, e);
        }
    }

    /**
     * Saves a boolean preference and flushes to persistent storage.
     *
     * @param key the preference key
     * @param value the value to save
     * @param description description for logging
     */
    private static void saveBoolean(String key, boolean value, String description) {
        try {
            prefs.putBoolean(key, value);
            prefs.flush();
            logger.info("key {} saved: {}", description, value);
        } catch (BackingStoreException e) {
            logger.error("Failed to save {}", description, e);
        }
    }
}