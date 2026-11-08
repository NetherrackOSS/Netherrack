package com.netherrack.server.network;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.BlockBreaking;
import com.netherrack.server.block.BlockDrops;
import com.netherrack.server.block.BlockEntities;
import com.netherrack.server.block.BlockPlacement;
import com.netherrack.server.block.BlockShapes;
import com.netherrack.server.block.BlockUse;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.block.Face;
import com.netherrack.server.block.Placer;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.player.Permission;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerInventory;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.util.Logger;
import com.netherrack.server.util.Scheduler;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Players breaking, placing and using blocks.
 * <p>
 * All are predicted by the client: it shows the change straight away and tells the server
 * what it did. The server checks it's allowed, applies it to the world (by the rules in
 * BlockPlacement, BlockBreaking and BlockUse) and shows everyone who can see it - or, when
 * it isn't allowed, tells the client what's really there so its prediction is undone.
 * <p>
 * Breaking in survival is timed by the client (StartGame turns on server-authoritative
 * block breaking, so the client reports each stage in PlayerAuthInput). The cracks, the
 * break particles and the sounds of placing and using blocks are the server's to show
 * though - to everyone, the player included, whose client leaves them to the server in
 * this mode. A block broken in survival drops what it drops for what the player holds.
 * Placing uses the block item the player holds, one of which is used up; a bed, banner or
 * head keeps what its item says of its looks - a color, patterns - in a block entity.
 * <p>
 * In creative, the first hit breaks a block and nothing drops, and placing uses nothing up.
 * Visitors, and players in adventure or spectator mode, may not build - their clients are
 * told so, but the server checks too, since a client can't be trusted to.
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

    /** Buckets that pour out a fluid, and the block each one places. */
    private static final Map<String, String> FLUID_BUCKETS = Map.of(
            "minecraft:water_bucket", "minecraft:water",
            "minecraft:lava_bucket", "minecraft:lava");

    /** Source fluids an empty bucket fills from, and the bucket it becomes. */
    private static final Map<String, String> FILLED_BUCKETS = Map.of(
            "minecraft:water", "minecraft:water_bucket",
            "minecraft:lava", "minecraft:lava_bucket");

    /** Half a player's width, and their height standing - the box a solid placed block mustn't overlap. */
    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;

    private final World world;
    private final PlayerManager players;
    private final ItemEntities itemEntities;
    private final Scheduler scheduler;

    /** The block this player is cracking, or null if they aren't breaking anything. */
    private Vector3i breaking;

    public BlockInteraction(World world, PlayerManager players, ItemEntities itemEntities, Scheduler scheduler) {
        this.world = world;
        this.players = players;
        this.itemEntities = itemEntities;
        this.scheduler = scheduler;
    }

    /** The block actions from one PlayerAuthInput: starting, stopping and finishing breaks. */
    public void handleActions(Player player, List<PlayerBlockActionData> actions) {
        for (PlayerBlockActionData action : actions) {
            Vector3i position = action.getBlockPosition();
            switch (action.getAction()) {
                case START_BREAK -> startBreaking(player, position);
                // Sent whenever the player, still holding the button, looks onto another
                // block - and again for the same block alongside BLOCK_PREDICT_DESTROY when
                // a break finishes. Only a different block starts new cracks.
                case BLOCK_CONTINUE_DESTROY -> {
                    if (!position.equals(breaking)) {
                        startBreaking(player, position);
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

    /**
     * A right click on a block with the held item: uses the block if it's one that's used
     * (a door, a lever...), unless the player sneaks; otherwise places the held block
     * against the clicked face. Returns false if neither happened because the player holds
     * an item that isn't a block, which might be used some other way, like armor put on.
     * <p>
     * The client reports clicks it found do nothing as well - while the button's held down,
     * every frame - having changed nothing for them, and nothing happens for them here
     * either. Otherwise a block it found wouldn't fit, like one at the player's feet, would
     * be placed anyway, and a carved pumpkin also worn, as the client falls back to that.
     */
    public boolean handleItemUse(Player player, InventoryTransactionPacket packet) {
        Vector3i clicked = packet.getBlockPosition();
        Face face = Face.of(packet.getBlockFace());
        int slot = packet.getHotbarSlot();
        if (face == null || slot < 0 || slot >= PlayerInventory.HOTBAR_SIZE
                || packet.getClientInteractPrediction() == ItemUseTransaction.PredictedResult.FAILURE) {
            return true;
        }
        Vector3i target = face.from(clicked);
        Block clickedBlock = world.getBlock(clicked.getX(), clicked.getY(), clicked.getZ());

        // Adventure mode can't build, but can still use doors and switches; visitors and spectators can't.
        boolean mayUse = !player.isSpectator() && player.getPermission() != Permission.VISITOR;
        if (!player.isSneaking() && mayUse && BlockUse.isUsable(clickedBlock) && withinReach(player, clicked)) {
            BlockUse.Used used = BlockUse.use(world, clicked, Face.facing(player.getRotation().getY()));
            if (used != null) {
                showUse(used);
                // The client may have predicted placing its block, which didn't happen.
                undo(player, slot, target);
                return true;
            }
        }
        if (!player.isSneaking() && BlockUse.opensOnClick(clickedBlock)) {
            undo(player, slot, target, clicked);
            return true; // what clicking it opens isn't done yet, but it never places anything
        }

        ItemData held = player.getInventory().get(slot);
        String fluid = held.isNull() || held.getDefinition() == null ? null
                : FLUID_BUCKETS.get(held.getDefinition().getIdentifier());
        if (fluid != null) {
            placeFluid(player, packet, slot, held, fluid, clicked, face);
            return true;
        }
        if (!held.isNull() && isBucket(held) && fillBucket(player, packet, slot, held, clicked)) {
            return true;
        }
        if (!held.isNull() && VanillaData.get().placedBlock(held) == null) {
            return false;
        }
        String refusal = placementRefusal(player, packet, held, clicked);
        List<BlockPlacement.Placed> placed = List.of();
        Placer placer = Placer.of(player.getPosition(), player.getRotation().getY(), player.isSneaking());
        if (refusal == null) {
            float clickY = packet.getClickPosition() != null ? packet.getClickPosition().getY() : 0;
            placed = BlockPlacement.plan(world, Blocks.of(VanillaData.get().placedBlock(held)), clicked, face, clickY,
                    placer);
            if (placed.isEmpty()) {
                refusal = "there's no room, or nothing to hold the block";
            } else if (blockedByPlayer(placed)) {
                refusal = "a player is in the way";
                placed = List.of();
            }
        }
        if (refusal != null) {
            Logger.debug(player.getUsername() + " could not place a block against " + clicked + ": " + refusal);
            undo(player, slot, target, clicked);
            return true;
        }

        List<Vector3i> changed = new ArrayList<>();
        List<Vector3i> withEntities = new ArrayList<>();
        for (BlockPlacement.Placed block : placed) {
            Vector3i at = block.position();
            world.setBlock(at.getX(), at.getY(), at.getZ(), block.block());
            NbtMap entity = BlockEntities.forPlaced(block.block(), held, placer);
            if (entity != null) {
                world.setBlockEntity(at, entity);
                withEntities.add(at);
            }
            changed.add(at);
        }
        BlockBreaking.Changes settled = BlockBreaking.settle(world, changed);
        changed.addAll(settled.reshaped());
        for (BlockBreaking.Broken broken : settled.broken()) {
            changed.add(broken.position());
        }
        for (Vector3i at : changed) {
            players.broadcastBlock(at, blockAt(at));
        }
        for (Vector3i at : withEntities) {
            players.broadcastBlockEntity(at, world.getBlockEntity(at));
        }
        showBroken(settled.broken(), null, player.isCreative(), null);
        showSounds(settled.sounds());
        updateLater(settled.later());
        BlockPlacement.Placed first = placed.get(0);
        players.broadcastSound(SoundEvent.PLACE, center(first.position()), first.block());
        if (!player.isCreative()) {
            sendSlot(player, slot, player.getInventory().useOne(slot));
        }
        // The client may have predicted its block somewhere else - beside grass the server
        // replaced, say - so whatever it put there that didn't happen is put back.
        for (Vector3i predicted : List.of(target, clicked)) {
            if (!changed.contains(predicted)) {
                player.getSession().sendPacket(PlayerManager.blockUpdate(predicted, blockAt(predicted)));
            }
        }
        return true;
    }

    /**
     * Pours a bucket of water or lava where a block placed against the clicked face would
     * go: into the clicked block if that's something a block can replace, or else into the
     * space beyond the face. The bucket becomes empty, except in creative.
     * <p>
     * The fluid is a source block, as vanilla's bucket places, so it's kept in the chunk
     * like any other block instead of being only what the client predicted.
     */
    private void placeFluid(Player player, InventoryTransactionPacket packet, int slot, ItemData held,
                            String fluid, Vector3i clicked, Face face) {
        String refusal = placementRefusal(player, packet, held, clicked);
        List<BlockPlacement.Placed> placed = List.of();
        if (refusal == null) {
            Block source = Blocks.of(VanillaData.get().defaultBlockState(fluid));
            float clickY = packet.getClickPosition() != null ? packet.getClickPosition().getY() : 0;
            Placer placer = Placer.of(player.getPosition(), player.getRotation().getY(), player.isSneaking());
            placed = BlockPlacement.plan(world, source, clicked, face, clickY, placer);
            if (placed.isEmpty()) {
                refusal = "the fluid doesn't fit there";
            }
        }
        if (refusal != null) {
            Logger.debug(player.getUsername() + " could not pour out " + fluid + " at " + clicked + ": " + refusal);
            undo(player, slot, face.from(clicked), clicked);
            return;
        }

        List<Vector3i> changed = new ArrayList<>();
        for (BlockPlacement.Placed block : placed) {
            Vector3i at = block.position();
            world.setBlock(at.getX(), at.getY(), at.getZ(), block.block());
            changed.add(at);
        }
        BlockBreaking.Changes settled = BlockBreaking.settle(world, changed);
        changed.addAll(settled.reshaped());
        for (BlockBreaking.Broken broken : settled.broken()) {
            changed.add(broken.position());
        }
        for (Vector3i at : changed) {
            players.broadcastBlock(at, blockAt(at));
        }
        showBroken(settled.broken(), null, player.isCreative(), null);
        showSounds(settled.sounds());
        updateLater(settled.later());

        BlockPlacement.Placed first = placed.get(0);
        SoundEvent sound = "minecraft:lava".equals(fluid)
                ? SoundEvent.BUCKET_EMPTY_LAVA : SoundEvent.BUCKET_EMPTY_WATER;
        players.broadcastSound(sound, center(first.position()), first.block());
        if (!player.isCreative()) {
            ItemData emptyBucket = VanillaData.get().item("minecraft:bucket", 1);
            sendSlot(player, slot, player.getInventory().useOne(slot, emptyBucket));
        }
        // The client may have predicted its fluid somewhere else - or none, where the fluid
        // went into a replaceable clicked block - so whatever didn't happen is put back.
        for (Vector3i predicted : List.of(face.from(clicked), clicked)) {
            if (!changed.contains(predicted)) {
                player.getSession().sendPacket(PlayerManager.blockUpdate(predicted, blockAt(predicted)));
            }
        }
    }

    /** Whether an item is an empty bucket. */
    private static boolean isBucket(ItemData item) {
        return item.getDefinition() != null && "minecraft:bucket".equals(item.getDefinition().getIdentifier());
    }

    /**
     * Fills an empty bucket from a water or lava source the player clicked: the source
     * block goes away and the bucket becomes the filled one. Returns false when the clicked
     * block isn't a source to fill from, leaving the click to whatever else the item does.
     */
    private boolean fillBucket(Player player, InventoryTransactionPacket packet, int slot, ItemData held,
                               Vector3i clicked) {
        Block clickedBlock = world.getBlock(clicked.getX(), clicked.getY(), clicked.getZ());
        String filled = clickedBlock == null ? null : FILLED_BUCKETS.get(clickedBlock.getIdentifier());
        if (filled == null || !isSource(clickedBlock)) {
            return false;
        }
        boolean allowed = !player.isSpectator() && player.getPermission() != Permission.VISITOR
                && packet.getItemInHand() != null && packet.getItemInHand().getDefinition() != null
                && packet.getItemInHand().getDefinition().getRuntimeId() == held.getDefinition().getRuntimeId()
                && withinReach(player, clicked);
        if (!allowed) {
            Logger.debug(player.getUsername() + " could not fill a bucket from " + clicked);
            undo(player, slot, clicked);
            return true;
        }

        world.setBlock(clicked.getX(), clicked.getY(), clicked.getZ(), Blocks.AIR);
        players.broadcastBlock(clicked, Blocks.AIR);
        SoundEvent sound = filled.equals("minecraft:water_bucket")
                ? SoundEvent.BUCKET_FILL_WATER : SoundEvent.BUCKET_FILL_LAVA;
        players.broadcastSound(sound, center(clicked), clickedBlock);
        sendSlot(player, slot, player.getInventory().useOne(slot, VanillaData.get().item(filled, 1)));
        return true;
    }

    /** Whether a block is a source of water or lava, rather than fluid that's still flowing. */
    private static boolean isSource(Block block) {
        return !(block.getProperty("liquid_depth") instanceof Number depth) || depth.intValue() == 0;
    }

    /** A creative player's instant break, which their client reports as its own action. */
    public void creativeBreak(Player player, Vector3i position) {        if (player.isCreative()) {
            breakBlock(player, position);
        }
    }

    /** Why the placement can't happen, or null if nothing stops it yet. */
    private String placementRefusal(Player player, InventoryTransactionPacket packet, ItemData held, Vector3i clicked) {
        if (!player.mayBuild()) {
            return "they may not build";
        }
        if (held.isNull() || packet.getItemInHand() == null || packet.getItemInHand().getDefinition() == null
                || held.getDefinition().getRuntimeId() != packet.getItemInHand().getDefinition().getRuntimeId()) {
            return "the client holds something else";
        }
        if (!inWorld(clicked) || !withinReach(player, clicked)) {
            return "out of reach";
        }
        return null;
    }

    /** Whether a block about to be placed would be inside a player who isn't spectating, where they'd bump into it. */
    private boolean blockedByPlayer(List<BlockPlacement.Placed> placed) {
        for (BlockPlacement.Placed block : placed) {
            BlockShapes.Box box = BlockShapes.collisionBox(block.block());
            if (box == null) {
                continue;
            }
            for (Player other : players.getPlayers()) {
                if (!other.isSpectator() && insidePlayer(other, block.position(), box)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startBreaking(Player player, Vector3i position) {
        stopBreaking(); // moving on from another block leaves no cracks behind on it
        if (!player.mayBuild()) {
            return;
        }
        Block block = world.getBlock(position.getX(), position.getY(), position.getZ());
        if (block == null || !block.isBreakable()) {
            return;
        }
        breaking = position;
        // The data is how far the cracks advance each tick, out of 65535 for a full break.
        int ticks = Math.max(1, block.getMining().breakTicks(heldName(player)));
        players.broadcastLevelEvent(LevelEvent.BLOCK_START_BREAK, center(position), 65535 / ticks);
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
        if (block == null) {
            // Air is what a break leaves, so this is a break the client has already reported another way.
            return;
        }
        String refusal = null;
        if (!player.mayBuild()) {
            refusal = "they may not build";
        } else if (!inWorld(position) || !withinReach(player, position)) {
            refusal = "out of reach";
        } else if (!block.isBreakable() && !player.isCreative()) {
            refusal = block.getIdentifier() + " only breaks in creative";
        }
        if (refusal != null) {
            Logger.debug(player.getUsername() + " could not break the block at " + position + ": " + refusal);
            stopBreaking();
            player.getSession().sendPacket(PlayerManager.blockUpdate(position, block));
            return;
        }

        stopBreaking();
        BlockBreaking.Changes changes = BlockBreaking.breakBlock(world, position);
        if (player.getSurvival() != null && !player.isCreative()) {
            player.getSurvival().exhaust(0.005); // mining a block, as in vanilla
        }
        for (BlockBreaking.Broken broken : changes.broken()) {
            players.broadcastBlock(broken.position(), Blocks.AIR);
        }
        for (Vector3i reshaped : changes.reshaped()) {
            players.broadcastBlock(reshaped, blockAt(reshaped));
        }
        showBroken(changes.broken(), position, player.isCreative(), heldName(player));
        showSounds(changes.sounds());
        updateLater(changes.later());
    }

    /**
     * A tick from now, makes the changes blocks wait a tick for - scaffolding breaking or
     * taking its new distance - and shows them, then goes on to any they lead to. What breaks
     * then drops whatever it drops, as nobody broke it.
     */
    private void updateLater(List<Vector3i> later) {
        if (later.isEmpty()) {
            return;
        }
        scheduler.schedule(1, () -> {
            BlockBreaking.Changes changes = BlockBreaking.updateLater(world, later);
            for (BlockBreaking.Broken broken : changes.broken()) {
                players.broadcastBlock(broken.position(), Blocks.AIR);
            }
            for (Vector3i reshaped : changes.reshaped()) {
                players.broadcastBlock(reshaped, blockAt(reshaped));
            }
            showBroken(changes.broken(), null, false, null);
            showSounds(changes.sounds());
            updateLater(changes.later());
        });
    }

    /** Plays the sounds blocks made as they changed shape, like a tripwire hook clicking. */
    private void showSounds(List<BlockBreaking.Sound> sounds) {
        for (BlockBreaking.Sound sound : sounds) {
            players.broadcastSound(sound.event(), center(sound.position()), sound.block());
        }
    }

    /**
     * The break particles and sound of each block that broke, and outside creative, what it
     * drops: the block broken by hand drops for what the player holds; blocks that went with
     * it, like a torch that was on it, drop whatever they drop.
     */
    private void showBroken(List<BlockBreaking.Broken> broken, Vector3i brokenByHand, boolean creative, String held) {
        for (BlockBreaking.Broken block : broken) {
            players.broadcastLevelEvent(LevelEvent.PARTICLE_DESTROY_BLOCK, center(block.position()),
                    block.block().getBlockStateHash());
            if (creative) {
                continue;
            }
            boolean byHand = block.position().equals(brokenByHand);
            for (ItemData drop : BlockDrops.of(block.block(), block.blockEntity(), byHand ? held : null, byHand)) {
                itemEntities.dropFromBlock(block.position(), drop);
            }
        }
    }

    /** Shows everyone a block being used, and lets a pressed button pop back out in time. */
    private void showUse(BlockUse.Used used) {
        for (Vector3i at : used.changed()) {
            players.broadcastBlock(at, blockAt(at));
        }
        if (used.sound() != null && !used.changed().isEmpty()) {
            Vector3i at = used.changed().get(0);
            players.broadcastSound(used.sound(), center(at), blockAt(at));
        }
        if (used.releaseIn() != null && !used.changed().isEmpty()) {
            Vector3i button = used.changed().get(0);
            scheduler.schedule(used.releaseIn(), () -> {
                if (BlockUse.releaseButton(world, button)) {
                    players.broadcastBlock(button, blockAt(button));
                    players.broadcastSound(SoundEvent.BUTTON_CLICK_OFF, center(button), blockAt(button));
                }
            });
        }
    }

    /** Puts back what the client predicted: the blocks it may have changed, and the item it may have used. */
    private void undo(Player player, int slot, Vector3i... positions) {
        for (Vector3i position : positions) {
            player.getSession().sendPacket(PlayerManager.blockUpdate(position, blockAt(position)));
        }
        sendSlot(player, slot, player.getInventory().get(slot));
    }

    private void sendSlot(Player player, int slot, ItemData item) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(ContainerId.INVENTORY);
        packet.setSlot(slot);
        packet.setItem(item);
        player.getSession().sendPacket(packet);
    }

    /** The name of the item in the player's hand, or null if it's empty. */
    private static String heldName(Player player) {
        ItemData held = player.getInventory().get(player.getSelectedSlot());
        return held.isNull() || held.getDefinition() == null ? null : held.getDefinition().getIdentifier();
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

    /** Whether a player overlaps the part of a block's space a box takes up. */
    private static boolean insidePlayer(Player player, Vector3i block, BlockShapes.Box box) {
        Vector3f feet = player.getFeetPosition();
        return feet.getX() + PLAYER_HALF_WIDTH > block.getX() + box.minX()
                && feet.getX() - PLAYER_HALF_WIDTH < block.getX() + box.maxX()
                && feet.getY() + PLAYER_HEIGHT > block.getY() + box.minY() && feet.getY() < block.getY() + box.maxY()
                && feet.getZ() + PLAYER_HALF_WIDTH > block.getZ() + box.minZ()
                && feet.getZ() - PLAYER_HALF_WIDTH < block.getZ() + box.maxZ();
    }

    private static Vector3f center(Vector3i position) {
        return Vector3f.from(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }
}
