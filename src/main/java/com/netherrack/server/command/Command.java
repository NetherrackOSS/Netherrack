package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

import java.util.List;

/**
 * A console command. Each command gets its own class (StopCommand, VersionCommand, ...)
 * registered with a CommandManager, rather than one big switch statement.
 */
public interface Command {

    String getName();

    /**
     * Alternate names this command also responds to (e.g. "?" for "help"). Empty by default.
     */
    default List<String> getAliases() {
        return List.of();
    }

    /**
     * Language key (see src/main/resources/lang/*.properties) for this command's one-line
     * description, shown by the "help" command.
     */
    String getHelpLangKey();

    void execute(Netherrack server, String[] args);
}
