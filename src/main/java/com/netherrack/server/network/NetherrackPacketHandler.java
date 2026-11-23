package com.netherrack.server.network;

import com.netherrack.api.event.player.PlayerChatEvent;
import com.netherrack.api.event.player.PlayerCommandEvent;
import com.netherrack.api.event.player.PlayerLoginEvent;
import com.netherrack.server.Netherrack;
import com.netherrack.server.block.Block;
import com.netherrack.server.block.BlockEntities;
import com.netherrack.server.command.AvailableCommands;
import com.netherrack.server.command.PlayerCommandSender;
import com.netherrack.server.crafting.Recipes;
import com.netherrack.server.entity.EntityIds;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.player.BanEntry;
import com.netherrack.server.player.Foods;
import com.netherrack.server.player.Permission;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerData;
import com.netherrack.server.player.PlayerInventory;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.player.PlayerSurvival;
import com.netherrack.server.player.SkinParser;
import com.netherrack.server.plugin.EventBridge;
import com.netherrack.server.util.ColorCodes;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.World;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.EduSharedUriResource;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.NetworkPermissions;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.editor.WorldType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.LegacySetItemSlotData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockPickRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.JigsawStructureDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.jose4j.json.JsonUtil;

import java.nio.charset.StandardCharsets;
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
 * -> ItemComponent -> CreativeContent -> TrimData -> CraftingData
 * -> RequestChunkRadius -> ChunkRadiusUpdated -> NetworkChunkPublisherUpdate -> LevelChunk(s)
 * -> PlayStatus(PLAYER_SPAWN) -> SetLocalPlayerAsInitialized
 * <p>
 * Targets Bedrock_v2225 (1.26.60) because that's what the real client actually speaks on
 * the wire - the codec has to match the client's real protocol version, full stop, or every
 * packet after the first few shared fields fails to decode. The bundled vanilla data (see
 * VanillaData) is a separate concern: it's currently from the "bedrock-1.26.30" BedrockData
 * tag, the closest available match, not an exact one - a couple of patches behind, so a
 * handful of newer blocks/entities may be missing or render as the unknown-block texture.
 */
public class NetherrackPacketHandler implements BedrockPacketHandler {

    /**
     * How many chunks out from a joining player are sent before they spawn, so there's ground
     * under them; the rest of their view follows on the server tick.
     */
    private static final int SPAWN_CHUNK_RADIUS = 4;

    /** The smallest view a player gets, whatever their client asks for. */
    private static final int MIN_CHUNK_RADIUS = 2;

    /** How far from a player's eyes the sounds they make can be: a little beyond what they reach. */
    private static final float SOUND_REACH = 16;

    private final BedrockServerSession session;
    private final World world;
    private final PlayerManager players;
    private final BlockInteraction blockInteraction;
    private final ItemEntities itemEntities;
    private final ChunkViews chunkViews;
    private final ChunkView chunkView;
    private final Netherrack netherrack;
    private final VanillaData vanillaData = VanillaData.get();
    private final long runtimeEntityId = EntityIds.next();

    private volatile String username;
    private volatile String uuid;
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
        this.blockInteraction = new BlockInteraction(world, players, itemEntities, netherrack.getScheduler(),
                netherrack);
        this.chunkViews = netherrack.getChunkViews();
        this.chunkView = new ChunkView(session, world);
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
        int serverProtocol = Bedrock_v2225.CODEC.getProtocolVersion();

        if (clientProtocol != serverProtocol) {
            Logger.warn("Client requested protocol " + clientProtocol + ", server only supports " + serverProtocol
                    + " (" + Bedrock_v2225.CODEC.getMinecraftVersion() + "); continuing anyway since multi-version support isn't implemented yet.");
        }

