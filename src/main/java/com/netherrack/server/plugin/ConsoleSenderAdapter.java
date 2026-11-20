package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.command.CommandSender;
import com.netherrack.api.entity.Player;
import com.netherrack.api.permission.PermissionAttachment;
import com.netherrack.api.permission.PermissionState;
import com.netherrack.api.plugin.Plugin;
import com.netherrack.server.util.Logger;

/**
 * The console as an API command sender.
 * <p>
 * One instance is kept for the whole server so a permission manager can attach nodes to the console
 * once and have them still apply to commands run later. It is always an operator for the purposes of
 * node defaults, but an explicit denial still wins.
 */
final class ConsoleSenderAdapter implements CommandSender {

    private final ServerAdapter server;
    private final PermissionSubject permissions;
    private final String name;

    ConsoleSenderAdapter(ServerAdapter server, PermissionSubject permissions, String name) {
        this.server = server;
        this.permissions = permissions;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public void sendMessage(String message) {
        Logger.info(message);
    }

    @Override
    public PermissionState getPermissionValue(String node) {
        return permissions.getPermissionValue(node);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return permissions.addAttachment(plugin);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        permissions.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        permissions.recalculatePermissions();
    }
}
