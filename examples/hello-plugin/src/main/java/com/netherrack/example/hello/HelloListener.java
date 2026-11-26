package com.netherrack.example.hello;

import com.netherrack.api.event.EventHandler;
import com.netherrack.api.event.Listener;
import com.netherrack.api.event.player.PlayerActionBarEvent;
import com.netherrack.api.event.player.PlayerChatEvent;
import com.netherrack.api.event.player.PlayerDamageEvent;
import com.netherrack.api.event.player.PlayerItemUseEvent;
import com.netherrack.api.event.player.PlayerJoinEvent;
import com.netherrack.api.event.player.PlayerPopupEvent;
import com.netherrack.api.event.player.PlayerToastEvent;
import com.netherrack.api.event.server.ServerStartEvent;
import com.netherrack.api.event.server.ServerStopEvent;

/** Shows how a plugin listens to events after registering itself in onEnable(). */
public final class HelloListener implements Listener {

    private final HelloPlugin plugin;

    HelloListener(HelloPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onStart(ServerStartEvent event) {
        plugin.getLogger().info("HelloPlugin heard the server start.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("Welcome, " + event.getPlayer().getName() + "!");
    }

    /**
     * A cancelled chat event never reaches the server's broadcast, so this answers "!hello" in a
     * private message instead of showing it to everyone.
     */
    @EventHandler
    public void onChat(PlayerChatEvent event) {
        if (event.getMessage().equalsIgnoreCase("!hello")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.greeting());
        }
    }

    @EventHandler
    public void onActionBar(PlayerActionBarEvent event) {
        if (event.getMessage().contains("HelloPlugin")) {
            plugin.getLogger().info("HelloPlugin heard action bar: " + event.getMessage());
        }
    }

    @EventHandler
    public void onToast(PlayerToastEvent event) {
        if (event.getTitle().contains("HelloPlugin")) {
            plugin.getLogger().info("HelloPlugin heard toast: " + event.getContent());
        }
    }

    @EventHandler
    public void onPopup(PlayerPopupEvent event) {
        if (event.getMessage().contains("HelloPlugin")) {
            plugin.getLogger().info("HelloPlugin heard popup: " + event.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(PlayerDamageEvent event) {
        plugin.getLogger().info(event.getPlayer().getName() + " took " + event.getAmount()
                + " damage (" + event.getCause() + ").");
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemUse(PlayerItemUseEvent event) {
        plugin.getLogger().info(event.getPlayer().getName() + " used " + event.getItem().getId()
                + " in slot " + event.getSlot() + ".");
    }

    @EventHandler
    public void onStop(ServerStopEvent event) {
        plugin.getLogger().info("HelloPlugin heard the server stop.");
    }
}