        session.setCodec(Bedrock_v2225.CODEC);

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
            }

            // NetherNet already validated a connecting client's identity at the transport
            // level (the transport installs the binding when it accepts the connection) and
            // recorded the key it proved it holds. This checks the Bedrock login chain's
            // identity key against that one, so a client can't present a login chain for an
            // identity it never actually proved it holds at the transport level.
            //
            // RakNet never needed this - it bound the login chain to the connection itself
            // through Bedrock's own encryption handshake (ServerToClientHandshakePacket /
            // ClientToServerHandshakePacket), which this project used to perform here. That
            // handshake is gone along with RakNet: NetherNet's encryption (DTLS) lives below
            // the Bedrock protocol entirely, so nothing else here would catch a mismatch.
            String mismatch = TransportIdentityBinding.mismatch(
                    session.getPeer().getChannel(), identityClaims.parsedIdentityPublicKey());
            if (mismatch != null) {
                Logger.warn("Disconnecting " + username + ": " + mismatch);
                SessionDisconnect.disconnect(session, mismatch);
                return PacketSignal.HANDLED;
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

        completeLogin();
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


    private void completeLogin() {
        // Players are saved by the UUID their login gives them, which stays the same for
        // an Xbox Live account.
        NbtMap data = world.loadPlayer(UUID.fromString(uuid));
        PlayerData.Saved saved = data != null ? PlayerData.read(data) : null;
        if (saved == null) {
            Logger.info("Player data not found for \"" + uuid + "\", creating new profile");
        }

        if (saved != null && saved.position() != null) {
            spawnPosition = saved.position();
        } else {
            // In the middle of the world spawn's block, as vanilla puts new players. The chunks
            // sent once the client asks for them are the ones around this spot.
            if (saved != null) {
                Logger.info(username + "'s saved position is outside the world, so they start at the world spawn.");
            }
            Vector3i spawn = world.getSpawn();
            spawnPosition = Vector3f.from(spawn.getX() + 0.5, spawn.getY() + Player.EYE_HEIGHT, spawn.getZ() + 0.5);
        }

        GameType gameMode = saved != null && saved.gameMode() != null ? saved.gameMode() : netherrack.getDefaultGameMode();
        player = new Player(session, username, UUID.fromString(uuid), xuid, runtimeEntityId,
                skin, deviceId, buildPlatform, spawnPosition, gameMode);
        // Health, hunger and what wears them down. A returning player gets their saved
        // numbers back; a new one starts full.
        PlayerSurvival survival = new PlayerSurvival(player, players, itemEntities);
        player.setSurvival(survival);
        if (saved != null) {
            survival.restore(saved.stats());
        }
        // What else the player is shown depends on which chunks their client has.
        player.setChunkView(chunkView);
        if (saved != null) {
            if (saved.position() != null) {
                player.setLocation(saved.position(), saved.rotation());
            }
            player.setSelectedSlot(saved.selectedSlot());
            player.getInventory().restore(saved.inventory());
        }
        // From permissions.json by XUID; players not signed into Xbox Live get the default.
        Permission permission = netherrack.getPermissions().of(xuid);
        player.setPermission(permission, netherrack.commandLevelFor(permission));

        // Turn non-whitelisted players away before LOGIN_SUCCESS, with the configured reason.
        // PlayerLoginEvent is deliberately not fired: this is a server rule, not a plugin veto.
        if (!netherrack.isWhitelisted(username, xuid, uuid)) {
            SessionDisconnect.disconnect(session, netherrack.getWhitelistMessage());
            return;
        }

        // Nukkit checks name bans at the same point in processLogin(), after the whitelist and
        // before the client is allowed to play, so the stored reason reaches the disconnect screen.
        BanEntry ban = netherrack.banFor(username, xuid, uuid);
        if (ban != null) {
            SessionDisconnect.disconnect(session, netherrack.getBanMessage(ban.reason()));
            return;
        }

        // Plugins get the last word before the client is told it can play. A cancelled login is
        // disconnected here; the player object still exists so onDisconnect() can save it once.
        PlayerLoginEvent login = EventBridge.playerLogin(netherrack, player);
        if (login.isCancelled()) {
            SessionDisconnect.disconnect(session, login.getKickMessage());
            return;
        }

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
                stack.setGameVersion(Bedrock_v2225.CODEC.getMinecraftVersion());
                session.sendPacket(stack);
            }
            case COMPLETED -> sendStartGame();
            case REFUSED -> SessionDisconnect.disconnect(session,
                    "You must accept the resource packs to play.");
            default -> {
                // NONE / SEND_PACKS: nothing to do, since we never send any packs to request.
            }
        }
        return PacketSignal.HANDLED;
    }

    /**
     * How far the client can see, which it asks as it joins and again whenever its render
     * distance changes: that far, as far as its device can handle (its max radius) and up
     * to the server's view-distance - but at least 2 chunks.
     */
    @Override
    public PacketSignal handle(RequestChunkRadiusPacket packet) {
        int wanted = packet.getMaxRadius() > 0 ? Math.min(packet.getRadius(), packet.getMaxRadius()) : packet.getRadius();
        int radius = Math.max(MIN_CHUNK_RADIUS, Math.min(wanted, netherrack.getViewDistance()));

        ChunkRadiusUpdatedPacket radiusUpdated = new ChunkRadiusUpdatedPacket();
        radiusUpdated.setRadius(radius);
        session.sendPacket(radiusUpdated);

        chunkView.setRadius(radius, player != null ? player.getPosition() : spawnPosition);

        // PLAYER_SPAWN is what moves the client off the loading screen, and the client only
        // sends SetLocalPlayerAsInitialized after receiving it - so it has to go out here,
        // unprompted, once the spawn chunks are on their way. The client can re-request
        // its radius later (e.g. a render distance change), hence the once-only flag.
        if (!spawned) {
            spawned = true;
            chunkView.sendWithin(SPAWN_CHUNK_RADIUS);
            chunkViews.add(chunkView);
            sendOwnPlayerState();
            players.sendWeather(player, world.isRaining(), world.isThundering());

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

        // Health, hunger, saturation and exhaustion as this player has them - the same
        // numbers their survival state uses from here on.
        PlayerSurvival survival = player.getSurvival();
        SetHealthPacket health = new SetHealthPacket();
        health.setHealth(survival.getHealthInt());
        session.sendPacket(health);

        float noLimit = Float.MAX_VALUE;
        UpdateAttributesPacket attributes = new UpdateAttributesPacket();
        attributes.setRuntimeEntityId(runtimeEntityId);
        attributes.setAttributes(List.of(
                new AttributeData("minecraft:health", 0, PlayerSurvival.MAX_HEALTH, survival.getHealth(), PlayerSurvival.MAX_HEALTH),
                new AttributeData("minecraft:absorption", 0, noLimit, 0, 0),
                new AttributeData("minecraft:movement", 0, noLimit, 0.1f, 0.1f),
                new AttributeData("minecraft:underwater_movement", 0, noLimit, 0.02f, 0.02f),
                new AttributeData("minecraft:lava_movement", 0, noLimit, 0.02f, 0.02f),
                new AttributeData("minecraft:player.hunger", 0, PlayerSurvival.MAX_FOOD, survival.getFood(), PlayerSurvival.MAX_FOOD),
                new AttributeData("minecraft:player.saturation", 0, PlayerSurvival.MAX_FOOD, survival.getSaturation(), PlayerSurvival.START_SATURATION),
                new AttributeData("minecraft:player.exhaustion", 0, 5, survival.getExhaustion(), 0),
                new AttributeData("minecraft:player.level", 0, 24791, 0, 0),
                new AttributeData("minecraft:player.experience", 0, 1, 0, 0)));
        session.sendPacket(attributes);

        session.sendPacket(player.createAbilities());
        // The commands their client suggests as they type "/": those their level allows.
        session.sendPacket(AvailableCommands.forPlayer(netherrack.getCommandManager(), netherrack, player));

        // Their inventory as they left it, or empty for a new player - and the hotbar slot
        // they had selected.
        sendInventory();
        PlayerHotbarPacket hotbar = new PlayerHotbarPacket();
        hotbar.setContainerId(ContainerId.INVENTORY);
        hotbar.setSelectedHotbarSlot(player.getSelectedSlot());
        hotbar.setSelectHotbarSlot(true);
        session.sendPacket(hotbar);
    }

    /** Shows the client its whole inventory as the server has it. */
    private void sendInventory() {
        for (BedrockPacket packet : player.getInventory().contentPackets()) {
            session.sendPacket(packet);
        }
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        // Only now, with the client past its loading screen, is the player actually in
        // the world for others to see. The items around them appear on the next tick.
        players.join(player);

        players.broadcastPresence(true, username);
        EventBridge.playerJoin(netherrack, player);

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
        PlayerSurvival survival = player.getSurvival();
        if (survival != null && survival.isDead()) {
            // Dead players stay where they fell until they answer the respawn handshake.
            return PacketSignal.HANDLED;
        }

        // Checked before the "standing still" shortcut below, since players can break
        // blocks and start or stop sneaking without moving.
        if (!packet.getPlayerActions().isEmpty()) {
            blockInteraction.handleActions(player, packet.getPlayerActions());
        }
        // Current clients carry their item uses and inventory requests in here rather than
        // in transactions - including the food use that starts eating, which has to be seen
        // even when the player stands perfectly still. An ITEM_RELEASE still comes as a
        // separate transaction, so a let-go cancels the meal the usual way.
        ItemUseTransaction itemUse = packet.getItemUseTransaction();
        ItemStackRequest stackRequest = packet.getItemStackRequest();
        if (itemUse != null || stackRequest != null) {
            List<ItemData> armorBefore = player.getInventory().getArmor();
            boolean changedInventory = false;
            if (itemUse != null) {
                changedInventory = processItemUse(new UseAction(itemUse.getActionType(),
                        itemUse.getHotbarSlot(), itemUse.getHand(), itemUse.getItemInHand(),
                        itemUse.getClientInteractPrediction(), itemUse.getBlockPosition(),
                        itemUse.getBlockFace(), itemUse.getClickPosition()));
                if (itemUse.getLegacyRequestId() != 0) {
                    ItemStackResponsePacket response = new ItemStackResponsePacket();
                    response.getEntries().add(player.getInventory().legacyResponse(itemUse.getLegacyRequestId(),
                            legacySlots(itemUse.getLegacySlots())));
                    session.sendPacket(response);
                    changedInventory = true;
                }
            }
            if (stackRequest != null) {
                handleStackRequests(List.of(stackRequest));
            }
            if (changedInventory) {
                sendInventory();
            }
            if (!player.getInventory().getArmor().equals(armorBefore)) {
                players.broadcastArmor(player);
            }
        }
        if (packet.getInputData().contains(PlayerAuthInputData.START_SNEAKING)) {
            player.setSneaking(true);
            players.broadcastEntityData(player);
        } else if (packet.getInputData().contains(PlayerAuthInputData.STOP_SNEAKING)) {
            player.setSneaking(false);
            players.broadcastEntityData(player);
        }

        // The client says it started or stopped flying. The server keeps that state and
        // echoes it back in an abilities packet: that echo is what keeps the client's own
        // flight state and the server's in step. A client whose abilities say it isn't
        // flying drops out of flight at once, even in mid-air, and the ghost fall damage
        // that can follow is why the state may never drift. A mode that can't fly gets a
        // packet that says so, which is the client's own cue to land.
        if (packet.getInputData().contains(PlayerAuthInputData.START_FLYING)) {
            player.setFlying(player.mayFly());
            player.sendAbilities();
        } else if (packet.getInputData().contains(PlayerAuthInputData.STOP_FLYING)) {
            player.setFlying(false);
            player.sendAbilities();
        }

        Vector3f position = packet.getPosition();
        Vector3f rotation = packet.getRotation();
        boolean onGround = packet.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION)
                && packet.getDelta().getY() <= 0;
        // Falls, landings and the hunger cost of moving are measured from what the client
        // reports - before the standing-still shortcut, so landing still hurts.
        if (survival != null) {
            survival.moved(world, player.getPosition(), position, onGround, packet.getInputData());
        }
        if (position.equals(player.getPosition()) && rotation.equals(player.getRotation())) {
            return PacketSignal.HANDLED; // standing still - nothing new to tell anyone
        }

        player.setLocation(position, rotation);
        chunkView.moveTo(position);
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
            PlayerSurvival survival = player.getSurvival();
            if (survival != null) {
                survival.swung();
            }
        }
        return PacketSignal.HANDLED;
    }

    /**
     * A sound the client made and plays itself, like the block its player is mining being
     * hit, for the players around to hear too. One from further than the player could
     * plausibly make it goes nowhere.
     */
    @Override
    public PacketSignal handle(LevelSoundEventPacket packet) {
        Vector3f position = packet.getPosition();
        if (player != null && position != null && Float.isFinite(position.getX()) && Float.isFinite(position.getY())
                && Float.isFinite(position.getZ())
                && position.distanceSquared(player.getPosition()) <= SOUND_REACH * SOUND_REACH) {
            players.relaySound(player, packet);
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
        // On one line, with no control characters: it goes into the log and to everyone's
        // chat, where a line break or escape sequence in it could pass for something else.
        message = ColorCodes.singleLine(message).trim();
        if (message.isEmpty()) {
            return PacketSignal.HANDLED;
        }

        if (message.startsWith("/")) {
            runCommand(message, null);
        } else {
            PlayerChatEvent chat = EventBridge.playerChat(netherrack, player, message);
            if (!chat.isCancelled()) {
                players.broadcastChat(player, chat.getMessage());
            }
        }

        return PacketSignal.HANDLED;
    }

    /**
     * The client's answer to a form. A cancel reason or the literal "null" means it was closed;
     * both are treated the same as Nukkit does so the form's close callback still runs. Unknown
     * ids are ignored by EventBridge, which keeps late answers from triggering another form.
     */
    @Override
    public PacketSignal handle(ModalFormResponsePacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }
        String data = packet.getFormData();
        boolean closed = packet.getCancelReason().isPresent()
                || data == null || data.isBlank() || data.trim().equals("null");
        EventBridge.formResponse(netherrack, player, packet.getFormId(), data, closed);
        return PacketSignal.HANDLED;
    }

    /**
     * A command typed in chat. Vanilla clients send these as their own packet rather than
     * as a chat message starting with "/".
     */
    @Override
    public PacketSignal handle(CommandRequestPacket packet) {
        if (player != null && packet.getCommand() != null && !packet.getCommand().isBlank()) {
            runCommand(packet.getCommand().trim(), packet.getCommandOriginData());
        }
        return PacketSignal.HANDLED;
    }

    /**
     * Runs a command as this player, if their permission level allows it (see
     * CommandManager). The origin is the request's, which replies answer to.
     */
    private void runCommand(String command, CommandOriginData origin) {
        command = ColorCodes.singleLine(command);
        String input = command.startsWith("/") ? command.substring(1) : command;
        PlayerCommandEvent event = EventBridge.playerCommand(netherrack, player, input);
        if (event.isCancelled()) {
            return;
        }
        input = event.getCommand();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        Logger.info(username + " issued command: " + input);
        netherrack.getCommandManager().dispatch(netherrack, new PlayerCommandSender(player, origin), input);
    }

    /**
     * Using the held item (an "item use" transaction): a right click on a block (action
     * type 0) uses the block or places the held block against it, and using armor - in the
     * air (action type 1), or on a block when it isn't a block itself - puts it on, as
     * vanilla does. A carved pumpkin or a head is put on only from the air.
     * <p>
     * A "normal" transaction is the client throwing items out from the hotbar (pressing
     * Q), which it has already done on its side: one action takes the items out of an
     * inventory slot, the other puts them into the world.
     * <p>
     * Either may carry a legacy request: slots the client already changed on its own, and
     * will go on naming by the request's id until it's answered (see
     * PlayerInventory.legacyResponse). After either, the client is shown its inventory as
     * the server has it, whether or not what it did was valid.
     */
    @Override
    public PacketSignal handle(InventoryTransactionPacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }
        PlayerInventory inventory = player.getInventory();
        PlayerSurvival survival = player.getSurvival();
        List<ItemData> armorBefore = inventory.getArmor();
        boolean changedInventory = false;

        if (packet.getTransactionType() == InventoryTransactionType.ITEM_USE) {
            boolean changed = processItemUse(new UseAction(packet.getActionType(), packet.getHotbarSlot(),
                    packet.getHand(), packet.getItemInHand(), packet.getClientInteractPrediction(),
                    packet.getBlockPosition(), packet.getBlockFace(), packet.getClickPosition()));
            if (packet.getLegacyRequestId() != 0) {
                ItemStackResponsePacket response = new ItemStackResponsePacket();
                response.getEntries().add(inventory.legacyResponse(packet.getLegacyRequestId(),
                        legacySlots(packet.getLegacySlots())));
                session.sendPacket(response);
                changed = true;
            }
            if (changed) {
                sendInventory();
            }
        } else if (packet.getTransactionType() == InventoryTransactionType.ITEM_RELEASE) {
            // Letting go early stops the meal; nothing has been taken out of the inventory.
            if (survival != null) {
                survival.releaseItem();
            }
        } else if (packet.getTransactionType() == InventoryTransactionType.ITEM_USE_ON_ENTITY) {
            // A click on another entity: players are the only ones there are, so this is
            // PvP. Action type 0 is a right click on them, which nothing uses yet.
            if (packet.getActionType() == 1) {
                handleAttack(packet);
            }
        } else if (packet.getTransactionType() == InventoryTransactionType.INVENTORY_MISMATCH) {
            // The client says its inventory no longer matches the server's, so it's shown
            // the inventory as the server has it - the mismatch is over with the resync.
            sendInventory();
            changedInventory = false;
        } else if (packet.getTransactionType() == InventoryTransactionType.NORMAL) {
            throwFromHotbar(packet.getActions());
            changedInventory = true;
        }

        if (changedInventory) {
            sendInventory();
        }
        if (!inventory.getArmor().equals(armorBefore)) {
            players.broadcastArmor(player);
        }
        return PacketSignal.HANDLED;
    }

    /**
     * One item use, from whichever packet the client sent it in: a right click on a block
     * (action type 0) uses the block or places the held block against it, and using armor -
     * in the air (action type 1), or on a block when it isn't a block itself - puts it on,
     * as vanilla does. Using food on air starts eating; the server tick finishes it after
     * 1.6s and takes the item then, as vanilla does. Returns whether the inventory changed.
     */
    /**
     * One player attacking another: the transaction names the target by runtime entity id
     * and the hotbar slot that swung. The server reads what they held from its own
     * inventory rather than trusting the item the client says was in their hand.
     */
    private void handleAttack(InventoryTransactionPacket packet) {
        PlayerSurvival survival = player.getSurvival();
        if (survival == null) {
            return;
        }
        Player target = players.byEntityId(packet.getRuntimeEntityId());
        if (target == null) {
            return; // players are the only entities, so nothing else is there to hit
        }
        int slot = packet.getHotbarSlot();
        ItemData held = slot >= 0 && slot < PlayerInventory.HOTBAR_SIZE
                ? player.getInventory().get(slot) : ItemData.AIR;
        survival.attack(target, held);
    }

    private boolean processItemUse(UseAction use) {
        if (use.actionType() != 0 && use.actionType() != 1) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        PlayerSurvival survival = player.getSurvival();
        int slot = use.hotbarSlot();
        boolean validSlot = slot >= 0 && slot < PlayerInventory.HOTBAR_SIZE;
        int heldId = use.itemInHand() != null && use.itemInHand().getDefinition() != null
                ? use.itemInHand().getDefinition().getRuntimeId() : 0;
        // On a block, a block item - a carved pumpkin or a head too - is placed, and a
        // block that's used is used; the prediction only says whether the client expects
        // that to work. An air use - eating, putting armor on - is handled whichever way
        // the prediction points, as vanilla servers do.
        boolean onBlockDone = use.actionType() == 0
                && blockInteraction.handleItemUse(player, use.toItemUseTransaction());
        boolean changedInventory = false;
        if (!onBlockDone) {
            boolean offhand = use.hand() == HandSlot.OFFHAND;
            boolean eating = false;
            if (use.actionType() == 1 && survival != null) {
                ItemData held = offhand ? inventory.getOffhand()
                        : validSlot ? inventory.get(slot) : ItemData.AIR;
                if (Foods.of(held) != null) {
                    survival.useItem(held, offhand ? -1 : slot, offhand);
                    eating = true;
                }
            }
            if (!eating && !offhand && validSlot && inventory.equipHeld(slot, heldId)) {
                changedInventory = true;
            }
        }
        return changedInventory;
    }

    /** The shared fields of an item use, from either packet that can carry one. */
    private record UseAction(int actionType, int hotbarSlot, HandSlot hand, ItemData itemInHand,
                             ItemUseTransaction.PredictedResult prediction, Vector3i blockPosition,
                             int blockFace, Vector3f clickPosition) {

        ItemUseTransaction toItemUseTransaction() {
            ItemUseTransaction use = new ItemUseTransaction();
            use.setActionType(actionType);
            use.setHotbarSlot(hotbarSlot);
            use.setHand(hand);
            use.setItemInHand(itemInHand);
            use.setClientInteractPrediction(prediction);
            use.setBlockPosition(blockPosition);
            use.setBlockFace(blockFace);
            use.setClickPosition(clickPosition);
            return use;
        }
    }

    /**
     * A legacy request's slots as item stack request slots. The containers arrive as their
     * network ids, which differ from the library's ContainerSlotType order, so the codec's
     * own table translates them.
     */
    private List<ItemStackRequestSlotData> legacySlots(List<LegacySetItemSlotData> legacySlots) {
        List<ItemStackRequestSlotData> slots = new ArrayList<>();
        for (LegacySetItemSlotData legacy : legacySlots) {
            ContainerSlotType container = session.getPeer().getCodecHelper()
                    .readContainerSlotType(Unpooled.wrappedBuffer(new byte[]{(byte) legacy.getContainerId()}));
            if (container == null) {
                continue;
            }
            for (byte slot : legacy.getSlots()) {
                slots.add(new ItemStackRequestSlotData(container, slot, 0, new FullContainerName(container, null)));
            }
        }
        return slots;
    }

    /** The client asking to move items around its inventory screen - see PlayerInventory. */
    @Override
    public PacketSignal handle(ItemStackRequestPacket packet) {
        if (player == null) {
            return PacketSignal.HANDLED;
        }
        handleStackRequests(packet.getRequests());
        return PacketSignal.HANDLED;
    }

    /**
     * Answers one or more inventory requests, whether they arrived on their own or inside
     * a PlayerAuthInputPacket - the eat confirmation a current client sends rides along
     * with its movement rather than in a packet of its own.
     */
    private void handleStackRequests(List<ItemStackRequest> requests) {
        List<ItemData> armorBefore = player.getInventory().getArmor();
        ItemStackResponsePacket response = new ItemStackResponsePacket();
        for (ItemStackRequest request : requests) {
            response.getEntries().add(player.getInventory().handle(request, player.isCreative()));
        }
        session.sendPacket(response);
        throwDroppedItems();
        if (!player.getInventory().getArmor().equals(armorBefore)) {
            players.broadcastArmor(player);
        }
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
     * server answers with ContainerOpen, which vanilla places at the player's feet. With
     * a crafting table open its screen is the inventory already, so this does nothing.
     */
    @Override
    public PacketSignal handle(InteractPacket packet) {
        if (player == null || packet.getAction() != InteractPacket.Action.OPEN_INVENTORY || inventoryOpen
                || player.getInventory().isTableOpen()) {
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
     * The client closing a screen. The server confirms it, and puts what was left on the
     * cursor or in the crafting grid back into the inventory: window 0 is the inventory
     * screen, window -1 the crafting table's (as Nukkit opens it, "no container" - the
     * workbench is the player's UI screen with a bigger grid).
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

        PlayerInventory inventory = player.getInventory();
        boolean returned;
        if (packet.getId() == -1) {
            returned = inventory.closeTable();
        } else if (packet.getId() == 0) {
            returned = inventory.returnScreenItems();
            // Clients have been known to close a screen by another's window id (Nukkit
            // works around the same); a table still open after this comes down too, its
            // leftovers already back with the rest.
            if (inventory.isTableOpen()) {
                inventory.closeTable();
            }
        } else if (inventory.isTableOpen()) {
            // Some other window id, but a table may be the screen that's closing.
            returned = inventory.closeTable();
        } else {
            returned = false;
        }
        if (returned) {
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

    /**
     * A middle-click on a block. The client asks for the item rather than taking one itself:
     * survival players must already have it, while creative players are given one. The picked
     * item keeps the clicked block's state, and the client is told both the new inventory and
     * the slot it should select.
     */
    @Override
    public PacketSignal handle(BlockPickRequestPacket packet) {
        if (!spawned || player == null || player.isDead() || player.isSpectator()) {
            return PacketSignal.HANDLED;
        }
        Vector3i position = packet.getBlockPosition();
        if (position == null || !blockInteraction.canReach(player, position)) {
            return PacketSignal.HANDLED;
        }
        Block block = world.getBlock(position.getX(), position.getY(), position.getZ());
        if (block == null) {
            return PacketSignal.HANDLED;
        }
        ItemData picked = VanillaData.get().pickedItem(block);
        if (picked.isNull()) {
            return PacketSignal.HANDLED; // blocks with no item, such as bedrock or water
        }
        picked = BlockEntities.dropFor(picked, world.getBlockEntity(position));

        PlayerInventory.PickResult result = player.getInventory()
                .pick(picked, player.isCreative(), player.getSelectedSlot());
        if (!result.changed()) {
            return PacketSignal.HANDLED;
        }
        player.setSelectedSlot(result.slot());
        sendInventory();
        PlayerHotbarPacket hotbar = new PlayerHotbarPacket();
        hotbar.setContainerId(ContainerId.INVENTORY);
        hotbar.setSelectedHotbarSlot(result.slot());
        hotbar.setSelectHotbarSlot(true);
        session.sendPacket(hotbar);
        return PacketSignal.HANDLED;
    }

    /**
     * The client's answer to the death screen: it's ready to come back. The server finishes
     * vanilla's respawn handshake - a searching packet and then a ready one - and puts the
     * player at the world spawn with full health and hunger.
     */
    @Override
    public PacketSignal handle(RespawnPacket packet) {
        if (player == null || packet.getState() != RespawnPacket.State.CLIENT_READY) {
            return PacketSignal.HANDLED;
        }
        PlayerSurvival survival = player.getSurvival();
        if (survival == null || !survival.isDead()) {
            return PacketSignal.HANDLED;
        }

        Vector3i spawn = world.getSpawn();
        Vector3f respawn = Vector3f.from(spawn.getX() + 0.5, spawn.getY() + Player.EYE_HEIGHT,
                spawn.getZ() + 0.5);

        RespawnPacket searching = new RespawnPacket();
        searching.setPosition(Vector3f.from(respawn.getX(), 32767, respawn.getZ()));
        searching.setState(RespawnPacket.State.SERVER_SEARCHING);
        searching.setRuntimeEntityId(0);
        session.sendPacket(searching);

        RespawnPacket ready = new RespawnPacket();
        ready.setPosition(respawn);
        ready.setState(RespawnPacket.State.SERVER_READY);
        ready.setRuntimeEntityId(0);
        session.sendPacket(ready);

        survival.respawn();
        player.setLocation(respawn, player.getRotation());
        chunkView.moveTo(respawn);

        // The corpse the others were shown was removed when the player died; they're
        // alive again, so show the reborn player to whoever can see them at the spawn.
        players.updateVisibility();

        MovePlayerPacket teleport = new MovePlayerPacket();
        teleport.setRuntimeEntityId(runtimeEntityId);
        teleport.setPosition(respawn);
        teleport.setRotation(player.getRotation());
        teleport.setMode(MovePlayerPacket.Mode.TELEPORT);
        teleport.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
        teleport.setOnGround(true);
        teleport.setTick(0);
        session.sendPacket(teleport);

        sendInventory();
        // What they wore dropped where they died, so everyone who can see them now sees
        // them without armor.
        players.broadcastArmor(player);
        players.broadcastMovement(player, true, 0);
        return PacketSignal.HANDLED;
    }

    /**
     * The client's eating animation, which carries the crunch sound. It isn't shown to
     * anyone automatically, so it's validated and echoed to the player and everyone who
     * can see them - that is what makes eating audible.
     */
    @Override
    public PacketSignal handle(EntityEventPacket packet) {
        if (player == null || packet.getType() != EntityEventType.EATING_ITEM
                || packet.getRuntimeEntityId() != player.getEntityId()) {
            return PacketSignal.HANDLED;
        }
        PlayerInventory inventory = player.getInventory();
        boolean food = Foods.of(inventory.get(player.getSelectedSlot())) != null
                || Foods.of(inventory.getOffhand()) != null;
        PlayerSurvival survival = player.getSurvival();
        if (!food && (survival == null || !survival.isUsingItem())) {
            return PacketSignal.HANDLED;
        }
        packet.setRuntimeEntityId(player.getEntityId());
        players.broadcastAt(player.getPosition(), packet);
        return PacketSignal.HANDLED;
    }

    /**
     * The client asking to turn an ability of its own on or off - in practice, flight
     * (though some clients still announce flight through the auth input instead). The
     * request is granted only in a game mode that may fly, and the answer goes back as an
     * abilities packet: that packet is what tells the client what it really is doing, so
     * a denied request gets one that repeats the state it already has.
     */
    @Override
    public PacketSignal handle(RequestAbilityPacket packet) {
        if (player == null || packet.getAbility() != Ability.FLYING
                || packet.getType() != Ability.Type.BOOLEAN) {
            return PacketSignal.HANDLED;
        }
        boolean requested = packet.isBoolValue();
        if (requested == player.isFlying()) {
            return PacketSignal.HANDLED; // already as it should be
        }
        if (requested && !player.mayFly()) {
            player.sendAbilities(); // a reminder that this client may not fly
            return PacketSignal.HANDLED;
        }
        player.setFlying(requested);
        player.sendAbilities();
        return PacketSignal.HANDLED;
    }

    /** The client says which hotbar slot the player has switched to. */
    @Override
    public PacketSignal handle(MobEquipmentPacket packet) {
        if (player != null && packet.getContainerId() == ContainerId.INVENTORY) {
            player.setSelectedSlot(packet.getHotbarSlot());
        }
        return PacketSignal.HANDLED;
    }

    /** Called once the connection has closed, for whatever reason. */
    public void onDisconnect() {
        chunkViews.remove(chunkView);
        if (player != null) {
            blockInteraction.stopBreaking();
            EventBridge.closeForm(netherrack, player);
            // Saved before they leave the player list: stopping the server saves everyone
            // still on it, so whoever is leaving at that moment is saved one way or the other.
            world.savePlayer(player.getUuid(), PlayerData.save(player));
            if (players.leave(player)) {
                players.broadcastPresence(false, username);
                EventBridge.playerQuit(netherrack, player);
            }
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
        startGame.setRotation(Vector2f.from(player.getRotation().getX(), player.getRotation().getY()));

        startGame.setSeed(world.getSeed());
        startGame.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGame.setCustomBiomeName("");
        startGame.setDimensionId(0); // overworld
        startGame.setGeneratorId(world.getGeneratorType());
        startGame.setLevelGameType(netherrack.getDefaultGameMode());
        startGame.setDifficulty(world.getDifficulty());
        startGame.setDefaultSpawn(world.getSpawn());
        startGame.setAchievementsDisabled(true);
        startGame.setDayCycleStopTime(0);
        startGame.setEduEditionOffers(0);
        startGame.setEduFeaturesEnabled(false);
        startGame.setEducationProductionId("");
        startGame.setRainLevel(world.isRaining() ? 1.0f : 0.0f);
        startGame.setLightningLevel(world.isThundering() ? 1.0f : 0.0f);
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
        startGame.setDefaultPlayerPermission(netherrack.getPermissions().getDefault().toPlayerPermission());
        startGame.setServerChunkTickRange(4);
        startGame.setBehaviorPackLocked(false);
        startGame.setResourcePackLocked(false);
        startGame.setFromLockedWorldTemplate(false);
        startGame.setUsingMsaGamertagsOnly(false);
        startGame.setFromWorldTemplate(false);
        startGame.setWorldTemplateOptionLocked(false);
        startGame.setOnlySpawningV1Villagers(false);
        startGame.setVanillaVersion(Bedrock_v2225.CODEC.getMinecraftVersion());
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

        startGame.setCurrentTick(world.getTime());
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

        // The trim registry goes out before the recipes: the client resolves the smithing
        // trim recipe's item tags against it while it builds its recipe book, and rejects
        // the join over an empty registry at that moment. The recipes packet carries
        // cleanRecipes, so the client drops its built-in copy for the server's own - two
        // lists numbered from 1 would collide on every network id.
        session.sendPacket(Recipes.get().trimPacket());
        session.sendPacket(Recipes.get().packet());
    }
}
