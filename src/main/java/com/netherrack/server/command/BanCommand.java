package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.network.SessionDisconnect;
import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;

import java.util.Arrays;
import java.util.List;

/**
 * ban &lt;player&gt; [reason...] - adds a persistent ban like Cloudburst Nukkit's BanCommand.
 * Offline players can be banned by name; banning one who is online also saves their XUID and
 * UUID and disconnects them immediately with the same message later joins receive. As in Nukkit,
 * {@code @s} targets the sender. The reason may contain spaces, and is stored in
 * banned-players.json beside the name and source.
 */
public class BanCommand implements Command {

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String getDescription() {
        return Lang.current.get("command.description.ban");
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(
                List.of(CommandParameter.string("player", false, "player")),
                List.of(CommandParameter.string("player", false, "player"),
                        CommandParameter.string("reason", true, "message")));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendFailure("command.ban.usage");
            return;
        }

        // Nukkit accepts @s as the sender, which matters when banning oneself from chat.
        String target = args[0].equals("@s") && sender.getPlayer() != null
                ? sender.getPlayer().getUsername()
                : args[0];
        Player online = server.getPlayerManager().getPlayer(target);
        String name = online != null ? online.getUsername() : target;
        String xuid = online != null ? online.getXuid() : "";
        String uuid = online != null ? online.getUuid().toString() : "";
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim()
                : "";

        server.getBans().add(name, xuid, uuid, reason, sender.getName());
        if (online != null) {
            SessionDisconnect.disconnect(online.getSession(), server.getBanMessage(reason));
        }

        if (reason.isEmpty()) {
            Command.broadcastCommandMessage(server, sender, "command.ban.success", name);
        } else {
            Command.broadcastCommandMessage(server, sender, "command.ban.success.reason", name, reason);
        }
    }
}
