package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.network.SessionDisconnect;
import com.netherrack.server.player.Player;

import java.util.Arrays;
import java.util.List;

/**
 * kick &lt;player&gt; [reason] - disconnects an online player. The optional reason is shown on
 * their disconnect screen and is also what the console's logout line reports. Command level 2 is
 * enough, so any operator whose op-permission-level is at least 2 can use it; the console can too.
 */
public class KickCommand implements Command {

    private static final String USAGE = "/kick <player> [reason]";

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(
                List.of(CommandParameter.player("player", false)),
                List.of(CommandParameter.player("player", false),
                        CommandParameter.string("reason", true, "message")));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendFailure("commands.generic.usage", USAGE);
            return;
        }

        Player target = server.getPlayerManager().getPlayer(args[0]);
        if (target == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim() : "";
        String kickMessage = reason.isEmpty()
                ? CommandSender.render("command.kick.message", sender.getName())
                : CommandSender.render("command.kick.message.reason", sender.getName(), reason);
        SessionDisconnect.disconnect(target.getSession(), kickMessage);

        if (reason.isEmpty()) {
            Command.broadcastCommandMessage(server, sender, "commands.kick.success", target.getUsername());
        } else {
            Command.broadcastCommandMessage(server, sender, "commands.kick.success.reason",
                    target.getUsername(), reason);
        }
    }
}
