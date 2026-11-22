package com.netherrack.server.player;

import com.netherrack.server.ServerConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Plain-Java checks for the whitelist file and the white-list setting. Everything is written
 * under target/, so the harness never touches a real server's configuration.
 */
public final class WhitelistTest {

    private static int failures;
    private static Path directory;

    public static void main(String[] args) throws Exception {
        directory = Path.of("target", "whitelist-test");
        Files.createDirectories(directory);

        missingFileCreatesEmptyWhitelist();
        offlineNamesAreCaseInsensitive();
        onlineIdentifiersSurviveNameChanges();
        removeMatchesAnyIdentifier();
        reloadPicksUpHandEdits();
        malformedFileLeavesEmptyWhitelist();
        toggleIsSavedWithoutLosingComments();

        System.out.println(failures == 0
                ? "All whitelist checks passed."
                : failures + " check(s) failed.");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void missingFileCreatesEmptyWhitelist() throws Exception {
        Path path = fresh("missing.json");
        Whitelist whitelist = Whitelist.load(path);
        check("missing file is created", Files.exists(path), true);
        check("missing file contains an empty list", Files.readString(path).trim(), "[]");
        check("missing file has no entries", whitelist.entries().isEmpty(), true);
    }

    private static void offlineNamesAreCaseInsensitive() throws Exception {
        Whitelist whitelist = Whitelist.load(fresh("offline.json"));
        check("offline name is added", whitelist.add("Friend", "", ""), true);
        check("name matches regardless of case", whitelist.contains("friend", "", ""), true);
        check("duplicate add is rejected", whitelist.add("FRIEND", "", ""), false);
        check("one offline entry is stored", whitelist.entries().size(), 1);
    }

    private static void onlineIdentifiersSurviveNameChanges() throws Exception {
        Whitelist whitelist = Whitelist.load(fresh("online.json"));
        check("online player is added", whitelist.add("SchleroMC", "25354123456789AB",
                "11111111-2222-3333-4444-AAAAAAAAAAAA"), true);
        check("XUID matches", whitelist.contains("SomeoneElse", "25354123456789AB", ""), true);
        check("XUID matching ignores case", whitelist.contains("", "25354123456789ab", ""), true);
        check("UUID matches", whitelist.contains("SomeoneElse", "", "11111111-2222-3333-4444-aaaaaaaaaaaa"), true);
        check("a new gamertag alone doesn't match", whitelist.contains("NewGamertag", "", ""), false);
    }

    private static void removeMatchesAnyIdentifier() throws Exception {
        Whitelist whitelist = Whitelist.load(fresh("remove.json"));
        whitelist.add("SchleroMC", "2535412345678901", "11111111-2222-3333-4444-555555555555");
        check("remove by XUID works", whitelist.remove("", "2535412345678901", ""), true);
        check("removed name no longer matches", whitelist.contains("SchleroMC", "", ""), false);
        check("removing again does nothing", whitelist.remove("", "2535412345678901", ""), false);
    }

    private static void reloadPicksUpHandEdits() throws Exception {
        Path path = fresh("reload.json");
        Whitelist whitelist = Whitelist.load(path);
        whitelist.add("Original", "", "");
        Files.writeString(path, "[{\"name\":\"Edited\"}]\n", StandardCharsets.UTF_8);

        whitelist.reload();
        check("hand-edited name matches", whitelist.contains("Edited", "", ""), true);
        check("old entry is gone", whitelist.contains("Original", "", ""), false);
    }

    private static void malformedFileLeavesEmptyWhitelist() throws Exception {
        Path path = fresh("broken.json");
        Files.writeString(path, "this is not json", StandardCharsets.UTF_8);
        Whitelist whitelist = Whitelist.load(path);
        check("malformed file leaves no entries", whitelist.entries().isEmpty(), true);
        check("malformed file doesn't match anyone", whitelist.contains("Anyone", "", ""), false);
    }

    private static void toggleIsSavedWithoutLosingComments() throws Exception {
        Path path = fresh("server.properties");
        Files.writeString(path, "white-list=false\n# a hand-written comment\nother=kept\n",
                StandardCharsets.ISO_8859_1);

        ServerConfig config = new ServerConfig(path);
        config.load("en-US");
        check("toggle starts off", config.getBoolean("white-list", true), false);

        config.setBoolean("white-list", true);
        check("toggle is on in memory", config.getBoolean("white-list", false), true);
        List<String> lines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
        check("toggle was rewritten", lines.get(0), "white-list=true");
        check("comment survived", lines.get(1), "# a hand-written comment");
        check("other setting survived", lines.get(2), "other=kept");

        ServerConfig reloaded = new ServerConfig(path);
        reloaded.load("en-US");
        check("toggle survives a reload", reloaded.getBoolean("white-list", false), true);
    }

    private static Path fresh(String name) throws Exception {
        Path path = directory.resolve(name);
        Files.deleteIfExists(path);
        return path;
    }

    private static void check(String name, Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            failures++;
            System.out.println("FAIL " + name + ": expected <" + expected + "> but got <" + actual + ">");
        } else {
            System.out.println("ok   " + name);
        }
    }
}
