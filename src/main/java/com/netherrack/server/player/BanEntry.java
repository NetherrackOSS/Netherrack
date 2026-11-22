package com.netherrack.server.player;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One player ban, saved in the shape Cloudburst Nukkit uses in banned-players.json: a lowercase
 * name, creation date, source, "Forever" or an expiry date, and a reason. XUID and UUID are
 * stored as well when they're known, so a ban follows the account after a gamertag change and
 * still works for a player whose entry was first added by name alone.
 *
 * @param name           the lowercase gamertag
 * @param xuid           Xbox Live ID when known, otherwise empty
 * @param uuid           player UUID when known, otherwise empty
 * @param reason         why the player was banned, empty when none was given
 * @param source         who ran the command
 * @param creationDate   when the ban was made
 * @param expirationDate when the ban ends, or null for a permanent ban
 */
public record BanEntry(String name, String xuid, String uuid, String reason, String source,
                       Instant creationDate, Instant expirationDate) {

    public static final String FOREVER = "Forever";

    /** Nukkit's date format, written in UTC so a file is readable wherever the server runs. */
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss Z", Locale.ROOT).withZone(ZoneOffset.UTC);

    public BanEntry {
        name = normalize(name);
        xuid = text(xuid);
        uuid = text(uuid);
        reason = text(reason);
        source = text(source).isEmpty() ? "(Unknown)" : text(source);
        creationDate = creationDate == null ? Instant.now() : creationDate;
    }

    /** Makes a new permanent ban with the current time. */
    public static BanEntry of(String name, String xuid, String uuid, String reason, String source) {
        return new BanEntry(name, xuid, uuid, reason, source, Instant.now(), null);
    }

    /** Reads one entry, returning null when it has no identifier to match a player by. */
    public static BanEntry fromMap(Map<?, ?> map) {
        String name = text(map.get("name"));
        String xuid = text(map.get("xuid"));
        String uuid = text(map.get("uuid"));
        if (name.isEmpty() && xuid.isEmpty() && uuid.isEmpty()) {
            return null;
        }
        Instant created = parseDate(map.get("creationDate"), Instant.now());
        Object expiration = map.get("expireDate");
        Instant expires = expiration == null || FOREVER.equalsIgnoreCase(text(expiration))
                ? null
                : parseDate(expiration, null);
        return new BanEntry(name, xuid, uuid, text(map.get("reason")), text(map.get("source")),
                created, expires);
    }

    public boolean hasExpired() {
        return expirationDate != null && !expirationDate.isAfter(Instant.now());
    }

    /** The identifier shown by /banlist: the name, or the stored id for a nameless entry. */
    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        return xuid.isEmpty() ? "UUID " + uuid : "XUID " + xuid;
    }

    /** The map written to banned-players.json, with Nukkit's fields first. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        if (!xuid.isEmpty()) {
            map.put("xuid", xuid);
        }
        if (!uuid.isEmpty()) {
            map.put("uuid", uuid);
        }
        map.put("creationDate", DATE.format(creationDate));
        map.put("source", source);
        map.put("expireDate", expirationDate == null ? FOREVER : DATE.format(expirationDate));
        map.put("reason", reason);
        return map;
    }

    private static Instant parseDate(Object value, Instant fallback) {
        try {
            return value == null ? fallback : Instant.from(DATE.parse(text(value)));
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    static String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT);
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
