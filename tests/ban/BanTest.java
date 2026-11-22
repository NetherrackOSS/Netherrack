package com.netherrack.server.player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Plain-Java checks for banned-players.json. Everything is written under target/, so the
 * harness never touches a real server's ban list.
 */
public final class BanTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss Z", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static int failures;
    private static Path directory;

    public static void main(String[] args) throws Exception {
        directory = Path.of("target", "ban-test");
        Files.createDirectories(directory);

        missingFileCreatesEmptyList();
        offlineNamesAreSavedLikeNukkit();
        onlineIdentifiersSurviveNameChanges();
        reasonAndSourceCanBeUpdated();
        removeMatchesAnyIdentifier();
        handEditsAreReloaded();
        expiredEntriesAreDropped();
        malformedFileLeavesNoBans();

        System.out.println(failures == 0
                ? "All ban checks passed."
                : failures + " check(s) failed.");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void missingFileCreatesEmptyList() throws Exception {
        Path path = fresh("missing.json");
        BanList bans = BanList.load(path);
        check("missing file is created", Files.exists(path), true);
        check("missing file contains an empty list", Files.readString(path).trim(), "[]");
        check("missing file has no bans", bans.entries().isEmpty(), true);
    }

    private static void offlineNamesAreSavedLikeNukkit() throws Exception {
        Path path = fresh("offline.json");
        BanList bans = BanList.load(path);
        BanEntry entry = bans.add("Griefer", "", "", "griefing", "Console");

        check("offline name is banned", bans.isBanned("GRIEFER", "", ""), true);
        check("name is lowercased like Nukkit", entry.name(), "griefer");
        check("reason is kept", entry.reason(), "griefing");
        check("source is kept", entry.source(), "Console");
        check("ban never expires", entry.expirationDate(), null);

        String json = Files.readString(path);
        for (String field : List.of("name", "creationDate", "source", "expireDate", "reason")) {
            check("saved JSON has " + field, json.contains("\"" + field + "\""), true);
        }
        check("saved JSON uses Nukkit's Forever", json.contains("\"expireDate\":\"Forever\""), true);
    }

    private static void onlineIdentifiersSurviveNameChanges() throws Exception {
        BanList bans = BanList.load(fresh("online.json"));
        bans.add("SchleroMC", "25354123456789AB", "11111111-2222-3333-4444-AAAAAAAAAAAA",
                "raiding", "SchleroMC");

        check("XUID matches", bans.isBanned("SomeoneElse", "25354123456789AB", ""), true);
        check("XUID matching ignores case", bans.isBanned("", "25354123456789ab", ""), true);
        check("UUID matches", bans.isBanned("SomeoneElse", "", "11111111-2222-3333-4444-aaaaaaaaaaaa"),
                true);
        check("old name still matches", bans.isBanned("schleromc", "", ""), true);
        check("unrelated name is free", bans.isBanned("Innocent", "", ""), false);
    }

    private static void reasonAndSourceCanBeUpdated() throws Exception {
        BanList bans = BanList.load(fresh("update.json"));
        bans.add("Trouble", "123", "", "first warning", "Console");
        BanEntry updated = bans.add("TROUBLE", "123", "", "second warning", "Moderator");

        check("one entry is kept", bans.entries().size(), 1);
        check("reason is replaced", updated.reason(), "second warning");
        check("source is replaced", updated.source(), "Moderator");
    }

    private static void removeMatchesAnyIdentifier() throws Exception {
        BanList bans = BanList.load(fresh("remove.json"));
        bans.add("SchleroMC", "2535412345678901", "11111111-2222-3333-4444-555555555555",
                "griefing", "Console");

        check("remove by UUID works", bans.remove("11111111-2222-3333-4444-555555555555"), true);
        check("removed name is free", bans.isBanned("SchleroMC", "", ""), false);
        check("removed XUID is free", bans.isBanned("", "2535412345678901", ""), false);
        check("removing again does nothing", bans.remove("SchleroMC"), false);
    }

    private static void handEditsAreReloaded() throws Exception {
        Path path = fresh("reload.json");
        BanList bans = BanList.load(path);
        bans.add("Original", "", "", "griefing", "Console");
        Files.writeString(path, "[{\"name\":\"Edited\",\"reason\":\"hand added\"}]\n",
                StandardCharsets.UTF_8);

        bans.reload();
        check("hand-edited name is banned", bans.isBanned("edited", "", ""), true);
        check("hand-edited reason is kept", bans.matching("Edited", "", "").reason(), "hand added");
        check("old entry is gone", bans.isBanned("Original", "", ""), false);
    }

    private static void expiredEntriesAreDropped() throws Exception {
        Path path = fresh("expired.json");
        String past = DATE.format(Instant.now().minusSeconds(60));
        String future = DATE.format(Instant.now().plusSeconds(3600));
        Files.writeString(path, "[\n"
                + "  {\"name\":\"OldBan\",\"expireDate\":\"" + past + "\",\"reason\":\"timed\"},\n"
                + "  {\"name\":\"StillBanned\",\"expireDate\":\"" + future + "\",\"reason\":\"later\"}\n"
                + "]\n", StandardCharsets.UTF_8);

        BanList bans = BanList.load(path);
        check("expired ban is ignored", bans.isBanned("OldBan", "", ""), false);
        check("future ban still applies", bans.isBanned("StillBanned", "", ""), true);
        check("expired ban is removed from the file", Files.readString(path).contains("OldBan"), false);
        check("future ban stays in the file", Files.readString(path).contains("stillbanned"), true);
    }

    private static void malformedFileLeavesNoBans() throws Exception {
        Path path = fresh("broken.json");
        Files.writeString(path, "this is not json", StandardCharsets.UTF_8);
        BanList bans = BanList.load(path);
        check("malformed file has no bans", bans.entries().isEmpty(), true);
        check("malformed file doesn't ban anyone", bans.isBanned("Anyone", "", ""), false);
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
