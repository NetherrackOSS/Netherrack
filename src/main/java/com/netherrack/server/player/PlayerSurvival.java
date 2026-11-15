package com.netherrack.server.player;

import com.netherrack.server.block.Block;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.Chunk;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.DeathInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;

import java.util.List;
import java.util.Set;

/**
 * A survival player's health, hunger and everything that wears them down: falling, lava,
 * fire, drowning, the void and starvation - and dying and coming back.
 * <p>
 * The server owns these numbers. Health goes to the client in a SetHealthPacket and in the
 * health attribute, while hunger, saturation and exhaustion are attributes the client draws
 * the hunger bar from. The client still moves itself (see
 * {@link com.netherrack.server.network.NetherrackPacketHandler}), so fall distance and the
 * exhaustion of moving are measured from the positions it reports.
 * <p>
 * The numbers match vanilla's where it matters: 20 health, 20 hunger, 5 starting
 * saturation, regeneration and starvation every 4 seconds, and exhaustion reducing
 * saturation first and hunger once that's gone. The world runs on easy difficulty (see
 * StartGamePacket in NetherrackPacketHandler), so starvation stops at 10 health.
 */
public final class PlayerSurvival {

    public static final float MAX_HEALTH = 20f;
    public static final int MAX_FOOD = 20;
    public static final int MAX_AIR = 300;
    public static final float START_SATURATION = 5f;

    /** StartGame tells the client the world is on this difficulty: 0 peaceful, 1 easy, 2 normal, 3 hard. */
    private static final int DIFFICULTY = 1;

    /** Vanilla's natural regeneration and starvation pace: once every four seconds. */
    private static final int FOOD_TICK_INTERVAL = 80;

    /** Below this much health, easy difficulty stops starvation. */
    private static final float STARVATION_FLOOR = 10f;

    private static final int DROWNING_INTERVAL = 20;
    private static final int DROWNING_DAMAGE = 2;
    private static final int LAVA_INTERVAL = 10;
    private static final int LAVA_DAMAGE = 4;
    private static final int FIRE_INTERVAL = 20;
    private static final int FIRE_DAMAGE = 1;
    private static final int VOID_INTERVAL = 10;
    private static final int VOID_DAMAGE = 4;

    /** A fall of more than this many blocks hurts, one point per block beyond it. */
    private static final float SAFE_FALL_DISTANCE = 3f;

    /** How often the client is reminded of its air, hunger and exhaustion, so they don't flood the connection. */
    private static final int AIR_SEND_INTERVAL = 20;
    private static final int FOOD_SEND_INTERVAL = 10;

    /** A single movement larger than this is a teleport, not a step or a fall. */
    private static final double MAX_STEP = 10;

<<<<<<< Updated upstream
=======
    /** How long eating an item takes, as vanilla does: 1.6 seconds for most foods. */
    private static final int EAT_DURATION_TICKS = 32;

    /** How long after a finished meal its consume request may still arrive and be confirmed. */
    private static final long EAT_CONFIRM_GRACE_MILLIS = 1000;

>>>>>>> Stashed changes
    private final Player player;
    /** For showing other players the hurt and death animations; null in tests. */
    private final PlayerManager players;

    // Written by the player's network thread as they move and by the tick thread as the
    // world wears them down - hence volatile. Changing methods synchronize on this object.
    private volatile float health = MAX_HEALTH;
    private volatile int food = MAX_FOOD;
    private volatile float saturation = START_SATURATION;
    private volatile float exhaustion;
    private volatile int air = MAX_AIR;
    private volatile int fireTicks;
    private volatile float fallDistance;
    private volatile boolean dead;

    // Only touched by the tick thread, inside synchronized methods.
    private int foodTickTimer;
    private int drowningTimer;
    private int lavaTimer;
    private int fireDamageTimer;
    private int voidTimer;
    private boolean inWater;
    private boolean inLava;
<<<<<<< Updated upstream
=======
    private boolean usingItem;
    /** What is being eaten, from which slot, and for how many ticks. */
    private String usingItemId;
    private ItemData usingItemData;
    private int usingSlot = -1;
    private boolean usingOffhand;
    private int useTicks;
>>>>>>> Stashed changes
    private int airSinceSent = MAX_AIR;
    private boolean fireWasSent;
    private int statsSinceSent;
    private float sentFood = -1;
    private float sentSaturation = -1;
    private float sentExhaustion = -1;

