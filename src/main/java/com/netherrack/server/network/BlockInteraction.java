package com.netherrack.server.network;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerInventory;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;

import java.util.List;

/**
 * Players breaking and placing blocks.
 * <p>
 * Both are predicted by the client: it shows the change straight away and tells the
 * server what it did. The server checks it's allowed, applies it to the world and shows
 * everyone else - or, when it isn't allowed, tells the client what's really there so its
 * prediction is undone.
 * <p>
 * Breaking in survival is timed by the client (StartGame turns on server-authoritative
 * block breaking, so the client reports each stage in PlayerAuthInput). The cracks and
 * the break particles are the server's to show though - to everyone, the breaking player
 * included, whose client draws neither itself in this mode. Placing uses the block item
 * the player holds, one of which is used up.
 * <p>
 * In creative, the first hit breaks a block and nothing drops, and placing uses nothing up.
 * <p>
 * There's one of these per player, since it keeps track of the block they're breaking.
 */
public class BlockInteraction {

    /**
     * Farthest a player may reach a block from, eyes to block center. Survival reach is
     * about 4.5 blocks; the rest is slack for lag between their movement and the action.
     */
    private static final double MAX_REACH = 8;

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;

    /** Clicked block face to the direction a placed block goes from it: down, up, north, south, west, east. */
    private static final Vector3i[] FACE_OFFSETS = {
            Vector3i.from(0, -1, 0), Vector3i.from(0, 1, 0),
            Vector3i.from(0, 0, -1), Vector3i.from(0, 0, 1),
            Vector3i.from(-1, 0, 0), Vector3i.from(1, 0, 0)
    };

    /** Half a player's width, and their height standing - the box a placed block mustn't overlap. */
    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;

    private final World world;
    private final PlayerManager players;
    private final ItemEntities itemEntities;

    /** The block this player is cracking, or null if they aren't breaking anything. */
    private Vector3i breaking;

    public BlockInteraction(World world, PlayerManager players, ItemEntities itemEntities) {
        this.world = world;
        this.players = players;
        this.itemEntities = itemEntities;
    }

    /** The block actions from one PlayerAuthInput: starting, stopping and finishing breaks. */
    public void handleActions(Player player, List<PlayerBlockActionData> actions) {
        for (PlayerBlockActionData action : actions) {
            Vector3i position = action.getBlockPosition();
            switch (action.getAction()) {
                case START_BREAK -> startBreaking(position);
                // Sent whenever the player, still holding the button, looks onto another
                // block - and again for the same block alongside BLOCK_PREDICT_DESTROY when
                // a break finishes. Only a different block starts new cracks.
                case BLOCK_CONTINUE_DESTROY -> {
                    if (!position.equals(breaking)) {
                        startBreaking(position);
                    }
                }
                case ABORT_BREAK, STOP_BREAK -> stopBreaking();
                case BLOCK_PREDICT_DESTROY -> breakBlock(player, position);
                case DIMENSION_CHANGE_REQUEST_OR_CREATIVE_DESTROY_BLOCK -> creativeBreak(player, position);
                default -> {
                    // Not a block action, or not one Netherrack handles yet.
                }
            }
        }
    }

    /** A right click on a block with the held item: places it against the clicked face. */
    public void handleItemUse(Player player, InventoryTransactionPacket packet) {
        Vector3i clicked = packet.getBlockPosition();
        int face = packet.getBlockFace();
        int slot = packet.getHotbarSlot();
        if (face < 0 || face >= FACE_OFFSETS.length || slot < 0 || slot >= PlayerInventory.HOTBAR_SIZE) {
            return;
        }
        Vector3i target = clicked.add(FACE_OFFSETS[face]);

        String refusal = placementRefusal(player, packet, clicked, target);
        if (refusal != null) {
            Logger.debug(player.getUsername() + " could not place a block at " + target + ": " + refusal);
            // Undo whatever the client predicted, block and item count both.
            player.getSession().sendPacket(PlayerManager.blockUpdate(target, blockAt(target)));
            player.getSession().sendPacket(PlayerManager.blockUpdate(clicked, blockAt(clicked)));
            sendSlot(player, slot, player.getInventory().get(slot));
            return;
        }

        // The block state the held item carries: e.g. which colour of wool, or a slab's half.
        ItemData held = player.getInventory().get(slot);
        Block block = Blocks.of(VanillaData.get().placedBlock(held), held.getBlockDefinition().getRuntimeId());
        world.setBlock(target.getX(), target.getY(), target.getZ(), block);
        players.broadcastBlock(target, block);
        players.broadcastPlaceSound(player, center(target), block);
        if (!player.isCreative()) {
            sendSlot(player, slot, player.getInventory().useOne(slot));
        }
    }

