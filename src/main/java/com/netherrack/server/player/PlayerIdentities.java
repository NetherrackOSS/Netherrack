package com.netherrack.server.player;

import com.netherrack.server.util.Logger;
import org.jose4j.json.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * The names and ids of players the server has seen, kept in Nukkit's {@code players/} folder.
 * <p>
 * Nukkit writes one data file per player there, names its UUID-based saves after the UUID and keeps
 * a separate name-to-UUID lookup. This store follows that shape for the small identity record the
 * plugin API needs: one {@code <uuid>.json} per player, with an in-memory name and XUID index so
 * lookups do not have to read every file. Like the world's LevelDB player data, the JSON is
 * identity-only - inventories and positions are not duplicated here.
 */
public final class PlayerIdentities {

    /** Nukkit-style player directory, beside server.properties. */
    public static final Path DIRECTORY = Path.of("players");

    /** The earlier single-file index, imported once into {@link #DIRECTORY} on startup. */
    public static final Path LEGACY_FILE = Path.of("player-identity.json");

    /**
     * One identity as it is written to disk. Either id may be empty for offline/self-signed logins.
     */
    public record Entry(String name, UUID uuid, String xuid, long firstPlayed, long lastPlayed) {

        public Entry(String name, UUID uuid, String xuid) {
            this(name, uuid, xuid, epochSeconds(), epochSeconds());
        }
    }

    /** The file backing one entry, so a renamed UUID can replace the old record. */
    private static final class Stored {
        private Entry entry;
        private Path path;

        private Stored(Entry entry, Path path) {
            this.entry = entry;
            this.path = path;
        }
    }

    private final Path directory;
    private final List<Stored> stored;

    private PlayerIdentities(Path directory, List<Stored> stored) {
        this.directory = directory;
        this.stored = stored;
    }

    /** Loads the standard player directory, importing the old single file if it is still there. */
    public static PlayerIdentities load() {
        return load(DIRECTORY);
    }

    /** Loads a given player directory; tests can point this at a scratch folder. */
    public static PlayerIdentities load(Path directory) {
        Path path = directory == null ? DIRECTORY : directory;
        PlayerIdentities identities = new PlayerIdentities(path, new ArrayList<>());
        identities.loadFiles();
        identities.importLegacy();
        return identities;
    }

