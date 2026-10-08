package com.netherrack.server.network;

import com.netherrack.server.block.Block;
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
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.NetworkPermissions;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.WorldType;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.JigsawStructureDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

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

    private static final AtomicLong ENTITY_ID_COUNTER = new AtomicLong(1);

    /** How many chunks out from spawn to actually send, regardless of what the client requests. */
    private static final int MAX_CHUNK_RADIUS = 4;

    /**
     * Bedrock player positions are at eye level, not the feet - spawning at the bare block
     * Y would put the player's feet 1.62 blocks under the floor.
     */
    private static final float PLAYER_EYE_HEIGHT = 1.62f;

    private final BedrockServerSession session;
    private final World world;
    private final VanillaData vanillaData = VanillaData.get();
    private final long runtimeEntityId = ENTITY_ID_COUNTER.getAndIncrement();

    private volatile String username;
    private volatile String uuid;
    private volatile boolean awaitingHandshakeAck;
    private volatile Vector3f spawnPosition;
    private volatile boolean spawned;

    public NetherrackPacketHandler(BedrockServerSession session, World world) {
        this.session = session;
        this.world = world;
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
        spawnPosition = Vector3f.from(spawnX, spawnY + PLAYER_EYE_HEIGHT, spawnZ);

        Logger.info("Player " + username + " cannot find the saved spawnpoint, reset the spawnpoint to "
                + spawnX + " " + spawnY + ".0 " + spawnZ + " / " + world.getName());

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
            PlayStatusPacket spawn = new PlayStatusPacket();
            spawn.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
            session.sendPacket(spawn);
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        Logger.info(username + " joined the game.");

        return PacketSignal.HANDLED;
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

        StartGamePacket startGame = new StartGamePacket();

        startGame.setUniqueEntityId(runtimeEntityId);
        startGame.setRuntimeEntityId(runtimeEntityId);
        startGame.setPlayerGameType(GameType.SURVIVAL);
        startGame.setPlayerPosition(spawnPosition);
        startGame.setRotation(Vector2f.from(0, 0));

        startGame.setSeed(0L);
        startGame.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGame.setCustomBiomeName("");
        startGame.setDimensionId(0); // overworld
        startGame.setGeneratorId(2); // flat
        startGame.setLevelGameType(GameType.SURVIVAL);
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
        // all empty/default here since Netherrack doesn't have a creative inventory,
        // crafting recipes or armor trims yet.
        //
        // SyncEntityProperty is deliberately not sent: vanilla sends one per entity type
        // that has properties ({type, properties...}), and an empty one makes the client
        // reject the join with a generic "Block" error. It belongs here again once there are
        // entities with real property data to send.
        session.sendPacket(new CreativeContentPacket());

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
