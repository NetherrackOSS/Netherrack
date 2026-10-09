package com.netherrack.server.player;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One connected player: who they are, how others should see them, and where they are.
 * <p>
 * Position is at eye level, the way Bedrock sends player positions in movement packets -
 * {@link #getFeetPosition()} is the same point at the player's feet, which is what
 * AddPlayerPacket wants instead.
 */
public class Player {

    /** How far a player's eyes are above their feet. */
    public static final float EYE_HEIGHT = 1.62f;

    /** A full air supply, in ticks (15 seconds), as vanilla players start with. */
    private static final short MAX_AIR_SUPPLY = 300;

    /** What a survival player may do; creative adds flying, instant building and invulnerability. */
    private static final Set<Ability> SURVIVAL_ABILITIES = EnumSet.of(Ability.BUILD, Ability.MINE,
            Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS);
    private static final Set<Ability> CREATIVE_EXTRA_ABILITIES = EnumSet.of(Ability.MAY_FLY, Ability.INSTABUILD,
            Ability.INVULNERABLE);

    private final BedrockServerSession session;
    private final String username;
    private final UUID uuid;
    private final String xuid;
    private final long entityId;
    private final SerializedSkin skin;
    private final String deviceId;
    private final BuildPlatform buildPlatform;
    private final PlayerInventory inventory = new PlayerInventory();

    // Written by this player's own network thread, read by every other player's when
    // they're shown this player - hence volatile.
    private volatile Vector3f position;
    private volatile Vector3f rotation = Vector3f.ZERO;
    private volatile boolean sneaking;
    private volatile GameType gameMode;
    private volatile Permission permission = Permission.MEMBER;
    private volatile int commandLevel;

    public Player(BedrockServerSession session, String username, UUID uuid, String xuid, long entityId,
                  SerializedSkin skin, String deviceId, BuildPlatform buildPlatform, Vector3f position,
                  GameType gameMode) {
        this.gameMode = gameMode;
        this.session = session;
        this.username = username;
        this.uuid = uuid;
        this.xuid = xuid;
        this.entityId = entityId;
        this.skin = skin;
        this.deviceId = deviceId;
        this.buildPlatform = buildPlatform;
        this.position = position;
    }

    public BedrockServerSession getSession() {
        return session;
    }

    public String getUsername() {
        return username;
    }

    public UUID getUuid() {
        return uuid;
    }

    /** Empty for players not signed into Xbox Live. */
    public String getXuid() {
        return xuid;
    }

    /**
     * Netherrack uses the same value for an entity's runtime and unique id, so this is
     * both.
     */
    public long getEntityId() {
        return entityId;
    }

    public SerializedSkin getSkin() {
        return skin;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public BuildPlatform getBuildPlatform() {
        return buildPlatform;
    }

    public PlayerInventory getInventory() {
        return inventory;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getFeetPosition() {
        return position.sub(0, EYE_HEIGHT, 0);
    }

    /** (pitch, yaw, head yaw), in degrees. */
    public Vector3f getRotation() {
        return rotation;
    }

    /**
     * The entity data every client needs about this player - their own client included.
     * Without these flags the client treats the player as a non-breathing, gravity-less
     * object: the air bubbles show, the player floats and slides instead of walking, and
     * other players' name tags are hidden. The name tag is set to show all the time, not
     * only when looked at.
     */
    public EntityDataMap createMetadata() {
        EntityDataMap metadata = new EntityDataMap();
        metadata.setFlag(EntityFlag.BREATHING, true);
        metadata.setFlag(EntityFlag.HAS_GRAVITY, true);
        metadata.setFlag(EntityFlag.HAS_COLLISION, true);
        metadata.setFlag(EntityFlag.CAN_CLIMB, true);
        metadata.setFlag(EntityFlag.CAN_SHOW_NAME, true);
        metadata.setFlag(EntityFlag.ALWAYS_SHOW_NAME, true);
        metadata.setFlag(EntityFlag.SNEAKING, sneaking);
        metadata.put(EntityDataTypes.NAME, username);
        metadata.put(EntityDataTypes.NAMETAG_ALWAYS_SHOW, (byte) 1);
        metadata.put(EntityDataTypes.AIR_SUPPLY, MAX_AIR_SUPPLY);
        metadata.put(EntityDataTypes.AIR_SUPPLY_MAX, MAX_AIR_SUPPLY);
        metadata.put(EntityDataTypes.SCALE, 1f);
        metadata.put(EntityDataTypes.WIDTH, 0.6f);
        metadata.put(EntityDataTypes.HEIGHT, 1.8f);
        return metadata;
    }

    public GameType getGameMode() {
        return gameMode;
    }

    public boolean isCreative() {
        return gameMode == GameType.CREATIVE;
    }

    /** Spectators fly through everything, unseen, and can't change or pick up anything. */
    public boolean isSpectator() {
        return gameMode == GameType.SPECTATOR;
    }

    /** Switches the player's game mode and tells their client, which changes what they can do. */
    public void setGameMode(GameType gameMode) {
        this.gameMode = gameMode;
        SetPlayerGameTypePacket packet = new SetPlayerGameTypePacket();
        packet.setGamemode(gameMode.ordinal());
        session.sendPacket(packet);
        sendAbilities();
    }

    public Permission getPermission() {
        return permission;
    }

    /** The commands this player may run: 0, or op-permission-level for an operator. See Command.getPermissionLevel(). */
    public int getCommandLevel() {
        return commandLevel;
    }

    /**
     * Sets what the player may do. Their client only hears of it with {@link #sendAbilities()},
     * so this can be set before they've joined.
     */
    public void setPermission(Permission permission, int commandLevel) {
        this.permission = permission;
        this.commandLevel = commandLevel;
    }

    /** Whether the player may break and place blocks: not as a visitor, nor in adventure or spectator mode. */
    public boolean mayBuild() {
        return permission != Permission.VISITOR && gameMode != GameType.ADVENTURE && gameMode != GameType.SPECTATOR;
    }

    /**
     * A message in the player's chat that their client words itself, from a vanilla
     * translation key (e.g. "commands.op.message"). A parameter starting with "%" is
     * itself a key.
     */
    public void sendTranslation(String key, String... params) {
        TextPacket text = new TextPacket();
        text.setType(TextPacket.Type.TRANSLATION);
        text.setNeedsTranslation(true);
        text.setSourceName("");
        text.setXuid("");
        text.setPlatformChatId("");
        text.setMessage(key);
        text.setParameters(List.of(params));
        session.sendPacket(text);
    }

    /** Tells the player's client what they may do, after their game mode or permission changes. */
    public void sendAbilities() {
        session.sendPacket(createAbilities());
    }

    /**
     * What the player's client lets them do: build, mine, attack and so on, plus flying
     * and instant building in creative, nothing at all in adventure but use things, and
     * only flying through everything in spectator.
     * Visitors may only look around; operators may also use operator commands.
     */
    public UpdateAbilitiesPacket createAbilities() {
        // One base layer that defines every ability, with the game mode's set enabled.
        AbilityLayer base = new AbilityLayer();
        base.setLayerType(AbilityLayer.Type.BASE);
        base.getAbilitiesSet().addAll(EnumSet.allOf(Ability.class));
        switch (gameMode) {
            case CREATIVE -> {
                base.getAbilityValues().addAll(SURVIVAL_ABILITIES);
                base.getAbilityValues().addAll(CREATIVE_EXTRA_ABILITIES);
            }
            case ADVENTURE -> base.getAbilityValues().addAll(EnumSet.of(Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS));
            // Always flying, through blocks, and unhurt - but touching nothing.
            case SPECTATOR -> base.getAbilityValues().addAll(EnumSet.of(Ability.MAY_FLY, Ability.FLYING,
                    Ability.NO_CLIP, Ability.INVULNERABLE));
            default -> base.getAbilityValues().addAll(SURVIVAL_ABILITIES);
        }
        if (permission == Permission.VISITOR) {
            base.getAbilityValues().removeAll(EnumSet.of(Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS));
        } else if (permission == Permission.OPERATOR) {
            base.getAbilityValues().add(Ability.OPERATOR_COMMANDS);
        }
        base.setWalkSpeed(0.1f);
        base.setFlySpeed(0.05f);
        base.setVerticalFlySpeed(1f);

        UpdateAbilitiesPacket abilities = new UpdateAbilitiesPacket();
        abilities.setUniqueEntityId(entityId);
        abilities.setPlayerPermission(permission.toPlayerPermission());
        abilities.setCommandPermission(CommandPermission.values()[commandLevel]);
        abilities.setAbilityLayers(List.of(base));
        return abilities;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public void setLocation(Vector3f position, Vector3f rotation) {
        this.position = position;
        this.rotation = rotation;
    }
}
