package com.netherrack.server.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal console logger producing output in the form:
 * [15:47:23] [main] [INFO] Starting Netherrack server version 0.1.0
 * <p>
 * Brackets are left in the default terminal color; only the content inside
 * each segment (timestamp, thread name, level) is colored.
 * <p>
 * A message may hold Minecraft formatting codes ("§e" for yellow, see {@link ColorCodes}):
 * the console shows them as colors, and the log file leaves them out.
 * <p>
 * Every line is also written, uncolored, to a log file once {@link #openLogFile(Path)} has
 * been called. DEBUG lines go only to that file: with debug logging on, the network
 * libraries log every packet, which buries everything else on the console and scrolls past
 * what the console keeps.
 */
public final class Logger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String RESET = "\u001B[0m";
    private static final String TIMESTAMP_COLOR = "\u001B[36m";
    private static final String THREAD_COLOR = "\u001B[0;33m";
    private static final String INFO_COLOR = "\u001B[0;34m";
    private static final String WARN_COLOR = "\u001B[0;31m";
    private static final String ERROR_COLOR = "\u001B[1;31m";
    private static final String DEBUG_COLOR = null;

    private static PrintWriter logFile = null;

    private Logger() {
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
    }

    public static void info(String message) {
        log("INFO", message, INFO_COLOR);
    }

    public static void warn(String message) {
        log("WARN", message, WARN_COLOR);
    }

    public static void error(String message) {
        log("ERROR", message, ERROR_COLOR);
    }

    public static void debug(String message) {
        log("DEBUG", message, DEBUG_COLOR);
    }

    // Synchronized since Netty's threads log concurrently with the main thread, and both
    // the console prompt handling and the file writer need whole lines at a time.
    private static synchronized void log(String level, String message, String levelColor) {
        String time = LocalTime.now().format(TIME_FORMAT);
        String thread = Thread.currentThread().getName();
        // Escape sequences in the text itself are never the server's own, so they're dropped:
        // text that came from a player must not be able to color or rewrite the console. The
        // formatting codes (like "§e") are what's meant to be colored.
        message = ColorCodes.removeControlCharacters(message);

        if (logFile != null) {
            logFile.println("[" + time + "] [" + thread + "] [" + level + "] " + ColorCodes.strip(message));
            // Flushed per line so the file is complete even if the server is killed or crashes.
            logFile.flush();
        }
        if (level.equals("DEBUG")) {
            return;
        }

        StringBuilder line = new StringBuilder();
        line.append('[').append(TIMESTAMP_COLOR).append(time).append(RESET).append(']');
        line.append(" [").append(THREAD_COLOR).append(thread).append(RESET).append(']');
        line.append(" [");
        if (levelColor != null) {
            line.append(levelColor).append(level).append(RESET);
        } else {
            line.append(level);
        }
        line.append(']');
        line.append(' ').append(ColorCodes.toAnsi(message));
        // Above the console's prompt, which is drawn again below it with whatever's been typed.
        Console.print(line.toString());
    }
}
