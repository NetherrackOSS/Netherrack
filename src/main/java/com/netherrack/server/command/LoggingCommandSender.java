package com.netherrack.server.command;

import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;

/**
 * Logs a player's command replies to the console as they're sent. The player still receives the
 * reply normally; the console already sees replies to console commands, so only player commands
 * are wrapped. Replies that go through the admin broadcast log themselves with a source prefix,
 * so {@link #unwrap(CommandSender)} lets that helper bypass this one.
 */
final class LoggingCommandSender implements CommandSender {

    private final CommandSender delegate;

    LoggingCommandSender(CommandSender delegate) {
        this.delegate = delegate;
    }

    /** The real sender behind a dispatch wrapper, used when a reply must not be logged twice. */
    static CommandSender unwrap(CommandSender sender) {
        return sender instanceof LoggingCommandSender logging ? logging.delegate : sender;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public int getPermissionLevel() {
        return delegate.getPermissionLevel();
    }

    @Override
    public void sendMessage(boolean success, String key, String... params) {
        delegate.sendMessage(success, key, params);
        Logger.info(CommandSender.render(lang(), key, params));
    }

    @Override
    public void sendText(String text) {
        delegate.sendText(text);
        Logger.info(text);
    }

    @Override
    public void sendColored(String color, String key, String... params) {
        delegate.sendColored(color, key, params);
        Logger.info(color + CommandSender.render(lang(), key, params));
    }

    @Override
    public Player getPlayer() {
        return delegate.getPlayer();
    }

    private Lang lang() {
        Player player = delegate.getPlayer();
        return player == null ? Lang.current : Lang.of(player.getLanguageCode());
    }
}
