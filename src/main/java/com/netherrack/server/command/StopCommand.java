package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

public class StopCommand implements Command {

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getHelpLangKey() {
        return "command.help.stop";
    }

    @Override
    public void execute(Netherrack server, String[] args) {
        server.shutdown();
    }
}
