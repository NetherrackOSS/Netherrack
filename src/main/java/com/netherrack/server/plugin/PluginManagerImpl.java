package com.netherrack.server.plugin;

import com.netherrack.api.command.PluginCommand;
import com.netherrack.api.event.Event;
import com.netherrack.api.plugin.CommandDescription;
import com.netherrack.api.event.EventManager;
import com.netherrack.api.event.Listener;
import com.netherrack.api.plugin.Plugin;
import com.netherrack.api.plugin.PluginBase;
import com.netherrack.api.plugin.PluginDescription;
import com.netherrack.api.plugin.PluginLoadOrder;
import com.netherrack.api.plugin.PluginManager;
import com.netherrack.server.Netherrack;
import com.netherrack.server.command.CommandSender;
import com.netherrack.server.util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Loads jars from plugins/, keeps their lifecycle in dependency order, dispatches events to their
 * listeners, and exposes the plugin-facing part of that state through the API's PluginManager
 * interface.
 * <p>
 * The server calls discover() once after its config is ready, then onLoad in two passes (STARTUP
 * before the world exists, POSTWORLD after it does), enableAll once everything is listening, and
 * disableAll on shutdown. A plugin that throws in a callback is marked failed: its dependents are
 * not enabled, while the rest of the plugins carry on.
 */
public final class PluginManagerImpl implements PluginManager {

    private static final Comparator<PluginContainer> LOAD_ORDER =
            Comparator.comparingInt((PluginContainer c) ->
                            c.description.getLoadOrder() == PluginLoadOrder.STARTUP ? 0 : 1)
                    .thenComparing(PluginContainer::name);

    private final Path pluginsDir;
    private final ServerAdapter server;
    private final JavaPluginLoader loader;
    private final EventManager events;
    private final List<PluginContainer> plugins = new ArrayList<>();
    private final Map<Plugin, List<PluginCommandAdapter>> pluginCommands = new IdentityHashMap<>();
    private boolean discovered;

    public PluginManagerImpl(Netherrack server, Path pluginsDir) {
        this.pluginsDir = pluginsDir;
        this.server = new ServerAdapter(server);
        this.events = new EventManager(error -> {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Logger.error("Error in a plugin event handler: " + describe(cause));
        });
        ClassLoader parent = server.getClass().getClassLoader();
        this.loader = new JavaPluginLoader(pluginsDir, this, this.server,
                parent != null ? parent : ClassLoader.getSystemClassLoader());
        this.server.setPluginManager(this);
    }

    /** Finds and parses plugin jars. Logging is already configured by the time this is called. */
    public void discover() {
        if (discovered) {
            return;
        }
        discovered = true;
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            Logger.error("Could not create plugin directory " + pluginsDir + ": " + e.getMessage());
            return;
        }

