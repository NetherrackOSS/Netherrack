package com.netherrack.server.plugin;

import com.netherrack.api.plugin.Plugin;
import com.netherrack.api.plugin.PluginDescription;

import java.nio.file.Path;

/** One loaded plugin and the state the manager keeps for it. */
final class PluginContainer {

    final PluginDescription description;
    final Plugin plugin;
    final Path jar;
    final PluginClassLoader classLoader;

    boolean loaded;
    boolean enabled;
    boolean failed;
    boolean closed;

    PluginContainer(PluginDescription description, Plugin plugin, Path jar,
                    PluginClassLoader classLoader) {
        this.description = description;
        this.plugin = plugin;
        this.jar = jar;
        this.classLoader = classLoader;
    }

    String name() {
        return description.getName();
    }
}
