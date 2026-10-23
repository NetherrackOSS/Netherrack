package com.netherrack.server.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft's formatting codes - a "§" followed by a character, such as "§e" for yellow -
 * which is how chat text and vanilla's own messages are colored. Players' clients show them
 * as they are; the console needs them turned into ANSI escape sequences, and the log file
 * needs them gone.
 * <p>
 * Only the codes every terminal can show are kept: the sixteen colors, bold, italic and
 * reset. Any other code (obfuscated text, Bedrock's material colors) is dropped rather than
 * left in the text as a stray "§k".
 */
public final class ColorCodes {

    /** The character that starts a formatting code. */
    public static final char PREFIX = '\u00a7';

    private static final String RESET = "\u001B[0m";

    private static final Map<Character, String> COLORS = new HashMap<>();
    private static final Map<Character, String> STYLES = new HashMap<>();

    static {
        COLORS.put('0', "\u001B[0;30m"); // black
        COLORS.put('1', "\u001B[0;34m"); // dark blue
        COLORS.put('2', "\u001B[0;32m"); // dark green
        COLORS.put('3', "\u001B[0;36m"); // dark aqua
        COLORS.put('4', "\u001B[0;31m"); // dark red
        COLORS.put('5', "\u001B[0;35m"); // dark purple
        COLORS.put('6', "\u001B[0;33m"); // gold
        COLORS.put('7', "\u001B[0;37m"); // gray
        COLORS.put('8', "\u001B[0;90m"); // dark gray
        COLORS.put('9', "\u001B[1;34m"); // blue
        COLORS.put('a', "\u001B[1;32m"); // green
        COLORS.put('b', "\u001B[1;36m"); // aqua
        COLORS.put('c', "\u001B[1;31m"); // red
        COLORS.put('d', "\u001B[1;35m"); // light purple
        COLORS.put('e', "\u001B[1;33m"); // yellow
        COLORS.put('f', "\u001B[1;37m"); // white

        STYLES.put('l', "\u001B[1m"); // bold
        STYLES.put('o', "\u001B[3m"); // italic
    }

    private ColorCodes() {
    }

    /**
     * The text with its formatting codes as ANSI escape sequences, ending in a reset so a
     * color left open doesn't carry on into whatever is printed next. A color code clears
     * what came before it, as in the game.
     */
    public static String toAnsi(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        boolean styled = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != PREFIX) {
                out.append(c);
                continue;
            }
            if (i + 1 >= text.length()) {
                break; // a code with nothing after it
            }
            char code = Character.toLowerCase(text.charAt(++i));
            if (COLORS.containsKey(code)) {
                out.append(RESET).append(COLORS.get(code));
                styled = true;
            } else if (STYLES.containsKey(code)) {
                out.append(STYLES.get(code));
                styled = true;
            } else if (code == 'r') {
                out.append(RESET);
                styled = false;
            }
            // Any other code is dropped, along with its character.
        }
        if (styled) {
            out.append(RESET);
        }
        return out.toString();
    }

    /** The text without its formatting codes - for the log file, which holds plain text. */
    public static String strip(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == PREFIX) {
                i++; // the code's character goes too
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * The text without control characters, keeping line breaks and tabs, which a log line may
     * legitimately have (a stack trace). Mainly this removes ESC: formatting codes are turned
     * into escape sequences on the way to the console, and text a player wrote must never be
     * able to bring its own.
     */
    public static String removeControlCharacters(String text) {
        return strip(text, true);
    }

    /**
     * The text on one line, without any control characters, for what a player typed (a chat
     * message, a command). Otherwise a line break in it would pass for a line of the log.
     */
    public static String singleLine(String text) {
        return strip(text, false);
    }

    private static String strip(String text, boolean keepWhitespace) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean control = c < 0x20 || (c >= 0x7F && c <= 0x9F);
            boolean whitespace = c == '\n' || c == '\r' || c == '\t';
            if (!control || (keepWhitespace && whitespace)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
