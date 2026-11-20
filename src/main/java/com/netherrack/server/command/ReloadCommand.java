package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.NetherrackConfig;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.LoggingConfig;
import com.netherrack.server.util.Logger;
import com.netherrack.server.util.NettyLogBridge;

import java.util.List;
import java.util.Map;

/**
 * Re-reads netherrack.yml's logging sections while the server is running, so a format or color
 * tweak doesn't need a restart. The file is read quietly first: {@link Logger} applies the new
 * settings before it prints the reply, so even that reply uses them. Network debug output is
 * picked up too, since it lives in the same file beside the logging settings.
 */
public class ReloadCommand implements Command {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rl");
    }

    @Override
    public String getDescription() {
        return Lang.current.get("command.description.reload");
    }

    @Override
    public int getPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        Map<String, String> values = NetherrackConfig.readQuietly(NetherrackConfig.FILE);
        Logger.configure(LoggingConfig.from(values));
        String debug = values.get("logs.debug");
        if (debug != null) {
            NettyLogBridge.setDebug(Boolean.parseBoolean(debug));
        }
        sender.sendText(Lang.current.get("command.reload.logging"));
    }
}
