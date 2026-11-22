package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * whitelist &lt;on|off|list|reload&gt; and whitelist &lt;add|remove&gt; &lt;player&gt; - controls
 * who may join while white-list is on. Names can be added while the player is offline; adding
 * an online player also saves their XUID and UUID so the entry survives a gamertag change.
 * Operators are allowed in either case, as they are in Cloudburst Nukkit.
 */
public class WhitelistCommand implements Command {

    @Override
    public String getName() {
        return "whitelist";
    }

    @Override
    public List<String> getAliases() {
        return List.of("wl");
    }

    @Override
    public String getDescription() {
        return Lang.current.get("command.description.whitelist");
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(
                List.of(CommandParameter.choice("action", false, "whitelistAction",
                        List.of("on", "off", "list", "reload"))),
                List.of(CommandParameter.choice("action", false, "whitelistPlayerAction",
                                List.of("add", "remove")),
                        CommandParameter.string("player", false, "player")));
    }

    @Override
    public int getPermissionLevel() {
        return 3;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length == 0 || args.length > 2) {
            sender.sendFailure("command.whitelist.usage");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "on":
                if (args.length != 1) {
                    usage(sender);
                    return;
                }
                server.setWhitelistEnabled(true);
                sender.sendSuccess("command.whitelist.enabled");
                return;
            case "off":
                if (args.length != 1) {
                    usage(sender);
                    return;
                }
                server.setWhitelistEnabled(false);
                sender.sendSuccess("command.whitelist.disabled");
                return;
            case "list":
                if (args.length != 1) {
                    usage(sender);
                    return;
                }
                list(server, sender);
                return;
            case "reload":
                if (args.length != 1) {
                    usage(sender);
                    return;
                }
                server.reloadWhitelist();
                sender.sendSuccess("command.whitelist.reloaded");
                return;
            case "add":
            case "remove":
                if (args.length != 2) {
                    sender.sendFailure("command.whitelist.need_player");
                    return;
                }
                change(server, sender, action, args[1]);
                return;
            default:
                usage(sender);
        }
    }

    private static void list(Netherrack server, CommandSender sender) {
        List<Map<String, Object>> entries = server.getWhitelist().entries();
        if (entries.isEmpty()) {
            sender.sendSuccess("command.whitelist.empty");
            return;
        }
        sender.sendSuccess("command.whitelist.list", String.valueOf(entries.size()));
        sender.sendText(entries.stream().map(WhitelistCommand::label)
                .collect(Collectors.joining(", ")));
    }

    private static void change(Netherrack server, CommandSender sender, String action,
                               String argument) {
        Player online = server.getPlayerManager().getPlayer(argument);
        String name = online != null ? online.getUsername() : argument;
        String xuid = online != null ? online.getXuid() : "";
        String uuid = online != null ? online.getUuid().toString() : "";

        boolean changed = action.equals("add")
                ? server.getWhitelist().add(name, xuid, uuid)
                : server.getWhitelist().remove(name, xuid, uuid);
        if (action.equals("add")) {
            String key = changed ? "command.whitelist.added" : "command.whitelist.already_added";
            sender.sendSuccess(key, name);
        } else {
            String key = changed ? "command.whitelist.removed" : "command.whitelist.not_added";
            sender.sendSuccess(key, name);
        }
    }

    private static void usage(CommandSender sender) {
        sender.sendFailure("command.whitelist.usage");
    }

    private static String label(Map<String, Object> entry) {
        String name = text(entry.get("name"));
        if (!name.isEmpty()) {
            return name;
        }
        String xuid = text(entry.get("xuid"));
        if (!xuid.isEmpty()) {
            return "XUID " + xuid;
        }
        return "UUID " + text(entry.get("uuid"));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
