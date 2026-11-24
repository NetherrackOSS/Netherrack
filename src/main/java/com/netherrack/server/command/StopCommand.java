package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

public class StopCommand implements Command {

    @Override
    public String getName() {
        return "stop";
    }

    /** Stopping the server is the console's, or a top-level operator's. */
    @Override
    public int getPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        Command.broadcastCommandMessage(server, sender, "commands.stop.start");
        server.shutdown();
    }
}
