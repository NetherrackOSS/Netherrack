package com.netherrack.server;

import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.LoggingConfig;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.FlatPreset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Netherrack's own settings, kept in netherrack.yml - server.properties keeps the ones
 * Bedrock Dedicated Server has too. A missing netherrack.yml is written with every setting
 * at its default and a comment above it saying what it does. Settings are named by their
 * section and key, like "logs.debug".
 * <p>
 * Netherrack once kept debug-logs and flat-world-preset in server.properties. A server that
 * still has them there gets their values in its new netherrack.yml.
 * <p>
 * Of YAML, this reads what the settings need: sections of "key: value" lines nested by
 * indentation, values plain or in quotes, and # comments.
 */
public class NetherrackConfig {

    public static final Path FILE = Path.of("netherrack.yml");

    /**
     * Every setting, in the order a new netherrack.yml lists them, by section.
     *
     * @param serverProperties where to take the values of the settings Netherrack used to
     *                         keep in server.properties, or null for their defaults
     */
    private static List<Section> defaults(ServerConfig serverProperties) {
        List<String> names = Arrays.stream(FlatPreset.values())
                .map(preset -> "\"" + preset.getDisplayName() + "\"")
                .toList();
        String presets = String.join(", ", names.subList(0, names.size() - 1)) + ", or " + names.get(names.size() - 1);
        boolean debug = false;
        String preset = FlatPreset.CLASSIC_FLAT.getDisplayName();
        if (serverProperties != null) {
            // "debug" is what debug-logs was called before that.
            debug = serverProperties.getBoolean("debug-logs", serverProperties.getBoolean("debug", debug));
            preset = serverProperties.get("flat-world-preset", preset);
        }
        return List.of(
                new Section("logs", List.of(
                        new Setting("debug", String.valueOf(debug), false,
                                "If true, every network packet and other debugging detail is written to"
                                        + " logs/latest.log as well, without showing in the console. Useful when"
                                        + " something goes wrong, but the log grows quickly.",
                                "Allowed values: true or false"))),
                new Section("logging", List.of(
                        new Setting("info-format", LoggingConfig.DEFAULT_INFO_FORMAT, true,
                                "The INFO line's pattern. {time}, {date}, {millis}, {thread}, {level}, {message},"
                                        + " {prefix} and {suffix} are filled in; <yellow> inserts a color, <bold> a style"
                                        + " and <reset> clears both. The log file keeps its own plain shape."),
                        new Setting("warn-format", LoggingConfig.DEFAULT_WARN_FORMAT, true,
                                "The WARN line's pattern, with the same placeholders as info-format."),
                        new Setting("error-format", LoggingConfig.DEFAULT_ERROR_FORMAT, true,
                                "The ERROR line's pattern, with the same placeholders as info-format."),
                        new Setting("debug-format", LoggingConfig.DEFAULT_DEBUG_FORMAT, true,
                                "The DEBUG line's pattern, shown when console-debug below is true."),
                        new Setting("format", LoggingConfig.DEFAULT_FORMAT, true,
                                "Fallback pattern for any level that has no pattern of its own above."),
                        new Setting("prefix", LoggingConfig.DEFAULT_PREFIX, true,
                                "Text put in {prefix}, before or after it in the patterns above."),
                        new Setting("suffix", LoggingConfig.DEFAULT_SUFFIX, true,
                                "Text put in {suffix}, before or after it in the patterns above."),
                        new Setting("info-prefix", LoggingConfig.DEFAULT_PREFIX, true,
                                "INFO's {prefix}, falling back to the shared prefix above."),
                        new Setting("info-suffix", LoggingConfig.DEFAULT_SUFFIX, true,
                                "INFO's {suffix}, falling back to the shared suffix above."),
                        new Setting("warn-prefix", LoggingConfig.DEFAULT_PREFIX, true,
                                "WARN's {prefix}, falling back to the shared prefix above."),
                        new Setting("warn-suffix", LoggingConfig.DEFAULT_SUFFIX, true,
                                "WARN's {suffix}, falling back to the shared suffix above."),
                        new Setting("error-prefix", LoggingConfig.DEFAULT_PREFIX, true,
                                "ERROR's {prefix}, falling back to the shared prefix above."),
                        new Setting("error-suffix", LoggingConfig.DEFAULT_SUFFIX, true,
                                "ERROR's {suffix}, falling back to the shared suffix above."),
                        new Setting("debug-prefix", LoggingConfig.DEFAULT_PREFIX, true,
                                "DEBUG's {prefix}, falling back to the shared prefix above."),
                        new Setting("debug-suffix", LoggingConfig.DEFAULT_SUFFIX, true,
                                "DEBUG's {suffix}, falling back to the shared suffix above."),
                        new Setting("time-format", LoggingConfig.DEFAULT_TIME_FORMAT, true,
                                "How {time} is rendered, as a Java time pattern (for example HH:mm:ss)."),
                        new Setting("date-format", LoggingConfig.DEFAULT_DATE_FORMAT, true,
                                "How {date} is rendered, as a Java date pattern (for example yyyy-MM-dd)."),
                        new Setting("colors", LoggingConfig.DEFAULT_COLOR_MODE, false,
                                "Whether console colors and message formatting codes are used: auto detects a"
                                        + " terminal, true always uses them, false never does.",
                                "Allowed values: auto, true or false"),
                        new Setting("console-debug", String.valueOf(LoggingConfig.DEFAULT_CONSOLE_DEBUG), false,
                                "Whether DEBUG lines use debug-format on the console, as well as in the log file.",
                                "Allowed values: true or false"),
                        new Setting("info-enabled", "true", false,
                                "Whether INFO lines are shown on the console (they stay in the log file).",
                                "Allowed values: true or false"),
                        new Setting("warn-enabled", "true", false,
                                "Whether WARN lines are shown on the console.",
                                "Allowed values: true or false"),
                        new Setting("error-enabled", "true", false,
                                "Whether ERROR lines are shown on the console.",
                                "Allowed values: true or false"),
                        new Setting("debug-enabled", "true", false,
                                "Whether DEBUG lines are shown on the console when console-debug is true.",
                                "Allowed values: true or false"))),
                new Section("labels", List.of(
                        new Setting("timestamp-color", LoggingConfig.DEFAULT_TIMESTAMP_COLOR, true,
                                "The color name for {time}, or null for none.",
                                "Allowed names: " + LoggingConfig.COLOR_NAMES),
                        new Setting("thread-color", LoggingConfig.DEFAULT_THREAD_COLOR, true,
                                "The color name for {thread}, or null for none."),
                        new Setting("info-color", LoggingConfig.DEFAULT_INFO_COLOR, true,
                                "The color name for the INFO label, or null for none."),
                        new Setting("warn-color", LoggingConfig.DEFAULT_WARN_COLOR, true,
                                "The color name for the WARN label, or null for none."),
                        new Setting("error-color", LoggingConfig.DEFAULT_ERROR_COLOR, true,
                                "The color name for the ERROR label, or null for none."),
                        new Setting("debug-color", String.valueOf(LoggingConfig.DEFAULT_DEBUG_COLOR), false,
                                "The color name for the DEBUG label, or null for none."),
                        new Setting("bracket-color", String.valueOf(LoggingConfig.DEFAULT_BRACKET_COLOR), false,
                                "The color name for brackets around placeholders in the format, or null for none.",
                                "Color names: " + LoggingConfig.COLOR_NAMES,
                                "Style names: " + LoggingConfig.STYLE_NAMES))),
                new Section("messages", List.of(
                        new Setting("server-shutdown", "Server closed", true,
                                "What the players still on the server are shown when it closes."),
                        new Setting("server-online-mode",
                                "You must be signed into Xbox Live to join this server!", true,
                                "What a player without Xbox Live authentication is shown when"
                                        + " online-mode is true."),
                        new Setting("server-whitelist", "You are not whitelisted on this server!", true,
                                "What a player who isn't on the whitelist is shown as they're turned away."),
                        new Setting("server-ban", "You are banned!", true,
                                "What a banned player is shown as they're turned away. The stored reason, if"
                                        + " any, is appended after \"Reason:\"."))),
                new Section("level", List.of(
                        new Setting("preset-type", preset, true,
                                "Which of the game's flat world presets a new flat level is made from, which sets"
                                        + " the blocks of each layer from the bottom of the world up. Only used when"
                                        + " level-type in server.properties is \"flat\".",
                                "Allowed values: " + presets))));
    }

