package com.netherrack.server.setup;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads translated strings for a given language code from the bundled .properties files
 * under src/main/resources/lang/. New languages just need a new <code>.properties file
 * added there, with a matching entry in AVAILABLE below for it to show up in the setup
 * wizard's language list.
 */
public class Lang {

    /**
     * Available languages: code -> native display name (shown in the wizard regardless
     * of which language is currently selected, same as most software's language pickers).
     */
    public static final Map<String, String> AVAILABLE = new LinkedHashMap<>();

    static {
        AVAILABLE.put("en-US", "English (United States)");
        AVAILABLE.put("pt-BR", "Português (Brasil)");
    }

    public static final String DEFAULT_CODE = "en-US";

    /**
     * The language the rest of the server (startup/shutdown messages, commands, etc.)
     * should use. Set once at startup, either from the setup wizard's choice or from the
     * "language" saved in an existing server.properties.
     */
    public static Lang current = new Lang(DEFAULT_CODE);

    private final String code;
    private final Properties properties = new Properties();

    public Lang(String code) {
        this.code = AVAILABLE.containsKey(code) ? code : DEFAULT_CODE;
        load(this.code);
    }

    private void load(String languageCode) {
        String path = "/lang/" + languageCode + ".properties";
        try (InputStreamReader reader = openResource(path)) {
            properties.load(reader);
        } catch (IOException | NullPointerException e) {
            if (!languageCode.equals(DEFAULT_CODE)) {
                // Fall back to the default language rather than crashing the wizard over
                // a missing/broken translation file.
                load(DEFAULT_CODE);
            } else {
                throw new IllegalStateException("Default language file is missing or unreadable: " + path, e);
            }
        }
    }

    private InputStreamReader openResource(String path) {
        return new InputStreamReader(Lang.class.getResourceAsStream(path), StandardCharsets.UTF_8);
    }

    public String getCode() {
        return code;
    }

    public String get(String key) {
        return properties.getProperty(key, key);
    }

    public String get(String key, Object... args) {
        return String.format(get(key), args);
    }
}
