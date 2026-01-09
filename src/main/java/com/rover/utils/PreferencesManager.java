package com.rover.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Manager for handling user preferences persistence.
 * Uses Java Preferences API to store settings between sessions.
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class PreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);
    private static final Preferences prefs = Preferences.userNodeForPackage(PreferencesManager.class);

    // Preference keys
    private static final String LANGUAGE_KEY = "language";
    private static final String WINDOW_WIDTH_KEY = "window.width";
    private static final String WINDOW_HEIGHT_KEY = "window.height";

    // SNMP preferences
    private static final String SNMP_COMMUNITY_KEY = "snmp.community";
    private static final String SNMP_TIMEOUT_KEY = "snmp.timeout";
    private static final String AUTO_ENRICH_SNMP_KEY = "auto.enrich.snmp";
    private static final String PORT_SNMP_KEY = "port.snmp.key";
    private static final String SNMP_RETRIES = "snmp.retries";

    // Discovery preferences
    private static final String DISCOVERY_SEND_PORT_KEY = "discovery.send.port";
    private static final String DISCOVERY_LISTEN_PORT_KEY = "discovery.listen.port";
    private static final String DISCOVERY_TIMEOUT_KEY = "discovery.timeout";

    /**
     * Saves the selected language preference.
     * @param languageCode the ISO 639-1 language code ("en", "fr")
     */
    public static void saveLanguage(String languageCode) {
        try {
            prefs.put(LANGUAGE_KEY, languageCode);
            prefs.flush(); // Force write to disk
            logger.info("Language preference saved: {}", languageCode);
        } catch (BackingStoreException e) {
            logger.error("Failed to save language preference", e);
        }
    }

    /**
     * Gets the saved language preference.
     * @return the saved language code, or "en" (English) as default
     */
    public static String getLanguage() {
        return prefs.get(LANGUAGE_KEY, "en"); // Default to English
    }

    /**
     * Saves the window size.
     * @param width the window width
     * @param height the window height
     */
    public static void saveWindowSize(int width, int height) {
        try {
            prefs.putInt(WINDOW_WIDTH_KEY, width);
            prefs.putInt(WINDOW_HEIGHT_KEY, height);
            prefs.flush();
            logger.debug("Window size saved : {}x{}", width, height);
        } catch (BackingStoreException e) {
            logger.error("Failed to save window size", e);
        }
    }

    /**
     * Gets the saved window width.
     * @param defaultWidth the default width if not saved
     * @return the saved window width
     */
    public static int getWindowWidth(int defaultWidth) {
        return prefs.getInt(WINDOW_WIDTH_KEY, defaultWidth);
    }

    /**
     * Gets the saved window height.
     * @param defaultHeight the default height if not saved
     * @return the saved window height
     */
    public static int getWindowHeight(int defaultHeight) {
        return prefs.getInt(WINDOW_HEIGHT_KEY, defaultHeight);
    }

    public static int getSnmpTimeout() {return prefs.getInt(SNMP_TIMEOUT_KEY, 1000);}

    public static String getSnmpCommunity() {return prefs.get(SNMP_COMMUNITY_KEY, "public");}

    public static int getDiscoveryTimeout() {return prefs.getInt(DISCOVERY_TIMEOUT_KEY, 5000);}

    public static boolean getAutoEnrichSnmp() {return prefs.getBoolean(AUTO_ENRICH_SNMP_KEY, true);}

    public static int getSNMPRetries() {return prefs.getInt(SNMP_RETRIES, 3);}

    public static int getPortSNMP() {return prefs.getInt(PORT_SNMP_KEY, 161);}

    public static void saveSNMPRetries(String retries) {
        try {
            prefs.put(SNMP_RETRIES, retries);
            prefs.flush();
            logger.info("SNMP retries saved");
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP retries", e);
        }
    }

    public static void saveSNMPPort(String snmpPort) {
        try {
            prefs.put(PORT_SNMP_KEY, snmpPort);
            prefs.flush();
            logger.info("SNMP port saved");
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP port", e);
        }
    }

    // SNMP Community
    public static void saveSnmpCommunity(String community) {
        try {
            prefs.put(SNMP_COMMUNITY_KEY, community);
            prefs.flush();
            logger.info("SNMP community saved");
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP community", e);
        }
    }

    // SNMP Timeout
    public static void saveSnmpTimeout(int timeout) {
        try {
            prefs.putInt(SNMP_TIMEOUT_KEY, timeout);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save SNMP timeout", e);
        }
    }

    // Discovery Timeout
    public static void saveDiscoveryTimeout(int timeout) {
        try {
            prefs.putInt(DISCOVERY_TIMEOUT_KEY, timeout);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save discovery timeout", e);
        }
    }

    // Auto Enrich SNMP
    public static void saveAutoEnrichSnmp(boolean autoEnrich) {
        try {
            prefs.putBoolean(AUTO_ENRICH_SNMP_KEY, autoEnrich);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save auto enrich SNMP", e);
        }
    }

    // Discovery Send Port
    public static void saveDiscoverySendPort(int port) {
        try {
            prefs.putInt(DISCOVERY_SEND_PORT_KEY, port);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save discovery send port", e);
        }
    }

    public static int getDiscoverySendPort() {
        return prefs.getInt(DISCOVERY_SEND_PORT_KEY, 62630);
    }

    // Discovery Listen Port
    public static void saveDiscoveryListenPort(int port) {
        try {
            prefs.putInt(DISCOVERY_LISTEN_PORT_KEY, port);
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to save discovery listen port", e);
        }
    }

    public static int getDiscoveryListenPort() {
        return prefs.getInt(DISCOVERY_LISTEN_PORT_KEY, 62631);
    }
}