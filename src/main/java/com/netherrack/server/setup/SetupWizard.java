package com.netherrack.server.setup;

import com.netherrack.server.ServerConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runs once, before anything else, whenever server.properties doesn't exist yet - i.e.
 * the server has never been set up before. Picks a language, requires accepting the GPLv3
 * license, then optionally collects the core server settings. This talks directly to the
 * console (no Logger timestamps/colors) since it's meant to read as a standalone
 * installer-style flow, not a log.
 */
public class SetupWizard {

    private static final String BOX_LINE = "═".repeat(63);
    private static final String RULE_LINE = "─".repeat(BOX_LINE.length());
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("[0-9A-Fa-f:.]{1,45}");
    private static final List<String> EXTERNAL_IP_ENDPOINTS = List.of(
            "https://api.ipify.org", "https://checkip.amazonaws.com");

    /** The language to save and the server.properties values chosen by the wizard. */
    public record Result(String language, Map<String, String> settings) {
    }

    /**
     * @return the setup result, or null if the license was declined (caller should log that
     *         through the normal Logger and exit - this class only draws the wizard).
     */
    public Result run() {
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

        if (!accepted) {
            System.out.println("[x] " + lang.get("license.declined"));
            System.out.println();
            return null;
        }

        System.out.println("\u2713 " + lang.get("license.accepted"));
        System.out.println();

        Map<String, String> settings = ServerConfig.defaultValues(lang.getCode());
        // Interactive shells have a Console; piped smoke tests and service managers don't.
        // Skipping there avoids swallowing console commands as wizard answers.
        if (System.console() != null) {
            configure(reader, lang, settings);
        }
        return new Result(lang.getCode(), settings);
    }

    /** Asks the optional setup questions and writes the answers into {@code settings}. */
    private void configure(BufferedReader reader, Lang lang, Map<String, String> settings) {
        printHeader(lang.get("wizard.setup.title"));
        System.out.println(lang.get("wizard.setup.intro"));
        System.out.println();
        System.out.println("[*] " + lang.get("wizard.setup.note"));
        System.out.println();

        String skip = prompt(reader, "» " + lang.get("wizard.setup.skip_prompt") + " ");
        if (!isNo(skip)) {
            System.out.println("\u2713 " + lang.get("wizard.setup.skipped"));
            finish(reader, lang, settings);
            return;
        }

        System.out.println("\u2713 " + lang.get("wizard.setup.proceeding"));
        printHeader(lang.get("wizard.server.title"));

        promptServerName(reader, lang, settings);
        separator();
        promptPort(reader, lang, settings);
        separator();
        promptGamemode(reader, lang, settings);
        separator();
        promptMaxPlayers(reader, lang, settings);

        finish(reader, lang, settings);
    }

    private void promptServerName(BufferedReader reader, Lang lang, Map<String, String> settings) {
        String current = settings.get("server-name");
        while (true) {
            String answer = prompt(reader, "» " + lang.get("wizard.server.name.prompt", current) + ": ");
            if (answer.isEmpty()) {
                break;
            }
            if (answer.contains(";")) {
                System.out.println("[x] " + lang.get("wizard.server.name.invalid"));
                continue;
            }
            current = answer;
            break;
        }
        settings.put("server-name", current);
        System.out.println("\u2713 " + lang.get("wizard.server.name.selected", current));
    }

    private void promptPort(BufferedReader reader, Lang lang, Map<String, String> settings) {
        int current = Integer.parseInt(settings.get("server-port"));
        while (true) {
            String answer = prompt(reader, "» " + lang.get("wizard.server.port.prompt", current) + ": ");
            if (answer.isEmpty()) {
                break;
            }
            try {
                int port = Integer.parseInt(answer);
                if (port < 1 || port > 65535) {
                    System.out.println("[x] " + lang.get("wizard.server.port.invalid"));
                    continue;
                }
                current = port;
                break;
            } catch (NumberFormatException e) {
                System.out.println("[x] " + lang.get("wizard.server.port.invalid"));
            }
        }
        settings.put("server-port", String.valueOf(current));
        System.out.println("\u2713 " + lang.get("wizard.server.port.selected", current));
    }

