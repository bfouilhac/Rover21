package com.rover.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Manager for handling internationalization (i18n) in the Rover21 application.
 * <p>
 * This singleton class manages language switching and provides translated strings
 * from resource bundles. It supports multiple languages (currently English and French)
 * and automatically loads the user's preferred language from saved preferences or
 * system defaults.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 in January-February 2026
 * @see ResourceBundle
 * @see MessageFormat
 * @see <a href="https://www.baeldung.com/java-singleton">Singleton Pattern in Java</a>
 */
public class LanguageManager {

    private static final Logger logger = LoggerFactory.getLogger(LanguageManager.class);

    private static LanguageManager instance;

    private ResourceBundle bundle;

    private Locale currentLocale;

    /**
     * Private constructor for a singleton pattern.
     *
     * @see PreferencesManager#getLanguage()
     * @see #setLanguage(String)
     */
    private LanguageManager() {
        // Try to load saved language preference
        String savedLanguage = PreferencesManager.getLanguage();

        if (savedLanguage != null && !savedLanguage.isEmpty()) {
            setLanguage(savedLanguage);
        } else {
            // Use system default or fallback to English
            Locale systemLocale = Locale.getDefault();

            if (systemLocale.getLanguage().equals("fr")) {
                setLocale(Locale.FRENCH);
            } else {
                setLocale(Locale.ENGLISH);
            }
        }

        logger.info("LanguageManager initialized with locale: {}", currentLocale);
    }

    /**
     * Returns the singleton instance of LanguageManager.
     * <p>
     * This method implements lazy initialization, creating the instance only
     * when first accessed. Subsequent calls return the same instance.
     * </p>
     *
     * @return the unique LanguageManager instance
     */
    public static LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    /**
     * Sets the current locale and reloads the resource bundle.
     * <p>
     * This method changes the application's language by loading the appropriate
     * resource bundle for the specified locale. If the bundle cannot be loaded,
     * it falls back to English to prevent application failure.
     * </p>
     *
     * @param locale the new locale to use (e.g., Locale.FRENCH, Locale.ENGLISH)
     * @see ResourceBundle#getBundle(String, Locale)
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
     * Sets the language using an ISO 639-1 language code.
     * <p>
     * This method accepts standard two-letter language codes and converts them
     * to appropriate Locale objects. The language preference is automatically
     * saved for persistence across application restarts.
     * </p>
     *
     * @param languageCode the ISO 639-1 language code (e.g., "en", "fr")
     * @see #setLocale(Locale)
     * @see PreferencesManager#saveLanguage(String)
     */
    public void setLanguage(String languageCode) {
        Locale locale = switch (languageCode) {
            case "fr" -> Locale.FRENCH;
            case "en" -> Locale.ENGLISH;
            default -> {
                logger.warn("Unsupported language code: {}. Falling back to English.", languageCode);
                yield Locale.ENGLISH;
            }
        };

        setLocale(locale);
        PreferencesManager.saveLanguage(languageCode);
    }

    /**
     * Retrieves a translated string for the given key.
     * <p>
     * This method looks up the translation in the current resource bundle.
     * If the key is not found, it returns the key wrapped in exclamation marks
     * (e.g., "!missing.key!") to make missing translations easily identifiable
     * during development.
     * </p>
     *
     * @param key the resource bundle key (e.g., "status.ready", "menu.file")
     * @return the translated string, or "!key!" if the translation is missing
     * @see ResourceBundle#getString(String)
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            logger.warn("Translation key not found: {}", key);
            return "!" + key + "!";
        }
    }

    /**
     * Retrieves a translated string with formatted parameters.
     * <p>
     * This method supports parameterized translations using MessageFormat syntax.
     * Parameters are referenced in the resource bundle using {0}, {1}, etc.
     * </p>
     *
     * @param key the resource bundle key
     * @param args the arguments to format into the message (referenced as {0}, {1}, etc.)
     * @return the formatted translated string
     * @see MessageFormat#format(String, Object...)
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
     * Returns the current ISO 639-1 language code.
     * <p>
     * This method provides the two-letter language code of the currently
     * active locale, which can be useful for conditional logic or UI updates.
     * </p>
     *
     * @return the language code (e.g., "en", "fr")
     * @see Locale#getLanguage()
     */
    public String getCurrentLanguageCode() {
        return currentLocale.getLanguage();
    }
}