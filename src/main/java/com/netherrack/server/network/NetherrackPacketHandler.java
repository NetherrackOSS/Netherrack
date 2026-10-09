package com.netherrack.server.network;

import com.netherrack.server.Netherrack;
import com.netherrack.server.block.Block;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.entity.EntityIds;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.player.GameModes;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerInventory;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.player.SkinParser;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.Chunk;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.EduSharedUriResource;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.NetworkPermissions;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.WorldType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.JigsawStructureDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.jose4j.json.JsonUtil;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives one player's login sequence:
 * RequestNetworkSettings -> NetworkSettings -> Login -> PlayStatus(LOGIN_SUCCESS)
 * -> ResourcePacksInfo -> ResourcePackClientResponse -> ResourcePackStack -> DimensionData
 * -> JigsawStructureData -> VoxelShapes -> StartGame -> BiomeDefinitionList -> AvailableEntityIdentifiers
 * -> ItemComponent -> CreativeContent -> CraftingData -> TrimData
 * -> RequestChunkRadius -> ChunkRadiusUpdated -> NetworkChunkPublisherUpdate -> LevelChunk(s)
 * -> PlayStatus(PLAYER_SPAWN) -> SetLocalPlayerAsInitialized
 * <p>
 * Targets Bedrock_v2193 (1.26.50) because that's what the real client actually speaks on
 * the wire - the codec has to match the client's real protocol version, full stop, or every
 * packet after the first few shared fields fails to decode. The bundled vanilla data (see
 * VanillaData) is a separate concern: it's currently from the "bedrock-1.26.30" BedrockData
 * tag, the closest available match, not an exact one - a couple of patches behind, so a
 * handful of newer blocks/entities may be missing or render as the unknown-block texture.
 */
public class NetherrackPacketHandler implements BedrockPacketHandler {

    /** How many chunks out from spawn to actually send, regardless of what the client requests. */
    private static final int MAX_CHUNK_RADIUS = 4;

    private final BedrockServerSession session;
    private final World world;
    private final PlayerManager players;
    private final BlockInteraction blockInteraction;
    private final ItemEntities itemEntities;
    private final Netherrack netherrack;
    private final VanillaData vanillaData = VanillaData.get();
    private final long runtimeEntityId = EntityIds.next();

    private volatile String username;
    private volatile String uuid;
    private volatile boolean awaitingHandshakeAck;
    private volatile Vector3f spawnPosition;
    private volatile boolean spawned;
    private volatile String xuid = "";
    private volatile SerializedSkin skin = SkinParser.fallback();
    private volatile String deviceId = "";
    private volatile BuildPlatform buildPlatform = BuildPlatform.UNKNOWN;
    private volatile Player player;
    private volatile boolean inventoryOpen;

    public NetherrackPacketHandler(BedrockServerSession session, World world, PlayerManager players, Netherrack netherrack) {
        this.session = session;
        this.world = world;
        this.players = players;
        this.netherrack = netherrack;
        this.itemEntities = netherrack.getItemEntities();
        this.blockInteraction = new BlockInteraction(world, players, itemEntities);
    }

