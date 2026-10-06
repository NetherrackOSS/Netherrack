package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class CommandManager {

    private final Map<String, Command> commandsByName = new LinkedHashMap<>();

    public void register(Command command) {
        commandsByName.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commandsByName.put(alias.toLowerCase(), command);
        }
    }

    public void dispatch(Netherrack server, String input) {
        if (input.isEmpty()) {
            return;
        }

        String[] parts = input.split("\\s+");
        String name = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command command = commandsByName.get(name);
        if (command == null) {
            Logger.info(Lang.current.get("command.unknown", parts[0]));
            return;
        }
        command.execute(server, args);
    }

    public void printHelp() {
        Logger.info(Lang.current.get("command.help.header"));
        // A LinkedHashSet so a command registered under multiple aliases (e.g. help/?)
        // is only printed once, in registration order.
        for (Command command : new LinkedHashSet<>(commandsByName.values())) {
            Logger.info(Lang.current.get(command.getHelpLangKey()));
        }
    }
}
