package com.netherrack.server.command;

import com.netherrack.api.plugin.Plugin;
import com.netherrack.server.Netherrack;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.ColorCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /plugins - lists the plugins the server loaded, with each name in the game's lime green.
 * <p>
 * The names are sent as plain text with formatting codes rather than through sendColored, so
 * a player's client and the console both see the color: the console converts the codes to ANSI
 * when it prints them, and the log file keeps the uncolored text.
 */
public class PluginsCommand implements Command {

    private static final String LIME = ColorCodes.PREFIX + "a";
    private static final String RESET = ColorCodes.PREFIX + "r";

    @Override
    public String getName() {
        return "plugins";
    }

    @Override
    public List<String> getAliases() {
        return List.of("pl");
    }

    @Override
    public String getDescription() {
        return Lang.current.get("command.description.plugins");
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        List<String> names = new ArrayList<>();
        for (Plugin plugin : server.getPluginManager().getPlugins()) {
            names.add(plugin.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        String colored = names.stream()
                .map(name -> LIME + name + RESET)
                .collect(Collectors.joining(", "));
        sender.sendText(Lang.current.get("command.plugins.list", names.size(), colored));
    }
}
