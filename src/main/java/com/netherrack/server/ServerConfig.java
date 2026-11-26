package com.netherrack.server;

import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class ServerConfig {

    /**
     * The settings Bedrock Dedicated Server has too, in the order a new server.properties
     * lists them, each with its default value and the comment written under it - laid out
     * the way BDS lays out its own.
     */
    private static List<Setting> vanillaDefaults() {
        return List.of(
                new Setting("server-name", "Netherrack Server",
                        "Used as the server name",
                        "Allowed values: Any string without semicolon symbol"),
                new Setting("gamemode", "survival",
                        "Sets the game mode for new players",
                        "Allowed values: \"survival\", \"creative\", \"adventure\", or \"spectator\""),
                new Setting("difficulty", "normal",
                        "Sets the difficulty of the world",
                        "Allowed values: \"peaceful\", \"easy\", \"normal\", or \"hard\""),
                new Setting("max-players", "20",
                        "The maximum number of players that can play on the server",
                        "Allowed values: Any positive integer"),
                new Setting("white-list", "false",
                        "If true, only players listed in whitelist.json (and operators) may join.",
                        "The whitelist command changes this while the server is running.",
                        "Allowed values: \"true\" or \"false\""),
                new Setting("online-mode", "true",
                        "If true then all connected players must be authenticated to Xbox Live.",
                        "Disabling it accepts self-signed/offline logins and NetherNet tokens, so only turn it off on"
                                + " a trusted LAN or behind a proxy.",
                        "Allowed values: \"true\" or \"false\""),
                new Setting("server-port", "19132",
                        "Which IPv4 port the server should listen to.",
                        "Allowed values: Integers in the range [1, 65535]"),
                new Setting("server-ip", "0.0.0.0",
                        "Which IP address the server should listen on. 0.0.0.0 listens on every address this"
                                + " machine has.",
                        "Allowed values: An IPv4 or IPv6 literal, or empty."),
                new Setting("view-distance", "32",
                        "The maximum allowed view distance in number of chunks.",
                        "Allowed values: Positive integer equal to 5 or greater."),
                new Setting("level-name", "Bedrock level",
                        "The name of the level to play on, which is also the name of its folder in worlds."
                                + " If there's no such level, a new one is created.",
                        "Allowed values: Any string without semicolon symbol or symbols illegal for file name:"
                                + " /\\n\\r\\t\\f`?*\\\\<>|\\\":"));
    }

    /**
     * Netherrack's own settings, listed after the vanilla ones under their own heading, in
     * alphabetical order. The language is the one picked in the setup wizard. The rest of
     * Netherrack's own settings are in netherrack.yml (see NetherrackConfig).
     */
    private static List<Setting> customDefaults(String language) {
        String languages = Lang.AVAILABLE.keySet().stream()
                .map(code -> "\"" + code + "\"")
                .collect(Collectors.joining(" or "));
        return List.of(
                new Setting("default-player-permission-level", "member",
                        "Permission level for new players joining for the first time.",
                        "Allowed values: \"visitor\", \"member\", \"operator\""),
                new Setting("language", language,
                        "The language of the server's own messages in the console. Players see the game in the"
                                + " language they've chosen themselves.",
                        "Allowed values: " + languages),
                new Setting("level-type", "DEFAULT",
                        "The type of world to create when the level doesn't exist yet. A level that already exists"
                                + " keeps the type it was created with.",
                        "Allowed values: \"flat\" (its layers are set by preset-type in netherrack.yml), or"
                                + " \"default\" for generated terrain - hills, valleys, lakes and trees, new every"
                                + " seed.",
                        "Commenting this out is the same as \"default\"."),
                new Setting("op-permission-level", "2",
                        "Which commands operators can use: 0 only the ones every player can, 2 adds commands"
                                + " like gamemode, 3 adds op and deop, and 4 adds stop.",
                        "Who is an operator is set in permissions.json, or with the op and deop commands.",
                        "Allowed values: Integers in the range [0, 4]"));
    }

    /** The heading between the vanilla settings and Netherrack's own. */
    private static final String CUSTOM_HEADING = "# " + "///// ".repeat(5) + "CUSTOM SERVER PROPERTIES "
            + ("\\".repeat(5) + " ").repeat(5) + "#";

    /**
     * The values a brand-new server.properties starts with, keyed by property name. The setup
     * wizard copies this map and replaces a few entries before asking ServerConfig to write it.
     */
    public static Map<String, String> defaultValues(String language) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting setting : vanillaDefaults()) {
            values.put(setting.key(), setting.value());
        }
        for (Setting setting : customDefaults(language)) {
            values.put(setting.key(), setting.value());
        }
        return values;
    }

    private record Setting(String key, String value, String... comment) {
    }

    private final Properties properties = new Properties();
    private final Path path;

    public ServerConfig(Path path) {
        this.path = path;
    }

    /**
     * @param language language code to save into a brand-new server.properties (e.g. the one
     *                 just picked in the setup wizard). Ignored if the file already exists.
     */
    public void load(String language) {
        load(language, Map.of());
    }

    /**
     * Loads the config, generating a new file with the supplied overrides when one doesn't
     * exist yet. Only the setup wizard passes overrides; existing files are never rewritten.
     */
    public void load(String language, Map<String, String> overrides) {
        if (!Files.exists(path)) {
            Logger.warn(Lang.current.get("server.config_missing"));
            writeDefaults(language, overrides);
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            Logger.error("Failed to load server.properties: " + e.getMessage());
        }
    }

    /**
     * Writes a new server.properties: each vanilla setting, then what it does, then a blank
     * line; then the heading, set apart by blank lines; then Netherrack's own settings the
     * same way.
     */
    private void writeDefaults(String language, Map<String, String> overrides) {
        String newline = System.lineSeparator();
        StringBuilder text = new StringBuilder();
        for (Setting setting : vanillaDefaults()) {
            write(text, setting, overrides, newline);
            text.append(newline);
        }
        text.append(newline).append(CUSTOM_HEADING).append(newline).append(newline).append(newline);
        List<Setting> custom = customDefaults(language);
        for (int i = 0; i < custom.size(); i++) {
            if (i > 0) {
                text.append(newline);
            }
            write(text, custom.get(i), overrides, newline);
        }
        try {
            // Properties files are read as ISO 8859-1; everything written here is plain ASCII.
            Files.writeString(path, text, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            Logger.error("Failed to write default server.properties: " + e.getMessage());
        }
    }

    private static void write(StringBuilder text, Setting setting, Map<String, String> overrides,
                              String newline) {
        String value = overrides.getOrDefault(setting.key(), setting.value());
        text.append(setting.key()).append('=').append(value).append(newline);
        for (String line : setting.comment()) {
            text.append("# ").append(line).append(newline);
        }
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    /**
     * Changes a setting in memory and writes it back to the file. The file is edited line by
     * line instead of being regenerated so the comments and hand-written settings around it
     * survive; a setting missing from an older file is appended.
     */
    public synchronized void set(String key, String value) {
        properties.setProperty(key, value);
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
            String assignment = key + "=" + value;
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith(key + "=")) {
                    lines.set(i, assignment);
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                    lines.add("");
                }
                lines.add(assignment);
            }
            Files.write(path, lines, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            Logger.error("Failed to save " + path + ": " + e.getMessage());
        }
    }

    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }
}
