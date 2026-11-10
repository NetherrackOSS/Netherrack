package com.netherrack.server.entity;

import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.TakeItemEntityPacket;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every item lying in the world. The server tick moves them, lets players pick them up
 * and removes them after 5 minutes, as vanilla does.
 * <p>
 * Each player is shown the items in the chunks their client has, as vanilla does: items
 * appear as they come into a player's view and go as they leave it, and their movement
 * only goes to those who can see them. Items are spawned from players' network threads
 * and ticked on the server thread, so everything here is synchronized.
 */
public class ItemEntities {

    /** Ticks before a thrown item can be picked up, and before a block's drop can. */
    private static final int THROWN_PICKUP_DELAY = 40;
    private static final int BLOCK_DROP_PICKUP_DELAY = 10;

    /** Ticks an item lasts: 5 minutes. */
    private static final int LIFETIME = 6000;

    /** Items that fall this far below the world are gone. */
    private static final int VOID_Y = -128;

    /** Clients draw items this far above the position they're given. */
    private static final double NETWORK_OFFSET = 0.125;

    private final World world;
    private final PlayerManager players;
    private final Map<Long, ItemEntity> items = new LinkedHashMap<>();
    private long tick;

    public ItemEntities(World world, PlayerManager players) {
        this.world = world;
        this.players = players;
    }

