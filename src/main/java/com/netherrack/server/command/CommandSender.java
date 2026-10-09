package com.netherrack.server.command;

import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;

/**
 * Whoever ran a command: the console, or a player from chat. Replies go back to them, and
 * their permission level decides which commands they may run.
 * <p>
 * Replies are translation keys with parameters. Where vanilla has a message for something,
 * its key is used (e.g. "commands.op.success"), so players see their own client's text, in
 * their own language - Netherrack's lang files only word these for the console. Keys of
 * Netherrack's own (starting "command.") are worded by the server for everyone. A parameter
 * starting with "%" is itself a key, such as "%createWorldScreen.gameMode.creative".
 */
public interface CommandSender {

    String getName();

    /**
     * 0 to 4, as vanilla's command permission levels - 0 for any player, up to 4 for
     * managing the server itself (such as stopping it). See Command.getPermissionLevel().
     */
    int getPermissionLevel();

    /** A reply. Failures are shown to players in red. */
    void sendMessage(boolean success, String key, String... params);

    default void sendSuccess(String key, String... params) {
        sendMessage(true, key, params);
    }

    default void sendFailure(String key, String... params) {
        sendMessage(false, key, params);
    }

    /** A reply that's text as it stands rather than a key, such as a command's usage line. */
    void sendText(String text);

    /**
     * A reply from a key, in a color - a formatting code such as "§2" for dark green, as
     * vanilla's help header and footer are. The console shows it uncolored.
     */
    void sendColored(String color, String key, String... params);

    /** The player who ran the command, or null for the console. */
    default Player getPlayer() {
        return null;
    }

    /**
     * A reply worded with Netherrack's own lang file, keys in parameters included. Whole
     * numbers are passed as numbers, since vanilla's messages use %d for some (e.g. help's
     * page numbers) - parameters are only ever strings on the wire.
     */
    static String render(String key, String... params) {
        Object[] resolved = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            String param = params[i];
            if (param.startsWith("%")) {
                resolved[i] = Lang.current.get(param.substring(1));
            } else if (param.matches("-?\\d{1,9}")) {
                resolved[i] = Integer.parseInt(param);
            } else {
                resolved[i] = param;
            }
        }
        return Lang.current.get(key, resolved);
    }
}
