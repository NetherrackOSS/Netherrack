package com.netherrack.server.player;

import com.netherrack.server.Netherrack;
import com.netherrack.server.block.Block;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.plugin.EventBridge;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.Chunk;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemUseType;
import org.cloudburstmc.protocol.bedrock.packet.CompletedUsingItemPacket;
import org.cloudburstmc.protocol.bedrock.packet.DeathInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;

import java.util.ArrayList;
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

    /** How long a dead player's body stays visible to the others: twenty ticks, a second of game time. */
    private static final long CORPSE_LINGER_MILLIS = 20 * 50;

    /** How far an arm can hit, from the eye to the target's box, in the survival modes. */
    private static final double ATTACK_REACH = 3;
    /** Creative arms reach about twice as far, as vanilla's let them. */
    private static final double CREATIVE_ATTACK_REACH = 6;

    /** Vanilla's knockback: a hit pushes its target away from the attacker, and up. */
    private static final float KNOCKBACK_HORIZONTAL = 0.4f;
    private static final float KNOCKBACK_UP = 0.4f;

    /** How long the hurt from a blow protects, in ticks: vanilla's damage immunity, ten ticks, half a second. */
    private static final int HURT_IMMUNITY_TICKS = 10;

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

    /** How long eating an item takes, as vanilla does: 1.6 seconds for most foods. */
    private static final int EAT_DURATION_TICKS = 32;

    /** How long after a finished meal its consume request may still arrive and be confirmed. */
    private static final long EAT_CONFIRM_GRACE_MILLIS = 1000;

    private final Netherrack server;
    private final Player player;
    /** For showing other players the hurt and death animations; null in tests. */
    private final PlayerManager players;
    /** For dropping what a dead player carried into the world; null in tests. */
    private final ItemEntities items;

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
    /** When the player died, so their corpse can linger on the others' screens for a moment. */
    private volatile long diedAt;
    /** Who hit them last, so a death by a player's hand can blame them. */
    private volatile Player lastAttacker;

    /** The hurt immunity every blow respects, what gives hits their rhythm (see {@link HurtImmunity}). */
    private final HurtImmunity immunity = new HurtImmunity();

    // Only touched by the tick thread, inside synchronized methods.
    private int foodTickTimer;
    private int drowningTimer;
    private int lavaTimer;
    private int fireDamageTimer;
    private int voidTimer;
    private boolean inWater;
    private boolean inLava;
    private boolean usingItem;
    /** What is being eaten, from which slot, and for how many ticks. */
    private String usingItemId;
    private ItemData usingItemData;
    private int usingSlot = -1;
    private boolean usingOffhand;
    private int useTicks;
    private int airSinceSent = MAX_AIR;
    private boolean fireWasSent;
    private int statsSinceSent;
    private float sentFood = -1;
    private float sentSaturation = -1;
    private float sentExhaustion = -1;

    public PlayerSurvival(Netherrack server, Player player, PlayerManager players, ItemEntities items) {
        this.server = server;
        this.player = player;
        this.players = players;
        this.items = items;
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

    /**
     * Whether a dead player's body should still be shown to the others: it lingers where
     * they fell for a second after they died, long enough for the death animation to play
     * out, and then the visibility tick despawns it. They stay hidden from then on, until
     * they respawn.
     */
    public synchronized boolean isCorpseLingering() {
        return dead && System.currentTimeMillis() - diedAt < CORPSE_LINGER_MILLIS;
    }

    public boolean isInWater() {
        return inWater;
    }

    public boolean isInLava() {
        return inLava;
    }

    /** Whether the player is holding down a food item, partway through eating it. */
    public boolean isUsingItem() {
        return usingItem;
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
        usingItem = false;
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
        immunity.tick();
        if (dead) {
            return;
        }
        tickUsingItem();
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

        boolean wasInLava = inLava;
        inWater = isWater(feetBlock) || isWater(eyeBlock);
        inLava = isLava(feetBlock) || isLava(eyeBlock);

        if (inLava) {
            air = MAX_AIR;
            drowningTimer = 0;
            fireTicks = Math.max(fireTicks, 300);
            fallDistance = 0;
            // The first tick in lava burns at once; after that every LAVA_INTERVAL ticks.
            if (!wasInLava || ++lavaTimer >= LAVA_INTERVAL) {
                lavaTimer = 0;
                damage(LAVA_DAMAGE, DamageCause.LAVA);
            }
        } else {
            lavaTimer = 0;
            if (inWater) {
                // Putting out a fire hisses, as vanilla does; the client can't know we
                // cleared it before it hears about the fire going away.
                if (fireTicks > 0 && players != null) {
                    players.broadcastEntitySound(player, SoundEvent.EXTINGUISH_FIRE);
                }
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
        // Flying clients measure no falls: their heights come and go with the flight, and
        // the client is the one that knows it's up there.
        if (dead || player.isCreative() || player.isSpectator() || player.isFlying()) {
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

    /** Notes that the server moved the player, so a teleport isn't mistaken for a fall. */
    public synchronized void teleported() {
        fallDistance = 0;
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
     * <p>
     * Every blow also respects the {@link HurtImmunity} left by the one before it, which
     * is what keeps a hammer on the attack button from being an unending stream of
     * damage.
     */
    public synchronized boolean damage(float amount, DamageCause cause) {
        if (dead || amount <= 0 || player.isCreative() || player.isSpectator()) {
            return false;
        }
        float taken = immunity.hit(amount);
        if (taken < 0) {
            return false; // shrugged off, as vanilla's damage immunity does
        }
        var event = EventBridge.playerDamage(server, player, EventBridge.damageCause(cause), taken);
        if (event.isCancelled() || event.getAmount() <= 0) {
            return false;
        }
        health = Math.max(0, health - (float) event.getAmount());
        exhaust(0.1); // being hurt is exhausting, as in vanilla
        playHurt(cause);
        if (health <= 0) {
            die(cause);
        } else {
            sendStats();
        }
        return true;
    }

    /**
     * Hurts the player for a blow another player dealt, remembering who dealt it: if it
     * kills them, the death screen and the chat say they were slain by that player.
     * Returns whether the damage was taken - and a creative player takes none, which is
     * what keeps them from being hit at all.
     */
    public synchronized boolean damage(float amount, DamageCause cause, Player attacker) {
        if (cause == DamageCause.PLAYER) {
            lastAttacker = attacker;
        }
        return damage(amount, cause);
    }

    /**
     * Attacks another player, as the attacker's client reported: hits them for what the
     * held item does and sends them flying back, as vanilla's knockback does. A
     * spectator can't attack, and a hit only lands if the attacker is alive and close
     * enough - vanilla's three blocks of reach, double that in creative - measured eye to
     * the target's box, with a little slack for the hit having been predicted before the
     * packet arrived. What the hit does to its target is up to their {@link #damage}: a
     * creative player takes none, which is what keeps creative players from being hit.
     *
     * @param held what the attacker held in the hand that swung, from the server's own
     *             inventory, not the item the client claimed to have held
     */
    public void attack(Player target, ItemData held) {
        if (dead || player.isSpectator() || target == player) {
            return;
        }
        PlayerSurvival targetSurvival = target.getSurvival();
        if (targetSurvival == null || target.isDead()) {
            return; // nothing living to hit
        }
        if (!withinReach(target)) {
            Logger.debug(player.getUsername() + " tried to hit " + target.getUsername() + " from out of reach.");
            return;
        }
        if (!targetSurvival.damage(attackDamage(held), DamageCause.PLAYER, player)) {
            return; // nothing happened to them: no knockback follows a hit that didn't land
        }
        knockback(target);
        exhaust(0.1); // swinging at someone is tiring, as in vanilla
    }

    /**
     * Whether the target is within swing's reach: the distance from the attacker's eye to
     * the closest point of the target's box - half a block wide and 1.8 tall, as players
     * are - against vanilla's attack range of three blocks, six in creative.
     */
    private boolean withinReach(Player target) {
        Vector3f eye = player.getPosition();
        Vector3f feet = target.getFeetPosition();
        double px = Math.max(feet.getX() - 0.3, Math.min(eye.getX(), feet.getX() + 0.3));
        double py = Math.max(feet.getY(), Math.min(eye.getY(), feet.getY() + 1.8));
        double pz = Math.max(feet.getZ() - 0.3, Math.min(eye.getZ(), feet.getZ() + 0.3));
        double dx = px - eye.getX();
        double dy = py - eye.getY();
        double dz = pz - eye.getZ();
        double reach = player.isCreative() ? CREATIVE_ATTACK_REACH : ATTACK_REACH;
        return dx * dx + dy * dy + dz * dz <= reach * reach + 0.5;
    }

    /**
     * What a held item does to what it hits, as vanilla Bedrock counts damage: a fist
     * does one point, a sword or an axe does its tier's four through eight (wood and gold
     * 4, stone and copper 5, iron 6, diamond 7, netherite 8), a pickaxe or shovel a
     * little less, and anything else hits as hard as a fist does.
     */
    private static float attackDamage(ItemData held) {
        if (held.isNull() || held.getDefinition() == null) {
            return 1;
        }
        String id = held.getDefinition().getIdentifier();
        if (id.endsWith("_sword") || id.endsWith("_axe")) {
            return 4 + tier(id);
        }
        if (id.endsWith("_pickaxe") || id.endsWith("_shovel")) {
            return 2 + tier(id);
        }
        return 1;
    }

    /** How far up a tool's tier is, 0 to 4, read from its name. */
    private static int tier(String id) {
        if (id.contains("netherite")) {
            return 4;
        }
        if (id.contains("diamond")) {
            return 3;
        }
        if (id.contains("iron")) {
            return 2;
        }
        if (id.contains("stone") || id.contains("copper")) {
            return 1;
        }
        return 0; // wood or gold
    }

    /**
     * Sends the player who was hit flying: away from the attacker, up a little, as
     * vanilla's knockback does. Movement here is the client's to make - the packet hands
     * their client a velocity, and their client then flies and reports where it went, so
     * everyone watching sees the blow land.
     */
    private void knockback(Player target) {
        Vector3f from = player.getFeetPosition();
        Vector3f to = target.getFeetPosition();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double x;
        double z;
        if (horizontal < 0.001) {
            // Standing inside each other, so there's no direction between them: knock
            // them back the way the attacker is looking instead.
            double yaw = Math.toRadians(player.getRotation().getY());
            x = -Math.sin(yaw);
            z = Math.cos(yaw);
        } else {
            x = dx / horizontal;
            z = dz / horizontal;
        }
        SetEntityMotionPacket motion = new SetEntityMotionPacket();
        motion.setRuntimeEntityId(target.getEntityId());
        motion.setMotion(Vector3f.from((float) (x * KNOCKBACK_HORIZONTAL), KNOCKBACK_UP,
                (float) (z * KNOCKBACK_HORIZONTAL)));
        target.getSession().sendPacket(motion);
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
        var event = EventBridge.itemConsume(server, player, item);
        if (event.isCancelled()) {
            return false;
        }
        ItemData food = EventBridge.item(event.getItem());
        if (Foods.of(food) == null || food.getDefinition() == null) {
            return false;
        }
        usingItem = true;
        usingItemId = food.getDefinition().getIdentifier();
        usingItemData = food;
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

    /** Dies: tells the client, and starts the respawn handshake once it says it's ready. */
    private synchronized void die(DamageCause cause) {
        if (dead) {
            return;
        }
        dead = true;
        // The body stays where they fell, on everyone's screen, for a moment before it
        // despawns (see isCorpseLingering).
        diedAt = System.currentTimeMillis();
        health = 0;
        fireTicks = 0;
        air = MAX_AIR;
        fallDistance = 0;
        inWater = false;
        inLava = false;
        usingItem = false;
        resetTimers();
        // The inventory is emptied here, before the event, so listeners can edit the drop list
        // without having to know how the server stores slots.
        List<ItemData> spilled = player.getInventory().spill();
        var event = EventBridge.playerDeath(server, player, EventBridge.damageCause(cause),
                cause.deathKey(), spilled);
        if (items != null) {
            List<ItemData> drops = new ArrayList<>();
            for (var drop : event.getDrops()) {
                ItemData item = EventBridge.item(drop);
                if (!item.isNull()) {
                    drops.add(item);
                }
            }
            items.dropFromDeath(player.getFeetPosition(), drops);
        }
        playDeath();
        sendStats();

        String key = cause.deathKey();
        String deathMessage = event.getDeathMessage();

        // Vanilla's respawn handshake starts here: the server searches at the death
        // position, and waits for the client to answer that it's ready (see the handler's
        // RespawnPacket).
        RespawnPacket searching = new RespawnPacket();
        searching.setPosition(player.getPosition());
        searching.setState(RespawnPacket.State.SERVER_SEARCHING);
        searching.setRuntimeEntityId(0);
        player.getSession().sendPacket(searching);

        DeathInfoPacket info = new DeathInfoPacket();
        info.setCauseAttackName(deathMessage);
        info.getMessageList().add(player.getUsername());
        // A death at a player's hand is "was slain by <attacker>", so it names them too.
        if (cause == DamageCause.PLAYER && lastAttacker != null) {
            info.getMessageList().add(lastAttacker.getUsername());
        }
        player.getSession().sendPacket(info);

        if (players != null) {
            List<String> params = new ArrayList<>();
            params.add(player.getUsername());
            if (cause == DamageCause.PLAYER && lastAttacker != null) {
                params.add(lastAttacker.getUsername());
            }
            for (Player other : players.getPlayers()) {
                if (!deathMessage.equals(key)) {
                    TextPacket plain = new TextPacket();
                    plain.setType(TextPacket.Type.SYSTEM);
                    plain.setSourceName("");
                    plain.setXuid("");
                    plain.setNeedsTranslation(false);
                    plain.setMessage(deathMessage);
                    other.getSession().sendPacket(plain);
                    continue;
                }
                Lang lang = Lang.of(other.getLanguageCode());
                if (!Lang.DEFAULT_CODE.equals(lang.getCode()) && !lang.get(key).equals(key)) {
                    other.sendText(lang.get(key, params.toArray()));
                    continue;
                }
                TextPacket message = new TextPacket();
                message.setType(TextPacket.Type.TRANSLATION);
                message.setSourceName("");
                message.setXuid("");
                message.setNeedsTranslation(true);
                message.setMessage(key);
                message.setParameters(params);
                other.getSession().sendPacket(message);
            }
        }
        Logger.info(player.getUsername() + " died (" + deathMessage + ").");
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
        usingItem = false;
        immunity.reset();
        resetTimers();
        airSinceSent = air;
        fireWasSent = false;
        sendStats();
    }

    private void playHurt(DamageCause cause) {
        EntityEventPacket hurt = event(EntityEventType.HURT);
        player.getSession().sendPacket(hurt);
        if (players != null) {
            players.broadcastHurt(player);
            // The entity event plays the ordinary hurt sound; the client can't tell what
            // hurt them, so causes with a voice of their own get a sound of their own.
            switch (cause) {
                case LAVA, FIRE -> players.broadcastEntitySound(player, SoundEvent.PLAYER_HURT_ON_FIRE);
                case DROWNING -> players.broadcastEntitySound(player, SoundEvent.PLAYER_HURT_DROWN);
                default -> {
                }
            }
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
        PLAYER("death.attack.player"),
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

    /**
     * Vanilla's damage immunity, the thing that gives blows their rhythm: for ten ticks
     * after any blow - half a second, the hurt flash still on screen - a blow of the same
     * strength or weaker is shrugged off entirely, and only a stronger one does anything,
     * and then only the difference between it and the blow before it. The window opens
     * with the first blow to land, is never extended by the ones it swallows, and
     * forgets what it held once it closes. Nukkit keeps the same ten-tick window on
     * every damage event (its EntityDamageEvent attack cooldown) and the same rule and
     * reset on its EntityLiving, and Netherrack follows both.
     */
    static final class HurtImmunity {

        /** How long the hurt from a blow protects, in ticks: ten, half a second of game time. */
        private static final int IMMUNITY_TICKS = 10;

        /** How many ticks the window has left. */
        private int ticks;
        /** What the blow that opened the window did, what it protects against. */
        private float remembered;

        /**
         * What a blow does right now: the damage to take, or less than 0 when the window
         * swallows it. A blow inside the window never pushes the window back.
         */
        float hit(float amount) {
            if (ticks > 0) {
                if (amount <= remembered) {
                    return -1;
                }
                return amount - remembered;
            }
            remembered = amount;
            ticks = IMMUNITY_TICKS;
            return amount;
        }

        /** One tick passes; when the window closes, it forgets the blow it protected against. */
        void tick() {
            if (ticks > 0 && --ticks == 0) {
                remembered = 0;
            }
        }

        /** Forgets everything, as coming back to life does. */
        void reset() {
            ticks = 0;
            remembered = 0;
        }
    }
}
