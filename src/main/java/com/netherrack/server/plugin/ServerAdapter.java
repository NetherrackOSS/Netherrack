package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.command.CommandSender;
import com.netherrack.api.entity.OfflinePlayer;
import com.netherrack.api.entity.Player;
import com.netherrack.api.permission.PermissionManager;
import com.netherrack.api.plugin.PluginLogger;
import com.netherrack.api.plugin.PluginManager;
import com.netherrack.api.scheduler.Scheduler;
import com.netherrack.api.world.World;
import com.netherrack.server.Netherrack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    public OfflinePlayer getOfflinePlayer(String nameOrXuid) {
        if (nameOrXuid == null || nameOrXuid.isBlank()) {
            return null;
        }
        for (OfflinePlayer candidate : getOfflinePlayers()) {
            if (candidate.getName().equalsIgnoreCase(nameOrXuid)
                    || !candidate.getXuid().isEmpty()
                    && candidate.getXuid().equalsIgnoreCase(nameOrXuid)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public OfflinePlayer getOfflinePlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (OfflinePlayer candidate : getOfflinePlayers()) {
            if (uuid.equals(candidate.getUuid())) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public Collection<OfflinePlayer> getOfflinePlayers() {
        Map<String, OfflinePlayerAdapter> byKey = new LinkedHashMap<>();
        for (com.netherrack.server.player.Player online : server.getPlayerManager().getPlayers()) {
            remember(byKey, new OfflinePlayerAdapter(this, online.getUsername(), online.getUuid(),
                    online.getXuid(), online));
        }
        if (server.getPlayerIdentities() != null) {
            for (var entry : server.getPlayerIdentities().entries()) {
                remember(byKey, new OfflinePlayerAdapter(this, entry.name(), entry.uuid(), entry.xuid(),
                        null));
            }
        }
        if (server.getPermissions() != null) {
            for (String xuid : server.getPermissions().xuids()) {
                remember(byKey, new OfflinePlayerAdapter(this, "", null, xuid, null));
            }
        }
        if (server.getWhitelist() != null) {
            for (Map<String, Object> entry : server.getWhitelist().entries()) {
                remember(byKey, new OfflinePlayerAdapter(this, text(entry.get("name")),
                        parseUuid(entry.get("uuid")), text(entry.get("xuid")), null));
            }
        }
        if (server.getBans() != null) {
            for (var entry : server.getBans().entries()) {
                remember(byKey, new OfflinePlayerAdapter(this, entry.name(), parseUuid(entry.uuid()),
                        entry.xuid(), null));
            }
        }
        if (server.getWorld() != null) {
            for (UUID uuid : server.getWorld().playerUuids()) {
                remember(byKey, new OfflinePlayerAdapter(this, "", uuid, "", null));
            }
        }
        return List.copyOf(byKey.values());
    }

    /** Keeps one view per identity, with an online player winning over an older saved record. */
    private void remember(Map<String, OfflinePlayerAdapter> byKey, OfflinePlayerAdapter candidate) {
        String key = !candidate.getXuid().isEmpty()
                ? "xuid:" + candidate.getXuid().toLowerCase(Locale.ROOT)
                : candidate.getUuid() != null
                        ? "uuid:" + candidate.getUuid() : "name:" + candidate.getName().toLowerCase(Locale.ROOT);
        OfflinePlayerAdapter existing = byKey.get(key);
        if (existing != null && completeness(existing) >= completeness(candidate)) {
            return; // keep the online player, or the record that knows the most about them
        }
        byKey.put(key, candidate);
    }

    /** How much a lookup knows about one identity, so a bare XUID can't hide a saved name. */
    private static int completeness(OfflinePlayerAdapter player) {
        if (player.isOnline()) {
            return 8;
        }
        int score = 0;
        String name = player.getName();
        if (!name.isEmpty() && !name.startsWith("XUID ") && !name.startsWith("UUID ")
                && !name.equals("Unknown player")) {
            score += 2;
        }
        if (player.getUuid() != null) {
            score += 2;
        }
        if (!player.getXuid().isEmpty()) {
            score += 1;
        }
        return score;
    }

    private static UUID parseUuid(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
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
