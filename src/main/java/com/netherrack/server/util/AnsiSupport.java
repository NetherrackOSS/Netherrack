package com.netherrack.server.util;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * Linux/macOS terminals interpret ANSI color escape codes by default; Windows consoles
 * (cmd.exe, older PowerShell) don't, unless something explicitly turns that on first.
 * <p>
 * Building a JLine system terminal does exactly that as a side effect - on Windows, via
 * the JNI provider (jline-terminal-jni), it calls SetConsoleMode with
 * ENABLE_VIRTUAL_TERMINAL_PROCESSING for us. This has to run once, early, before any
 * colored output is printed. The same terminal is where the console's prompt reads
 * commands from later (see Console).
 */
public final class AnsiSupport {

    private static volatile Terminal terminal;

    private AnsiSupport() {
    }

    /** The terminal {@link #init} opened, or null if it couldn't. */
    public static Terminal getTerminal() {
        return terminal;
    }

    /**
     * Whether output is going to a terminal that can show ANSI colors. JLine builds a "dumb"
     * terminal when standard output is redirected, so a piped log gets no escape codes; before
     * init runs, the Java console check is the best signal available.
     */
    public static boolean supportsColors() {
        Terminal current = terminal;
        if (current != null) {
            String type = current.getType();
            return type != null && !type.equalsIgnoreCase("dumb");
        }
        return System.console() != null;
    }

    public static void init() {
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true) // fall back instead of throwing if a real terminal can't be created
                    .build();
        } catch (IOException e) {
            Logger.warn("Failed to initialize the terminal, ANSI colors may not work correctly: " + e.getMessage());
        }
    }
}
