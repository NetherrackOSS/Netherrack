package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Permission;
import com.netherrack.server.player.Player;

import java.util.List;

/**
 * op &lt;player&gt; - makes an online player an operator, saving it to permissions.json under
 * their XUID. Players who aren't signed into Xbox Live have no XUID to save it under.
 */
public class OpCommand implements Command {

    @Override
    public String getName() {
        return "op";
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
            sender.sendFailure("commands.generic.usage", "/op <player>");
            return;
        }
        Player player = server.getPlayerManager().getPlayer(args[0]);
        if (player == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }
        if (player.getXuid().isEmpty()) {
            sender.sendFailure("command.op.no_xuid", player.getUsername());
            return;
        }
        if (!server.getPermissions().op(player.getXuid())) {
            sender.sendFailure("commands.op.failed", player.getUsername());
            return;
        }
        server.applyPermission(player, Permission.OPERATOR);
        sender.sendSuccess("commands.op.success", player.getUsername());
        player.sendTranslation("commands.op.message");
    }
}
