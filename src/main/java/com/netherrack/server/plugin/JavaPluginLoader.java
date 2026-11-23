package com.netherrack.server.plugin;

import com.netherrack.api.ApiVersion;
import com.netherrack.api.Server;
import com.netherrack.api.plugin.Plugin;
import com.netherrack.api.plugin.PluginBase;
import com.netherrack.api.plugin.PluginDescription;
import com.netherrack.api.plugin.PluginDescriptionException;
import com.netherrack.api.plugin.PluginLogger;
import com.netherrack.api.plugin.PluginManager;
import com.netherrack.server.util.Logger;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reads a jar's plugin.yml and constructs its main class in a classloader of its own.
 * <p>
 * The jar stays open only while the descriptor is read. After that the classloader holds the
 * URL, so the manager can close it when the plugin is disabled and let the file be replaced.
 */
final class JavaPluginLoader {

    private final Path pluginsDir;
    private final PluginManagerImpl manager;
    private final Server server;
    private final ClassLoader parent;

    JavaPluginLoader(Path pluginsDir, PluginManagerImpl manager, Server server, ClassLoader parent) {
        this.pluginsDir = pluginsDir;
        this.manager = manager;
        this.server = server;
        this.parent = parent;
    }

    PluginContainer load(Path jar) throws Exception {
        PluginDescription description = describe(jar);
        if (!ApiVersion.isCompatible(description.getApiVersion())) {
            throw new PluginDescriptionException("api-version " + description.getApiVersion()
                    + " is not compatible with " + ApiVersion.CURRENT);
        }

        PluginClassLoader loader = new PluginClassLoader(new URL[]{jar.toUri().toURL()}, parent);
        Class<?> main = Class.forName(description.getMain(), false, loader);
        if (!Plugin.class.isAssignableFrom(main)) {
            throw new PluginDescriptionException("main class " + description.getMain()
                    + " does not implement Plugin");
        }

        Constructor<?> constructor = main.getDeclaredConstructor();
        constructor.setAccessible(true);
        Plugin plugin = (Plugin) constructor.newInstance();

        Path dataFolder = pluginsDir.resolve(description.getName());
        PluginLogger logger = new PluginLoggerImpl(description.getName());
        if (plugin instanceof PluginBase base) {
            base.initialize(manager, server, description, dataFolder, logger, loader);
        } else {
            initializeDirectImplementation(plugin, description, dataFolder, logger, loader);
        }
        return new PluginContainer(description, plugin, jar, loader);
    }

    private static PluginDescription describe(Path jar) throws Exception {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry("plugin.yml");
            if (entry == null) {
                throw new PluginDescriptionException("jar has no plugin.yml at its root");
            }
            try (InputStream in = file.getInputStream(entry)) {
                return PluginDescription.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * PluginBase is the supported way to write a plugin, but a main class implementing Plugin
     * directly can opt in to the same setup by declaring the same initialize method. Without it
     * the lifecycle methods still run; such a plugin is responsible for its own state.
     */
    private void initializeDirectImplementation(Plugin plugin, PluginDescription description,
                                                Path dataFolder, PluginLogger logger,
                                                PluginClassLoader loader) throws Exception {
        try {
            Method initialize = plugin.getClass().getMethod("initialize", PluginManager.class,
                    Server.class, PluginDescription.class, Path.class, PluginLogger.class,
                    ClassLoader.class);
            initialize.invoke(plugin, manager, server, description, dataFolder, logger, loader);
        } catch (NoSuchMethodException e) {
            Logger.debug("Plugin " + description.getName()
                    + " implements Plugin directly without an initialize method.");
        }
    }
}
