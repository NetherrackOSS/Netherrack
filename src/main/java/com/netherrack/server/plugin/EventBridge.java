package com.netherrack.server.plugin;

import com.netherrack.api.entity.DamageCause;
import com.netherrack.api.entity.GameMode;
import com.netherrack.api.entity.TeleportCause;
import com.netherrack.api.event.Event;
import com.netherrack.api.event.block.BlockBreakEvent;
import com.netherrack.api.event.block.BlockPlaceEvent;
import com.netherrack.api.event.player.PlayerActionBarEvent;
import com.netherrack.api.event.player.PlayerChatEvent;
import com.netherrack.api.event.player.PlayerCommandEvent;
import com.netherrack.api.event.player.PlayerDamageEvent;
import com.netherrack.api.event.player.PlayerDeathEvent;
import com.netherrack.api.event.player.PlayerFormRespondedEvent;
import com.netherrack.api.event.player.PlayerGameModeChangeEvent;
import com.netherrack.api.event.player.PlayerInventoryClickEvent;
import com.netherrack.api.event.player.PlayerItemConsumeEvent;
import com.netherrack.api.event.player.PlayerItemDropEvent;
import com.netherrack.api.event.player.PlayerItemPickupEvent;
import com.netherrack.api.event.player.PlayerItemUseEvent;
import com.netherrack.api.event.player.PlayerJoinEvent;
import com.netherrack.api.event.player.PlayerLoginEvent;
import com.netherrack.api.event.player.PlayerMetadataChangeEvent;
import com.netherrack.api.event.player.PlayerMoveEvent;
import com.netherrack.api.event.player.PlayerPopupEvent;
import com.netherrack.api.event.player.PlayerQuitEvent;
import com.netherrack.api.event.player.PlayerRespawnEvent;
import com.netherrack.api.event.player.PlayerTeleportEvent;
import com.netherrack.api.event.player.PlayerToastEvent;
import com.netherrack.api.event.server.ServerStartEvent;
import com.netherrack.api.event.server.ServerStopEvent;
import com.netherrack.api.form.Form;
import com.netherrack.api.item.InventoryClickAction;
import com.netherrack.api.item.ItemStack;
import com.netherrack.api.world.BlockPosition;
import com.netherrack.api.world.Location;
import com.netherrack.server.Netherrack;
import com.netherrack.server.block.Block;
import com.netherrack.server.network.VanillaData;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns server actions into API events and sends them to the plugin manager.
 * <p>
 * Hook sites call one method and, for cancellable events, use the returned event to decide what
 * the server does next. Conversions from the server's vector and block types live here so packet
 * handlers never have to build API objects themselves.
 */
public final class EventBridge {

    private EventBridge() {
    }

    public static void serverStart(Netherrack server) {
        call(server, new ServerStartEvent());
    }

    public static void serverStop(Netherrack server) {
        call(server, new ServerStopEvent());
    }

    public static PlayerLoginEvent playerLogin(Netherrack server,
                                               com.netherrack.server.player.Player player) {
        PlayerLoginEvent event = new PlayerLoginEvent(apiPlayer(server, player));
        call(server, event);
        return event;
    }

    public static void playerJoin(Netherrack server, com.netherrack.server.player.Player player) {
        call(server, new PlayerJoinEvent(apiPlayer(server, player)));
    }

    public static void playerQuit(Netherrack server, com.netherrack.server.player.Player player) {
        call(server, new PlayerQuitEvent(apiPlayer(server, player)));
    }

    /** Announces a metadata change before the adapter applies it. */
    public static PlayerMetadataChangeEvent playerMetadataChange(Netherrack server,
                                                                com.netherrack.server.player.Player player,
                                                                String key, String oldValue,
                                                                String newValue) {
        PlayerMetadataChangeEvent event =
                new PlayerMetadataChangeEvent(apiPlayer(server, player), key, oldValue, newValue);
        call(server, event);
        return event;
    }

    public static PlayerChatEvent playerChat(Netherrack server,
                                             com.netherrack.server.player.Player player,
                                             String message) {
        PlayerChatEvent event = new PlayerChatEvent(apiPlayer(server, player), message);
        call(server, event);
        return event;
    }

    public static PlayerCommandEvent playerCommand(Netherrack server,
                                                   com.netherrack.server.player.Player player,
                                                   String command) {
        PlayerCommandEvent event = new PlayerCommandEvent(apiPlayer(server, player), command);
        call(server, event);
        return event;
    }

    /** Announces damage after immunity, before health is written. The amount is mutable. */
    public static PlayerDamageEvent playerDamage(Netherrack server,
                                                  com.netherrack.server.player.Player player,
                                                  DamageCause cause, double amount) {
        PlayerDamageEvent event = new PlayerDamageEvent(apiPlayer(server, player), cause, amount);
        call(server, event);
        return event;
    }

