package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.entity.Player;
import com.netherrack.api.plugin.PluginLogger;
import com.netherrack.api.plugin.PluginManager;
import com.netherrack.api.world.World;
import com.netherrack.server.Netherrack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presents the running server to plugins through the API's small Server interface.
 * <p>
 * The adapters for players and the world are created here so plugin code never sees protocol or
 * storage classes. Player adapters are kept by UUID because a listener may hold the Player from an
 * event after the event has finished.
 */
public final class ServerAdapter implements Server {

    private final Netherrack server;
    private final PluginLogger logger;
    private final WorldAdapter worldAdapter;
    private final Map<UUID, PlayerAdapter> players = new ConcurrentHashMap<>();
    private PluginManagerImpl pluginManager;

    public ServerAdapter(Netherrack server) {
        this.server = server;
        this.logger = new PluginLoggerImpl("Server");
        this.worldAdapter = new WorldAdapter(server);
    }

    /** Set once by the manager, after the circular reference between the two is established. */
    void setPluginManager(PluginManagerImpl pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public String getName() {
        return "Netherrack";
    }

    @Override
    public String getVersion() {
        return Netherrack.VERSION;
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    @Override
    public World getWorld() {
        return server.getWorld() == null ? null : worldAdapter;
    }

    @Override
    public Collection<Player> getPlayers() {
        List<Player> result = new ArrayList<>();
        for (com.netherrack.server.player.Player player : server.getPlayerManager().getPlayers()) {
            result.add(player(player));
        }
        return List.copyOf(result);
    }

    /** Wraps a server player, replacing the cached adapter if the UUID has been reused by a new login. */
    PlayerAdapter player(com.netherrack.server.player.Player player) {
        PlayerAdapter adapter = players.get(player.getUuid());
        if (adapter == null || adapter.serverPlayer() != player) {
            adapter = new PlayerAdapter(player, this);
            players.put(player.getUuid(), adapter);
        }
        return adapter;
    }

    WorldAdapter worldAdapter() {
        return worldAdapter;
    }

    /** The server behind the adapter, for other implementation-layer classes. */
    Netherrack server() {
        return server;
    }
}
