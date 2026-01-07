package com.rover.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Manager for handling internationalization (i18n) in Rover21.
 * Manages language switching and provides translated strings.
 * Singleton pattern to ensure a single instance across the application.
 * @see <a href="https://www.baeldung.com/java-singleton"> Singletons in Java</a>
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026.
 */
public class LanguageManager{

    private static final Logger logger = LoggerFactory.getLogger(LanguageManager.class);
    private static LanguageManager instance;

    private ResourceBundle bundle;
    private Locale currentLocale;

    /**
     * Private constructor for singleton pattern.
     * Initializes with system default locale or English.
     */
    private LanguageManager() {
        String savedLanguage = PreferencesManager.getLanguage(); // Try to load saved language preference, otherwise use system default
        if (savedLanguage != null && !savedLanguage.isEmpty()) {
            setLanguage(savedLanguage);
        } else {
            Locale systemLocale = Locale.getDefault(); // Use system default or fallback to English
            if (systemLocale.getLanguage().equals("fr")) { // Manages 2 languages for now : French and English
                setLocale(Locale.FRENCH);
            } else {
                setLocale(Locale.ENGLISH);
            }
        }
        logger.info("LanguageManager initialized with locale : {}", currentLocale);
    }

    /**
     * Gets the singleton instance of LanguageManager.
     * @return the unique instance of LanguageManager
     */
    public static LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    /**
     * Sets the locale and reloads the resource bundle.
     * @param locale the new locale to use
     * @see Locale
     */
    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        try {
            this.bundle = ResourceBundle.getBundle("i18n.messages", locale);
            logger.info("Locale changed to: {}", locale);
        } catch (Exception e) {
            logger.error("Failed to load resource bundle for locale: {}", locale, e);
            // Fallback to English
            this.bundle = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        }
    }

    /**
     * Sets the language using a language code.
     * @param languageCode the ISO 639-1 language code (e.g., "en", "fr")
     */
    public void setLanguage(String languageCode) {
        Locale locale = switch (languageCode) {
            case "fr" -> Locale.FRENCH;
            case "en" -> Locale.ENGLISH;
            default -> {
                logger.warn("Unsupported language code: {}. Going back to English.", languageCode);
                yield Locale.ENGLISH;
            }
        };
        setLocale(locale);
        PreferencesManager.saveLanguage(languageCode);
    }

    /**
     * Gets a translated string for the given key.
     * @param key the resource bundle key
     * @return the translated string, or "!key!" if not found
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            logger.warn("Translation key not found : {}", key);
            return "!" + key + "!"; // Returns the key with ! markers if translation is missing
        }
    }

    /**
     * Gets a translated string with formatted parameters.
     * Uses MessageFormat for parameter substitution.
     * ex: get("status.ready_adapters", 3) => "Ready - 3 adapter(s) available."
     * @param key the resource bundle key
     * @param args the arguments to format into the message
     * @return the formatted translated string
     */
    public String get(String key, Object... args) {
        String message = get(key);
        try {
            return MessageFormat.format(message, args);
        } catch (Exception e) {
            logger.error("Error formatting message for key: {} with args: {}", key, args, e);
            return message; // Return unformatted message if formatting fails
        }
    }

    /**
     * Gets the current locale.
     * @return the current locale
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Gets the current language code.
     * @return the ISO 639-1 language code (e.g., "en", "fr")
     */
    public String getCurrentLanguageCode() {
        return currentLocale.getLanguage();
    }

    /**
     * Checks if the current language is English.
     * @return true if current language is English
     */
    public boolean isEnglish() {
        return "en".equals(getCurrentLanguageCode());
    }

    /**
     * Checks if the current language is French.
     * @return true if current language is French
     */
    public boolean isFrench() {
        return "fr".equals(getCurrentLanguageCode());
    }
}























