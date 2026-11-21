package com.netherrack.server.entity;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.BlockID;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Set;

/**
 * A stack of items lying in the world, after being dropped or broken out of a block. It
 * falls, lands, slides to a stop and waits to be picked up. Lava and fire burn it away the
 * way Cloudburst Nukkit does, including a short afterburn once it leaves the hazard.
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

    /** Nukkit gives dropped items five health; lava and fire eat through it. */
    private static final int MAX_HEALTH = 5;
    /** Lava and fire leave an item alight for eight seconds. */
    private static final int FIRE_TICKS = 160;
    /** A burning item loses one health every second. */
    private static final int FIRE_DAMAGE_INTERVAL = 20;
    /** Fireproof items burn off four times faster, as they do in Nukkit. */
    private static final int FIREPROOF_BURN_RATE = 4;
    /** Nukkit's fireproof range covers netherite gear and raw materials, but not the template. */
    private static final Set<String> FIREPROOF_ITEMS = Set.of(
            "minecraft:netherite_ingot",
            "minecraft:netherite_sword",
            "minecraft:netherite_shovel",
            "minecraft:netherite_pickaxe",
            "minecraft:netherite_axe",
            "minecraft:netherite_hoe",
            "minecraft:netherite_helmet",
            "minecraft:netherite_chestplate",
            "minecraft:netherite_leggings",
            "minecraft:netherite_boots",
            "minecraft:netherite_scrap",
            "minecraft:netherite_block");

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
    private int health = MAX_HEALTH;
    private int fireTicks;
    private final boolean fireproof;

    ItemEntity(long entityId, ItemData item, Vector3f position, Vector3f velocity, int pickupDelay) {
        this.entityId = entityId;
        this.item = item;
        this.fireproof = isFireproof(item);
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

    int getHealth() {
        return health;
    }

    boolean isBurning() {
        return fireTicks > 0;
    }

    boolean isFireproof() {
        return fireproof;
    }

    /** Ages the item one tick and counts down its pickup delay. */
    void age() {
        age++;
        if (pickupDelay > 0) {
            pickupDelay--;
        }
    }

    /**
     * Applies this tick's lava, fire and afterburn damage. Nukkit gives items five health,
     * four damage in lava and one in fire, then keeps them alight for eight seconds. A burning
     * item takes one more damage every twenty ticks, so an item thrown out of lava still burns
     * away instead of surviving on the bank. Returns whether the stack was destroyed.
     */
    boolean applyHazards(World world) {
        if (health <= 0) {
            return true;
        }
        Block block = world.getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (isLava(block)) {
            burn(4);
        } else if (isFire(block)) {
            burn(1);
        }

        if (fireTicks > 0) {
            if (fireproof) {
                fireTicks = Math.max(0, fireTicks - FIREPROOF_BURN_RATE);
            } else {
                if (fireTicks % FIRE_DAMAGE_INTERVAL == 0) {
                    damage(1);
                }
                fireTicks--;
            }
        }
        return health <= 0;
    }

    /**
     * Ignites the item and applies a hazard's direct damage. Nukkit stops damaging a fireproof
     * entity with blocks once it is alight; its flames simply burn off faster instead.
     */
    private void burn(int damage) {
        if (!fireproof || fireTicks == 0) {
            fireTicks = Math.max(fireTicks, FIRE_TICKS);
            damage(damage);
        }
    }

    private void damage(int amount) {
        health = Math.max(0, health - amount);
    }

    private static boolean isLava(Block block) {
        if (block == null) {
            return false;
        }
        String id = block.getIdentifier();
        return id.equals(BlockID.LAVA) || id.equals(BlockID.FLOWING_LAVA);
    }

    private static boolean isFire(Block block) {
        if (block == null) {
            return false;
        }
        String id = block.getIdentifier();
        return id.equals(BlockID.FIRE) || id.equals(BlockID.SOUL_FIRE);
    }

    private static boolean isFireproof(ItemData item) {
        if (item.isNull() || item.getDefinition() == null) {
            return false;
        }
        return FIREPROOF_ITEMS.contains(item.getDefinition().getIdentifier());
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

    /** Whether a block stops items: a solid one, not air or something to fall through, like a flower or a torch. */
    private static boolean solid(World world, double x, double y, double z) {
        Block block = world.getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return block != null && block.isSolid();
    }
}