    /** Pops a broken block's item out of the middle of the block, with a little sideways scatter. */
    public synchronized void dropFromBlock(Vector3i block, ItemData item) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector3f position = Vector3f.from(block.getX() + 0.5, block.getY() + 0.5 - ItemEntity.SIZE / 2, block.getZ() + 0.5);
        Vector3f velocity = Vector3f.from((random.nextDouble() - 0.5) * 0.1, 0.2, (random.nextDouble() - 0.5) * 0.1);
        spawn(item, position, velocity, BLOCK_DROP_PICKUP_DELAY);
    }

    /** Throws an item out of a player, from about chest height, the way they're looking. */
    public synchronized void throwFrom(Player player, ItemData item) {
        double pitch = Math.toRadians(player.getRotation().getX());
        double yaw = Math.toRadians(player.getRotation().getY());
        Vector3f feet = player.getFeetPosition();
        Vector3f position = Vector3f.from(feet.getX(), feet.getY() + 1.4 - ItemEntity.SIZE / 2, feet.getZ());
        Vector3f velocity = Vector3f.from(
                -Math.sin(yaw) * Math.cos(pitch) * 0.4,
                -Math.sin(pitch) * 0.4,
                Math.cos(yaw) * Math.cos(pitch) * 0.4);
        spawn(item, position, velocity, THROWN_PICKUP_DELAY);
    }

    /**
     * One server tick: ages, moves and removes items, lets players pick them up, then shows
     * each player the items that have come into their view and stops showing those that left.
     */
    public synchronized void tick() {
        tick++;
        Iterator<ItemEntity> iterator = items.values().iterator();
        while (iterator.hasNext()) {
            ItemEntity item = iterator.next();
            // Items where no player is near wait, as vanilla's do, rather than loading their chunk back.
            Vector3f position = item.getPosition();
            if (!world.isChunkLoaded(position.getFloorX() >> 4, position.getFloorZ() >> 4)) {
                continue;
            }
            item.age();
            if (item.getAge() >= LIFETIME || item.getPosition().getY() < VOID_Y) {
                iterator.remove();
                hideFromEveryone(item);
                continue;
            }
            if (item.step(world)) {
                sendToShowing(item, movePacket(item));
                sendToShowing(item, motionPacket(item));
            }
        }

        for (Player player : players.getPlayers()) {
            if (!player.isSpectator()) {
                pickUp(player);
            }
        }

        for (ItemEntity item : items.values()) {
            updateVisibility(item);
        }
    }

    /**
     * Picks up the items within the player's reach, as much of each as fits in their
     * inventory. Everyone who can see it sees the item fly to the player; what's left
     * stays on the ground.
     */
    private void pickUp(Player player) {
        boolean picked = false;
        Iterator<ItemEntity> iterator = items.values().iterator();
        while (iterator.hasNext()) {
            ItemEntity item = iterator.next();
            if (item.getPickupDelay() > 0 || !inReach(player, item)) {
                continue;
            }
            int count = item.getItem().getCount();
            int left = player.getInventory().add(item.getItem());
            if (left == count) {
                continue; // the inventory is full
            }
            picked = true;
            // The item's own flying animation comes from TakeItemEntity; this is the "pop" it
            // makes on the way, which vanilla sends as the infinity-arrow pickup level event.
            players.broadcastLevelEvent(LevelEvent.SOUND_INFINITY_ARROW_PICKUP, item.getPosition(), 0);

            TakeItemEntityPacket take = new TakeItemEntityPacket();
            take.setItemRuntimeEntityId(item.getEntityId());
            take.setRuntimeEntityId(player.getEntityId());
            sendToShowing(item, take);

            // Taking an item removes it on clients, so what's left is shown again as a new stack.
            if (left == 0) {
                iterator.remove();
                hideFromEveryone(item);
            } else {
                sendToShowing(item, removePacket(item));
                item.setItem(item.getItem().toBuilder().count(left).build());
                sendToShowing(item, addPacket(item));
            }
        }
        if (picked) {
            for (BedrockPacket packet : player.getInventory().contentPackets()) {
                player.getSession().sendPacket(packet);
            }
        }
    }

    private void spawn(ItemData item, Vector3f position, Vector3f velocity, int pickupDelay) {
        if (item.isNull()) {
            return;
        }
        // Items on the ground aren't inventory stacks, so they carry no stack network id.
        ItemData onGround = item.toBuilder().usingNetId(false).netId(0).build();
        ItemEntity entity = new ItemEntity(EntityIds.next(), onGround, position, velocity, pickupDelay);
        items.put(entity.getEntityId(), entity);
        updateVisibility(entity);
    }

    /**
     * Shows an item to the players whose clients have its chunk, and stops showing it to
     * those whose clients don't any more.
     */
    private void updateVisibility(ItemEntity item) {
        for (Player player : players.getPlayers()) {
            boolean visible = player.canSee(item.getPosition());
            if (visible && player.startShowing(item.getEntityId())) {
                player.getSession().sendPacket(addPacket(item));
            } else if (!visible && player.stopShowing(item.getEntityId())) {
                player.getSession().sendPacket(removePacket(item));
            }
        }
    }

    /** Takes an item that's gone off every client showing it. */
    private void hideFromEveryone(ItemEntity item) {
        for (Player player : players.getPlayers()) {
            if (player.stopShowing(item.getEntityId())) {
                player.getSession().sendPacket(removePacket(item));
            }
        }
    }

    /** Sends something about an item to the players whose clients are showing it. */
    private void sendToShowing(ItemEntity item, BedrockPacket packet) {
        for (Player player : players.getPlayers()) {
            if (player.isShowing(item.getEntityId())) {
                player.getSession().sendPacket(packet);
            }
        }
    }

    /**
     * Whether an item is close enough for a player to pick up: the item's box grown by 1
     * sideways and 0.5 up and down touches the player's box.
     */
    private static boolean inReach(Player player, ItemEntity item) {
        double reach = ItemEntity.HALF_SIZE + 1 + 0.3;
        Vector3f feet = player.getFeetPosition();
        Vector3f position = item.getPosition();
        return Math.abs(position.getX() - feet.getX()) < reach
                && Math.abs(position.getZ() - feet.getZ()) < reach
                && position.getY() - 0.5 < feet.getY() + 1.8
                && position.getY() + ItemEntity.SIZE + 0.5 > feet.getY();
    }

    private static Vector3f shownAt(ItemEntity item) {
        return item.getPosition().add(0, NETWORK_OFFSET, 0);
    }

    private static AddItemEntityPacket addPacket(ItemEntity item) {
        EntityDataMap metadata = new EntityDataMap();
        metadata.setFlag(EntityFlag.HAS_GRAVITY, true);
        metadata.put(EntityDataTypes.WIDTH, (float) ItemEntity.SIZE);
        metadata.put(EntityDataTypes.HEIGHT, (float) ItemEntity.SIZE);

        AddItemEntityPacket packet = new AddItemEntityPacket();
        packet.setUniqueEntityId(item.getEntityId());
        packet.setRuntimeEntityId(item.getEntityId());
        packet.setItemInHand(item.getItem());
        packet.setPosition(shownAt(item));
        packet.setMotion(item.getVelocity());
        packet.setMetadata(metadata);
        packet.setFromFishing(false);
        return packet;
    }

    private static MoveEntityAbsolutePacket movePacket(ItemEntity item) {
        MoveEntityAbsolutePacket packet = new MoveEntityAbsolutePacket();
        packet.setRuntimeEntityId(item.getEntityId());
        packet.setPosition(shownAt(item));
        packet.setRotation(Vector3f.ZERO);
        packet.setOnGround(item.isOnGround());
        return packet;
    }

    private SetEntityMotionPacket motionPacket(ItemEntity item) {
        SetEntityMotionPacket packet = new SetEntityMotionPacket();
        packet.setRuntimeEntityId(item.getEntityId());
        packet.setMotion(item.getVelocity());
        packet.setTick(tick);
        return packet;
    }

    private static RemoveEntityPacket removePacket(ItemEntity item) {
        RemoveEntityPacket packet = new RemoveEntityPacket();
        packet.setUniqueEntityId(item.getEntityId());
        return packet;
    }
}
