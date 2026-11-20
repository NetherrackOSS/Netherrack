package com.netherrack.server.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal console logger, whose line formats and label colors come from netherrack.yml. The
 * defaults produce output in the form:
 * <p>
 * [15:47:23] [main] [INFO] Starting Netherrack server version 0.4.0-beta
 * <p>
 * The sections are read before anything logs ({@link LoggingConfig}), so even the first startup
 * line uses the operator's settings. Brackets are left in the default terminal color unless
 * labels.bracket-color is set; only the content inside each segment is colored. Each level has
 * its own format, falling back to the shared one, and both {value} and &lt;color&gt; placeholders
 * are resolved in a single pass so a message can never be scanned for placeholders again.
 * <p>
 * A message may hold Minecraft formatting codes ("§e" for yellow, see {@link ColorCodes}):
 * the console shows them as colors, and the log file leaves them out. Turning colors off
 * drops them from the console too.
 * <p>
 * Every line is also written, uncolored, to a log file once {@link #openLogFile(Path)} has
 * been called. DEBUG lines go only to that file unless logging.console-debug is on: with debug
 * logging enabled, the network libraries log every packet, which buries everything else on the
 * console and scrolls past what the console keeps.
 */
public final class Logger {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String RESET = "\u001B[0m";

    private static LoggingConfig config = LoggingConfig.defaults();
    private static PrintWriter logFile = null;
    // configure() runs before openLogFile() at startup, so config warnings are held until the
    // file exists; otherwise they'd appear on the console but never in logs/latest.log.
    private static final List<String> pendingWarnings = new ArrayList<>();

    private Logger() {
    }

    /**
     * Applies the settings read from netherrack.yml. Warnings are printed with the settings just
     * applied, and replayed into the log file once it's open. Safe to call again on reload.
     */
    public static synchronized void configure(LoggingConfig settings) {
        config = settings;
        for (String warning : settings.warnings()) {
            log("WARN", warning);
            if (logFile == null) {
                pendingWarnings.add(warning);
            }
        }
    }

    /**
     * Starts copying every log line into the given file, replacing whatever a previous run
     * left there. Failing to open it isn't fatal - the console still works.
     */
    public static synchronized void openLogFile(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            logFile = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            error("Could not open log file " + path + ": " + e.getMessage());
        }
        for (String warning : pendingWarnings) {
            writeFile("WARN", warning, LocalTime.now(), Thread.currentThread().getName());
        }
        pendingWarnings.clear();
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }

    public static void debug(String message) {
        log("DEBUG", message);
    }

    // Synchronized since Netty's threads log concurrently with the main thread, and both
    // the console prompt handling and the file writer need whole lines at a time.
    private static synchronized void log(String level, String message) {
        LocalTime now = LocalTime.now();
        String thread = Thread.currentThread().getName();
        // Escape sequences in the text itself are never the server's own, so they're dropped:
        // text that came from a player must not be able to color or rewrite the console. The
        // formatting codes (like "§e") are what's meant to be colored.
        message = ColorCodes.removeControlCharacters(message);
        writeFile(level, message, now, thread);

        if (!config.enabled(level)) {
            return;
        }
        if (level.equals("DEBUG") && !config.consoleDebug()) {
            return;
        }

        boolean colors = config.useColors();
        String time = now.format(config.timeFormat());
        String millis = String.format(Locale.ROOT, "%03d", now.getNano() / 1_000_000);
        Map<String, String> values = Map.of(
                "time", paint(time, colors, config.timestampColor()),
                "date", LocalDate.now().format(config.dateFormat()),
                "millis", millis,
                "thread", paint(thread, colors, config.threadColor()),
                "level", paint(level, colors, config.colorFor(level)),
                "message", colors ? ColorCodes.toAnsi(message) : ColorCodes.strip(message),
                "prefix", config.prefixFor(level),
                "suffix", config.suffixFor(level));

        String line = render(config.formatFor(level), values, colors, colors ? config.bracketColor() : null);
        // A trailing <color> would otherwise carry on into the console prompt and the next line.
        if (colors && line.contains("\u001B") && !line.endsWith(RESET)) {
            line += RESET;
        }
        // Above the console's prompt, which is drawn again below it with whatever's been typed.
        Console.print(line);
    }

    /** Writes the plain-text copy. The file's shape never changes with the console format. */
    private static void writeFile(String level, String message, LocalTime now, String thread) {
        PrintWriter file = logFile;
        if (file == null) {
            return;
        }
        file.println("[" + now.format(FILE_TIME_FORMAT) + "] [" + thread + "] [" + level + "] "
                + ColorCodes.strip(message));
        // Flushed per line so the file is complete even if the server is killed or crashes.
        file.flush();
    }

    /**
     * Fills {value} and <color> placeholders. Both kinds are handled in one pass: the text of
     * {message} is appended as it stands, so braces a player wrote can't be read as more
     * placeholders. Bracket coloring only touches a bracket at the edge of a placeholder, not
     * every square bracket in the pattern.
     */
    static String render(String pattern, Map<String, String> values, boolean colors,
                         String bracketColor) {
        StringBuilder out = new StringBuilder();
        int from = 0;
        boolean afterPlaceholder = false;
        while (from < pattern.length()) {
            char c = pattern.charAt(from);
            if (c == '{' || c == '<') {
                char end = c == '{' ? '}' : '>';
                int close = pattern.indexOf(end, from + 1);
                if (close >= 0) {
                    String name = pattern.substring(from + 1, close);
                    String replacement = c == '{'
                            ? values.get(name.strip().toLowerCase(Locale.ROOT))
                            : LoggingConfig.inline(name, values, colors);
                    if (replacement != null) {
                        out.append(replacement);
                        from = close + 1;
                        afterPlaceholder = true;
                        continue;
                    }
                }
            }
            if (bracketColor != null && colors && c == '[' && afterBracketedPlaceholder(pattern, from)) {
                out.append(bracketColor).append(c).append(RESET);
            } else if (bracketColor != null && colors && c == ']' && afterPlaceholder) {
                out.append(bracketColor).append(c).append(RESET);
            } else {
                out.append(c);
            }
            afterPlaceholder = false;
            from++;
        }
        return out.toString();
    }

    private static boolean afterBracketedPlaceholder(String pattern, int from) {
        return from + 1 < pattern.length()
                && (pattern.charAt(from + 1) == '{' || pattern.charAt(from + 1) == '<');
    }

    private static String paint(String value, boolean colors, String color) {
        if (!colors || color == null || color.isEmpty()) {
            return value;
        }
        return color + value + RESET;
    }
}
