package com.netherrack.server.plugin;

import com.netherrack.api.permission.Permission;
import com.netherrack.api.permission.PermissionManager;
import com.netherrack.api.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The registry of permission nodes plugins have declared.
 * <p>
 * Registration happens during plugin startup, while checks happen from network and tick threads.
 * Reads return snapshots so a permission check never sees a half-updated map. Nodes are keyed by
 * their lower-case name because permission strings are case-insensitive.
 */
public final class PermissionManagerImpl implements PermissionManager {

    private final Map<String, Permission> permissions = new LinkedHashMap<>();

    @Override
    public synchronized Permission getPermission(String node) {
        return node == null ? null : permissions.get(node.toLowerCase(Locale.ROOT));
    }

    @Override
    public synchronized Collection<Permission> getPermissions() {
        return List.copyOf(permissions.values());
    }

    @Override
    public synchronized void registerPermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission may not be null");
        }
        permissions.put(permission.getKey(), permission);
    }

    @Override
    public synchronized void unregisterPermission(Permission permission) {
        if (permission != null) {
            permissions.remove(permission.getKey(), permission);
        }
    }

    @Override
    public synchronized void unregisterPermissions(Plugin plugin) {
        permissions.values().removeIf(permission -> permission.getPlugin() == plugin);
    }

    /** A stable copy for permission checks, which may run while a plugin registers more nodes. */
    synchronized List<Permission> snapshot() {
        return List.copyOf(permissions.values());
    }
}
