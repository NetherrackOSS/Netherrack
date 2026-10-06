package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

import java.util.List;

public class HelpCommand implements Command {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public List<String> getAliases() {
        return List.of("?");
    }

    @Override
    public String getHelpLangKey() {
        return "command.help.help";
    }

    @Override
    public void execute(Netherrack server, String[] args) {
        server.getCommandManager().printHelp();
    }
}
