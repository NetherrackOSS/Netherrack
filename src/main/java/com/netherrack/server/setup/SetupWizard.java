package com.netherrack.server.setup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Runs once, before anything else, whenever server.properties doesn't exist yet - i.e.
 * the server has never been set up before. Picks a language, then requires accepting the
 * GPLv3 license to continue. This talks directly to the console (no Logger timestamps/
 * colors) since it's meant to read as a standalone installer-style flow, not a log.
 */
public class SetupWizard {

    private static final String BOX_LINE = "═".repeat(63);

    /**
     * @return the chosen language code, or null if the license was declined (caller should
     *         log that through the normal Logger and exit - this class only draws the wizard).
     */
    public String run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        printHeader("Netherrack Setup Wizard - Language Selection");

        System.out.println("Welcome! Please select a language first.");
        System.out.println();
        System.out.println("[*] Enter a language code from the list below (press Enter for default).");
        for (Map.Entry<String, String> entry : Lang.AVAILABLE.entrySet()) {
            System.out.println("  [" + entry.getKey() + "] " + entry.getValue());
        }
        System.out.println();

        String code = prompt(reader, "» Language [" + Lang.DEFAULT_CODE + "]: ");
        if (code.isEmpty() || !Lang.AVAILABLE.containsKey(code)) {
            code = Lang.DEFAULT_CODE;
        }
        Lang lang = new Lang(code);
        System.out.println("\u2713 " + lang.get("wizard.language.selected", Lang.AVAILABLE.get(lang.getCode())));

        printHeader(lang.get("license.title"));
        System.out.println(lang.get("license.subtitle"));
        System.out.println();
        System.out.println(lang.get("license.body"));
        System.out.println();
        System.out.println("[!] " + lang.get("license.must_accept"));
        System.out.println();

        String answer = prompt(reader, "» " + lang.get("license.accept_prompt") + " ");
        boolean accepted = answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")
                || answer.equalsIgnoreCase("s") || answer.equalsIgnoreCase("sim");

        if (accepted) {
            System.out.println("\u2713 " + lang.get("license.accepted"));
            System.out.println();
            return lang.getCode();
        } else {
            System.out.println("[x] " + lang.get("license.declined"));
            System.out.println();
            return null;
        }
    }

    private void printHeader(String title) {
        System.out.println();
        System.out.println(BOX_LINE);
        int padding = Math.max(0, (BOX_LINE.length() - title.length()) / 2);
        System.out.println(" ".repeat(padding) + title);
        System.out.println(BOX_LINE);
        System.out.println();
    }

    private String prompt(BufferedReader reader, String text) {
        System.out.print(text);
        System.out.flush();
        try {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
