package com.netherrack.server.network;

import com.netherrack.server.block.Block;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.Chunk;
import com.netherrack.server.world.World;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
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
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.CompressedBiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives one player's login sequence:
 * RequestNetworkSettings -> NetworkSettings -> Login -> PlayStatus(LOGIN_SUCCESS)
 * -> ResourcePacksInfo -> ResourcePackClientResponse -> ResourcePackStack -> StartGame
 * -> CompressedBiomeDefinitionList -> AvailableEntityIdentifiers -> RequestChunkRadius
 * -> ChunkRadiusUpdated -> LevelChunk(s) -> NetworkChunkPublisherUpdate
 * -> SetLocalPlayerAsInitialized -> PlayStatus(PLAYER_SPAWN)
 * <p>
 * Targets Bedrock_v776 (1.21.60) rather than the newest codec available, because that's
 * the exact version the bundled vanilla block palette/entity/biome data (see VanillaData)
 * matches - protocol compatibility here depends on the client and server agreeing on that
 * data byte-for-byte, so using a newer codec with this data would likely be worse than
 * using an older one that actually lines up.
 */
public class NetherrackPacketHandler implements BedrockPacketHandler {

    private static final AtomicLong ENTITY_ID_COUNTER = new AtomicLong(1);

    /** How many chunks out from spawn to actually send, regardless of what the client requests. */
    private static final int MAX_CHUNK_RADIUS = 4;

    private final BedrockServerSession session;
    private final World world;
    private final VanillaData vanillaData = VanillaData.get();
    private final long runtimeEntityId = ENTITY_ID_COUNTER.getAndIncrement();

    private volatile String username;
    private volatile String uuid;
    private volatile Vector3f spawnPosition;

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
        int serverProtocol = Bedrock_v776.CODEC.getProtocolVersion();

        if (clientProtocol != serverProtocol) {
            Logger.warn("Client requested protocol " + clientProtocol + ", server only supports " + serverProtocol
                    + " (" + Bedrock_v776.CODEC.getMinecraftVersion() + "); continuing anyway since multi-version support isn't implemented yet.");
        }

        session.setCodec(Bedrock_v776.CODEC);

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
            ChainValidationResult.IdentityData identityData = result.identityClaims().extraData;

            username = identityData.displayName;
            uuid = identityData.identity != null
                    ? identityData.identity.toString()
                    : UUID.nameUUIDFromBytes(("offline:" + username).getBytes(StandardCharsets.UTF_8)).toString();

            if (!result.signed()) {
                Logger.warn("Player " + username + " is not signed into Xbox Live (offline/self-signed login).");
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

        // There's no player data storage yet, so every login is treated as a new profile.
        Logger.info("Player data not found for \"" + uuid + "\", creating new profile");

        // Kept within chunk (0,0), the one Netherrack always pre-generates - otherwise the
        // player could spawn in a chunk we haven't sent yet and fall through the void.
        double spawnX = ThreadLocalRandom.current().nextDouble(-6, 6);
        double spawnZ = ThreadLocalRandom.current().nextDouble(-6, 6);
        int spawnY = world.getSpawnY();
        spawnPosition = Vector3f.from(spawnX, spawnY, spawnZ);

        Logger.info("Player " + username + " cannot find the saved spawnpoint, reset the spawnpoint to "
                + spawnX + " " + spawnY + ".0 " + spawnZ + " / " + world.getName());

        PlayStatusPacket loginSuccess = new PlayStatusPacket();
        loginSuccess.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendPacket(loginSuccess);

        // Empty resource pack list - nothing required to join yet.
        session.sendPacket(new ResourcePacksInfoPacket());

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        switch (packet.getStatus()) {
            case HAVE_ALL_PACKS -> {
                ResourcePackStackPacket stack = new ResourcePackStackPacket();
                stack.setGameVersion(Bedrock_v776.CODEC.getMinecraftVersion());
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

        sendChunks(radius);

        NetworkChunkPublisherUpdatePacket publisherUpdate = new NetworkChunkPublisherUpdatePacket();
        publisherUpdate.setPosition(Vector3i.from(spawnPosition.getFloorX(), spawnPosition.getFloorY(), spawnPosition.getFloorZ()));
        publisherUpdate.setRadius(radius * 16);
        session.sendPacket(publisherUpdate);

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        PlayStatusPacket spawn = new PlayStatusPacket();
        spawn.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        session.sendPacket(spawn);

        Logger.info(username + " joined the game.");

        return PacketSignal.HANDLED;
    }

    private void sendStartGame() {
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
        startGame.setVanillaVersion(Bedrock_v776.CODEC.getMinecraftVersion());
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
        startGame.setBlockNetworkIdsHashed(false); // runtime IDs are palette indices, not state hashes
        startGame.setCreatedInEditor(false);
        startGame.setExportedFromEditor(false);
        startGame.setNetworkPermissions(NetworkPermissions.DEFAULT);
        startGame.setHardcore(false);
        startGame.setServerId("");
        startGame.setWorldId("");
        startGame.setScenarioId("");

        session.sendPacket(startGame);

        CompressedBiomeDefinitionListPacket biomes = new CompressedBiomeDefinitionListPacket();
        biomes.setDefinitions(vanillaData.getBiomeDefinitions());
        session.sendPacket(biomes);

        AvailableEntityIdentifiersPacket entities = new AvailableEntityIdentifiersPacket();
        entities.setIdentifiers(vanillaData.getEntityIdentifiers());
        session.sendPacket(entities);
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

        // Only sub-chunk 0 (world Y 0-15) - everything Netherrack currently generates
        // (the single grass layer) fits inside it.
        ByteBuf subChunk = ChunkEncoder.encodeSubChunk(chunk, 0, vanillaData);

        LevelChunkPacket packet = new LevelChunkPacket();
        packet.setChunkX(chunkX);
        packet.setChunkZ(chunkZ);
        packet.setSubChunksLength(1);
        packet.setCachingEnabled(false);
        packet.setRequestSubChunks(false);
        packet.setData(subChunk);

        session.sendPacket(packet);
    }
}
