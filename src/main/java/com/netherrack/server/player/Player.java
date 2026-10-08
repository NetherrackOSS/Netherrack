package com.netherrack.server.player;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;

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

    private final BedrockServerSession session;
    private final String username;
    private final UUID uuid;
    private final String xuid;
    private final long entityId;
    private final SerializedSkin skin;
    private final String deviceId;
    private final BuildPlatform buildPlatform;

    // Written by this player's own network thread, read by every other player's when
    // they're shown this player - hence volatile.
    private volatile Vector3f position;
    private volatile Vector3f rotation = Vector3f.ZERO;
    private volatile boolean sneaking;

    public Player(BedrockServerSession session, String username, UUID uuid, String xuid, long entityId,
                  SerializedSkin skin, String deviceId, BuildPlatform buildPlatform, Vector3f position) {
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
