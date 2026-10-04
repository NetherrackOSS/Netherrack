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
 * ENABLE_VIRTUAL_TERMINAL_PROCESSING for us. We don't actually need JLine's Terminal
 * object for anything afterwards (System.out keeps working as normal, now with ANSI
 * codes rendering correctly) - we just need this to run once, early, before any colored
 * output is printed.
 */
public final class AnsiSupport {

    private static volatile Terminal terminal;

    private AnsiSupport() {
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
