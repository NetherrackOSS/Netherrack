package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.network.VanillaData;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerInventory;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.List;
import java.util.Locale;

/**
 * give &lt;player&gt; &lt;item&gt; [amount] - gives a player an item, as vanilla's command does.
 * The item may be written with or without "minecraft:"; what doesn't fit in their inventory
 * is thrown out at their feet.
 */
public class GiveCommand implements Command {

    /** As many items as a full inventory holds, so a typo can't ask for millions. */
    private static final int MAX_AMOUNT = PlayerInventory.SIZE * 64;

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(
                CommandParameter.player("player", false),
                CommandParameter.item("item", false),
                CommandParameter.integer("amount", true)));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendFailure("commands.generic.usage", "/give <player> <item> [amount]");
            return;
        }

        Player target = args[0].equalsIgnoreCase("@s")
                ? sender.getPlayer()
                : server.getPlayerManager().getPlayer(args[0]);
        if (target == null) {
            sender.sendFailure("commands.generic.player.notFound");
            return;
        }

        String identifier = args[1].toLowerCase(Locale.ROOT);
        if (!identifier.contains(":")) {
            identifier = "minecraft:" + identifier;
        }
        if (VanillaData.get().getItemRegistry().getDefinition(identifier) == null) {
            sender.sendFailure("command.give.item_not_found", args[1]);
            return;
        }

        int amount = 1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                amount = 0;
            }
            if (amount < 1) {
                sender.sendFailure("commands.generic.usage", "/give <player> <item> [amount]");
                return;
            }
            if (amount > MAX_AMOUNT) {
                sender.sendFailure("command.give.too_many", String.valueOf(MAX_AMOUNT));
                return;
            }
        }

        ItemData item = VanillaData.get().item(identifier, amount);
        int left = target.getInventory().add(item);
        // Whatever doesn't fit is thrown out at the player's feet, a stack at a time.
        while (left > 0 && server.getItemEntities() != null) {
            int stack = Math.min(left, VanillaData.get().maxStackSize(item));
            server.getItemEntities().throwFrom(target, VanillaData.get().item(identifier, stack));
            left -= stack;
        }
        for (BedrockPacket packet : target.getInventory().contentPackets()) {
            target.getSession().sendPacket(packet);
        }

        Command.broadcastCommandMessage(server, sender, "commands.give.success",
                displayName(identifier), String.valueOf(amount), target.getUsername());
    }

    /** What the item looks like in a message, e.g. "diamond_sword" as "diamond sword". */
    private static String displayName(String identifier) {
        String name = identifier.substring(identifier.indexOf(':') + 1);
        return name.replace('_', ' ');
    }
}
