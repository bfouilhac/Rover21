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
}