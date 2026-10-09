package com.netherrack.server.player;

import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;

import java.util.Locale;

/**
 * What a player may do, as vanilla's permission levels:
 * <ul>
 *   <li><b>visitor</b>: only looks around;</li>
 *   <li><b>member</b>: builds, mines, uses doors and containers and fights;</li>
 *   <li><b>operator</b>: everything, and commands up to the server's op-permission-level.</li>
 * </ul>
 */
public enum Permission {
    VISITOR,
    MEMBER,
    OPERATOR;

    /** The name used in permissions.json and server.properties. */
    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The permission by name, in any case, or null if it's none of them. */
    public static Permission fromName(String name) {
        for (Permission permission : values()) {
            if (permission.getName().equalsIgnoreCase(name)) {
                return permission;
            }
        }
        return null;
    }

    public PlayerPermission toPlayerPermission() {
        return switch (this) {
            case VISITOR -> PlayerPermission.VISITOR;
            case MEMBER -> PlayerPermission.MEMBER;
            case OPERATOR -> PlayerPermission.OPERATOR;
        };
    }
}