    /**
     * Best-effort display name for logging, even before login finishes.
     */
    public String getDisplayName() {
        return username != null ? username : String.valueOf(session.getSocketAddress());
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        int clientProtocol = packet.getProtocolVersion();
        int serverProtocol = Bedrock_v2193.CODEC.getProtocolVersion();

        if (clientProtocol != serverProtocol) {
            Logger.warn("Client requested protocol " + clientProtocol + ", server only supports " + serverProtocol
                    + " (" + Bedrock_v2193.CODEC.getMinecraftVersion() + "); continuing anyway since multi-version support isn't implemented yet.");
        }

        session.setCodec(Bedrock_v2193.CODEC);

        NetworkSettingsPacket settings = new NetworkSettingsPacket();
        settings.setCompressionThreshold(512);
        settings.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        session.sendPacketImmediately(settings);
        session.setCompression(PacketCompressionAlgorithm.ZLIB);

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        try {
            // Handles both the legacy 3-link certificate chain (and the 1-link self-signed/
            // offline variant) and the newer single-Token format that signed-in Xbox Live
            // clients use. Does real signature verification against Mojang where applicable,
            // which means this makes a blocking HTTPS call on this event loop thread the
            // first time - fine for now, worth moving off-thread once more is going on.
            ChainValidationResult result = EncryptionUtils.validatePayload(packet.getAuthPayload());
            ChainValidationResult.IdentityClaims identityClaims = result.identityClaims();
            ChainValidationResult.IdentityData identityData = identityClaims.extraData;

            username = identityData.displayName;
            if (identityData.xuid != null) {
                xuid = identityData.xuid;
            }
            readClientData(packet.getClientJwt(), identityClaims);
            uuid = identityData.identity != null
                    ? identityData.identity.toString()
                    : UUID.nameUUIDFromBytes(("offline:" + username).getBytes(StandardCharsets.UTF_8)).toString();

            if (!result.signed()) {
                Logger.warn("Player " + username + " is not signed into Xbox Live (offline/self-signed login).");
            } else {
                // Xbox Live-signed-in clients require the server to complete this Diffie-
                // Hellman handshake before anything past login will work - if we skip it,
                // the client starts expecting an encrypted stream that never comes, and
                // just silently stalls (no error either side) rather than disconnecting.
                // completeLogin() is deferred to handle(ClientToServerHandshakePacket) below
                // rather than called right after this - waiting for the client's actual ack
                // instead of assuming ordered delivery makes that safe. Flag is only set
                // once the handshake packet actually sent without throwing - if it throws,
                // we fall through to completeLogin() below same as an unsigned login, rather
                // than leaving the connection stuck waiting for an ack that'll never come.
                beginEncryptionHandshake(identityClaims);
                awaitingHandshakeAck = true;
            }
        } catch (Exception e) {
            Logger.warn("Failed to validate the login chain, falling back to a random profile: " + e.getMessage());
        }

        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (username == null) {
            username = "Player" + ThreadLocalRandom.current().nextInt(1000, 9999);
        }

        if (!awaitingHandshakeAck) {
            completeLogin();
        }
        return PacketSignal.HANDLED;
    }

    /**
     * Reads the skin and device details from the client data JWT, checked against the
     * identity key from the login chain. A failure here only costs the player their skin
     * (they get a blank one), not the login.
     */
    private void readClientData(String clientJwt, ChainValidationResult.IdentityClaims identityClaims) {
        try {
            byte[] payload = EncryptionUtils.verifyClientData(clientJwt, identityClaims.parsedIdentityPublicKey());
            Map<String, Object> clientData = JsonUtil.parseJson(new String(payload, StandardCharsets.UTF_8));

            skin = SkinParser.parse(clientData);
            if (clientData.get("DeviceId") instanceof String id) {
                deviceId = id;
            }
            if (clientData.get("DeviceOS") instanceof Number os) {
                buildPlatform = BuildPlatform.from(os.intValue());
            }
        } catch (Exception e) {
            Logger.warn("Could not read the client data for " + username + ", using a blank skin: " + e.getMessage());
        }
    }

    /**
     * Performs the server side of Bedrock's Diffie-Hellman encryption handshake: generates
     * a server EC key pair + random token, derives the shared AES secret using the client's
     * identity public key (from the already-validated login chain), switches this session
     * to encrypted from here on, then sends the client the handshake packet it needs to
     * derive the same secret on its end. The @NoEncryption annotation on that packet class
     * means it goes out in plaintext despite encryption already being enabled - everything
     * sent after it won't be.
     */
    private void beginEncryptionHandshake(ChainValidationResult.IdentityClaims identityClaims) throws Exception {
        Logger.info("Starting the encryption handshake for " + username + " (signed into Xbox Live)...");

        KeyPair serverKeyPair = EncryptionUtils.createKeyPair();
        byte[] token = EncryptionUtils.generateRandomToken();

        // Order matters here: send the (plaintext, @NoEncryption) handshake packet BEFORE
        // flipping encryption on, not after. This matches AllayMC's proven-working sequence
        // rather than the reverse order used previously.
        ServerToClientHandshakePacket handshake = new ServerToClientHandshakePacket();
        handshake.setJwt(EncryptionUtils.createHandshakeJwt(serverKeyPair, token));
        session.sendPacketImmediately(handshake);

        SecretKey secretKey = EncryptionUtils.getSecretKey(
                serverKeyPair.getPrivate(), identityClaims.parsedIdentityPublicKey(), token);
        session.enableEncryption(secretKey);

        Logger.info("Sent the encryption handshake packet to " + username + ", waiting for its response...");
    }

