package com.netherrack.server.player;

import org.cloudburstmc.protocol.bedrock.data.GameType;

import java.util.Locale;

/**
 * The game modes Netherrack supports - survival, creative and adventure - by the names
 * (or numbers) used in server.properties and the gamemode command.
 */
public final class GameModes {

    private GameModes() {
    }

    /** "survival"/"creative"/"adventure", their first letters, or 0/1/2. Null if it's none of them. */
    public static GameType parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            default -> null;
        };
    }

    public static String name(GameType gameMode) {
        return gameMode.name().toLowerCase(Locale.ROOT);
    }
}