    private record Section(String name, List<Setting> settings) {
    }

    /** A setting: its key in its section, its default value, whether that's written in quotes, and its comment. */
    private record Setting(String key, String value, boolean quoted, String... comment) {
    }

    /** A section of the file being read, by how far its keys are indented. */
    private record Open(int indent, String name) {
    }

    private final Path path;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> fallbacks = new HashMap<>();

    public NetherrackConfig(Path path) {
        this.path = path;
    }

    /**
     * Reads netherrack.yml, writing it first if it's missing.
     *
     * @param serverProperties where a new netherrack.yml takes the settings Netherrack used to
     *                         keep in server.properties from, if they're still there
     */
    public void load(ServerConfig serverProperties) {
        for (Section section : defaults(null)) {
            for (Setting setting : section.settings()) {
                fallbacks.put(section.name() + "." + setting.key(), setting.value());
            }
        }
        if (!Files.exists(path)) {
            Logger.warn(Lang.current.get("server.netherrack_config_missing"));
            writeDefaults(defaults(serverProperties));
        }
        try {
            read(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            Logger.error("Failed to load " + path + ": " + e.getMessage());
        }
    }

    /**
     * Reads the file without writing defaults or logging anything, so logging can be set up
     * before the first line is printed. A missing or unreadable file simply has no settings;
     * {@link #load(ServerConfig)} reports those problems once logging is available.
     */
    public static Map<String, String> readQuietly(Path path) {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(path)) {
            return values;
        }
        try {
            readInto(Files.readAllLines(path, StandardCharsets.UTF_8), values, null);
        } catch (IOException ignored) {
            // The normal load reports this once the console logger has been configured.
        }
        return values;
    }