    @Override
    public PacketSignal handle(ClientToServerHandshakePacket packet) {
        // Acknowledges the client finished deriving its half of the shared secret and
        // switched its own pipeline to decrypt from here on - this is what completeLogin()
        // was waiting on, since sending encrypted packets before this arrives risks the
        // client not being ready to decrypt them yet.
        Logger.info("Player " + username + " acknowledged the encryption handshake.");
        awaitingHandshakeAck = false;
        completeLogin();
        return PacketSignal.HANDLED;
    }

    private void completeLogin() {
        // There's no player data storage yet, so every login is treated as a new profile.
        Logger.info("Player data not found for \"" + uuid + "\", creating new profile");

        // Kept within chunk (0,0), the one Netherrack always pre-generates - otherwise the
        // player could spawn in a chunk we haven't sent yet and fall through the void.
        double spawnX = ThreadLocalRandom.current().nextDouble(-6, 6);
        double spawnZ = ThreadLocalRandom.current().nextDouble(-6, 6);
        int spawnY = world.getSpawnY();
        spawnPosition = Vector3f.from(spawnX, spawnY + Player.EYE_HEIGHT, spawnZ);

        Logger.info("Player " + username + " cannot find the saved spawnpoint, reset the spawnpoint to "
                + spawnX + " " + spawnY + ".0 " + spawnZ + " / " + world.getName());

        player = new Player(session, username, UUID.fromString(uuid), xuid, runtimeEntityId,
                skin, deviceId, buildPlatform, spawnPosition, defaultGameMode());

        PlayStatusPacket loginSuccess = new PlayStatusPacket();
        loginSuccess.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendPacket(loginSuccess);

        // Empty resource pack list - nothing required to join yet. worldTemplateId has to
        // be set to *something* non-null, or the encoder throws (checkNotNull) and this
        // packet silently never reaches the client - which looks exactly like being stuck
        // on the resource pack screen, since the client never hears it can move on.
        ResourcePacksInfoPacket packsInfo = new ResourcePacksInfoPacket();
        packsInfo.setWorldTemplateId(new UUID(0, 0));
        packsInfo.setWorldTemplateVersion("");
        session.sendPacket(packsInfo);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        switch (packet.getStatus()) {
            case HAVE_ALL_PACKS -> {
                ResourcePackStackPacket stack = new ResourcePackStackPacket();
                stack.setGameVersion(Bedrock_v2193.CODEC.getMinecraftVersion());
                session.sendPacket(stack);
            }
            case COMPLETED -> sendStartGame();
            case REFUSED -> session.disconnect("You must accept the resource packs to play.");
            default -> {
                // NONE / SEND_PACKS: nothing to do, since we never send any packs to request.
            }
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(RequestChunkRadiusPacket packet) {
        int radius = Math.min(packet.getRadius(), MAX_CHUNK_RADIUS);

        ChunkRadiusUpdatedPacket radiusUpdated = new ChunkRadiusUpdatedPacket();
        radiusUpdated.setRadius(radius);
        session.sendPacket(radiusUpdated);

        // Sent ahead of the chunks so the client already treats this area as in range
        // when they arrive.
        NetworkChunkPublisherUpdatePacket publisherUpdate = new NetworkChunkPublisherUpdatePacket();
        publisherUpdate.setPosition(Vector3i.from(spawnPosition.getFloorX(), spawnPosition.getFloorY(), spawnPosition.getFloorZ()));
        publisherUpdate.setRadius(radius * 16);
        session.sendPacket(publisherUpdate);

        sendChunks(radius);

        // PLAYER_SPAWN is what moves the client off the loading screen, and the client only
        // sends SetLocalPlayerAsInitialized after receiving it - so it has to go out here,
        // unprompted, once the spawn chunks are on their way. The client can re-request
        // its radius later (e.g. a render distance change), hence the once-only flag.
        if (!spawned) {
            spawned = true;
            sendOwnPlayerState();

            PlayStatusPacket spawn = new PlayStatusPacket();
            spawn.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
            session.sendPacket(spawn);
        }

        return PacketSignal.HANDLED;
    }

    /**
     * What the client needs to know about its own player before spawning: its entity
     * flags (breathing, gravity, collision - see Player.createMetadata()), its attributes
     * (walk speed, health, hunger), and what it's allowed to do. Without these the client
     * shows the air bar and moves the player as if it were floating on ice.
     */
    private void sendOwnPlayerState() {
        SetEntityDataPacket entityData = new SetEntityDataPacket();
        entityData.setRuntimeEntityId(runtimeEntityId);
        entityData.setMetadata(player.createMetadata());
        session.sendPacket(entityData);

        // Vanilla defaults for a new survival player: (name, min, max, value, default).
        float noLimit = Float.MAX_VALUE;
        UpdateAttributesPacket attributes = new UpdateAttributesPacket();
        attributes.setRuntimeEntityId(runtimeEntityId);
        attributes.setAttributes(List.of(
                new AttributeData("minecraft:health", 0, 20, 20, 20),
                new AttributeData("minecraft:absorption", 0, noLimit, 0, 0),
                new AttributeData("minecraft:movement", 0, noLimit, 0.1f, 0.1f),
                new AttributeData("minecraft:underwater_movement", 0, noLimit, 0.02f, 0.02f),
                new AttributeData("minecraft:lava_movement", 0, noLimit, 0.02f, 0.02f),
                new AttributeData("minecraft:player.hunger", 0, 20, 20, 20),
                new AttributeData("minecraft:player.saturation", 0, 20, 5, 5),
                new AttributeData("minecraft:player.exhaustion", 0, 5, 0, 0),
                new AttributeData("minecraft:player.level", 0, 24791, 0, 0),
                new AttributeData("minecraft:player.experience", 0, 1, 0, 0)));
        session.sendPacket(attributes);

        session.sendPacket(player.createAbilities());

        giveStarterItems();
    }

    /**
     * Until there's a real inventory system, every player starts with a stack of each
     * block Netherrack has, so there's something to build with.
     */
    private void giveStarterItems() {
        PlayerInventory inventory = player.getInventory();
        inventory.set(0, vanillaData.blockItem(Blocks.GRASS_BLOCK, 64));
        inventory.set(1, vanillaData.blockItem(Blocks.COBBLESTONE, 64));
        sendInventory();
    }

    /** Shows the client its whole inventory as the server has it. */
    private void sendInventory() {
        for (BedrockPacket packet : player.getInventory().contentPackets()) {
            session.sendPacket(packet);
        }
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        Logger.info(username + " joined the game.");

        // Only now, with the client past its loading screen, is the player actually in
        // the world for others to see.
        players.join(player);
        itemEntities.showAll(player);

        return PacketSignal.HANDLED;
    }

    /**
     * With server-authoritative movement (see StartGame), the client sends its position
     * and rotation in one of these every tick, moving or not. The client moves its own
     * player; the server's job for now is to remember where they are and show everyone
     * else. Positions aren't checked yet - whatever the client says is accepted.
     */
    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }

        // Checked before the "standing still" shortcut below, since players can break
        // blocks and start or stop sneaking without moving.
        if (!packet.getPlayerActions().isEmpty()) {
            blockInteraction.handleActions(player, packet.getPlayerActions());
        }
        if (packet.getInputData().contains(PlayerAuthInputData.START_SNEAKING)) {
            player.setSneaking(true);
            players.broadcastEntityData(player);
        } else if (packet.getInputData().contains(PlayerAuthInputData.STOP_SNEAKING)) {
            player.setSneaking(false);
            players.broadcastEntityData(player);
        }

        Vector3f position = packet.getPosition();
        Vector3f rotation = packet.getRotation();
        if (position.equals(player.getPosition()) && rotation.equals(player.getRotation())) {
            return PacketSignal.HANDLED; // standing still - nothing new to tell anyone
        }

        player.setLocation(position, rotation);
        boolean onGround = packet.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION)
                && packet.getDelta().getY() <= 0;
        players.broadcastMovement(player, onGround, packet.getTick());

