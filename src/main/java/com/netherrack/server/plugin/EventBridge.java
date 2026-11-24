package com.netherrack.server.plugin;

import com.netherrack.api.event.Event;
import com.netherrack.api.event.block.BlockBreakEvent;
import com.netherrack.api.event.block.BlockPlaceEvent;
import com.netherrack.api.event.player.PlayerActionBarEvent;
import com.netherrack.api.event.player.PlayerChatEvent;
import com.netherrack.api.event.player.PlayerCommandEvent;
import com.netherrack.api.event.player.PlayerFormRespondedEvent;
import com.netherrack.api.event.player.PlayerJoinEvent;
import com.netherrack.api.event.player.PlayerLoginEvent;
import com.netherrack.api.event.player.PlayerPopupEvent;
import com.netherrack.api.event.player.PlayerQuitEvent;
import com.netherrack.api.event.player.PlayerToastEvent;
import com.netherrack.api.event.server.ServerStartEvent;
import com.netherrack.api.event.server.ServerStopEvent;
import com.netherrack.api.form.Form;
import com.netherrack.api.world.BlockPosition;
import com.netherrack.server.Netherrack;
import com.netherrack.server.block.Block;
import org.cloudburstmc.math.vector.Vector3i;

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
