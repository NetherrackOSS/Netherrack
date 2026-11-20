package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.plugin.PluginLogger;
import com.netherrack.api.plugin.PluginManager;
import com.netherrack.server.Netherrack;

/**
 * Presents the running server to plugins through the API's small Server interface.
 * <p>
 * Only the pieces that exist in the lifecycle phase are exposed here. Player, world and
 * scheduler access will be added as those parts of the API are built; the adapter keeps that
 * growth away from plugin code.
 */
public final class ServerAdapter implements Server {

    private final Netherrack server;
    private final PluginLogger logger;
    private PluginManagerImpl pluginManager;

    public ServerAdapter(Netherrack server) {
        this.server = server;
        this.logger = new PluginLoggerImpl("Server");
    }

    /** Set once by the manager, after the circular reference between the two is established. */
    void setPluginManager(PluginManagerImpl pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public String getName() {
        return "Netherrack";
    }

    @Override
    public String getVersion() {
        return Netherrack.VERSION;
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    /** The server behind the adapter, for other implementation-layer classes. */
    Netherrack server() {
        return server;
    }
}
