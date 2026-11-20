package com.netherrack.server.util;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The console's line formats and label colors, read from netherrack.yml before the server logs
 * anything.
 * <p>
 * Keeping these values here rather than in Logger lets the logger simply render whatever it
 * is given. Colors are written as names such as "cyan" or "bright-red" - or as "#RRGGBB" for
 * terminals that support it - and resolved here, so the config file never has to hold escape
 * characters. A missing file, a missing section, or a bad value leaves the built-in default in
 * place, so even the first line of a fresh install is readable. Only the console uses these
 * settings; the log file stays plain text in its usual shape.
 */
public final class LoggingConfig {

    public static final String DEFAULT_FORMAT = "[{time}] [{thread}] [{level}] {message}";
    public static final String DEFAULT_INFO_FORMAT = DEFAULT_FORMAT;
    public static final String DEFAULT_WARN_FORMAT = DEFAULT_FORMAT;
    public static final String DEFAULT_ERROR_FORMAT = DEFAULT_FORMAT;
    public static final String DEFAULT_DEBUG_FORMAT = DEFAULT_FORMAT;
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    /** Colors on a real terminal, off when output is redirected, as the safest default. */
    public static final String DEFAULT_COLOR_MODE = "auto";
    public static final String DEFAULT_PREFIX = "";
    public static final String DEFAULT_SUFFIX = "";
    public static final boolean DEFAULT_CONSOLE_DEBUG = false;

    public static final String DEFAULT_TIMESTAMP_COLOR = "cyan";
    public static final String DEFAULT_THREAD_COLOR = "yellow";
    public static final String DEFAULT_INFO_COLOR = "blue";
    public static final String DEFAULT_WARN_COLOR = "red";
    public static final String DEFAULT_ERROR_COLOR = "bright-red";
    public static final String DEFAULT_DEBUG_COLOR = null;
    public static final String DEFAULT_BRACKET_COLOR = null;

    /** The names operators can write in a color setting, for config comments and warnings. */
    public static final String COLOR_NAMES = "black, red, green, yellow, blue, magenta, cyan, white,"
            + " gray, bright-black, bright-red, bright-green, bright-yellow, bright-blue,"
            + " bright-magenta, bright-cyan, bright-white (also purple and grey), or #RRGGBB";
    public static final String STYLE_NAMES = "bold, italic, underline, reset";
    public static final String VALUE_NAMES = "time, date, millis, thread, level, message, prefix, suffix";

    private static final String RESET = "\u001B[0m";

    /** Names usable inside <...> in a format, beyond colors and styles. */
    private static final Set<String> INLINE_NAMES = Set.of(
            "time", "date", "millis", "thread", "level", "message", "prefix", "suffix");

