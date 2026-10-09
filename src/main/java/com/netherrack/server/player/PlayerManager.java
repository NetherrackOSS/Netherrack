package com.netherrack.server.player;

import com.netherrack.server.block.Block;
import com.netherrack.server.network.HashedBlockDefinitions;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every player currently in the world, and keeping them visible to each other: showing
 * players to each other as they join, removing them as they leave, and passing each
 * player's movement on to everyone else. Spectators are the exception: they stay in
 * everyone's player list, but nobody else sees them in the world.
 * <p>
 * Players' packets are handled on different network threads. join() and leave() are
 * synchronized so two players joining at once can't each miss the other; sending is
 * safe from any thread, since the protocol library queues packets per connection.
 */
public class PlayerManager {

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();

    public Collection<Player> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** The player in the world with this name, ignoring case, or null if there's none. */
    public Player getPlayer(String username) {
        for (Player player : players.values()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Adds a player who has just finished loading into the world: they're sent everyone
     * already here (themselves included in the player list, as vanilla does), and
     * everyone already here is sent them.
     */
    public synchronized void join(Player joining) {
        players.put(joining.getUuid(), joining);

        List<PlayerListPacket.Entry> everyone = new ArrayList<>();
        for (Player player : players.values()) {
            everyone.add(listEntry(player));
        }
        joining.getSession().sendPacket(playerList(PlayerListPacket.Action.ADD, everyone));

        PlayerListPacket joiningEntry = playerList(PlayerListPacket.Action.ADD, List.of(listEntry(joining)));
        AddPlayerPacket joiningEntity = addPlayer(joining);
        MobArmorEquipmentPacket joiningArmor = armor(joining);
        for (Player other : others(joining)) {
            if (!other.isSpectator()) {
                joining.getSession().sendPacket(addPlayer(other));
                joining.getSession().sendPacket(armor(other));
            }
            other.getSession().sendPacket(joiningEntry);
            if (!joining.isSpectator()) {
                other.getSession().sendPacket(joiningEntity);
                other.getSession().sendPacket(joiningArmor);
            }
        }
    }

    /**
     * Switches a player's game mode, hiding them from everyone else as they become a
     * spectator and showing them again as they stop being one.
     */
    public synchronized void changeGameMode(Player player, GameType gameMode) {
        boolean wasSpectator = player.isSpectator();
        player.setGameMode(gameMode);
        if (!wasSpectator && player.isSpectator()) {
            RemoveEntityPacket removeEntity = new RemoveEntityPacket();
            removeEntity.setUniqueEntityId(player.getEntityId());
            broadcast(player, removeEntity);
        } else if (wasSpectator && !player.isSpectator()) {
            broadcast(player, addPlayer(player));
            broadcast(player, armor(player));
        }
    }

    public synchronized void leave(Player leaving) {
        if (players.remove(leaving.getUuid()) == null) {
            return; // never finished joining, so nobody was shown them
        }

        RemoveEntityPacket removeEntity = new RemoveEntityPacket();
        removeEntity.setUniqueEntityId(leaving.getEntityId());

        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(leaving.getUuid());
        PlayerListPacket removeFromList = playerList(PlayerListPacket.Action.REMOVE, List.of(entry));

        broadcast(leaving, removeEntity);
        broadcast(leaving, removeFromList);
    }

    /** Shows everyone else where the player has moved to and which way they're facing. */
    public void broadcastMovement(Player mover, boolean onGround, long tick) {
        MovePlayerPacket move = new MovePlayerPacket();
        move.setRuntimeEntityId(mover.getEntityId());
        move.setPosition(mover.getPosition());
        move.setRotation(mover.getRotation());
        move.setMode(MovePlayerPacket.Mode.NORMAL);
        move.setOnGround(onGround);
        move.setTick(tick);
        broadcastSeen(mover, move);
    }

    /** Shows everyone else a change to the player's entity data, e.g. starting to sneak. */
    public void broadcastEntityData(Player player) {
        SetEntityDataPacket entityData = new SetEntityDataPacket();
        entityData.setRuntimeEntityId(player.getEntityId());
        entityData.setMetadata(player.createMetadata());
        broadcastSeen(player, entityData);
    }

    /**
     * Sends a chat message from one player to every player currently in the world, sender
     * included - the same way vanilla echoes a sent message back to its own sender.
     */
    public void broadcastChat(Player sender, String message) {
        TextPacket text = new TextPacket();
        text.setType(TextPacket.Type.CHAT);
        text.setSourceName(sender.getUsername());
        text.setXuid(sender.getXuid());
        text.setMessage(message);
        text.setNeedsTranslation(false);
        for (Player player : players.values()) {
            player.getSession().sendPacket(text);
        }
    }

    /** Shows every player, the one who made it included, that a block has changed. */
    public void broadcastBlock(Vector3i position, Block block) {
        UpdateBlockPacket update = blockUpdate(position, block);
        for (Player player : players.values()) {
            player.getSession().sendPacket(update);
        }
    }

    /** A world effect, such as block cracks or break particles, for every player. */
    public void broadcastLevelEvent(LevelEvent event, Vector3f position, int data) {
        LevelEventPacket packet = levelEvent(event, position, data);
        for (Player player : players.values()) {
            player.getSession().sendPacket(packet);
        }
    }

    /** The sound of a block being placed, for everyone but the player who placed it. */
    public void broadcastPlaceSound(Player placer, Vector3f position, Block block) {
        LevelSoundEventPacket sound = new LevelSoundEventPacket();
        sound.setSound(SoundEvent.PLACE);
        sound.setPosition(position);
        sound.setExtraData(block.getBlockStateHash());
        sound.setIdentifier("");
        sound.setEntityUniqueId(-1);
        broadcast(placer, sound);
    }

    /** Tells a client what block is at a position - used to correct a refused change it predicted. */
    public static UpdateBlockPacket blockUpdate(Vector3i position, Block block) {
        UpdateBlockPacket update = new UpdateBlockPacket();
        update.setBlockPosition(position);
        update.setDefinition(HashedBlockDefinitions.of(block.getBlockStateHash()));
        update.setDataLayer(0);
        update.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        update.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
        return update;
    }

    /** Shows everyone else what the player is wearing, after it changes. */
    public void broadcastArmor(Player player) {
        broadcastSeen(player, armor(player));
    }

    /** Shows everyone else the player swinging their arm. */
    public void broadcastSwing(Player swinger, AnimatePacket.SwingSource source) {
        AnimatePacket animate = new AnimatePacket();
        animate.setRuntimeEntityId(swinger.getEntityId());
        animate.setAction(AnimatePacket.Action.SWING_ARM);
        animate.setSwingSource(source != null ? source : AnimatePacket.SwingSource.NONE);
        broadcastSeen(swinger, animate);
    }

    /** Sends a packet to every player in the world. */
    public void sendToAll(BedrockPacket packet) {
        for (Player player : players.values()) {
            player.getSession().sendPacket(packet);
        }
    }

    /** Sends everyone else something about how a player looks or moves - unless nobody can see them. */
    private void broadcastSeen(Player player, BedrockPacket packet) {
        if (!player.isSpectator()) {
            broadcast(player, packet);
        }
    }

    private void broadcast(Player except, BedrockPacket packet) {
        for (Player other : others(except)) {
            other.getSession().sendPacket(packet);
        }
    }

    private List<Player> others(Player player) {
        List<Player> others = new ArrayList<>();
        for (Player other : players.values()) {
            if (!other.getUuid().equals(player.getUuid())) {
                others.add(other);
            }
        }
        return others;
    }

    private static MobArmorEquipmentPacket armor(Player player) {
        List<ItemData> worn = player.getInventory().getArmor();
        MobArmorEquipmentPacket packet = new MobArmorEquipmentPacket();
        packet.setRuntimeEntityId(player.getEntityId());
        packet.setHelmet(worn.get(0));
        packet.setChestplate(worn.get(1));
        packet.setLeggings(worn.get(2));
        packet.setBoots(worn.get(3));
        packet.setBody(ItemData.AIR);
        return packet;
    }

    private static LevelEventPacket levelEvent(LevelEvent event, Vector3f position, int data) {
        LevelEventPacket packet = new LevelEventPacket();
        packet.setType(event);
        packet.setPosition(position);
        packet.setData(data);
        return packet;
    }

    private static PlayerListPacket playerList(PlayerListPacket.Action action, List<PlayerListPacket.Entry> entries) {
        PlayerListPacket packet = new PlayerListPacket();
        packet.setAction(action);
        // Current protocol versions carry the action per entry too, and won't encode without it.
        for (PlayerListPacket.Entry entry : entries) {
            entry.setAction(action);
        }
        packet.getEntries().addAll(entries);
        return packet;
    }

    private static PlayerListPacket.Entry listEntry(Player player) {
        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(player.getUuid());
        entry.setEntityId(player.getEntityId());
        entry.setName(player.getUsername());
        entry.setXuid(player.getXuid());
        entry.setPlatformChatId("");
        entry.setBuildPlatform(player.getBuildPlatform());
        entry.setSkin(player.getSkin());
        entry.setTrustedSkin(true);
        // The player's color on other players' locator bars. Derived from their UUID so
        // it's different per player but the same every time they join.
        entry.setColor(Color.getHSBColor((player.getUuid().hashCode() & 0xFFFF) / 65536f, 0.7f, 1f));
        return entry;
    }

    private static AddPlayerPacket addPlayer(Player player) {
        AddPlayerPacket packet = new AddPlayerPacket();
        packet.setUuid(player.getUuid());
        packet.setUsername(player.getUsername());
        packet.setUniqueEntityId(player.getEntityId());
        packet.setRuntimeEntityId(player.getEntityId());
        packet.setPlatformChatId("");
        // Unlike movement packets, AddPlayer places the player by their feet.
        packet.setPosition(player.getFeetPosition());
        packet.setMotion(Vector3f.ZERO);
        packet.setRotation(player.getRotation());
        packet.setHand(ItemData.AIR);
        packet.setGameType(GameType.SURVIVAL);
        packet.setDeviceId(player.getDeviceId());
        packet.setBuildPlatform(player.getBuildPlatform());
        packet.getAdventureSettings().setUniqueEntityId(player.getEntityId());
        packet.getAdventureSettings().setPlayerPermission(player.getPermission().toPlayerPermission());
        packet.getAdventureSettings().setCommandPermission(CommandPermission.ANY);
        packet.setMetadata(player.createMetadata());
        return packet;
    }
}
