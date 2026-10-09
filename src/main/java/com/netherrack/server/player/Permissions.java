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
 * Who is an operator, a member or a visitor, kept in permissions.json next to
 * server.properties - vanilla's file, a list of entries each naming a permission and the
 * player it's for:
 * <pre>
 * [
 *   { "permission": "operator", "xuid": "2535412345678901" }
 * ]
 * </pre>
 * Players are named by XUID, which a player signed into Xbox Live has from their verified
 * login. Players who aren't signed in have none, so they always get the default.
 * <p>
 * Players without an entry get default-player-permission-level from server.properties
 * (member unless set). The op and deop commands add and remove operator entries; nothing
 * else writes the file, so entries written by hand stay as they are, extra fields included.
 */
public class Permissions {

    public static final Path FILE = Path.of("permissions.json");

    private final Path path;
    private final Permission defaultPermission;
    private final List<Map<String, Object>> entries;

    private Permissions(Path path, Permission defaultPermission, List<Map<String, Object>> entries) {
        this.path = path;
        this.defaultPermission = defaultPermission;
        this.entries = entries;
    }

    /**
     * Reads the file at {@code path}. Without one, an empty list is written there, ready
     * to edit. A file that can't be read leaves everyone on the default, rather than
     * stopping the server.
     */
    public static Permissions load(Path path, Permission defaultPermission) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) {
                // jose4j's parser only takes an object at the top level, so the list is wrapped in one.
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
            Logger.error("Could not read " + path + ", so every player gets the default permission: " + e.getMessage());
        }

        long unusable = entries.stream().filter(entry -> !(entry.get("xuid") instanceof String)
                || Permission.fromName(String.valueOf(entry.get("permission"))) == null).count();
        if (unusable > 0) {
            Logger.warn(unusable + " entries in " + path + " have no \"xuid\" or no valid \"permission\" "
                    + "(visitor, member or operator), so they do nothing.");
        }
        return new Permissions(path, defaultPermission, entries);
    }

    /** What the player with this XUID may do: their entry's permission, or the default. */
    public synchronized Permission of(String xuid) {
        Map<String, Object> entry = entryFor(xuid);
        if (entry == null) {
            return defaultPermission;
        }
        Permission permission = Permission.fromName(String.valueOf(entry.get("permission")));
        return permission != null ? permission : defaultPermission;
    }

    /** The permission of players without an entry, which a player gets back when deopped. */
    public Permission getDefault() {
        return defaultPermission;
    }

    /**
     * Makes the player with this XUID an operator, saving the file. Returns false if they
     * already were one.
     */
    public synchronized boolean op(String xuid) {
        Map<String, Object> entry = entryFor(xuid);
        if (entry != null && Permission.OPERATOR.getName().equals(entry.get("permission"))) {
            return false;
        }
        if (entry == null) {
            entry = new LinkedHashMap<>();
            entries.add(entry);
        }
        entry.put("permission", Permission.OPERATOR.getName());
        entry.put("xuid", xuid);
        save();
        return true;
    }

    /**
     * Takes away the player's operator status by removing their entry, so they get the
     * default, saving the file. Returns false if they weren't an operator.
     */
    public synchronized boolean deop(String xuid) {
        Map<String, Object> entry = entryFor(xuid);
        if (entry == null || !Permission.OPERATOR.getName().equals(entry.get("permission"))) {
            return false;
        }
        entries.remove(entry);
        save();
        return true;
    }

    private Map<String, Object> entryFor(String xuid) {
        if (xuid == null || xuid.isEmpty()) {
            return null;
        }
        for (Map<String, Object> entry : entries) {
            if (entry.get("xuid") instanceof String entryXuid && entryXuid.equalsIgnoreCase(xuid)) {
                return entry;
            }
        }
        return null;
    }

    /** Writes the list back, logging a failure: the change still holds until the server stops. */
    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            json.append("  ").append(JsonUtil.toJson(entries.get(i))).append(i < entries.size() - 1 ? ",\n" : "\n");
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
}
