package com.netherrack.server.player;

import com.netherrack.server.util.Logger;
import org.jose4j.json.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The players allowed onto the server while {@code white-list} is on, kept in whitelist.json
 * beside permissions.json. Entries are deliberately small and readable:
 * <pre>
 * [
 *   { "name": "SchleroMC", "xuid": "2535412345678901", "uuid": "..." },
 *   { "name": "AnOfflineFriend" }
 * ]
 * </pre>
 * An entry can name a player by gamertag, XUID or UUID, or by any combination of them. Matching
 * accepts whichever of those the joining player actually has, so an entry added while someone was
 * online keeps working if their gamertag changes, and an offline name still works for an account
 * that has never been seen. Operators bypass the whitelist, the way they do in Cloudburst Nukkit.
 */
public class Whitelist {

    public static final Path FILE = Path.of("whitelist.json");

    private final Path path;
    private final List<Map<String, Object>> entries;

    private Whitelist(Path path, List<Map<String, Object>> entries) {
        this.path = path;
        this.entries = entries;
    }

    /**
     * Reads the whitelist, creating an empty one if the file is missing. A broken file leaves the
     * whitelist empty rather than stopping startup, so an operator can always fix it by hand.
     */
    public static Whitelist load(Path path) {
        List<Map<String, Object>> entries = new ArrayList<>();
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
                        entries.add(copy(map));
                    }
                }
            }
        } catch (NoSuchFileException e) {
            writeQuietly(path, "[]\n");
        } catch (Exception e) {
            Logger.error("Could not read " + path + ", so the whitelist is empty: "
                    + e.getMessage());
        }

        long unusable = entries.stream()
                .filter(entry -> text(entry.get("name")).isEmpty()
                        && text(entry.get("xuid")).isEmpty()
                        && text(entry.get("uuid")).isEmpty())
                .count();
        if (unusable > 0) {
            Logger.warn(unusable + " entries in " + path + " have no name, xuid or uuid, so they "
                    + "do nothing.");
        }
        return new Whitelist(path, entries);
    }

    /** Reads the file again, so changes made while the server is running can be applied. */
    public synchronized void reload() {
        List<Map<String, Object>> reloaded = load(path).entries;
        entries.clear();
        entries.addAll(reloaded);
    }

    /** Whether any entry matches this player's gamertag, XUID or UUID. */
    public synchronized boolean contains(String name, String xuid, String uuid) {
        return find(name, xuid, uuid) != null;
    }

    /**
     * Adds an entry, preferably with all the identifiers the online player has. Returns false if
     * an entry already matches, so repeating the command never creates duplicates.
     */
    public synchronized boolean add(String name, String xuid, String uuid) {
        String entryName = text(name);
        String entryXuid = text(xuid);
        String entryUuid = text(uuid);
        if (entryName.isEmpty() && entryXuid.isEmpty() && entryUuid.isEmpty()) {
            return false;
        }
        if (find(entryName, entryXuid, entryUuid) != null) {
            return false;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        if (!entryName.isEmpty()) {
            entry.put("name", entryName);
        }
        if (!entryXuid.isEmpty()) {
            entry.put("xuid", entryXuid);
        }
        if (!entryUuid.isEmpty()) {
            entry.put("uuid", entryUuid);
        }
        entries.add(entry);
        save();
        return true;
    }

    /** Removes the first entry matching these identifiers, returning whether one was removed. */
    public synchronized boolean remove(String name, String xuid, String uuid) {
        Map<String, Object> entry = find(name, xuid, uuid);
        if (entry == null) {
            return false;
        }
        entries.remove(entry);
        save();
        return true;
    }

    /** A copy of the entries, for /whitelist list without exposing the live list. */
    public synchronized List<Map<String, Object>> entries() {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            copy.add(new LinkedHashMap<>(entry));
        }
        return List.copyOf(copy);
    }

    private Map<String, Object> find(String name, String xuid, String uuid) {
        String wantedName = text(name);
        String wantedXuid = text(xuid);
        String wantedUuid = text(uuid);
        for (Map<String, Object> entry : entries) {
            String entryXuid = text(entry.get("xuid"));
            if (!wantedXuid.isEmpty() && entryXuid.equalsIgnoreCase(wantedXuid)) {
                return entry;
            }
            String entryUuid = text(entry.get("uuid"));
            if (!wantedUuid.isEmpty() && entryUuid.equalsIgnoreCase(wantedUuid)) {
                return entry;
            }
            String entryName = text(entry.get("name"));
            if (!wantedName.isEmpty() && entryName.equalsIgnoreCase(wantedName)) {
                return entry;
            }
        }
        return null;
    }

    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            json.append("  ").append(JsonUtil.toJson(entries.get(i)))
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

    private static Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
