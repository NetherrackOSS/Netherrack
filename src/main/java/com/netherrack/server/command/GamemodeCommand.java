package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.GameModes;
import com.netherrack.server.player.Player;
import org.cloudburstmc.protocol.bedrock.data.GameType;

import java.util.List;

/**
 * gamemode &lt;survival|creative|adventure|spectator|default&gt; [player] - switches a player's game
 * mode. "default" (or "d") is the server's, from server.properties. A player running it
 * without a name switches their own; the console has to name someone.
 */
public class GamemodeCommand implements Command {

    @Override
    public String getName() {
        return "gamemode";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(
                CommandParameter.choice("gameMode", false, "GameMode", List.of("survival", "creative", "adventure", "spectator", "default")),
                CommandParameter.player("player", true)));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2 || (args.length == 1 && sender.getPlayer() == null)) {
            sender.sendFailure("commands.generic.usage", "/gamemode <survival|creative|adventure|spectator|default> [player]");
            return;
        }
        boolean serverDefault = args[0].equalsIgnoreCase("default") || args[0].equalsIgnoreCase("d");
        GameType gameMode = serverDefault ? server.getDefaultGameMode() : GameModes.parse(args[0]);
        if (gameMode == null) {
            sender.sendFailure("commands.gamemode.fail.invalid", args[0]);
            return;
        }
        Player player = args.length == 2 ? server.getPlayerManager().getPlayer(args[1]) : sender.getPlayer();
        if (player == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }

        server.getPlayerManager().changeGameMode(player, gameMode);
        String modeName = "%createWorldScreen.gameMode." + GameModes.name(gameMode);
        if (player == sender.getPlayer()) {
            sender.sendSuccess("commands.gamemode.success.self", modeName);
        } else {
            sender.sendSuccess("commands.gamemode.success.other", modeName, player.getUsername());
            player.sendTranslation("gameMode.changed", modeName);
        }
    }
}