    private void loadFiles() {
        try {
            Files.createDirectories(directory);
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    Entry entry = readFile(path);
                    if (entry != null) {
                        rememberEntry(entry, path);
                    }
                }
            }
        } catch (IOException e) {
            Logger.error("Could not read player files in " + directory + ": " + e.getMessage());
        }
    }

    /**
     * Remembers a name and ids as a player logs in, merging with an earlier entry when possible.
     */
    public synchronized void record(String name, UUID uuid, String xuid) {
        if (blank(name)) {
            return;
        }
        long now = epochSeconds();
        rememberEntry(new Entry(name, uuid, xuid == null ? "" : xuid, now, now), null);
    }

    /** A copy of the known identities, in the order they were first seen. */
    public synchronized List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Stored value : stored) {
            entries.add(value.entry);
        }
        return List.copyOf(entries);
    }

    /** The identity with this name, XUID or UUID string, ignoring case, or null. */
    public synchronized Entry find(String nameOrXuid) {
        if (blank(nameOrXuid)) {
            return null;
        }
        for (Stored value : stored) {
            Entry entry = value.entry;
            if (nameOrXuid.equalsIgnoreCase(entry.name())
                    || nameOrXuid.equalsIgnoreCase(entry.xuid())
                    || entry.uuid() != null
                            && nameOrXuid.equalsIgnoreCase(entry.uuid().toString())) {
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
        for (Stored value : stored) {
            if (uuid.equals(value.entry.uuid())) {
                return value.entry;
            }
        }
        return null;
    }

    /**
     * Merges one observed identity into the in-memory index and writes its file. A UUID is the file
     * name, matching Nukkit's UUID-based player data; a record without one can only be merged into
     * an existing identity, since there is no stable file to own.
     */
    private void rememberEntry(Entry candidate, Path source) {
        if (candidate == null || blank(candidate.name())) {
            return;
        }
        List<Stored> matches = matches(candidate);
        if (matches.isEmpty()) {
            if (candidate.uuid() == null) {
                return;
            }
            Path path = pathFor(candidate.uuid());
            stored.add(new Stored(candidate, path));
            save(path, candidate);
            return;
        }

        Entry merged = candidate;
        for (Stored match : matches) {
            merged = combine(merged, match.entry);
        }
        Path target = pathFor(merged.uuid());
        if (target == null) {
            // Two records matched but neither supplied a usable UUID; keep the first file.
            target = matches.get(0).path;
        }
        stored.removeAll(matches);
        stored.add(new Stored(merged, target));
        save(target, merged);
        for (Stored match : matches) {
            if (!match.path.equals(target)) {
                deleteQuietly(match.path);
            }
        }
        if (source != null && !source.equals(target)
                && matches.stream().noneMatch(match -> match.path.equals(source))) {
            deleteQuietly(source);
        }
    }

    /**
     * Entries that describe the same player, preferring UUID, then XUID, then a name-only record.
     */
    private List<Stored> matches(Entry candidate) {
        Set<Stored> matches = new LinkedHashSet<>();
        for (Stored value : stored) {
            Entry entry = value.entry;
            if (candidate.uuid() != null && candidate.uuid().equals(entry.uuid())
                    || !blank(candidate.xuid()) && !blank(entry.xuid())
                            && candidate.xuid().equalsIgnoreCase(entry.xuid())
                    || candidate.uuid() == null && blank(candidate.xuid())
                            && candidate.name().equalsIgnoreCase(entry.name())) {
                matches.add(value);
            }
        }
        return List.copyOf(matches);
    }

    /** Prefers the newer observation's identifiers, but keeps the earliest first-played time. */
    private static Entry combine(Entry first, Entry second) {
        Entry newer = first.lastPlayed() >= second.lastPlayed() ? first : second;
        Entry older = newer == first ? second : first;
        UUID uuid = newer.uuid() != null ? newer.uuid() : older.uuid();
        String name = !blank(newer.name()) ? newer.name() : older.name();
        String xuid = !blank(newer.xuid()) ? newer.xuid() : older.xuid();

        long earliest = Long.MAX_VALUE;
        if (first.firstPlayed() > 0) {
            earliest = Math.min(earliest, first.firstPlayed());
        }
        if (second.firstPlayed() > 0) {
            earliest = Math.min(earliest, second.firstPlayed());
        }
        if (earliest == Long.MAX_VALUE) {
            earliest = epochSeconds();
        }
        long last = Math.max(first.lastPlayed(), second.lastPlayed());
        return new Entry(name, uuid, xuid, earliest, Math.max(last, earliest));
    }

    private Path pathFor(UUID uuid) {
        return uuid == null ? null : directory.resolve(uuid + ".json");
    }

    private Entry readFile(Path path) {
        try {
            Object parsed = JsonUtil.parseJson(Files.readString(path, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IOException("expected a JSON object");
            }
            Entry entry = read(map);
            if (entry == null) {
                return null;
            }
            if (entry.uuid() == null) {
                String fileName = path.getFileName().toString();
                try {
                    UUID fromName = UUID.fromString(fileName.substring(0, fileName.length() - 5));
                    entry = new Entry(entry.name(), fromName, entry.xuid(), entry.firstPlayed(),
                            entry.lastPlayed());
                } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
                    // A name-keyed file from an earlier build can still be merged by name.
                }
            }
            return entry;
        } catch (Exception e) {
            Logger.error("Could not read " + path + ", so that player identity is skipped: "
                    + e.getMessage());
            return null;
        }
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
        long now = epochSeconds();
        long first = number(map.get("firstPlayed"));
        long last = number(map.get("lastPlayed"));
        return new Entry(text, uuid, xuid, first > 0 ? first : now, last > 0 ? last : now);
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // A missing or bad timestamp just means "now".
            }
        }
        return 0;
    }

    /** Imports the old single index once, then removes it so there is only one identity store. */
    private void importLegacy() {
        Path legacy = directory.resolveSibling(LEGACY_FILE.getFileName());
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        try {
            String text = Files.readString(legacy, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) {
                Object list = JsonUtil.parseJson("{\"entries\":" + text + "}").get("entries");
                if (!(list instanceof List<?> items)) {
                    throw new IOException("expected a list of entries");
                }
                int imported = 0;
                for (Object item : items) {
                    if (item instanceof Map<?, ?> map) {
                        Entry entry = read(map);
                        if (entry != null) {
                            rememberEntry(entry, null);
                            imported++;
                        }
                    }
                }
                Logger.info("Imported " + imported + " player identities from " + legacy + " into "
                        + directory + ".");
            }
            Files.deleteIfExists(legacy);
        } catch (Exception e) {
            Logger.error("Could not import " + legacy + " into " + directory + ": "
                    + e.getMessage());
        }
    }

    /** Writes one identity, preserving keys Nukkit also uses for player data where they apply. */
    private void save(Path path, Entry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", entry.name());
        if (entry.uuid() != null) {
            map.put("uuid", entry.uuid().toString());
        }
        if (!blank(entry.xuid())) {
            map.put("xuid", entry.xuid());
        }
        map.put("firstPlayed", entry.firstPlayed());
        map.put("lastPlayed", entry.lastPlayed());
        writeQuietly(path, JsonUtil.toJson(map) + "\n");
    }

    private static void writeQuietly(Path path, String text) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, REPLACE_EXISTING, ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Logger.error("Could not write " + path + ": " + e.getMessage());
            deleteQuietly(temporary);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Logger.error("Could not remove " + path + ": " + e.getMessage());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static long epochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
