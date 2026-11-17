package com.netherrack.server.block;

import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerInventory;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;

/**
 * The crafting table: clicking one opens its screen, whose 3x3 crafting grid is the bigger
 * cousin of the inventory screen's 2x2. The recipes it can make come from vanilla's own
 * list (see {@link com.netherrack.server.crafting.Recipes}); whatever's left in the grid
 * goes back into the inventory when the screen closes, dies or the player leaves.
 */
public final class CraftingTable {

    private CraftingTable() {
    }

    /** Whether clicking this block opens the crafting table screen. */
    public static boolean opens(Block block) {
        return block != null && block.getIdentifier().equals(BlockID.CRAFTING_TABLE);
    }

    /**
     * Opens the crafting table at a position for a player, the way Nukkit does: the
     * workbench is no container of its own but the player's UI screen with a bigger grid,
     * so the window id is -1, "no container", and the block's position and the player's
     * own entity id are what place the screen. The 3x3 grid's slots are sent as well, so
     * the client starts from what the server has - normally nothing, unless a screen was
     * closed without a word last time.
     */
    public static void open(Player player, Vector3i position) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.isTableOpen()) {
            // The screens capture the clicks that would open another, so this is only a
            // client misbehaving - and answering it would stack one screen upon another.
            Logger.debug(player.getUsername() + " tried to open a crafting table with one already open");
            return;
        }
        inventory.openTable();

        ContainerOpenPacket open = new ContainerOpenPacket();
        open.setId((byte) -1);
        open.setType(ContainerType.WORKBENCH);
        open.setBlockPosition(position);
        open.setUniqueEntityId(player.getEntityId());
        player.getSession().sendPacket(open);

        for (BedrockPacket packet : inventory.tableGridPackets()) {
            player.getSession().sendPacket(packet);
        }
    }
}
