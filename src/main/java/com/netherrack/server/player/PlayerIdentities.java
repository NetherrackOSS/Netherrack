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
import java.util.UUID;

/**
 * The names and ids of players the server has seen, kept in {@code player-identity.json}.
 * <p>
 * Saved worlds only hold a player's UUID, and permissions.json only holds XUIDs, so neither can
 * answer "who was this?" on its own. This small file gives offline player lookups a name without
 * asking plugins to keep their own registry. It is deliberately identity-only: inventories and
 * positions stay in the world's LevelDB data.
 */
public final class PlayerIdentities {

    /** The vanilla-style file kept beside server.properties. */
    public static final Path FILE = Path.of("player-identity.json");

    /** One identity as it is written to disk. Either id may be empty for offline/self-signed logins. */
    public record Entry(String name, UUID uuid, String xuid) {
    }

    private final Path path;
    private final List<Entry> entries;

    private PlayerIdentities(Path path, List<Entry> entries) {
        this.path = path;
        this.entries = entries;
    }

    /** Reads the identity file, creating an empty one on a first run. Bad files start empty. */
    public static PlayerIdentities load(Path path) {
        List<Entry> entries = new ArrayList<>();
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) {
                Object list = JsonUtil.parseJson("{\"entries\":" + text + "}").get("entries");
                if (!(list instanceof List<?> items)) {
                    throw new IOException("expected a list of entries");
                }
                for (Object item : items) {
                    if (item instanceof Map<?, ?> map) {
                        Entry entry = read(map);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                }
            }
        } catch (NoSuchFileException e) {
            writeQuietly(path, "[]\n");
        } catch (Exception e) {
            Logger.error("Could not read " + path + ", so offline player names start empty: "
                    + e.getMessage());
        }
        return new PlayerIdentities(path, entries);
    }

    /** Remembers a name and ids as a player logs in, merging with an earlier entry when possible. */
    public synchronized void record(String name, UUID uuid, String xuid) {
        if (name == null || name.isBlank()) {
            return;
        }
        Entry found = null;
        for (Entry entry : entries) {
            if (uuid != null && uuid.equals(entry.uuid())
                    || !blank(xuid) && xuid.equalsIgnoreCase(entry.xuid())) {
                found = entry;
                break;
            }
        }
        if (found != null) {
            UUID mergedUuid = found.uuid() != null ? found.uuid() : uuid;
            String mergedXuid = !blank(found.xuid()) ? found.xuid() : xuid;
            boolean sameName = name.equals(found.name());
            boolean sameUuid = java.util.Objects.equals(mergedUuid, found.uuid());
            boolean sameXuid = blank(mergedXuid) ? blank(found.xuid()) : mergedXuid.equals(found.xuid());
            if (sameName && sameUuid && sameXuid) {
                return;
            }
            entries.remove(found);
            entries.add(new Entry(name, mergedUuid, mergedXuid == null ? "" : mergedXuid));
        } else {
            entries.add(new Entry(name, uuid, xuid == null ? "" : xuid));
        }
        save();
    }

    /** A copy of the known identities, in the order they were first seen. */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** The identity with this name, XUID or UUID string, ignoring case, or null. */
    public synchronized Entry find(String nameOrXuid) {
        if (blank(nameOrXuid)) {
            return null;
        }
        for (Entry entry : entries) {
            if (nameOrXuid.equalsIgnoreCase(entry.name()) || nameOrXuid.equalsIgnoreCase(entry.xuid())
                    || entry.uuid() != null && nameOrXuid.equalsIgnoreCase(entry.uuid().toString())) {
                return entry;
            }
        }
        return null;
    }

    /** The identity with this UUID, or null. */
    public synchronized Entry find(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (uuid.equals(entry.uuid())) {
                return entry;
            }
        }
        return null;
    }

    private static Entry read(Map<?, ?> map) {
        Object name = map.get("name");
        if (!(name instanceof String text) || text.isBlank()) {
            return null;
        }
        UUID uuid = null;
        if (map.get("uuid") instanceof String value && !value.isBlank()) {
            try {
                uuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // A bad id is better dropped than shown to a plugin as a player's UUID.
            }
        }
        String xuid = map.get("xuid") instanceof String value ? value : "";
        return new Entry(text, uuid, xuid);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Writes the list back, logging a failure: the change still holds until the server stops. */
    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", entry.name());
            if (entry.uuid() != null) {
                map.put("uuid", entry.uuid().toString());
            }
            if (!entry.xuid().isEmpty()) {
                map.put("xuid", entry.xuid());
            }
            json.append("  ").append(JsonUtil.toJson(map))
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
