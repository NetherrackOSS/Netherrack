package com.netherrack.server.plugin;

import com.netherrack.api.entity.OfflinePlayer;
import com.netherrack.api.entity.Player;

import java.util.UUID;

/**
 * One identity plugins can look up while its owner is offline.
 * <p>
 * Online players wrap the live server player so name and UUID never drift. Offline entries rely on
 * whatever source supplied them; when only an id is known, the name is a readable placeholder
 * rather than a guess.
 */
final class OfflinePlayerAdapter implements OfflinePlayer {

    private final ServerAdapter server;
    private final com.netherrack.server.player.Player online;
    private final String name;
    private final UUID uuid;
    private final String xuid;

    OfflinePlayerAdapter(ServerAdapter server, String name, UUID uuid, String xuid,
                         com.netherrack.server.player.Player online) {
        this.server = server;
        this.name = name == null ? "" : name;
        this.uuid = uuid;
        this.xuid = xuid == null ? "" : xuid;
        this.online = online;
    }

    @Override
    public String getName() {
        if (online != null) {
            return online.getUsername();
        }
        if (!name.isEmpty()) {
            return name;
        }
        return !xuid.isEmpty() ? "XUID " + xuid
                : uuid != null ? "UUID " + uuid : "Unknown player";
    }

    @Override
    public UUID getUuid() {
        return online != null ? online.getUuid() : uuid;
    }

    @Override
    public String getXuid() {
        return online != null ? online.getXuid() : xuid;
    }

    @Override
    public boolean isOnline() {
        return online != null;
    }

    @Override
    public Player getPlayer() {
        return online == null ? null : server.player(online);
    }
}
