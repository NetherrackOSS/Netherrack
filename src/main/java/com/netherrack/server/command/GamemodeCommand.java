package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.GameModes;
import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.protocol.bedrock.data.GameType;

/**
 * gamemode &lt;survival|creative|adventure&gt; &lt;player&gt; - switches a player's game mode.
 * The player is named rather than assumed, since commands run as the console even when
 * typed in chat.
 */
public class GamemodeCommand implements Command {

    @Override
    public String getName() {
        return "gamemode";
    }

    @Override
    public String getHelpLangKey() {
        return "command.help.gamemode";
    }

    @Override
    public void execute(Netherrack server, String[] args) {
        if (args.length != 2) {
            Logger.info(Lang.current.get("command.gamemode.usage"));
            return;
        }
        GameType gameMode = GameModes.parse(args[0]);
        if (gameMode == null) {
            Logger.info(Lang.current.get("command.gamemode.unknown_mode", args[0]));
            return;
        }
        Player player = server.getPlayerManager().getPlayer(args[1]);
        if (player == null) {
            Logger.info(Lang.current.get("command.gamemode.unknown_player", args[1]));
            return;
        }
        player.setGameMode(gameMode);
        Logger.info(Lang.current.get("command.gamemode.changed", player.getUsername(), GameModes.name(gameMode)));
    }
}
