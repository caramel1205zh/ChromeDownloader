import java.util.Locale;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Internationalization utility class for loading and formatting localized messages.
 */
public class I18n {
    private static ResourceBundle bundle;
    private static Locale currentLocale;
    
    // Supported locales with their resource file names
    public static final Locale ENGLISH_US = Locale.US;
    public static final Locale SIMPLIFIED_CHINESE = Locale.SIMPLIFIED_CHINESE;
    
    // Map of supported locales to their resource file names
    private static final Map<Locale, String> LOCALE_FILE_MAP = new HashMap<>();
    static {
        LOCALE_FILE_MAP.put(ENGLISH_US, "messages-en_US");
        LOCALE_FILE_MAP.put(SIMPLIFIED_CHINESE, "messages-zh_CN");
    }
    
    // Default locale (English US)
    private static final Locale DEFAULT_LOCALE = ENGLISH_US;
    private static final String DEFAULT_BUNDLE = "messages-en_US";
    
    static {
        // Initialize with detected locale
        setLocale(detectSystemLocale());
    }
    
    /**
     * Detects the best matching locale for the system.
     * @return The detected locale
     */
    public static Locale detectSystemLocale() {
        Locale systemLocale = Locale.getDefault();
        
        // Check if we have an exact match
        if (LOCALE_FILE_MAP.containsKey(systemLocale)) {
            return systemLocale;
        }
        
        // Check language only (ignore country)
        String systemLanguage = systemLocale.getLanguage();
        for (Locale supportedLocale : LOCALE_FILE_MAP.keySet()) {
            if (supportedLocale.getLanguage().equals(systemLanguage)) {
                return supportedLocale;
            }
        }
        
        // Default to English US
        return DEFAULT_LOCALE;
    }
    
    /**
     * Sets the locale for internationalization.
     * @param locale The locale to set
     */
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        String bundleName = LOCALE_FILE_MAP.get(locale);
        
        if (bundleName == null) {
            // If locale not found in map, try to find by language
            for (Locale supportedLocale : LOCALE_FILE_MAP.keySet()) {
                if (supportedLocale.getLanguage().equals(locale.getLanguage())) {
                    bundleName = LOCALE_FILE_MAP.get(supportedLocale);
                    currentLocale = supportedLocale;
                    break;
                }
            }
            
            // If still not found, use default
            if (bundleName == null) {
                bundleName = DEFAULT_BUNDLE;
                currentLocale = DEFAULT_LOCALE;
            }
        }
        
        try {
            bundle = ResourceBundle.getBundle("i18n." + bundleName, currentLocale);
        } catch (Exception e) {
            // Fallback to default if specified locale is not available
            System.err.println("Failed to load locale: " + currentLocale + ", falling back to default");
            try {
                bundle = ResourceBundle.getBundle("i18n." + DEFAULT_BUNDLE, DEFAULT_LOCALE);
                currentLocale = DEFAULT_LOCALE;
            } catch (Exception ex) {
                System.err.println("Failed to load default bundle: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Gets the current locale.
     * @return The current locale
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }
    
    /**
     * Gets available locales.
     * @return Array of available locales
     */
    public static Locale[] getAvailableLocales() {
        return LOCALE_FILE_MAP.keySet().toArray(new Locale[0]);
    }
    
    /**
     * Gets locale display name in its own language.
     * @param locale The locale
     * @return Display name
     */
    public static String getLocaleDisplayName(Locale locale) {
        if (locale.equals(ENGLISH_US)) {
            return "English";
        } else if (locale.equals(SIMPLIFIED_CHINESE)) {
            return "简体中文";
        }
        return locale.getDisplayName(locale);
    }
    
    /**
     * Gets the resource bundle file name for a locale.
     * @param locale The locale
     * @return Bundle file name without path
     */
    public static String getBundleFileName(Locale locale) {
        return LOCALE_FILE_MAP.getOrDefault(locale, DEFAULT_BUNDLE);
    }
    
    /**
     * Gets a localized string for the given key.
     * @param key The message key
     * @return The localized string, or the key itself if not found
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }
    
    /**
     * Gets a localized string with arguments.
     * @param key The message key
     * @param args Arguments to format into the message
     * @return The formatted localized string
     */
    public static String format(String key, Object... args) {
        String pattern = get(key);
        if (pattern.equals(key)) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }
    
    /**
     * Gets the localized OS display name.
     * @param osName The OS name from PlatformUtils
     * @return Localized OS name
     */
    public static String getLocalizedOSName(String osName) {
        switch (osName.toLowerCase()) {
            case "windows": return get("os.windows");
            case "macos": return get("os.macos");
            default: return get("os.unknown");
        }
    }
    
    /**
     * Gets the localized version name.
     * @param version The version key (Stable, Beta, Dev, Canary)
     * @return Localized version name
     */
    public static String getLocalizedVersion(String version) {
        String key = "version." + version.toLowerCase();
        String localized = get(key);
        return localized.equals(key) ? version : localized;
    }
    
    /**
     * Gets the localized proxy type string.
     * @param proxyType The proxy type string
     * @return Localized proxy type
     */
    public static String getLocalizedProxyType(String proxyType) {
        if (proxyType == null || proxyType.equals("No Proxy")) {
            return get("proxyType.none");
        }
        String key = "proxyType." + proxyType.toLowerCase();
        String localized = get(key);
        return localized.equals(key) ? proxyType : localized;
    }
}