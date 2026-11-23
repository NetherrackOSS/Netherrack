package com.netherrack.server.plugin;

import com.netherrack.api.plugin.PluginLogger;
import com.netherrack.server.util.Logger;

/**
 * Routes a plugin's console output through the server logger, with the plugin's name in front.
 * <p>
 * The prefix means an operator can tell which plugin said what without plugins needing to know
 * how Netherrack formats its log lines. All four levels go through the same logger the server
 * uses, so plugin output obeys the logging section of netherrack.yml like everything else.
 */
public final class PluginLoggerImpl implements PluginLogger {

    private final String prefix;

    public PluginLoggerImpl(String name) {
        this.prefix = "[" + name + "] ";
    }

    @Override
    public void info(String message) {
        Logger.info(prefix + message);
    }

    @Override
    public void warn(String message) {
        Logger.warn(prefix + message);
    }

    @Override
    public void error(String message) {
        Logger.error(prefix + message);
    }

    @Override
    public void debug(String message) {
        Logger.debug(prefix + message);
    }
}
