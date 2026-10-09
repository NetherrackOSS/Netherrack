package com.netherrack.server.entity;

import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

/**
 * A stack of items lying in the world, after being dropped or broken out of a block. It
 * falls, lands, slides to a stop and waits to be picked up.
 * <p>
 * The physics and timings follow Dragonfly's: gravity 0.04 and drag 0.02 per tick, and
 * vanilla's ground friction of 0.6 for sliding to a stop.
 */
public class ItemEntity {

    /** Half the width, and the height, of an item's box. */
    static final double HALF_SIZE = 0.125;
    static final double SIZE = 0.25;

    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.02;
    private static final double GROUND_FRICTION = 0.6;
    /** Below this sideways speed, an item on the ground stops. */
    private static final double REST_SPEED = 0.001;

    private final long entityId;
    private ItemData item;

    // The bottom center of the item's box.
    private double x;
    private double y;
    private double z;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private boolean onGround;

    private int pickupDelay;
    private int age;

    ItemEntity(long entityId, ItemData item, Vector3f position, Vector3f velocity, int pickupDelay) {
        this.entityId = entityId;
        this.item = item;
        this.x = position.getX();
        this.y = position.getY();
        this.z = position.getZ();
        this.velocityX = velocity.getX();
        this.velocityY = velocity.getY();
        this.velocityZ = velocity.getZ();
        this.pickupDelay = pickupDelay;
    }

    public long getEntityId() {
        return entityId;
    }

    public ItemData getItem() {
        return item;
    }

    void setItem(ItemData item) {
        this.item = item;
    }

    public Vector3f getPosition() {
        return Vector3f.from(x, y, z);
    }

    public Vector3f getVelocity() {
        return Vector3f.from(velocityX, velocityY, velocityZ);
    }

    public boolean isOnGround() {
        return onGround;
    }

    int getPickupDelay() {
        return pickupDelay;
    }

    int getAge() {
        return age;
    }

    /** Ages the item one tick and counts down its pickup delay. */
    void age() {
        age++;
        if (pickupDelay > 0) {
            pickupDelay--;
        }
    }

    /**
     * Moves the item one tick: gravity, then movement that solid blocks stop, one axis at
     * a time, then drag and ground friction. Returns whether it moved.
     */
    boolean step(World world) {
        boolean resting = onGround
                && Math.abs(velocityX) < REST_SPEED && Math.abs(velocityZ) < REST_SPEED
                && solid(world, x, y - 0.01, z);
        if (resting) {
            velocityX = 0;
            velocityY = 0;
            velocityZ = 0;
            return false;
        }

        double beforeX = x;
        double beforeY = y;
        double beforeZ = z;
        velocityY -= GRAVITY;

        // Vertical first, so an item lands before it slides.
        double nextY = y + velocityY;
        if (velocityY < 0 && solid(world, x, nextY, z)) {
            nextY = Math.floor(nextY) + 1;
            velocityY = 0;
            onGround = true;
        } else if (velocityY > 0 && solid(world, x, nextY + SIZE, z)) {
            nextY = y;
            velocityY = 0;
        } else {
            onGround = velocityY == 0 && solid(world, x, nextY - 0.01, z);
        }
        y = nextY;

        // Then each sideways axis, checking the block at the leading edge of the box.
        double movedX = x + velocityX;
        if (solid(world, movedX + Math.signum(velocityX) * HALF_SIZE, y + 0.01, z)) {
            velocityX = 0;
        } else {
            x = movedX;
        }
        double movedZ = z + velocityZ;
        if (solid(world, x, y + 0.01, movedZ + Math.signum(velocityZ) * HALF_SIZE)) {
            velocityZ = 0;
        } else {
            z = movedZ;
        }

        velocityX *= 1 - DRAG;
        velocityY *= 1 - DRAG;
        velocityZ *= 1 - DRAG;
        if (onGround) {
            velocityX *= GROUND_FRICTION;
            velocityZ *= GROUND_FRICTION;
        }
        return x != beforeX || y != beforeY || z != beforeZ;
    }

    /** Whether a block stops items - for now, anything but air. */
    private static boolean solid(World world, double x, double y, double z) {
        return world.getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) != null;
    }
}
