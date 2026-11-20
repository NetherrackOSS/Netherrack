package com.netherrack.example.hello;

import com.netherrack.api.plugin.PluginBase;

/**
 * A small plugin that shows all three lifecycle callbacks and how to register a listener.
 * <p>
 * The greeting is kept in the plugin's config.yml, so the first start writes a file that can be
 * edited without rebuilding the jar. The listener demonstrates the events a plugin can hear.
 */
public final class HelloPlugin extends PluginBase {

    @Override
    public void onLoad() {
        getLogger().info("HelloPlugin loaded");
    }

    @Override
    public void onEnable() {
        String greeting = getConfig().get("greeting", "Hello from HelloPlugin!");
        getConfig().set("greeting", greeting);
        saveConfig();
        getLogger().info(greeting + " (running on " + getServer().getVersion() + ")");
        getServer().getPluginManager().registerEvents(new HelloListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("HelloPlugin disabled");
    }
}
