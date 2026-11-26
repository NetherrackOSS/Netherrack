package com.netherrack.server.plugin;

import com.netherrack.api.command.PluginCommand;
import com.netherrack.server.Netherrack;
import com.netherrack.server.command.Command;
import com.netherrack.server.command.CommandSender;

import java.util.List;

/**
 * Adapts a plugin's API command to the server's own command registry.
 * <p>
 * This is deliberately thin: the registry still handles dispatch, permission levels and
 * suggestions, while the API command only decides what happens when it runs. Returning false from
 * the plugin's executor is turned into the usage line the plugin declared in plugin.yml.
 */
final class PluginCommandAdapter implements Command {

    private final PluginCommand command;
    private final ServerAdapter server;

    PluginCommandAdapter(PluginCommand command, ServerAdapter server) {
        this.command = command;
        this.server = server;
    }

    @Override
    public String getName() {
        return command.getName();
    }

    @Override
    public List<String> getAliases() {
        return command.getAliases();
    }

    @Override
    public String getDescription() {
        return command.getDescription();
    }

    @Override
    public String getPermissionNode() {
        return command.getPermission();
    }

    @Override
    public void execute(Netherrack netherrack, CommandSender sender, String[] args, String label) {
        com.netherrack.api.command.CommandSender target = server.sender(sender);
        boolean handled = command.execute(target, label, args);
        if (!handled) {
            sender.sendText(command.getUsage());
        }
    }

    /** Kept for callers that don't supply the alias that was typed. */
    @Override
    public void execute(Netherrack netherrack, CommandSender sender, String[] args) {
        execute(netherrack, sender, args, command.getName());
    }

    PluginCommand apiCommand() {
        return command;
    }
}
