package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.List;

/**
 * clear [player] - empties a player's inventory, including their armor, offhand and anything
 * they have on the cursor or in an open crafting grid. Without a name a player clears their own
 * inventory; the console has to name someone. Command level 2 is enough, as for the other
 * commands that manage players and their items.
 */
public class ClearCommand implements Command {

    private static final String USAGE = "/clear [player]";

    @Override
    public String getName() {
        return "clear";
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
            sender.sendFailure("commands.generic.usage", USAGE);
            return;
        }

        Player target = args.length == 1
                ? (args[0].equalsIgnoreCase("@s") ? sender.getPlayer() : server.getPlayerManager().getPlayer(args[0]))
                : sender.getPlayer();
        if (target == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }

        int removed = target.getInventory().clear();
        for (BedrockPacket packet : target.getInventory().contentPackets()) {
            target.getSession().sendPacket(packet);
        }
        if (target.getInventory().isTableOpen()) {
            for (BedrockPacket packet : target.getInventory().tableGridPackets()) {
                target.getSession().sendPacket(packet);
            }
        }
        // Other players see the target's hand, offhand and armor too, so refresh all three.
        server.getPlayerManager().updateEquipment();
        server.getPlayerManager().broadcastArmor(target);

        if (removed == 0) {
            sender.sendFailure("commands.clear.failure.no.items", target.getUsername());
        } else {
            Command.broadcastCommandMessage(server, sender, "commands.clear.success",
                    target.getUsername(), String.valueOf(removed));
        }
    }
}