    /** A creative player's instant break, which their client reports as its own action. */
    public void creativeBreak(Player player, Vector3i position) {
        if (player.isCreative()) {
            breakBlock(player, position);
        }
    }

    /** Why the placement can't happen, or null if it can. */
    private String placementRefusal(Player player, InventoryTransactionPacket packet, Vector3i clicked, Vector3i target) {
        ItemData held = player.getInventory().get(packet.getHotbarSlot());
        if (held.isNull() || packet.getItemInHand() == null || packet.getItemInHand().getDefinition() == null
                || held.getDefinition().getRuntimeId() != packet.getItemInHand().getDefinition().getRuntimeId()) {
            return "the client holds something else";
        }
        if (VanillaData.get().placedBlock(held) == null || held.getBlockDefinition() == null) {
            return "the held item doesn't place a block";
        }
        if (!inWorld(target) || !withinReach(player, target)) {
            return "out of reach";
        }
        if (world.getBlock(clicked.getX(), clicked.getY(), clicked.getZ()) == null) {
            return "the clicked block is air";
        }
        if (world.getBlock(target.getX(), target.getY(), target.getZ()) != null) {
            return "something is already there";
        }
        for (Player other : players.getPlayers()) {
            if (insidePlayer(other, target)) {
                return other.getUsername() + " is standing there";
            }
        }
        return null;
    }

    private void startBreaking(Vector3i position) {
        stopBreaking(); // moving on from another block leaves no cracks behind on it
        Block block = world.getBlock(position.getX(), position.getY(), position.getZ());
        if (block == null) {
            return;
        }
        breaking = position;
        // The data is how far the cracks advance each tick, out of 65535 for a full break.
        int perTick = 65535 / block.getHandBreakTicks();
        players.broadcastLevelEvent(LevelEvent.BLOCK_START_BREAK, center(position), perTick);
    }

    /** Clears the cracks from the block the player was breaking, if any - when they let go, finish or leave. */
    public void stopBreaking() {
        if (breaking != null) {
            players.broadcastLevelEvent(LevelEvent.BLOCK_STOP_BREAK, center(breaking), 0);
            breaking = null;
        }
    }

    private void breakBlock(Player player, Vector3i position) {
        Block block = world.getBlock(position.getX(), position.getY(), position.getZ());
        if (block == null || !inWorld(position) || !withinReach(player, position)) {
            Logger.debug(player.getUsername() + " could not break the block at " + position);
            stopBreaking();
            player.getSession().sendPacket(PlayerManager.blockUpdate(position, blockAt(position)));
            return;
        }

        world.setBlock(position.getX(), position.getY(), position.getZ(), null);
        stopBreaking();
        players.broadcastBlock(position, Blocks.AIR);
        // The break particles and sound, telling the client which block it was.
        players.broadcastLevelEvent(LevelEvent.PARTICLE_DESTROY_BLOCK, center(position), block.getBlockStateHash());

        // Outside creative, the block's own item pops out. Vanilla's rules differ for some
        // blocks (grass drops dirt, stone-like blocks need a pickaxe), but Netherrack doesn't
        // have per-block drops or tools yet.
        if (!player.isCreative()) {
            itemEntities.dropFromBlock(position, VanillaData.get().blockItem(block, 1));
        }
    }

    private void sendSlot(Player player, int slot, ItemData item) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(ContainerId.INVENTORY);
        packet.setSlot(slot);
        packet.setItem(item);
        player.getSession().sendPacket(packet);
    }

    private Block blockAt(Vector3i position) {
        Block block = world.getBlock(position.getX(), position.getY(), position.getZ());
        return block != null ? block : Blocks.AIR;
    }

    private static boolean inWorld(Vector3i position) {
        return position.getY() >= MIN_Y && position.getY() <= MAX_Y;
    }

    private static boolean withinReach(Player player, Vector3i position) {
        return player.getPosition().distanceSquared(center(position)) <= MAX_REACH * MAX_REACH;
    }

    private static boolean insidePlayer(Player player, Vector3i block) {
        Vector3f feet = player.getFeetPosition();
        return feet.getX() + PLAYER_HALF_WIDTH > block.getX() && feet.getX() - PLAYER_HALF_WIDTH < block.getX() + 1
                && feet.getY() + PLAYER_HEIGHT > block.getY() && feet.getY() < block.getY() + 1
                && feet.getZ() + PLAYER_HALF_WIDTH > block.getZ() && feet.getZ() - PLAYER_HALF_WIDTH < block.getZ() + 1;
    }

    private static Vector3f center(Vector3i position) {
        return Vector3f.from(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }
}