    /** A setting's value, like "messages.server-shutdown", or its default if the file doesn't set it. */
    public String getString(String key) {
        return values.getOrDefault(key, fallbacks.get(key));
    }

    /** A setting that's true or false, or its default if the file doesn't set it to either. */
    public boolean getBoolean(String key) {
        String value = getString(key);
        if (value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(value.toLowerCase(Locale.ROOT));
        }
        Logger.warn(key + " in " + path + " is \"" + value + "\", but has to be true or false, so it's "
                + fallbacks.get(key) + ".");
        return Boolean.parseBoolean(fallbacks.get(key));
    }

    /** Writes a new netherrack.yml: each section with its settings, each under its comment, a blank line between sections. */
    private void writeDefaults(List<Section> sections) {
        String newline = System.lineSeparator();
        StringBuilder text = new StringBuilder();
        for (Section section : sections) {
            if (!text.isEmpty()) {
                text.append(newline);
            }
            text.append(section.name()).append(':').append(newline);
            for (Setting setting : section.settings()) {
                for (String line : setting.comment()) {
                    text.append("  # ").append(line).append(newline);
                }
                String value = setting.quoted() ? quoted(setting.value()) : setting.value();
                text.append("  ").append(setting.key()).append(": ").append(value).append(newline);
            }
        }
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.error("Failed to write default " + path + ": " + e.getMessage());
        }
    }

    /**
     * Reads the file's settings: a "key:" line opens a section, whose keys are indented
     * further, and a "key: value" line sets the setting named by the sections around it and
     * its key. A line that's neither is warned about and skipped.
     */
    private void read(List<String> lines) {
        readInto(lines, values, path);
    }

    private static void readInto(List<String> lines, Map<String, String> values, Path path) {
        Deque<Open> open = new ArrayDeque<>();
        for (int number = 1; number <= lines.size(); number++) {
            String line = withoutComment(lines.get(number - 1));
            if (line.isBlank()) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            String content = line.strip();
            while (!open.isEmpty() && open.peek().indent() >= indent) {
                open.pop();
            }
            int colon = keyEnd(content);
            if (colon < 0) {
                if (path != null) {
                    Logger.warn(path + " line " + number + " isn't \"key: value\" (is there a space after the"
                            + " colon?), so it's skipped: " + content);
                }
                continue;
            }
            String key = unquoted(content.substring(0, colon).strip());
            String name = open.isEmpty() ? key : open.peek().name() + "." + key;
            String value = content.substring(colon + 1).strip();
            if (value.isEmpty()) {
                open.push(new Open(indent, name));
            } else {
                values.put(name, unquoted(value));
            }
        }
    }

    /** The line without its comment: from a # that starts it or follows a space, outside quotes. */
    private static String withoutComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** Where the colon ending a line's key is - one followed by a space, or ending the line - or -1 for none. */
    private static int keyEnd(String content) {
        char quote = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == ':' && (i == content.length() - 1 || content.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A value as written: in double quotes, with backslash escapes for a quote, a backslash,
     * a line feed, a tab, and a four-digit hex character code; in single quotes, with '' for
     * one quote; or plain, as it is. The hex codes matter for the label colors, which are
     * ANSI escape sequences.
     */
    private static String unquoted(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) {
            return value;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length() - 1) {
                char escaped = value.charAt(++i);
                switch (escaped) {
                    case 'n' -> text.append('\n');
                    case 't' -> text.append('\t');
                    case 'u' -> {
                        int end = i + 5; // the u plus four hex digits
                        if (end <= value.length() - 1) {
                            try {
                                text.append((char) Integer.parseInt(value.substring(i + 1, end), 16));
                                i = end - 1;
                            } catch (NumberFormatException e) {
                                text.append('u');
                            }
                        } else {
                            text.append('u');
                        }
                    }
                    default -> text.append(escaped);
                }
            } else {
                text.append(c);
            }
        }
        return text.toString();
    }

    private static String quoted(String value) {
        StringBuilder text = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> text.append("\\\\");
                case '"' -> text.append("\\\"");
                case '\n' -> text.append("\\n");
                case '\t' -> text.append("\\t");
                default -> {
                    if (c < 0x20) {
                        text.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    } else {
                        text.append(c);
                    }
                }
            }
        }
        return text.append('"').toString();
    }
}
