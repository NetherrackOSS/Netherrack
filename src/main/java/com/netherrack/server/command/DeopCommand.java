package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Player;

import java.util.List;

/**
 * deop &lt;player&gt; - takes away an online player's operator status, removing their
 * permissions.json entry so they get default-player-permission-level.
 */
public class DeopCommand implements Command {

    @Override
    public String getName() {
        return "deop";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(CommandParameter.player("player", false)));
    }

    @Override
    public int getPermissionLevel() {
        return 3;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendFailure("commands.generic.usage", "/deop <player>");
            return;
        }
        Player player = server.getPlayerManager().getPlayer(args[0]);
        if (player == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }
        if (!server.getPermissions().deop(player.getXuid())) {
            sender.sendFailure("command.deop.not_op", player.getUsername());
            return;
        }
        server.applyPermission(player, server.getPermissions().getDefault());
        sender.sendSuccess("commands.deop.success", player.getUsername());
        player.sendTranslation("commands.deop.message");
    }
}
