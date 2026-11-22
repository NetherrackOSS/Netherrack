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
 * Re-reads netherrack.yml's logging sections and hot-reloads plugins while the server is
 * running. Config is read quietly first: {@link Logger} applies the new logging settings before
 * it prints the reply, so even that reply uses them. Plugin reload follows the same rough shape
 * as Cloudburst Nukkit's reload: loaded plugins are disabled, their listeners/commands/tasks are
 * cleared, and jars in plugins/ are discovered and loaded again.
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
        server.getPluginManager().reload();
        sender.sendSuccess("command.reload.plugins");
    }
}
