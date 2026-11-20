package com.netherrack.example.hello;

import com.netherrack.api.plugin.PluginBase;

/**
 * A small plugin that shows all three lifecycle callbacks and how to register a listener.
 * <p>
 * The greeting is kept in the plugin's config.yml, so the first start writes a file that can be
 * edited without rebuilding the jar. The listener demonstrates the events a plugin can hear.
 */
public final class HelloPlugin extends PluginBase {

    private String greeting = "Hello from HelloPlugin!";

    @Override
    public void onLoad() {
        getLogger().info("HelloPlugin loaded");
    }

    @Override
    public void onEnable() {
        greeting = getConfig().get("greeting", greeting);
        getConfig().set("greeting", greeting);
        saveConfig();
        getLogger().info(greeting + " (running on " + getServer().getVersion() + ")");
        getServer().getPluginManager().registerEvents(new HelloListener(this), this);
    }

    /** The configured greeting, used both at startup and when a player chats "!hello". */
    String greeting() {
        return greeting;
    }

    @Override
    public void onDisable() {
        getLogger().info("HelloPlugin disabled");
    }
}
