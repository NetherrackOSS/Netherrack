package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.setup.Lang;

public class VersionCommand implements Command {

    @Override
    public String getName() {
        return "version";
    }

    /** Vanilla has no version command, so there's no description of it for the client to word. */
    @Override
    public String getDescription() {
        return Lang.current.get("command.description.version");
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        sender.sendSuccess("command.version", Netherrack.VERSION);
    }
}
