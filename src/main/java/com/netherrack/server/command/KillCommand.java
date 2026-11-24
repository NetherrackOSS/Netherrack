package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerSurvival;

import java.util.List;

/**
 * kill [player] - kills a player, showing them the death screen and starting the respawn
 * handshake just as a killing blow would. Without a name a player kills themselves; the
 * console has to name someone. Works on creative players too, as vanilla's command does.
 */
public class KillCommand implements Command {

    @Override
    public String getName() {
        return "kill";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(CommandParameter.player("player", true)));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 0 && sender.getPlayer() == null)) {
            sender.sendFailure("commands.generic.usage", "/kill [player]");
            return;
        }

        Player target = args.length == 1
                ? (args[0].equalsIgnoreCase("@s") ? sender.getPlayer() : server.getPlayerManager().getPlayer(args[0]))
                : sender.getPlayer();
        if (target == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }

        PlayerSurvival survival = target.getSurvival();
        if (survival != null) {
            survival.kill(PlayerSurvival.DamageCause.GENERIC);
        }
        Command.broadcastCommandMessage(server, sender, "commands.kill.successful", target.getUsername());
    }
}