        return PacketSignal.HANDLED;
    }

    /**
     * The client sends this whenever its player swings their arm (hitting, mining, or
     * swinging at nothing), so other players can be shown it. The client's own entity id
     * in the packet isn't trusted - the swing is always passed on as this player's.
     */
    @Override
    public PacketSignal handle(AnimatePacket packet) {
        if (player != null && packet.getAction() == AnimatePacket.Action.SWING_ARM) {
            players.broadcastSwing(player, packet.getSwingSource());
        }
        return PacketSignal.HANDLED;
    }

    /**
     * A message typed in chat. A leading "/" runs it as a server command (through the same
     * CommandManager the console uses) instead of broadcasting it - so "/stop" in chat does
     * the same thing as typing "stop" in the console. Anything else is broadcast to every
     * player as an ordinary chat message and logged to the console, the same way the
     * console itself already logs everything else.
     */
    @Override
    public PacketSignal handle(TextPacket packet) {
        if (player == null || packet.getType() != TextPacket.Type.CHAT) {
            return PacketSignal.HANDLED;
        }

        String message = packet.getMessage();
        if (message == null || message.isBlank()) {
            return PacketSignal.HANDLED;
        }
        message = message.trim();

        if (message.startsWith("/")) {
            Logger.info(username + " issued command: " + message);
            netherrack.getCommandManager().dispatch(netherrack, message.substring(1));
        } else {
            Logger.info("<" + username + "> " + message);
            players.broadcastChat(player, message);
        }

        return PacketSignal.HANDLED;
    }

    /**
     * Using the held item. The only use handled so far is a right click on a block
     * (action type 0), which places the held block against it.
     * <p>
     * A "normal" transaction is the client throwing items out from the hotbar (pressing
     * Q), which it has already done on its side: one action takes the items out of an
     * inventory slot, the other puts them into the world. The client is then shown its
     * inventory as the server has it, whether or not the throw was valid.
     */
    @Override
    public PacketSignal handle(InventoryTransactionPacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }
        if (packet.getTransactionType() == InventoryTransactionType.ITEM_USE && packet.getActionType() == 0) {
            blockInteraction.handleItemUse(player, packet);
        } else if (packet.getTransactionType() == InventoryTransactionType.NORMAL) {
            throwFromHotbar(packet.getActions());
            sendInventory();
        }
        return PacketSignal.HANDLED;
    }

    /** The client asking to move items around its inventory screen - see PlayerInventory. */
    @Override
    public PacketSignal handle(ItemStackRequestPacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }
        ItemStackResponsePacket response = new ItemStackResponsePacket();
        for (ItemStackRequest request : packet.getRequests()) {
            response.getEntries().add(player.getInventory().handle(request, player.isCreative()));
        }
        session.sendPacket(response);
        throwDroppedItems();
        return PacketSignal.HANDLED;
    }

    private void throwFromHotbar(List<InventoryActionData> actions) {
        InventoryActionData intoWorld = null;
        InventoryActionData fromSlot = null;
        for (InventoryActionData action : actions) {
            InventorySource source = action.getSource();
            if (source.getType() == InventorySource.Type.WORLD_INTERACTION && action.getSlot() == 0) {
                intoWorld = action;
            } else if (source.getType() == InventorySource.Type.CONTAINER && source.getContainerId() == 0) {
                fromSlot = action;
            }
        }
        if (actions.size() != 2 || intoWorld == null || fromSlot == null || fromSlot.getFromItem().isNull()) {
            Logger.debug("Ignored an inventory transaction from " + username + " that isn't a throw: " + actions);
            return;
        }
        player.getInventory().throwFromSlot(fromSlot.getSlot(),
                fromSlot.getFromItem().getDefinition().getRuntimeId(), intoWorld.getToItem().getCount());
        throwDroppedItems();
    }

    /** Puts whatever the player's inventory threw out into the world, flying the way they look. */
    private void throwDroppedItems() {
        for (ItemData item : player.getInventory().takeDropped()) {
            itemEntities.throwFrom(player, item);
        }
    }

    /**
     * The client opening its own inventory screen. It shows the screen only once the
     * server answers with ContainerOpen, which vanilla places at the player's feet.
     */
    @Override
    public PacketSignal handle(InteractPacket packet) {
        if (player == null || packet.getAction() != InteractPacket.Action.OPEN_INVENTORY || inventoryOpen) {
            return PacketSignal.HANDLED;
        }
        inventoryOpen = true;

        ContainerOpenPacket open = new ContainerOpenPacket();
        open.setId((byte) 0);
        open.setType(ContainerType.INVENTORY);
        open.setBlockPosition(player.getFeetPosition().toInt());
        open.setUniqueEntityId(-1);
        session.sendPacket(open);
        return PacketSignal.HANDLED;
    }

    /**
     * The client closing a screen. The server confirms it, and for the inventory screen
     * puts what was left on the cursor and in the crafting grid back into the inventory.
     */
    @Override
    public PacketSignal handle(ContainerClosePacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }
        inventoryOpen = false;

        ContainerClosePacket close = new ContainerClosePacket();
        close.setId(packet.getId());
        close.setType(packet.getType());
        close.setServerInitiated(false);
        session.sendPacket(close);

        if (packet.getId() == 0 && player.getInventory().returnScreenItems()) {
            sendInventory();
            throwDroppedItems();
        }
        return PacketSignal.HANDLED;
    }

    /** Some player actions arrive on their own: a creative player's instant block break is one. */
    @Override
    public PacketSignal handle(PlayerActionPacket packet) {
        if (player != null && packet.getAction() == PlayerActionType.DIMENSION_CHANGE_REQUEST_OR_CREATIVE_DESTROY_BLOCK) {
            blockInteraction.creativeBreak(player, packet.getBlockPosition());
        }
        return PacketSignal.HANDLED;
    }

    /** The game mode players join in, from "gamemode" in server.properties. */
    private GameType defaultGameMode() {
        String configured = netherrack.getConfig().get("gamemode", "survival");
        GameType gameMode = GameModes.parse(configured);
        if (gameMode == null) {
            Logger.warn("Unknown gamemode \"" + configured + "\" in server.properties, using survival.");
            return GameType.SURVIVAL;
        }
        return gameMode;
    }

    /** Called once the connection has closed, for whatever reason. */
    public void onDisconnect() {
        if (player != null) {
            blockInteraction.stopBreaking();
            players.leave(player);
        }
    }

    private void sendStartGame() {
        // Must be sent before StartGame, not after - AllayMC's own comment on the
        // equivalent code is explicit that chunks get ignored and the client can't join
        // without this preceding it.
        //
        // Empty definitions list: the client keeps its default overworld bounds (-64 to
        // 319), which is what ChunkEncoder sends (all 24 sub-chunks inline in each
        // LevelChunkPacket). Clients join fine with this; the "Block" error on joining
        // turned out to be caused by an empty SyncEntityPropertyPacket, not the dimension
        // height. If this ever declares different bounds, ChunkEncoder's sub-chunk range
        // has to change to match.
        session.sendPacket(new DimensionDataPacket());

        // Both of these also have to arrive before StartGame on current clients - without the
        // jigsaw data the client disconnects with "Missing structure data from server". Empty
        // is enough since Netherrack doesn't generate any structures; GeyserMC sends the same
        // empty shape for both. Every list is set explicitly rather than left to defaults so
        // nothing reaches the encoder as null.
        JigsawStructureDataPacket jigsawData = new JigsawStructureDataPacket();
        jigsawData.setJigsawStructureDataTag(NbtMap.builder()
                .putList("processors", NbtType.COMPOUND, new ArrayList<>())
                .putList("template_pools", NbtType.COMPOUND, new ArrayList<>())
                .putList("jigsaws", NbtType.COMPOUND, new ArrayList<>())
                .putList("structure_sets", NbtType.COMPOUND, new ArrayList<>())
                .build());
        session.sendPacket(jigsawData);

        VoxelShapesPacket voxelShapes = new VoxelShapesPacket();
        voxelShapes.setShapes(new ArrayList<>());
        voxelShapes.setNameMap(new HashMap<>());
        session.sendPacket(voxelShapes);

        // Client packets that carry item stacks can only be decoded once the codec knows
        // the item ids, so this has to be in place before the client starts sending them.
        session.getPeer().getCodecHelper().setItemDefinitions(vanillaData.getItemRegistry());
        session.getPeer().getCodecHelper().setBlockDefinitions(new HashedBlockDefinitions());

        StartGamePacket startGame = new StartGamePacket();

        startGame.setUniqueEntityId(runtimeEntityId);
        startGame.setRuntimeEntityId(runtimeEntityId);
        startGame.setPlayerGameType(player.getGameMode());
        startGame.setPlayerPosition(spawnPosition);
        startGame.setRotation(Vector2f.from(0, 0));

        startGame.setSeed(0L);
        startGame.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGame.setCustomBiomeName("");
        startGame.setDimensionId(0); // overworld
        startGame.setGeneratorId(2); // flat
        startGame.setLevelGameType(defaultGameMode());
        startGame.setDifficulty(1);
        startGame.setDefaultSpawn(Vector3i.from(spawnPosition.getFloorX(), spawnPosition.getFloorY(), spawnPosition.getFloorZ()));
        startGame.setAchievementsDisabled(true);
        startGame.setDayCycleStopTime(0);
        startGame.setEduEditionOffers(0);
        startGame.setEduFeaturesEnabled(false);
        startGame.setEducationProductionId("");
        startGame.setRainLevel(0);
        startGame.setLightningLevel(0);
        startGame.setPlatformLockedContentConfirmed(false);
        startGame.setMultiplayerGame(true);
        startGame.setBroadcastingToLan(true);
        startGame.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGame.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        startGame.setCommandsEnabled(true);
        startGame.setTexturePacksRequired(false);
        startGame.setExperimentsPreviouslyToggled(false);
        startGame.setBonusChestEnabled(false);
        startGame.setStartingWithMap(false);
        startGame.setTrustingPlayers(true);
        startGame.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        startGame.setServerChunkTickRange(4);
        startGame.setBehaviorPackLocked(false);
        startGame.setResourcePackLocked(false);
        startGame.setFromLockedWorldTemplate(false);
        startGame.setUsingMsaGamertagsOnly(false);
        startGame.setFromWorldTemplate(false);
        startGame.setWorldTemplateOptionLocked(false);
        startGame.setOnlySpawningV1Villagers(false);
        startGame.setVanillaVersion(Bedrock_v2193.CODEC.getMinecraftVersion());
        startGame.setLimitedWorldWidth(0);
        startGame.setLimitedWorldHeight(0);
        startGame.setNetherType(false);
        startGame.setEduSharedUriResource(EduSharedUriResource.EMPTY);
        startGame.setForceExperimentalGameplay(OptionalBoolean.empty());
        startGame.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        startGame.setDisablingPlayerInteractions(false);
        startGame.setDisablingPersonas(false);
        startGame.setDisablingCustomSkins(false);

        startGame.setLevelId(uuid);
        startGame.setLevelName(world.getName());
        startGame.setPremiumWorldTemplateId("");
        startGame.setTrial(false);

        startGame.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER);
        startGame.setRewindHistorySize(0);
        startGame.setServerAuthoritativeBlockBreaking(true);

        startGame.setCurrentTick(0);
        startGame.setEnchantmentSeed(0);
        startGame.setBlockPalette(vanillaData.getBlockPalette());
        // The vanilla blocks defined in the vanilla behavior pack rather than built into the
        // client - see VanillaData. Without these the client has no such blocks, and warns
        // about every texture its resource pack has for them.
        startGame.getBlockProperties().addAll(vanillaData.getDataDrivenBlocks());
        startGame.setMultiplayerCorrelationId(UUID.randomUUID().toString());
        startGame.setInventoriesServerAuthoritative(true);
        startGame.setServerEngine("Netherrack");
        startGame.setBlockRegistryChecksum(0L); // 0 = client won't validate it
        startGame.setWorldTemplateId(new UUID(0, 0));
        startGame.setEditorWorldType(WorldType.NON_EDITOR);
        startGame.setClientSideGenerationEnabled(false);
        startGame.setEmoteChatMuted(false);
        // Hashed scheme: block identity is the hash of each block's own NBT state, computed
        // the same way on both ends, rather than an index into a palette whose ordering has
        // to exactly match the client's internal table version-for-version. AllayMC (a real,
        // working server on this same library) uses this too - switched to it after an
        // ordinal-index mismatch against our 1.26.30 data caused clients to disconnect with
        // a generic "Block" error on first receiving chunk data.
        startGame.setBlockNetworkIdsHashed(true);
        startGame.setCreatedInEditor(false);
        startGame.setExportedFromEditor(false);
        startGame.setNetworkPermissions(NetworkPermissions.DEFAULT);
        startGame.setHardcore(false);
        startGame.setServerId("");
        startGame.setWorldId("");
        startGame.setScenarioId("");
        // Both are written unconditionally by the encoder, so leaving either null makes
        // StartGame fail to encode - and since that failure is silent, the client just
        // never hears StartGame and sits on the loading screen forever.
        startGame.setOwnerId("");
        startGame.setPlayerPropertyData(NbtMap.EMPTY);
        startGame.getItemDefinitions().addAll(vanillaData.getItemDefinitions());

        session.sendPacket(startGame);

        BiomeDefinitionListPacket biomes = new BiomeDefinitionListPacket();
        biomes.setBiomes(vanillaData.getBiomeDefinitions());
        session.sendPacket(biomes);

        AvailableEntityIdentifiersPacket entities = new AvailableEntityIdentifiersPacket();
        entities.setIdentifiers(vanillaData.getEntityIdentifiers());
        session.sendPacket(entities);

        // Clients don't have a built-in item list any more - every item (block items
        // included) has to come from the server, or the client can't finish joining.
        ItemComponentPacket items = new ItemComponentPacket();
        items.getItems().addAll(vanillaData.getItemDefinitions());
        session.sendPacket(items);

        // The rest of these are part of the standard join sequence real clients expect to
        // see regardless of whether a server actually has anything custom to put in them -
        // empty/default here since Netherrack doesn't have crafting recipes or armor trims yet.
        //
        // SyncEntityProperty is deliberately not sent: vanilla sends one per entity type
        // that has properties ({type, properties...}), and an empty one makes the client
        // reject the join with a generic "Block" error. It belongs here again once there are
        // entities with real property data to send.
        // Sent to everyone whatever their game mode, as vanilla does: it's only usable in
        // creative, but switching modes doesn't send it again.
        CreativeContentPacket creative = new CreativeContentPacket();
        creative.getGroups().addAll(vanillaData.getCreativeGroups());
        creative.getContents().addAll(vanillaData.getCreativeItems());
        session.sendPacket(creative);

        // cleanRecipes left false (the default) deliberately - true would clear the
        // client's own built-in vanilla recipes, and we have nothing to replace them with
        // yet, which would leave the player unable to craft anything at all.
        session.sendPacket(new CraftingDataPacket());

        session.sendPacket(new TrimDataPacket());
    }

    private void sendChunks(int radius) {
        int centerChunkX = spawnPosition.getFloorX() >> 4;
        int centerChunkZ = spawnPosition.getFloorZ() >> 4;

        for (int cx = centerChunkX - radius; cx <= centerChunkX + radius; cx++) {
            for (int cz = centerChunkZ - radius; cz <= centerChunkZ + radius; cz++) {
                sendChunk(cx, cz);
            }
        }
    }

    private void sendChunk(int chunkX, int chunkZ) {
        Chunk chunk = world.getChunk(chunkX, chunkZ);

        LevelChunkPacket packet = new LevelChunkPacket();
        packet.setChunkX(chunkX);
        packet.setChunkZ(chunkZ);
        packet.setSubChunksLength(ChunkEncoder.SUB_CHUNK_COUNT);
        packet.setCachingEnabled(false);
        packet.setRequestSubChunks(false);
        packet.setData(ChunkEncoder.encodeChunk(chunk));

        session.sendPacket(packet);
    }
}