    /** Names to ANSI colors. The regular ones match ColorCodes, so console colors stay consistent. */
    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("black", "\u001B[0;30m"),
            Map.entry("red", "\u001B[0;31m"),
            Map.entry("green", "\u001B[0;32m"),
            Map.entry("yellow", "\u001B[0;33m"),
            Map.entry("blue", "\u001B[0;34m"),
            Map.entry("magenta", "\u001B[0;35m"),
            Map.entry("purple", "\u001B[0;35m"),
            Map.entry("cyan", "\u001B[0;36m"),
            Map.entry("white", "\u001B[0;37m"),
            Map.entry("gray", "\u001B[0;90m"),
            Map.entry("grey", "\u001B[0;90m"),
            Map.entry("bright-black", "\u001B[0;90m"),
            Map.entry("bright-red", "\u001B[1;31m"),
            Map.entry("bright-green", "\u001B[1;32m"),
            Map.entry("bright-yellow", "\u001B[1;33m"),
            Map.entry("bright-blue", "\u001B[1;34m"),
            Map.entry("bright-magenta", "\u001B[1;35m"),
            Map.entry("bright-cyan", "\u001B[1;36m"),
            Map.entry("bright-white", "\u001B[1;37m"));

    /** Styles are separate from colors so <bold> can be combined with a colored label. */
    private static final Map<String, String> STYLES = Map.of(
            "bold", "\u001B[1m",
            "italic", "\u001B[3m",
            "underline", "\u001B[4m",
            "reset", RESET);

    /** Whether color output is forced, dropped, or decided by whether stdout is a terminal. */
    public enum ColorMode {
        ALWAYS,
        NEVER,
        AUTO
    }

    private final String format;
    private final String infoFormat;
    private final String warnFormat;
    private final String errorFormat;
    private final String debugFormat;
    private final DateTimeFormatter timeFormat;
    private final DateTimeFormatter dateFormat;
    private final ColorMode colorMode;
    private final boolean consoleDebug;
    private final String prefix;
    private final String suffix;
    private final String infoPrefix;
    private final String infoSuffix;
    private final String warnPrefix;
    private final String warnSuffix;
    private final String errorPrefix;
    private final String errorSuffix;
    private final String debugPrefix;
    private final String debugSuffix;
    private final boolean infoEnabled;
    private final boolean warnEnabled;
    private final boolean errorEnabled;
    private final boolean debugEnabled;
    private final String timestampColor;
    private final String threadColor;
    private final String infoColor;
    private final String warnColor;
    private final String errorColor;
    private final String debugColor;
    private final String bracketColor;
    private final List<String> warnings;

    private LoggingConfig(String format, String infoFormat, String warnFormat, String errorFormat,
                          String debugFormat, DateTimeFormatter timeFormat, DateTimeFormatter dateFormat,
                          ColorMode colorMode, boolean consoleDebug, String prefix, String suffix,
                          String infoPrefix, String infoSuffix, String warnPrefix, String warnSuffix,
                          String errorPrefix, String errorSuffix, String debugPrefix, String debugSuffix,
                          boolean infoEnabled, boolean warnEnabled, boolean errorEnabled,
                          boolean debugEnabled, String timestampColor, String threadColor,
                          String infoColor, String warnColor, String errorColor, String debugColor,
                          String bracketColor, List<String> warnings) {
        this.format = format;
        this.infoFormat = infoFormat;
        this.warnFormat = warnFormat;
        this.errorFormat = errorFormat;
        this.debugFormat = debugFormat;
        this.timeFormat = timeFormat;
        this.dateFormat = dateFormat;
        this.colorMode = colorMode;
        this.consoleDebug = consoleDebug;
        this.prefix = prefix;
        this.suffix = suffix;
        this.infoPrefix = infoPrefix;
        this.infoSuffix = infoSuffix;
        this.warnPrefix = warnPrefix;
        this.warnSuffix = warnSuffix;
        this.errorPrefix = errorPrefix;
        this.errorSuffix = errorSuffix;
        this.debugPrefix = debugPrefix;
        this.debugSuffix = debugSuffix;
        this.infoEnabled = infoEnabled;
        this.warnEnabled = warnEnabled;
        this.errorEnabled = errorEnabled;
        this.debugEnabled = debugEnabled;
        this.timestampColor = timestampColor;
        this.threadColor = threadColor;
        this.infoColor = infoColor;
        this.warnColor = warnColor;
        this.errorColor = errorColor;
        this.debugColor = debugColor;
        this.bracketColor = bracketColor;
        this.warnings = warnings;
    }

    /** The settings used when netherrack.yml has nothing to say about logging. */
    public static LoggingConfig defaults() {
        return new LoggingConfig(DEFAULT_FORMAT, DEFAULT_INFO_FORMAT, DEFAULT_WARN_FORMAT,
                DEFAULT_ERROR_FORMAT, DEFAULT_DEBUG_FORMAT,
                DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT),
                DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT), ColorMode.AUTO, DEFAULT_CONSOLE_DEBUG,
                DEFAULT_PREFIX, DEFAULT_SUFFIX, DEFAULT_PREFIX, DEFAULT_SUFFIX, DEFAULT_PREFIX,
                DEFAULT_SUFFIX, DEFAULT_PREFIX, DEFAULT_SUFFIX, DEFAULT_PREFIX, DEFAULT_SUFFIX,
                true, true, true, true,
                named(DEFAULT_TIMESTAMP_COLOR), named(DEFAULT_THREAD_COLOR),
                named(DEFAULT_INFO_COLOR), named(DEFAULT_WARN_COLOR), named(DEFAULT_ERROR_COLOR),
                named(DEFAULT_DEBUG_COLOR), named(DEFAULT_BRACKET_COLOR), List.of());
    }

    /**
     * Builds the settings from the flattened keys of netherrack.yml, ignoring any section
     * other than logging and labels. Problems are collected as warnings for Logger to print
     * once the settings are in place. Missing per-level settings fall back to their shared
     * setting, which in turn falls back to the built-in default.
     */
    public static LoggingConfig from(Map<String, String> values) {
        List<String> warnings = new ArrayList<>();
        String format = text(values, "logging.format", DEFAULT_FORMAT, warnings);
        String infoFormat = text(values, "logging.info-format", format, warnings);
        String warnFormat = text(values, "logging.warn-format", format, warnings);
        String errorFormat = text(values, "logging.error-format", format, warnings);
        String debugFormat = text(values, "logging.debug-format", format, warnings);
        DateTimeFormatter timeFormat = formatter(values, "logging.time-format", DEFAULT_TIME_FORMAT,
                warnings);
        DateTimeFormatter dateFormat = formatter(values, "logging.date-format", DEFAULT_DATE_FORMAT,
                warnings);
        ColorMode colorMode = colorMode(values, warnings);
        boolean consoleDebug = bool(values, "logging.console-debug", DEFAULT_CONSOLE_DEBUG, warnings);

        String prefix = optional(values, "logging.prefix", DEFAULT_PREFIX);
        String suffix = optional(values, "logging.suffix", DEFAULT_SUFFIX);
        String infoPrefix = optional(values, "logging.info-prefix", prefix);
        String infoSuffix = optional(values, "logging.info-suffix", suffix);
        String warnPrefix = optional(values, "logging.warn-prefix", prefix);
        String warnSuffix = optional(values, "logging.warn-suffix", suffix);
        String errorPrefix = optional(values, "logging.error-prefix", prefix);
        String errorSuffix = optional(values, "logging.error-suffix", suffix);
        String debugPrefix = optional(values, "logging.debug-prefix", prefix);
        String debugSuffix = optional(values, "logging.debug-suffix", suffix);
        boolean infoEnabled = bool(values, "logging.info-enabled", true, warnings);
        boolean warnEnabled = bool(values, "logging.warn-enabled", true, warnings);
        boolean errorEnabled = bool(values, "logging.error-enabled", true, warnings);
        boolean debugEnabled = bool(values, "logging.debug-enabled", true, warnings);

        String timestamp = color(values, "labels.timestamp-color", DEFAULT_TIMESTAMP_COLOR, warnings);
        String thread = color(values, "labels.thread-color", DEFAULT_THREAD_COLOR, warnings);
        String info = color(values, "labels.info-color", DEFAULT_INFO_COLOR, warnings);
        String warn = color(values, "labels.warn-color", DEFAULT_WARN_COLOR, warnings);
        String error = color(values, "labels.error-color", DEFAULT_ERROR_COLOR, warnings);
        String debug = color(values, "labels.debug-color", DEFAULT_DEBUG_COLOR, warnings);
        String bracket = color(values, "labels.bracket-color", DEFAULT_BRACKET_COLOR, warnings);

        checkInline(format, "logging.format", warnings);
        checkInline(infoFormat, "logging.info-format", warnings);
        checkInline(warnFormat, "logging.warn-format", warnings);
        checkInline(errorFormat, "logging.error-format", warnings);
        checkInline(debugFormat, "logging.debug-format", warnings);
        checkInline(prefix, "logging.prefix", warnings);
        checkInline(suffix, "logging.suffix", warnings);
        checkInline(infoPrefix, "logging.info-prefix", warnings);
        checkInline(infoSuffix, "logging.info-suffix", warnings);
        checkInline(warnPrefix, "logging.warn-prefix", warnings);
        checkInline(warnSuffix, "logging.warn-suffix", warnings);
        checkInline(errorPrefix, "logging.error-prefix", warnings);
        checkInline(errorSuffix, "logging.error-suffix", warnings);
        checkInline(debugPrefix, "logging.debug-prefix", warnings);
        checkInline(debugSuffix, "logging.debug-suffix", warnings);

        return new LoggingConfig(format, infoFormat, warnFormat, errorFormat, debugFormat, timeFormat,
                dateFormat, colorMode, consoleDebug, prefix, suffix, infoPrefix, infoSuffix,
                warnPrefix, warnSuffix, errorPrefix, errorSuffix, debugPrefix, debugSuffix,
                infoEnabled, warnEnabled, errorEnabled, debugEnabled, timestamp, thread, info, warn,
                error, debug, bracket, warnings.stream().distinct().toList());
    }

    public String format() {
        return format;
    }

    /** The format for one level, falling back to the shared format when it has none of its own. */
    public String formatFor(String level) {
        return switch (level) {
            case "INFO" -> infoFormat;
            case "WARN" -> warnFormat;
            case "ERROR" -> errorFormat;
            case "DEBUG" -> debugFormat;
            default -> format;
        };
    }

    public String prefixFor(String level) {
        return switch (level) {
            case "INFO" -> infoPrefix;
            case "WARN" -> warnPrefix;
            case "ERROR" -> errorPrefix;
            case "DEBUG" -> debugPrefix;
            default -> prefix;
        };
    }

    public String suffixFor(String level) {
        return switch (level) {
            case "INFO" -> infoSuffix;
            case "WARN" -> warnSuffix;
            case "ERROR" -> errorSuffix;
            case "DEBUG" -> debugSuffix;
            default -> suffix;
        };
    }

    /** Whether a level is shown on the console; the log file keeps every level regardless. */
    public boolean enabled(String level) {
        return switch (level) {
            case "INFO" -> infoEnabled;
            case "WARN" -> warnEnabled;
            case "ERROR" -> errorEnabled;
            case "DEBUG" -> debugEnabled;
            default -> true;
        };
    }

    public boolean consoleDebug() {
        return consoleDebug;
    }

    public DateTimeFormatter timeFormat() {
        return timeFormat;
    }

    public DateTimeFormatter dateFormat() {
        return dateFormat;
    }

    /** Resolves "auto" against the terminal when it's asked for, not when the file is read. */
    public boolean useColors() {
        return switch (colorMode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> AnsiSupport.supportsColors();
        };
    }

    public String timestampColor() {
        return timestampColor;
    }

    public String threadColor() {
        return threadColor;
    }

    public String infoColor() {
        return infoColor;
    }

    public String warnColor() {
        return warnColor;
    }

    public String errorColor() {
        return errorColor;
    }

    public String debugColor() {
        return debugColor;
    }

    public String bracketColor() {
        return bracketColor;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** The color of a level's label, or null when that level is left plain. */
    public String colorFor(String level) {
        return switch (level) {
            case "INFO" -> infoColor;
            case "WARN" -> warnColor;
            case "ERROR" -> errorColor;
            case "DEBUG" -> debugColor;
            default -> null;
        };
    }

    /**
     * The replacement for an inline placeholder inside <...>: a style, a color, or a value.
     * Unknown names return null so the caller can leave the placeholder as it was written.
     * Style and color placeholders disappear when colors are off; values always remain.
     */
    public static String inline(String name, Map<String, String> values, boolean colors) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        String style = styleCode(key);
        if (style != null) {
            return colors ? style : "";
        }
        String color = colorCode(name);
        if (color != null) {
            return colors ? color : "";
        }
        return values.get(key);
    }

    /** Whether a name written inside <...> is one the format language understands. */
    public static boolean isKnownInline(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        return styleCode(key) != null || colorCode(name) != null || INLINE_NAMES.contains(key);
    }

    /**
     * The ANSI sequence for a color name, "" for "null" or "none", or null when the name isn't
     * a color. Hex codes use the terminal's 24-bit color form when it has one.
     */
    public static String colorCode(String name) {
        String color = name.strip();
        if (color.equalsIgnoreCase("null") || color.equalsIgnoreCase("none")) {
            return "";
        }
        if (color.equalsIgnoreCase("reset")) {
            return RESET;
        }
        if (color.startsWith("#")) {
            return hex(color);
        }
        return COLORS.get(color.toLowerCase(Locale.ROOT));
    }

    private static String styleCode(String name) {
        return STYLES.get(name.strip().toLowerCase(Locale.ROOT));
    }

    private static String hex(String value) {
        if (value.length() != 7) {
            return null;
        }
        try {
            int red = Integer.parseInt(value.substring(1, 3), 16);
            int green = Integer.parseInt(value.substring(3, 5), 16);
            int blue = Integer.parseInt(value.substring(5, 7), 16);
            return "\u001B[38;2;" + red + ";" + green + ";" + blue + "m";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Warns about <...> placeholders that aren't colors, styles or values, once per setting. */
    private static void checkInline(String format, String key, List<String> warnings) {
        int from = 0;
        while (true) {
            int open = format.indexOf('<', from);
            if (open < 0) {
                return;
            }
            int close = format.indexOf('>', open + 1);
            if (close < 0) {
                return;
            }
            String name = format.substring(open + 1, close).strip();
            if (!isKnownInline(name)) {
                warnings.add(key + " has <" + name + ">, which isn't a known color, style or value."
                        + " Allowed names: " + COLOR_NAMES + ", " + STYLE_NAMES + ", " + VALUE_NAMES + ".");
            }
            from = close + 1;
        }
    }

    private static DateTimeFormatter formatter(Map<String, String> values, String key, String fallback,
                                               List<String> warnings) {
        String pattern = text(values, key, fallback, warnings);
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            warnings.add(key + " \"" + pattern + "\" is not a valid time pattern, so " + fallback
                    + " is used.");
            return DateTimeFormatter.ofPattern(fallback);
        }
    }

    private static String text(Map<String, String> values, String key, String fallback,
                               List<String> warnings) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isBlank()) {
            warnings.add(key + " in netherrack.yml is empty, so \"" + fallback + "\" is used.");
            return fallback;
        }
        return value;
    }

    /** A setting where an empty value is meaningful, such as a prefix or suffix. */
    private static String optional(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback,
                                List<String> warnings) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        warnings.add(key + " in netherrack.yml is \"" + value + "\", but has to be true or false, so "
                + fallback + " is used.");
        return fallback;
    }

    private static ColorMode colorMode(Map<String, String> values, List<String> warnings) {
        String value = values.get("logging.colors");
        if (value == null) {
            return ColorMode.AUTO;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "true", "always" -> ColorMode.ALWAYS;
            case "false", "never" -> ColorMode.NEVER;
            case "auto" -> ColorMode.AUTO;
            default -> {
                warnings.add("logging.colors in netherrack.yml is \"" + value
                        + "\", but has to be true, false or auto, so auto is used.");
                yield ColorMode.AUTO;
            }
        };
    }

    /** An explicit "null" means no color, while a missing key keeps the default. */
    private static String color(Map<String, String> values, String key, String fallback,
                                List<String> warnings) {
        String value = values.get(key);
        return value == null ? ansi(fallback, null, warnings, key) : ansi(value, fallback, warnings, key);
    }

    /**
     * Turns a color name into its ANSI sequence. Raw escape sequences still work for configs
     * written before names were supported. An unknown name keeps that label's default, and the
     * operator is warned about it.
     */
    private static String ansi(String value, String fallback, List<String> warnings, String key) {
        if (value == null) {
            return null;
        }
        // strip(), not trim(): trim() also removes the ESC that starts a raw ANSI sequence.
        String name = value.strip();
        if (name.isEmpty() || name.equalsIgnoreCase("null")) {
            return null;
        }
        if (name.startsWith("\u001B") || name.startsWith("\\u001B")) {
            return value;
        }
        String code = colorCode(name);
        if (code == null) {
            code = styleCode(name);
        }
        if (code != null) {
            return code;
        }
        warnings.add(key + " in netherrack.yml is \"" + value + "\", which isn't a known color name,"
                + " so " + fallback + " is used. Allowed names: " + COLOR_NAMES + ".");
        return ansi(fallback, null, warnings, key);
    }

    private static String named(String name) {
        return name == null ? null : COLORS.get(name.toLowerCase(Locale.ROOT));
    }
}