    /** Announces a death before drops are spawned and the death message goes out. */
    public static PlayerDeathEvent playerDeath(Netherrack server,
                                                com.netherrack.server.player.Player player,
                                                DamageCause cause, String deathMessage,
                                                List<ItemData> drops) {
        List<ItemStack> apiDrops = new ArrayList<>();
        for (ItemData drop : drops) {
            apiDrops.add(stack(drop));
        }
        PlayerDeathEvent event = new PlayerDeathEvent(apiPlayer(server, player), cause, deathMessage,
                apiDrops);
        call(server, event);
        return event;
    }

    /** Announces the spawn point chosen for a respawn, which listeners may replace. */
    public static PlayerRespawnEvent playerRespawn(Netherrack server,
                                                    com.netherrack.server.player.Player player,
                                                    Location respawnLocation) {
        PlayerRespawnEvent event = new PlayerRespawnEvent(apiPlayer(server, player), respawnLocation);
        call(server, event);
        return event;
    }

    /** Announces a position change before the server records it. The destination is mutable. */
    public static PlayerMoveEvent playerMove(Netherrack server,
                                              com.netherrack.server.player.Player player,
                                              Vector3f from, Vector3f to, Vector3f rotation) {
        PlayerMoveEvent event = new PlayerMoveEvent(apiPlayer(server, player), location(from, rotation),
                location(to, rotation));
        call(server, event);
        return event;
    }