    private void promptGamemode(BufferedReader reader, Lang lang, Map<String, String> settings) {
        Map<String, String> modes = gamemodes();
        String current = settings.get("gamemode");

        System.out.println("[*] " + lang.get("wizard.server.gamemode.available"));
        for (Map.Entry<String, String> mode : modes.entrySet()) {
            System.out.println("» [" + mode.getKey() + "] " + lang.get("wizard.gamemode." + mode.getValue()));
        }
        System.out.println();

        while (true) {
            String answer = prompt(reader,
                    "» " + lang.get("wizard.server.gamemode.prompt", lang.get("wizard.gamemode." + current))
                            + ": ").toLowerCase(Locale.ROOT);
            if (answer.isEmpty()) {
                break;
            }
            String selected = gamemodeFromAnswer(answer);
            if (selected == null) {
                System.out.println("[x] " + lang.get("wizard.server.gamemode.invalid"));
                continue;
            }
            current = selected;
            break;
        }
        settings.put("gamemode", current);
        System.out.println("\u2713 " + lang.get("wizard.server.gamemode.selected",
                lang.get("wizard.gamemode." + current)));
    }

    private void promptMaxPlayers(BufferedReader reader, Lang lang, Map<String, String> settings) {
        int current = Integer.parseInt(settings.get("max-players"));
        while (true) {
            String answer = prompt(reader, "» " + lang.get("wizard.server.players.prompt", current) + ": ");
            if (answer.isEmpty()) {
                break;
            }
            try {
                int players = Integer.parseInt(answer);
                if (players < 1) {
                    System.out.println("[x] " + lang.get("wizard.server.players.invalid"));
                    continue;
                }
                current = players;
                break;
            } catch (NumberFormatException e) {
                System.out.println("[x] " + lang.get("wizard.server.players.invalid"));
            }
        }
        settings.put("max-players", String.valueOf(current));
        System.out.println("\u2713 " + lang.get("wizard.server.players.selected", current));
    }

    /** Shows the configured addresses and waits for the operator before startup continues. */
    private void finish(BufferedReader reader, Lang lang, Map<String, String> settings) {
        printHeader(lang.get("wizard.complete.title"));
        System.out.println("\u2713 " + lang.get("wizard.complete.ready"));
        System.out.println();

        String port = settings.get("server-port");
        System.out.println("» " + lang.get("wizard.address.local", localAddress(settings), port));
        System.out.println("[*] " + lang.get("wizard.address.detecting"));
        String external = externalAddress();
        String externalDisplay = external == null
                ? lang.get("wizard.address.external.unavailable", port)
                : external + ":" + port;
        System.out.println("» " + lang.get("wizard.address.external", externalDisplay));
        System.out.println();

        System.out.println(BOX_LINE);
        System.out.println();
        prompt(reader, lang.get("wizard.finish.press_enter") + " ");
        System.out.println();
    }

    /** Returns the bound address when it is specific, otherwise a usable local address. */
    private String localAddress(Map<String, String> settings) {
        String bound = settings.getOrDefault("server-ip", "0.0.0.0");
        if (!bound.isBlank() && !bound.equals("0.0.0.0") && !bound.equals("::")) {
            return bound;
        }
        try {
            for (NetworkInterface networkInterface
                    : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()
                        || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (SocketException | UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * Looks up the public address from two well-known plain-text endpoints. A failed or
     * unavailable lookup only leaves the address out of the summary; setup still continues.
     */
    private String externalAddress() {
        for (String endpoint : EXTERNAL_IP_ENDPOINTS) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                String body = client.send(request, HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8)).body().trim();
                if (ADDRESS_PATTERN.matcher(body).matches()
                        && (body.contains(".") || body.contains(":"))) {
                    return body;
                }
            } catch (Exception ignored) {
                // The wizard must never fail just because an IP lookup service is unavailable.
            }
        }
        return null;
    }

    private Map<String, String> gamemodes() {
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("0", "survival");
        modes.put("1", "creative");
        modes.put("2", "adventure");
        modes.put("3", "spectator");
        return modes;
    }

    private String gamemodeFromAnswer(String answer) {
        return switch (answer) {
            case "0", "survival", "s" -> "survival";
            case "1", "creative", "c" -> "creative";
            case "2", "adventure", "a" -> "adventure";
            case "3", "6", "spectator", "spec" -> "spectator";
            default -> null;
        };
    }

    private boolean isNo(String answer) {
        return answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")
                || answer.equalsIgnoreCase("não") || answer.equalsIgnoreCase("nao");
    }

    private void printHeader(String title) {
        System.out.println();
        System.out.println(BOX_LINE);
        int padding = Math.max(0, (BOX_LINE.length() - title.length()) / 2);
        System.out.println(" ".repeat(padding) + title);
        System.out.println(BOX_LINE);
        System.out.println();
    }

    private void separator() {
        System.out.println();
        System.out.println(RULE_LINE);
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
