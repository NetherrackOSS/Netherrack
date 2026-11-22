package com.netherrack.server.player;

import com.netherrack.server.util.Logger;
import org.jose4j.json.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The player bans in banned-players.json, named and saved the way Cloudburst Nukkit does: a
 * pretty-printed list of entries with a creation date, source, expiry and reason. An entry can
 * match by gamertag, XUID or UUID, whichever the joining player has. Expired entries are dropped
 * whenever the list is read, so a temporary ban in a hand-edited file lifts itself.
 */
public class BanList {

    public static final Path FILE = Path.of("banned-players.json");

    private final Path path;
    private final List<BanEntry> entries;

    private BanList(Path path, List<BanEntry> entries) {
        this.path = path;
        this.entries = entries;
    }

    /**
     * Reads the ban list, creating an empty one if the file is missing. A broken file leaves no
     * one banned rather than stopping startup, so an operator can always fix it by hand.
     */
    public static BanList load(Path path) {
        List<BanEntry> entries = new ArrayList<>();
        int unusable = 0;
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) {
                // jose4j's parser only takes an object at the top level, so the list is wrapped.
                Object list = JsonUtil.parseJson("{\"entries\":" + text + "}").get("entries");
                if (!(list instanceof List<?> items)) {
                    throw new IOException("expected a list of entries");
                }
                for (Object item : items) {
                    if (item instanceof Map<?, ?> map) {
                        BanEntry entry = BanEntry.fromMap(map);
                        if (entry != null) {
                            entries.add(entry);
                        } else {
                            unusable++;
                        }
                    }
                }
            }
        } catch (NoSuchFileException e) {
            writeQuietly(path, "[]\n");
        } catch (Exception e) {
            Logger.error("Could not read " + path + ", so no players are banned: " + e.getMessage());
        }
        if (unusable > 0) {
            Logger.warn(unusable + " entries in " + path + " have no name, xuid or uuid, so they "
                    + "do nothing.");
        }
        return new BanList(path, entries);
    }

    /** Reads the file again, so hand-edited bans take effect after /banlist reload. */
    public synchronized void reload() {
        List<BanEntry> reloaded = load(path).entries;
        entries.clear();
        entries.addAll(reloaded);
    }

    /** The ban matching any of these identifiers, or null when the player isn't banned. */
    public synchronized BanEntry matching(String name, String xuid, String uuid) {
        removeExpired();
        String wantedName = BanEntry.normalize(name);
        String wantedXuid = BanEntry.text(xuid);
        String wantedUuid = BanEntry.text(uuid);
        for (BanEntry entry : entries) {
            if (!wantedName.isEmpty() && entry.name().equals(wantedName)) {
                return entry;
            }
            if (!wantedXuid.isEmpty() && entry.xuid().equalsIgnoreCase(wantedXuid)) {
                return entry;
            }
            if (!wantedUuid.isEmpty() && entry.uuid().equalsIgnoreCase(wantedUuid)) {
                return entry;
            }
        }
        return null;
    }

    /** Convenience check for callers that only need to know whether to turn a player away. */
    public synchronized boolean isBanned(String name, String xuid, String uuid) {
        return matching(name, xuid, uuid) != null;
    }

    /**
     * Bans a player, replacing an existing matching entry like Nukkit's BanList.add does. The
     * online player's identifiers are saved when they're available, so later names still match.
     */
    public synchronized BanEntry add(String name, String xuid, String uuid, String reason,
                                     String source) {
        String entryName = BanEntry.normalize(name);
        String entryXuid = BanEntry.text(xuid);
        String entryUuid = BanEntry.text(uuid);
        if (entryName.isEmpty() && entryXuid.isEmpty() && entryUuid.isEmpty()) {
            return null;
        }
        BanEntry existing = matching(entryName, entryXuid, entryUuid);
        if (existing != null) {
            entries.remove(existing);
        }
        BanEntry entry = BanEntry.of(entryName, entryXuid, entryUuid, reason, source);
        entries.add(entry);
        save();
        return entry;
    }

    /** Removes the ban matching this name, XUID or UUID, returning whether one was removed. */
    public synchronized boolean remove(String target) {
        BanEntry entry = matching(target, target, target);
        if (entry == null) {
            return false;
        }
        entries.remove(entry);
        save();
        return true;
    }

    /** A copy of the live bans, for /banlist without exposing the list itself. */
    public synchronized List<BanEntry> entries() {
        removeExpired();
        return List.copyOf(entries);
    }

    private void removeExpired() {
        if (entries.removeIf(BanEntry::hasExpired)) {
            save();
        }
    }

    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            json.append("  ").append(JsonUtil.toJson(entries.get(i).toMap()))
                    .append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        writeQuietly(path, json.append("]\n").toString());
    }

    private static void writeQuietly(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.error("Could not write " + path + ": " + e.getMessage());
        }
    }
}