    /** Announces a teleport before the server moves the player or sends any packet. */
    public static PlayerTeleportEvent playerTeleport(Netherrack server,
                                                      com.netherrack.server.player.Player player,
                                                      Vector3f from, Vector3f fromRotation, Vector3f to,
                                                      Vector3f toRotation, TeleportCause cause) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(apiPlayer(server, player),
                location(from, fromRotation), location(to, toRotation), cause);
        call(server, event);
        return event;
    }

    /** Announces a game mode change before the client is told. Cancelling keeps the old mode. */
    public static PlayerGameModeChangeEvent playerGameModeChange(Netherrack server,
                                                                  com.netherrack.server.player.Player player,
                                                                  GameType from, GameType to) {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(apiPlayer(server, player),
                PlayerAdapter.gameMode(from), PlayerAdapter.gameMode(to));
        call(server, event);
        return event;
    }

    /** Announces a dropped item before it is added to a player's inventory. */
    public static PlayerItemPickupEvent itemPickup(Netherrack server,
                                                    com.netherrack.server.player.Player player,
                                                    ItemData item) {
        PlayerItemPickupEvent event = new PlayerItemPickupEvent(apiPlayer(server, player), stack(item));
        call(server, event);
        return event;
    }

    /** Announces an item being dropped into the world. The stack is mutable. */
    public static PlayerItemDropEvent itemDrop(Netherrack server,
                                                com.netherrack.server.player.Player player,
                                                ItemStack item) {
        PlayerItemDropEvent event = new PlayerItemDropEvent(apiPlayer(server, player), item);
        call(server, event);
        return event;
    }

    /** Announces an inventory click before the whole stack request is applied. */
    public static PlayerInventoryClickEvent inventoryClick(Netherrack server,
                                                            com.netherrack.server.player.Player player,
                                                            int slot, ItemData item, ItemData cursor,
                                                            InventoryClickAction action) {
        PlayerInventoryClickEvent event = new PlayerInventoryClickEvent(apiPlayer(server, player), slot,
                stack(item), stack(cursor), action);
        call(server, event);
        return event;
    }

    /** Announces an item use before placement, armor equipping or eating begins. */
    public static PlayerItemUseEvent itemUse(Netherrack server,
                                              com.netherrack.server.player.Player player,
                                              ItemStack item, int slot, boolean offhand,
                                              Vector3i blockPosition) {
        PlayerItemUseEvent event = new PlayerItemUseEvent(apiPlayer(server, player), item, slot, offhand,
                blockPosition == null ? null : position(blockPosition));
        call(server, event);
        return event;
    }

    /** Announces the start of a meal before the client is told and the food is consumed. */
    public static PlayerItemConsumeEvent itemConsume(Netherrack server,
                                                      com.netherrack.server.player.Player player,
                                                      ItemData item) {
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(apiPlayer(server, player), stack(item));
        call(server, event);
        return event;
    }

    /** Announces an action bar send and returns the message listeners want the client to see. */
    public static PlayerActionBarEvent playerActionBar(Netherrack server,
                                                       com.netherrack.server.player.Player player,
                                                       String message, int fadeInTime,
                                                       int stayTime, int fadeOutTime) {
        PlayerActionBarEvent event = new PlayerActionBarEvent(apiPlayer(server, player), message,
                fadeInTime, stayTime, fadeOutTime);
        call(server, event);
        return event;
    }

    /** Announces a toast send and returns the title and content listeners want the client to see. */
    public static PlayerToastEvent playerToast(Netherrack server,
                                               com.netherrack.server.player.Player player,
                                               String title, String content) {
        PlayerToastEvent event = new PlayerToastEvent(apiPlayer(server, player), title, content);
        call(server, event);
        return event;
    }

    /** Announces a popup send and returns the message listeners want the client to see. */
    public static PlayerPopupEvent playerPopup(Netherrack server,
                                               com.netherrack.server.player.Player player,
                                               String message) {
        PlayerPopupEvent event = new PlayerPopupEvent(apiPlayer(server, player), message);
        call(server, event);
        return event;
    }

    /** Completes the matching form, then lets other plugins observe the raw response. */
    public static void formResponse(Netherrack server,
                                    com.netherrack.server.player.Player player,
                                    int formId, String data, boolean closed) {
        PlayerAdapter adapter = apiPlayer(server, player);
        Form form = adapter.completeForm(formId, data, closed);
        if (form != null) {
            call(server, new PlayerFormRespondedEvent(adapter, form, formId, data, closed));
        }
    }

    /** Clears a disconnected player's open form without calling its callbacks. */
    public static void closeForm(Netherrack server, com.netherrack.server.player.Player player) {
        apiPlayer(server, player).discardForm();
    }

    public static BlockPlaceEvent blockPlace(Netherrack server,
                                             com.netherrack.server.player.Player player,
                                             Vector3i position, Block block) {
        BlockPlaceEvent event = new BlockPlaceEvent(apiPlayer(server, player), position(position),
                BlockSnapshots.snapshot(block));
        call(server, event);
        return event;
    }

    public static BlockBreakEvent blockBreak(Netherrack server,
                                             com.netherrack.server.player.Player player,
                                             Vector3i position, Block block) {
        BlockBreakEvent event = new BlockBreakEvent(apiPlayer(server, player), position(position),
                BlockSnapshots.snapshot(block));
        call(server, event);
        return event;
    }

    /** The API's view of a server damage cause, with a custom escape hatch for unknown sources. */
    public static DamageCause damageCause(com.netherrack.server.player.PlayerSurvival.DamageCause cause) {
        return switch (cause) {
            case PLAYER -> DamageCause.ATTACK;
            case FALL -> DamageCause.FALL;
            case LAVA -> DamageCause.LAVA;
            case FIRE -> DamageCause.FIRE;
            case DROWNING -> DamageCause.DROWNING;
            case STARVATION -> DamageCause.STARVATION;
            case VOID -> DamageCause.VOID;
            default -> DamageCause.CUSTOM;
        };
    }

    /** Converts a protocol stack into the immutable API form; air is used for unknown stacks. */
    public static ItemStack stack(ItemData item) {
        if (item == null || item.isNull() || item.getDefinition() == null) {
            return new ItemStack("minecraft:air", 0);
        }
        return new ItemStack(item.getDefinition().getIdentifier(), item.getCount(),
                (short) item.getDamage());
    }

    /** Converts an API stack back to the protocol form, for events that change what is used. */
    public static ItemData item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemData.AIR;
        }
        ItemData item = VanillaData.get().item(stack.getId(), stack.getCount());
        if (item.isNull()) {
            return ItemData.AIR;
        }
        return item.getDamage() == Short.toUnsignedInt(stack.getDamage()) ? item
                : item.toBuilder().damage(Short.toUnsignedInt(stack.getDamage())).build();
    }

    /** API locations are at the feet and use yaw then pitch; server positions are eyes. */
    public static Location location(Vector3f eyes, Vector3f rotation) {
        return new Location(eyes.getX(), eyes.getY() - com.netherrack.server.player.Player.EYE_HEIGHT,
                eyes.getZ(), rotation.getY(), rotation.getX());
    }

    /** Turns an API location back into the eye position movement and teleport packets carry. */
    public static Vector3f position(Location location) {
        return Vector3f.from(location.getX(),
                location.getY() + com.netherrack.server.player.Player.EYE_HEIGHT, location.getZ());
    }

    /** Bedrock stores rotation as pitch, yaw, head yaw; the API location stores yaw then pitch. */
    public static Vector3f rotation(Location location) {
        return Vector3f.from(location.getPitch(), location.getYaw(), location.getYaw());
    }

    private static PlayerAdapter apiPlayer(Netherrack server,
                                           com.netherrack.server.player.Player player) {
        return server.getPluginManager().adapter().player(player);
    }

    private static void call(Netherrack server, Event event) {
        server.getPluginManager().callEvent(event);
    }

    private static BlockPosition position(Vector3i position) {
        return new BlockPosition(position.getX(), position.getY(), position.getZ());
    }
}
