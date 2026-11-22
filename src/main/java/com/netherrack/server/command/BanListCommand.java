package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.BanEntry;
import com.netherrack.server.setup.Lang;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * banlist [ips|players|reload] - shows the players in banned-players.json, as Nukkit's
 * BanListCommand does. Nukkit's "ips" argument is accepted; Netherrack doesn't keep IP bans, so
 * it says so rather than pretending the list is empty. "reload" re-reads the file after a hand
 * edit.
 */
public class BanListCommand implements Command {

    @Override
    public String getName() {
        return "banlist";
    }

    @Override
    public String getDescription() {
        return Lang.current.get("command.description.banlist");
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(CommandParameter.choice("action", true, "banListAction",
                List.of("ips", "players", "reload"))));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.sendFailure("command.banlist.usage");
            return;
        }
        String action = args.length == 0 ? "players" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("ips")) {
            sender.sendFailure("command.banlist.no_ips");
            return;
        }
        if (!action.equals("players") && !action.equals("reload")) {
            sender.sendFailure("command.banlist.usage");
            return;
        }
        if (action.equals("reload")) {
            server.getBans().reload();
            sender.sendSuccess("command.banlist.reloaded");
            return;
        }

        List<BanEntry> bans = server.getBans().entries();
        if (bans.isEmpty()) {
            sender.sendSuccess("command.banlist.empty");
            return;
        }
        sender.sendSuccess("command.banlist.players", String.valueOf(bans.size()));
        sender.sendText(bans.stream().map(BanEntry::displayName)
                .collect(Collectors.joining(", ")));
    }
}
