package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.permission.PermissionAttachment;
import com.netherrack.api.permission.PermissionState;
import com.netherrack.api.plugin.Plugin;
import com.netherrack.server.util.Logger;

/**
 * Logs a plugin command's replies to the console. The console's own API sender already logs its
 * replies, so this wraps only player senders. Plugin commands stay responsible for what they say;
 * this just makes those words visible to an operator watching the console.
 */
final class LoggingApiCommandSender implements com.netherrack.api.command.CommandSender {

    private final com.netherrack.api.command.CommandSender delegate;

    LoggingApiCommandSender(com.netherrack.api.command.CommandSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isPlayer() {
        return delegate.isPlayer();
    }

    @Override
    public com.netherrack.api.entity.Player getPlayer() {
        return delegate.getPlayer();
    }

    @Override
    public Server getServer() {
        return delegate.getServer();
    }

    @Override
    public void sendMessage(String message) {
        delegate.sendMessage(message);
        Logger.info(message);
    }

    @Override
    public PermissionState getPermissionValue(String node) {
        return delegate.getPermissionValue(node);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return delegate.addAttachment(plugin);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        delegate.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        delegate.recalculatePermissions();
    }
}
