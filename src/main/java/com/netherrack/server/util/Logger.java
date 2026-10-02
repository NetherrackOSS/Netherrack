package com.netherrack.server.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal console logger producing output in the form:
 * [15:47:23] [main] [INFO] Starting Netherrack server version 0.1.0
 * <p>
 * Brackets are left in the default terminal color; only the content inside
 * each segment (timestamp, thread name, level) is colored.
 */
public final class Logger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String RESET = "\u001B[0m";
    private static final String TIMESTAMP_COLOR = "\u001B[36m";
    private static final String THREAD_COLOR = "\u001B[0;33m";
    private static final String INFO_COLOR = "\u001B[0;34m";
    private static final String WARN_COLOR = "\u001B[1;33m";
    private static final String ERROR_COLOR = "\u001B[1;31m";
    private static final String DEBUG_COLOR = null;

    private static volatile Thread consoleThread = null;
    private static volatile boolean promptActive = false;

    private Logger() {
    }

    /**
     * Marks the given thread as the one that owns the console prompt (i.e. the thread
     * blocked in readLine()). Any log call from a different thread will redraw the
     * prompt after itself, since it would otherwise print in the middle of it.
     */
    public static void bindConsoleThread(Thread thread) {
        consoleThread = thread;
    }

    /**
     * Called right after the prompt ("> ") has been printed and before the console
     * thread blocks on readLine(), so other threads know a prompt is currently showing.
     */
    public static void notePromptShown() {
        promptActive = true;
    }

    /**
     * Called right after the console thread receives a line, so the prompt is no
     * longer considered "on screen" until it's printed again.
     */
    public static void notePromptConsumed() {
        promptActive = false;
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

    private static void log(String level, String message, String levelColor) {
        String time = LocalTime.now().format(TIME_FORMAT);
        String thread = Thread.currentThread().getName();

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
        line.append(' ').append(message);

        boolean redrawPrompt = promptActive && Thread.currentThread() != consoleThread;
        if (redrawPrompt) {
            // Erase the currently-displayed "> " prompt (carriage return + clear line)
            // so it doesn't get absorbed as a prefix to this log line.
            System.out.print("\r\u001B[K");
        }
        System.out.println(line);
        if (redrawPrompt) {
            System.out.print("> ");
            System.out.flush();
        }
    }
}