    public PlayerSurvival(Player player, PlayerManager players) {
        this.player = player;
        this.players = players;
    }

    public float getHealth() {
        return health;
    }

    /** Health as the SetHealthPacket carries it: the client's health bar has whole points. */
    public int getHealthInt() {
        return Math.max(0, (int) health);
    }

    public int getFood() {
        return food;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getExhaustion() {
        return exhaustion;
    }

    public int getAir() {
        return air;
    }

    public int getFireTicks() {
        return fireTicks;
    }

    public float getFallDistance() {
        return fallDistance;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isInWater() {
        return inWater;
    }

    public boolean isInLava() {
        return inLava;
    }

    /** Puts back what {@link PlayerData} saved for a returning player. */
    public synchronized void restore(PlayerData.Stats stats) {
        health = clamp(stats.health(), 0.01f, MAX_HEALTH);
        food = Math.max(0, Math.min(stats.food(), MAX_FOOD));
        saturation = clamp(stats.saturation(), 0f, food);
        exhaustion = clamp(stats.exhaustion(), 0f, 4f);
        air = Math.max(0, Math.min(stats.air(), MAX_AIR));
        fireTicks = Math.max(0, Math.min(stats.fireTicks(), 1000));
        dead = false;
        fallDistance = 0;
        airSinceSent = air;
        fireWasSent = fireTicks > 0;
    }

    /** The four attributes the hunger and health bars are drawn from. */
    public List<AttributeData> attributes() {
        return List.of(
                new AttributeData("minecraft:health", 0, MAX_HEALTH, health, MAX_HEALTH),
                new AttributeData("minecraft:player.hunger", 0, MAX_FOOD, food, MAX_FOOD),
                new AttributeData("minecraft:player.saturation", 0, MAX_FOOD, saturation, START_SATURATION),
                new AttributeData("minecraft:player.exhaustion", 0, 5, exhaustion, 0));
    }

    /** Sends health, hunger and the entity data the client needs for air and fire, all at once. */
    public void sendStats() {
        SetHealthPacket setHealth = new SetHealthPacket();
        setHealth.setHealth(getHealthInt());
        player.getSession().sendPacket(setHealth);
        sendAttributes();
        sendMetadata();
    }

    private void sendAttributes() {
        UpdateAttributesPacket packet = new UpdateAttributesPacket();
        packet.setRuntimeEntityId(player.getEntityId());
        packet.setAttributes(attributes());
        player.getSession().sendPacket(packet);
        sentFood = food;
        sentSaturation = saturation;
        sentExhaustion = exhaustion;
        statsSinceSent = 0;
    }

    /** Tells this client and everyone who can see them about the fire and air. */
    private void sendMetadata() {
        player.sendMetadata();
        if (players != null) {
            players.broadcastEntityData(player);
        }
    }

    /**
     * One tick of the world on this player: the blocks they're in, their fire and air, and
     * their hunger. Called from the server tick thread.
     */
    public synchronized void tick(World world) {
        if (dead) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            // Creative players neither burn nor hunger; leave their stats as they are.
            if (fireTicks != 0) {
                fireTicks = 0;
                sendMetadata();
            }
            air = MAX_AIR;
            fallDistance = 0;
            return;
        }

        Vector3f feet = player.getFeetPosition();
        Vector3f eyes = player.getPosition();
        Block feetBlock = blockAt(world, feet);
        Block eyeBlock = blockAt(world, eyes);
        Block belowBlock = blockAt(world, Vector3f.from(feet.getX(), feet.getY() - 0.1f, feet.getZ()));

        inWater = isWater(feetBlock) || isWater(eyeBlock);
        inLava = isLava(feetBlock) || isLava(eyeBlock);

        if (inLava) {
            air = MAX_AIR;
            drowningTimer = 0;
            fireTicks = Math.max(fireTicks, 300);
            fallDistance = 0;
            if (++lavaTimer >= LAVA_INTERVAL) {
                lavaTimer = 0;
                damage(LAVA_DAMAGE, DamageCause.LAVA);
            }
        } else {
            lavaTimer = 0;
            if (inWater) {
                fireTicks = 0;
                fallDistance = 0;
                // Only a head under water runs out of air; standing in it is fine.
                if (isWater(eyeBlock)) {
                    air = Math.max(0, air - 1);
                    if (air == 0 && ++drowningTimer >= DROWNING_INTERVAL) {
                        drowningTimer = 0;
                        damage(DROWNING_DAMAGE, DamageCause.DROWNING);
                    }
                } else {
                    air = Math.min(MAX_AIR, air + 4);
                    drowningTimer = 0;
                }
            } else {
                air = Math.min(MAX_AIR, air + 4);
                drowningTimer = 0;
            }
        }

        if (!dead && (isFire(feetBlock) || isFire(belowBlock))) {
            fireTicks = Math.max(fireTicks, 160);
        }

        if (fireTicks > 0) {
            fireTicks--;
            if (++fireDamageTimer >= FIRE_INTERVAL) {
                fireDamageTimer = 0;
                damage(FIRE_DAMAGE, DamageCause.FIRE);
            }
        } else {
            fireDamageTimer = 0;
        }

        if (!dead && feet.getY() < Chunk.MIN_Y) {
            if (++voidTimer >= VOID_INTERVAL) {
                voidTimer = 0;
                damage(VOID_DAMAGE, DamageCause.VOID);
            }
        } else {
            voidTimer = 0;
        }

        if (!dead) {
            tickFood();
        }

        // Air bubbles and the burning overlay.
        if (air != airSinceSent && (air == MAX_AIR || air == 0 || air % AIR_SEND_INTERVAL == 0)) {
            airSinceSent = air;
            sendMetadata();
        }
        if ((fireTicks > 0) != fireWasSent) {
            fireWasSent = fireTicks > 0;
            sendMetadata();
        }

        if (statsSinceSent > 0) {
            statsSinceSent++;
        }
        if (statsSinceSent >= FOOD_SEND_INTERVAL
                && (sentFood != food || sentSaturation != saturation || sentExhaustion != exhaustion)) {
            sendAttributes();
        }
    }

    /** Natural regeneration above 18 hunger, starvation at 0, once every four seconds. */
    private void tickFood() {
        if (++foodTickTimer < FOOD_TICK_INTERVAL) {
            return;
        }
        foodTickTimer = 0;
        if (food >= 18 && health < MAX_HEALTH) {
            heal(1);
            exhaust(6);
        } else if (food <= 0) {
            boolean starves = DIFFICULTY == 3
                    || (DIFFICULTY == 2 && health > 1)
                    || (DIFFICULTY == 1 && health > STARVATION_FLOOR);
            if (starves) {
                damage(1, DamageCause.STARVATION);
            }
        }
    }

    /**
     * Notes how the player moved, on every position packet they send: standing on the
     * ground ends a fall (and hurts if it was long enough), and moving or jumping uses up
     * hunger through exhaustion.
     */
    public synchronized void moved(World world, Vector3f from, Vector3f to, boolean onGround,
                                   Set<PlayerAuthInputData> input) {
        if (dead || player.isCreative() || player.isSpectator()) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        Vector3f feet = to.sub(0, Player.EYE_HEIGHT, 0);
        boolean overLiquid = isWater(blockAt(world, feet)) || isLava(blockAt(world, feet));
        if (overLiquid) {
            fallDistance = 0;
        } else if (onGround) {
            // The last bit of the fall is in this packet, from the last mid-air position.
            if (dy < 0 && dy > -MAX_STEP) {
                fallDistance += (float) -dy;
            }
            if (fallDistance > SAFE_FALL_DISTANCE) {
                damage((float) Math.floor(fallDistance - SAFE_FALL_DISTANCE), DamageCause.FALL);
            }
            fallDistance = 0;
        } else if (dy < 0 && dy > -MAX_STEP) {
            fallDistance += (float) -dy;
        }

        // A teleport-sized step is not walking; ignore it for hunger.
        if (horizontal <= MAX_STEP && onGround) {
            boolean sprinting = input.contains(PlayerAuthInputData.SPRINTING);
            if (horizontal > 0) {
                if (sprinting) {
                    exhaust(horizontal * 0.1);
                } else if (inWater) {
                    exhaust(horizontal * 0.01);
                }
            }
            if (input.contains(PlayerAuthInputData.START_JUMPING)) {
                exhaust(sprinting ? 0.2 : 0.05);
            }
        }
    }

    /** A swing of the arm while mining or attacking costs a little hunger, as in vanilla. */
    public synchronized void swung() {
        if (!dead) {
            exhaust(0.005);
        }
    }

    /** Adds exhaustion, which first eats saturation and then hunger once saturation is gone. */
    public synchronized void exhaust(double amount) {
        if (dead || amount <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        exhaustion = (float) (exhaustion + amount);
        while (exhaustion >= 4) {
            exhaustion -= 4;
            if (saturation > 0) {
                saturation = Math.max(0, saturation - 1);
            } else if (food > 0) {
                food--;
            }
        }
        saturation = Math.min(saturation, food);
        statsSinceSent = Math.max(statsSinceSent, 1);
    }

    /**
     * Hurts the player. Returns whether the damage was taken; creative and spectator
     * players, and the dead, take none.
     */
    public synchronized boolean damage(float amount, DamageCause cause) {
        if (dead || amount <= 0 || player.isCreative() || player.isSpectator()) {
            return false;
        }
        health = Math.max(0, health - amount);
        exhaust(0.1); // being hurt is exhausting, as in vanilla
        playHurt();
        if (health <= 0) {
            die(cause);
        } else {
            sendStats();
        }
        return true;
    }

    public synchronized void heal(float amount) {
        if (dead || amount <= 0) {
            return;
        }
        float before = health;
        health = Math.min(MAX_HEALTH, health + amount);
        if (health != before) {
            sendStats();
        }
    }

<<<<<<< Updated upstream
=======
    /**
     * Eats one serving of a food item: restores hunger and the saturation that comes with
     * it, capped at full, and burps. The item itself comes out of the inventory in
     * {@link #useItem}. Returns whether the player could eat it at all.
     */
    public synchronized boolean eat(ItemData item) {
        Foods.Food eaten = Foods.of(item);
        if (dead || eaten == null) {
            return false;
        }
        if (food >= MAX_FOOD && !Foods.alwaysEdible(item) && !player.isCreative()) {
            return false; // too full to eat, though the client usually won't ask
        }
        if (food < MAX_FOOD) {
            food = Math.min(MAX_FOOD, food + eaten.nutrition());
            saturation = Math.min(food, saturation + eaten.nutrition() * eaten.saturationModifier() * 2f);
            sendAttributes();
        }
        if (players != null) {
            players.broadcastEntitySound(player, SoundEvent.BURP);
        }
        return true;
    }

    /**
     * The player using what they hold on air: this starts eating. Finishing is driven by
     * the server tick (see {@link #tickUsingItem}), as vanilla does, so it doesn't depend
     * on the client sending more use packets. Returns whether something was eaten right
     * away, which it never is - the caller has nothing to take out of the inventory yet.
     */
    public synchronized boolean useItem(ItemData item) {
        return useItem(item, player.getSelectedSlot(), false);
    }

    /**
     * Starts eating the item held in the given hotbar slot, or the offhand. Called from
     * every click-air the client sends while holding the button down; only the first one
     * starts the meal, the rest leave it running.
     */
    public synchronized boolean useItem(ItemData item, int slot, boolean offhand) {
        if (dead || player.isSpectator()) {
            usingItem = false;
            return false;
        }
        if (Foods.of(item) == null) {
            // The server holds something else than the client's food, or it isn't food.
            usingItem = false;
            return false;
        }
        if (food >= MAX_FOOD && !Foods.alwaysEdible(item) && !player.isCreative()) {
            usingItem = false;
            return false;
        }
        String id = item.getDefinition() == null ? null : item.getDefinition().getIdentifier();
        if (usingItem && id != null && id.equals(usingItemId)
                && offhand == usingOffhand && (offhand || slot == usingSlot)) {
            return false; // the same meal, still going
        }
        usingItem = true;
        usingItemId = id;
        usingItemData = item;
        usingSlot = slot;
        usingOffhand = offhand;
        useTicks = 0;
        player.getInventory().clearMealTake();
        return false;
    }

    /** The player let go before finishing what they were eating. */
    public synchronized void releaseItem() {
        if (usingItem && player.getInventory().mealTakenByRequest()) {
            // The client already took the item out, so the meal completes rather than
            // leaving a taken item uneaten.
            finishMeal();
            return;
        }
        usingItem = false;
    }

    /**
     * Advances a meal by one tick, finishing it after {@link #EAT_DURATION_TICKS}: the food
     * is applied, one is taken out of the slot it came from, and the client is told both
     * that the use completed and what the slot holds now.
     */
    private void tickUsingItem() {
        if (!usingItem) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (!usingOffhand && player.getSelectedSlot() != usingSlot) {
            cancelMeal("switched away from slot " + usingSlot);
            return;
        }
        ItemData held = usingOffhand ? inventory.getOffhand()
                : usingSlot >= 0 && usingSlot < PlayerInventory.HOTBAR_SIZE
                        ? inventory.get(usingSlot) : ItemData.AIR;
        boolean sameItem = !held.isNull() && held.getDefinition() != null
                && held.getDefinition().getIdentifier().equals(usingItemId);
        if (!sameItem && !inventory.mealTakenByRequest()) {
            cancelMeal(usingItemId + " is no longer in " + (usingOffhand ? "the offhand" : "slot " + usingSlot));
            return;
        }
        if (++useTicks < EAT_DURATION_TICKS) {
            return;
        }
        finishMeal();
    }

    /** Finishes the meal: the client is told the use completed, the food is applied, and
     * one item leaves the inventory - unless the client's own request already took it. */
    private void finishMeal() {
        usingItem = false;
        ItemData eaten = usingItemData;
        int slot = usingSlot;
        boolean offhand = usingOffhand;
        if (eaten == null || eaten.isNull() || eaten.getDefinition() == null) {
            return;
        }
        // The completion packet goes out before the inventory change, as vanilla does -
        // after it, the client stops using the item and shows the slot as the server has it.
        CompletedUsingItemPacket completed = new CompletedUsingItemPacket();
        completed.setItemId(eaten.getDefinition().getRuntimeId());
        completed.setType(ItemUseType.EAT);
        player.getSession().sendPacket(completed);
        ItemData left = null;
        if (!player.isCreative()) {
            PlayerInventory inventory = player.getInventory();
            left = inventory.takeForMeal(slot, offhand, eaten);
        }
        if (left != null) {
            sendSlot(slot, offhand, left);
        }
        eat(eaten); // too full after all: nothing more to do, the use is completed either way
    }

    private void cancelMeal(String reason) {
        usingItem = false;
    }

    /** Tells this client what one of its inventory slots holds after the meal took an item. */
    private void sendSlot(int slot, boolean offhand, ItemData item) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(offhand ? ContainerId.OFFHAND : ContainerId.INVENTORY);
        packet.setSlot(offhand ? 0 : slot);
        packet.setItem(item);
        player.getSession().sendPacket(packet);
    }

    /**
     * Kills the player outright, as /kill does. Unlike {@link #damage}, this ignores the
     * invulnerability of creative and spectator players: the command kills them too.
     */
    public synchronized void kill(DamageCause cause) {
        if (dead) {
            return;
        }
        health = 0;
        die(cause);
    }

>>>>>>> Stashed changes
    /** Dies: tells the client, and starts the respawn handshake once it says it's ready. */
    private synchronized void die(DamageCause cause) {
        if (dead) {
            return;
        }
        dead = true;
        health = 0;
        fireTicks = 0;
        air = MAX_AIR;
        fallDistance = 0;
        inWater = false;
        inLava = false;
        resetTimers();
        playDeath();
        sendStats();

        // Vanilla's respawn handshake starts here: the server searches at the death
        // position, and waits for the client to answer that it's ready (see the handler's
        // RespawnPacket).
        RespawnPacket searching = new RespawnPacket();
        searching.setPosition(player.getPosition());
        searching.setState(RespawnPacket.State.SERVER_SEARCHING);
        searching.setRuntimeEntityId(0);
        player.getSession().sendPacket(searching);

        DeathInfoPacket info = new DeathInfoPacket();
        info.setCauseAttackName(cause.deathKey());
        info.getMessageList().add(player.getUsername());
        player.getSession().sendPacket(info);

        if (players != null) {
            TextPacket message = new TextPacket();
            message.setType(TextPacket.Type.TRANSLATION);
            message.setSourceName("");
            message.setXuid("");
            message.setNeedsTranslation(true);
            message.setMessage(cause.deathKey());
            message.getParameters().add(player.getUsername());
            players.sendToAll(message);
        }
        Logger.info(player.getUsername() + " died (" + cause.deathKey() + ").");
    }

    private void resetTimers() {
        foodTickTimer = 0;
        drowningTimer = 0;
        lavaTimer = 0;
        fireDamageTimer = 0;
        voidTimer = 0;
    }

    /**
     * Brings a dead player back: full health and hunger, no fire, and back on their feet.
     * The caller sends the RespawnPackets and moves them to the spawn.
     */
    public synchronized void respawn() {
        dead = false;
        health = MAX_HEALTH;
        food = MAX_FOOD;
        saturation = START_SATURATION;
        exhaustion = 0;
        air = MAX_AIR;
        fireTicks = 0;
        fallDistance = 0;
        inWater = false;
        inLava = false;
        resetTimers();
        airSinceSent = air;
        fireWasSent = false;
        sendStats();
    }

    private void playHurt() {
        EntityEventPacket hurt = event(EntityEventType.HURT);
        player.getSession().sendPacket(hurt);
        if (players != null) {
            players.broadcastHurt(player);
        }
    }

    private void playDeath() {
        EntityEventPacket death = event(EntityEventType.DEATH);
        player.getSession().sendPacket(death);
        if (players != null) {
            players.broadcastDeath(player);
        }
    }

    private EntityEventPacket event(EntityEventType type) {
        EntityEventPacket event = new EntityEventPacket();
        event.setRuntimeEntityId(player.getEntityId());
        event.setType(type);
        event.setData(0);
        return event;
    }

    private static Block blockAt(World world, Vector3f position) {
        return world.getBlock(position.getFloorX(), position.getFloorY(), position.getFloorZ());
    }

    private static boolean isWater(Block block) {
        if (block == null) {
            return false;
        }
        String id = block.getIdentifier();
        return id.equals("minecraft:water") || id.equals("minecraft:flowing_water");
    }

    private static boolean isLava(Block block) {
        if (block == null) {
            return false;
        }
        String id = block.getIdentifier();
        return id.equals("minecraft:lava") || id.equals("minecraft:flowing_lava");
    }

    private static boolean isFire(Block block) {
        if (block == null) {
            return false;
        }
        String id = block.getIdentifier();
        return id.equals("minecraft:fire") || id.equals("minecraft:soul_fire");
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** What killed the player, and the translation key the client's death screen shows. */
    public enum DamageCause {
        GENERIC("death.attack.generic"),
        FALL("death.attack.fall"),
        LAVA("death.attack.lava"),
        FIRE("death.attack.onFire"),
        DROWNING("death.attack.drown"),
        STARVATION("death.attack.starve"),
        VOID("death.attack.outOfWorld");

        private final String deathKey;

        DamageCause(String deathKey) {
            this.deathKey = deathKey;
        }

        public String deathKey() {
            return deathKey;
        }
    }
}
