package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;

public class VersionCommand implements Command {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public String getHelpLangKey() {
        return "command.help.version";
    }

    @Override
    public void execute(Netherrack server, String[] args) {
        Logger.info(Lang.current.get("command.version", Netherrack.VERSION));
    }
}
