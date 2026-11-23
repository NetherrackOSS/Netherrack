package com.netherrack.example.hello;

import com.netherrack.api.event.EventHandler;
import com.netherrack.api.event.Listener;
import com.netherrack.api.event.player.PlayerChatEvent;
import com.netherrack.api.event.player.PlayerJoinEvent;
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
    public void onStop(ServerStopEvent event) {
        plugin.getLogger().info("HelloPlugin heard the server stop.");
    }
}
