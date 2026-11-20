package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandManager {

    private final Map<String, Command> commandsByName = new LinkedHashMap<>();

    public void register(Command command) {
        commandsByName.put(command.getName().toLowerCase(Locale.ROOT), command);
        for (String alias : command.getAliases()) {
            commandsByName.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    /** Removes one command and all of its aliases, matched by identity. */
    public void unregister(Command command) {
        commandsByName.entrySet().removeIf(entry -> entry.getValue() == command);
    }

    /** Whether any command already owns this name or alias. */
    public boolean hasName(String name) {
        return name != null && commandsByName.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Every registered command once, in registration order - a LinkedHashSet, since a
     * command registered under multiple aliases (e.g. help/?) appears once per name.
     */
    public List<Command> getCommands() {
        return List.copyOf(new LinkedHashSet<>(commandsByName.values()));
    }

    public void dispatch(Netherrack server, CommandSender sender, String input) {
        if (input.isEmpty()) {
            return;
        }

        String[] parts = input.split("\\s+");
        String label = parts[0];
        String name = label.toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command command = commandsByName.get(name);
        if (command == null) {
            sender.sendFailure("commands.generic.unknown", parts[0]);
            return;
        }
        if (sender.getPermissionLevel() < command.getPermissionLevel()) {
            sender.sendFailure("commands.generic.error.permissions", label);
            return;
        }
        String node = command.getPermissionNode();
        if (!node.isBlank() && !server.getPluginManager().hasPermission(sender, node)) {
            sender.sendFailure("commands.generic.error.permissions", label);
            return;
        }
        command.execute(server, sender, args, label);
    }
}
