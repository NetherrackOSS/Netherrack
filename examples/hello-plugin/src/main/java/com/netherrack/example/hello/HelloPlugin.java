package com.netherrack.example.hello;

import com.netherrack.api.plugin.PluginBase;

/**
 * The smallest plugin that shows all three lifecycle callbacks.
 * <p>
 * The greeting is kept in the plugin's config.yml, so the first start writes a file that can be
 * edited without rebuilding the jar. Nothing here touches players or blocks: those parts of the
 * API are still being built.
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
    }

    @Override
    public void onDisable() {
        getLogger().info("HelloPlugin disabled");
    }
}