        List<Path> jars;
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            Logger.error("Could not list plugin directory " + pluginsDir + ": " + e.getMessage());
            return;
        }

        Map<String, PluginContainer> byName = new LinkedHashMap<>();
        for (Path jar : jars) {
            try {
                PluginContainer container = loader.load(jar);
                String key = container.name().toLowerCase(Locale.ROOT);
                if (byName.containsKey(key)) {
                    Logger.warn("Duplicate plugin name \"" + container.name() + "\" in "
                            + jar.getFileName() + "; skipping that jar.");
                    close(container);
                    continue;
                }
                byName.put(key, container);
                Logger.info("Loaded plugin " + container.name() + " v"
                        + container.description.getVersion() + " from " + jar.getFileName());
            } catch (Exception e) {
                Logger.error("Could not load plugin from " + jar.getFileName() + ": " + describe(e));
            }
        }

        List<PluginContainer> ordered = order(byName);
        Set<PluginContainer> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(ordered);
        for (PluginContainer container : byName.values()) {
            if (!kept.contains(container)) {
                close(container);
            }
        }
        plugins.addAll(ordered);
    }

    /** Calls onLoad() for plugins of the given load order, once each. */
    public void callOnLoad(PluginLoadOrder order) {
        for (PluginContainer container : List.copyOf(plugins)) {
            if (container.description.getLoadOrder() == order && !container.loaded && !container.failed) {
                container.loaded = true;
                lifecycle(container, "onLoad", Plugin::onLoad);
            }
        }
    }

    /** Enables every plugin that loaded successfully, in dependency order. */
    public void enableAll() {
        for (PluginContainer container : List.copyOf(plugins)) {
            if (!container.loaded || container.failed || !dependenciesReady(container)) {
                continue;
            }
            setEnabled(container, true);
            registerCommands(container);
            lifecycle(container, "onEnable", Plugin::onEnable);
            if (container.failed) {
                setEnabled(container, false);
                cleanup(container);
            }
        }
    }

    /** Disables every loaded plugin in reverse order and releases its jar. */
    public void disableAll() {
        List<PluginContainer> reverse = new ArrayList<>(plugins);
        Collections.reverse(reverse);
        for (PluginContainer container : reverse) {
            if (!container.loaded || container.closed) {
                continue;
            }
            setEnabled(container, false);
            lifecycle(container, "onDisable", Plugin::onDisable);
            cleanup(container);
            close(container);
        }
    }

    @Override
    public Plugin getPlugin(String name) {
        if (name == null) {
            return null;
        }
        for (PluginContainer container : plugins) {
            if (container.name().equalsIgnoreCase(name)) {
                return container.plugin;
            }
        }
        return null;
    }

    @Override
    public Collection<Plugin> getPlugins() {
        List<Plugin> result = new ArrayList<>();
        for (PluginContainer container : plugins) {
            result.add(container.plugin);
        }
        return List.copyOf(result);
    }

    @Override
    public boolean isPluginEnabled(String name) {
        if (name == null) {
            return false;
        }
        for (PluginContainer container : plugins) {
            if (container.name().equalsIgnoreCase(name)) {
                return container.enabled && !container.failed;
            }
        }
        return false;
    }

    @Override
    public PluginCommand getCommand(String name, Plugin plugin) {
        if (name == null || plugin == null) {
            return null;
        }
        for (PluginCommandAdapter adapter : pluginCommands.getOrDefault(plugin, List.of())) {
            PluginCommand command = adapter.apiCommand();
            if (command.getName().equalsIgnoreCase(name)) {
                return command;
            }
            for (String alias : command.getAliases()) {
                if (alias.equalsIgnoreCase(name)) {
                    return command;
                }
            }
        }
        return null;
    }

    /** Checks an API permission node for a server command sender. */
    public boolean hasPermission(CommandSender sender, String node) {
        if (sender.getPlayer() != null) {
            return hasPermission(sender.getPlayer(), node);
        }
        return server.consoleSender().hasPermission(node);
    }

    /** Checks an API permission node for a connected player. */
    public boolean hasPermission(com.netherrack.server.player.Player player, String node) {
        return server.player(player).hasPermission(node);
    }

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        events.registerEvents(listener, plugin);
    }

    @Override
    public void unregisterEvents(Listener listener) {
        events.unregisterEvents(listener);
    }

    @Override
    public void unregisterEvents(Plugin plugin) {
        events.unregisterEvents(plugin);
    }

    @Override
    public void callEvent(Event event) {
        events.callEvent(event);
    }

    /** The API-facing server, for EventBridge's conversions. */
    ServerAdapter adapter() {
        return server;
    }

    /**
     * Creates each declared command before onEnable(), so a plugin can configure its executor as
     * soon as it starts. Names already owned by the server or another plugin are left alone:
     * letting a plugin replace /help by accident would be worse than refusing the command.
     */
    private void registerCommands(PluginContainer container) {
        List<PluginCommandAdapter> adapters = new ArrayList<>();
        for (CommandDescription description : container.description.getCommands()) {
            PluginCommand apiCommand = new PluginCommand(container.plugin, description);
            PluginCommandAdapter adapter = new PluginCommandAdapter(apiCommand, server);
            adapters.add(adapter);

            List<String> names = new ArrayList<>();
            names.add(description.getName());
            names.addAll(description.getAliases());
            boolean taken = names.stream().anyMatch(name -> server.server().getCommandManager().hasName(name));
            if (taken) {
                Logger.warn("Plugin " + container.name() + " could not register command "
                        + description.getName() + ": that name or an alias is already in use.");
                continue;
            }
            server.server().getCommandManager().register(adapter);
        }
        pluginCommands.put(container.plugin, adapters);
    }

    /** Removes everything a plugin registered or scheduled, whether it stopped cleanly or failed. */
    private void cleanup(PluginContainer container) {
        server.pluginScheduler().cancelTasks(container.plugin);
        List<PluginCommandAdapter> adapters = pluginCommands.remove(container.plugin);
        if (adapters != null) {
            for (PluginCommandAdapter adapter : adapters) {
                server.server().getCommandManager().unregister(adapter);
            }
        }
        server.permissionManager().unregisterPermissions(container.plugin);
        events.unregisterEvents(container.plugin);
    }

    private boolean dependenciesReady(PluginContainer container) {
        for (String name : container.description.getDepend()) {
            if (!isPluginEnabled(name)) {
                Logger.warn("Plugin " + container.name() + " was not enabled because required plugin "
                        + name + " is not enabled.");
                container.failed = true;
                return false;
            }
        }
        return true;
    }

    private static void setEnabled(PluginContainer container, boolean enabled) {
        container.enabled = enabled;
        if (container.plugin instanceof PluginBase base) {
            base.setEnabled(enabled);
        }
    }

    private static void lifecycle(PluginContainer container, String phase, Consumer<Plugin> action) {
        try {
            action.accept(container.plugin);
        } catch (Exception | LinkageError e) {
            container.failed = true;
            Logger.error("Plugin " + container.name() + " threw during " + phase + ": " + describe(e));
        }
    }

    /** Resolves depend, softdepend and loadbefore into a stable order, dropping broken plugins. */
    private static List<PluginContainer> order(Map<String, PluginContainer> byName) {
        Map<PluginContainer, Set<PluginContainer>> dependencies = new LinkedHashMap<>();
        for (PluginContainer container : byName.values()) {
            dependencies.put(container, new LinkedHashSet<>());
        }

        Set<PluginContainer> failed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PluginContainer container : byName.values()) {
            for (String name : container.description.getDepend()) {
                requiredDependency(container, name, byName, dependencies, failed);
            }
            for (String name : container.description.getSoftDepend()) {
                PluginContainer dependency = byName.get(name.toLowerCase(Locale.ROOT));
                if (dependency != null && dependency != container && !failed.contains(dependency)) {
                    dependencies.get(container).add(dependency);
                }
            }
        }
        for (PluginContainer container : byName.values()) {
            for (String name : container.description.getLoadBefore()) {
                PluginContainer later = byName.get(name.toLowerCase(Locale.ROOT));
                if (later != null && later != container && !failed.contains(container)
                        && !failed.contains(later)) {
                    dependencies.get(later).add(container);
                }
            }
        }

        boolean changed;
        do {
            changed = false;
            for (PluginContainer container : byName.values()) {
                if (failed.contains(container)) {
                    continue;
                }
                for (PluginContainer dependency : dependencies.get(container)) {
                    if (failed.contains(dependency)) {
                        failed.add(container);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        List<PluginContainer> remaining = new ArrayList<>();
        for (PluginContainer container : byName.values()) {
            if (!failed.contains(container)) {
                remaining.add(container);
            }
        }

        List<PluginContainer> ordered = new ArrayList<>();
        Set<PluginContainer> done = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!remaining.isEmpty()) {
            List<PluginContainer> ready = remaining.stream()
                    .filter(container -> dependencies.get(container).stream().allMatch(done::contains))
                    .sorted(LOAD_ORDER)
                    .toList();
            if (ready.isEmpty()) {
                Logger.warn("Plugin dependency cycle detected among: " + names(remaining));
                ready = List.of(remaining.stream().sorted(LOAD_ORDER).findFirst().orElseThrow());
            }
            PluginContainer next = ready.get(0);
            remaining.remove(next);
            done.add(next);
            ordered.add(next);
        }
        return ordered;
    }

    private static void requiredDependency(PluginContainer container, String name,
                                           Map<String, PluginContainer> byName,
                                           Map<PluginContainer, Set<PluginContainer>> dependencies,
                                           Set<PluginContainer> failed) {
        String key = name.toLowerCase(Locale.ROOT);
        PluginContainer dependency = byName.get(key);
        if (dependency == null) {
            Logger.warn("Plugin " + container.name() + " requires missing plugin " + name
                    + "; it will not be loaded.");
            failed.add(container);
            return;
        }
        if (dependency == container) {
            Logger.warn("Plugin " + container.name() + " depends on itself; it will not be loaded.");
            failed.add(container);
            return;
        }
        dependencies.get(container).add(dependency);
    }

    private static String names(List<PluginContainer> containers) {
        List<String> result = new ArrayList<>();
        for (PluginContainer container : containers) {
            result.add(container.name());
        }
        return String.join(", ", result);
    }

    private static void close(PluginContainer container) {
        if (container.closed) {
            return;
        }
        container.closed = true;
        try {
            container.classLoader.close();
        } catch (IOException e) {
            Logger.debug("Could not close the classloader for " + container.name() + ": " + e.getMessage());
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.toString()
                : e.getClass().getSimpleName() + ": " + message;
    }
}
