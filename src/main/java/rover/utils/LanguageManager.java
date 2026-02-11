package rover.utils;

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
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 * @see ResourceBundle
 * @see MessageFormat
 * @see PreferencesManager
 */
public class LanguageManager {

    private static final Logger logger = LoggerFactory.getLogger(LanguageManager.class);

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private static final String LANGUAGE_CODE_ENGLISH = "en";
    private static final String LANGUAGE_CODE_FRENCH = "fr";

    private static LanguageManager instance;

    private ResourceBundle bundle;

    private Locale currentLocale;

    /**
     * Private constructor for a singleton pattern.
     * <p>
     * Initializes the language manager by loading the saved language preference
     * or falling back to the system default locale. If the system locale is French,
     * French will be used; otherwise, English is used as the default.
     * </p>
     *
     * @see PreferencesManager#getLanguage()
     * @see #setLanguage(String)
     */
    private LanguageManager() {
        String savedLanguage = PreferencesManager.getLanguage();

        if (savedLanguage != null && !savedLanguage.isEmpty()) {
            setLanguage(savedLanguage);
        } else {
            initializeFromSystemLocale();
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
            this.bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
            logger.info("Locale changed to: {}", locale);
        } catch (Exception e) {
            logger.error("Failed to load resource bundle for locale: {}", locale, e);
            this.bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);
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
        Locale locale = getLocaleForLanguageCode(languageCode);
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
     * @param key  the resource bundle key
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
            return message;
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

    /**
     * Initializes the locale from the system by default.
     * <p>
     * If the system locale is French, French will be used; otherwise,
     * English is used as the default language.
     * </p>
     */
    private void initializeFromSystemLocale() {
        Locale systemLocale = Locale.getDefault();

        if (systemLocale.getLanguage().equals(LANGUAGE_CODE_FRENCH)) {
            setLocale(Locale.FRENCH);
        } else {
            setLocale(Locale.ENGLISH);
        }
    }

    /**
     * Converts a language code to a Locale object.
     *
     * @param languageCode the ISO 639-1 language code
     * @return the corresponding Locale, or Locale.ENGLISH if unsupported
     */
    private Locale getLocaleForLanguageCode(String languageCode) {
        return switch (languageCode) {
            case LANGUAGE_CODE_FRENCH -> Locale.FRENCH;
            case LANGUAGE_CODE_ENGLISH -> Locale.ENGLISH;
            default -> {
                logger.warn("Unsupported language code: {}. Falling back to English.", languageCode);
                yield Locale.ENGLISH;
            }
        };
    }
}