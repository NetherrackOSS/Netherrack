package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.command.CommandSender;
import com.netherrack.api.entity.Player;
import com.netherrack.api.permission.PermissionManager;
import com.netherrack.api.plugin.PluginLogger;
import com.netherrack.api.plugin.PluginManager;
import com.netherrack.api.scheduler.Scheduler;
import com.netherrack.api.world.World;
import com.netherrack.server.Netherrack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final PermissionManagerImpl permissionManager = new PermissionManagerImpl();
    private final PluginScheduler pluginScheduler;
    private final ConsoleSenderAdapter consoleSender;
    private final Map<UUID, PlayerAdapter> players = new ConcurrentHashMap<>();
    private final AtomicInteger nextFormId = new AtomicInteger();
    private PluginManagerImpl pluginManager;

    public ServerAdapter(Netherrack server) {
        this.server = server;
        this.logger = new PluginLoggerImpl("Server");
        this.worldAdapter = new WorldAdapter(server);
        this.pluginScheduler = new PluginScheduler(server.getScheduler());
        PermissionSubject consolePermissions = new PermissionSubject(() -> true, permissionManager);
        this.consoleSender = new ConsoleSenderAdapter(this, consolePermissions, "Console");
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

    @Override
    public Scheduler getScheduler() {
        return pluginScheduler;
    }

    @Override
    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    /** The implementation type, for permission checks inside the plugin layer. */
    PermissionManagerImpl permissionManager() {
        return permissionManager;
    }

    /** The implementation type, for cancelling plugin tasks during shutdown. */
    PluginScheduler pluginScheduler() {
        return pluginScheduler;
    }

    /** The single API view of the console, so its permission attachments survive between commands. */
    CommandSender consoleSender() {
        return consoleSender;
    }

    /** Maps the server's command sender to the matching API one. */
    CommandSender sender(com.netherrack.server.command.CommandSender sender) {
        return sender.getPlayer() == null ? consoleSender : player(sender.getPlayer());
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

    /** Ids are server-wide so a late response for an old adapter cannot hit a new form. */
    int nextFormId() {
        return nextFormId.incrementAndGet();
    }

    WorldAdapter worldAdapter() {
        return worldAdapter;
    }

    /** The server behind the adapter, for other implementation-layer classes. */
    Netherrack server() {
        return server;
    }
}
