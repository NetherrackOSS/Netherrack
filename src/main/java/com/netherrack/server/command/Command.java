package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Permission;
import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;

import java.util.List;

/**
 * A command, run from the console or by a player in chat. Each command gets its own class
 * (StopCommand, VersionCommand, ...) registered with a CommandManager, rather than one big
 * switch statement.
 */
public interface Command {

    String getName();

    /**
     * Alternate names this command also responds to (e.g. "?" for "help"). Empty by default.
     */
    default List<String> getAliases() {
        return List.of();
    }

    /**
     * The description players see when typing the command, and in help: vanilla's own
     * description key by default ("commands.&lt;name&gt;.description"), which the client
     * words itself.
     */
    default String getDescription() {
        return "commands." + getName() + ".description";
    }

    /**
     * The ways the command can be written, for players' command suggestions and help's
     * usage lines: each one a list of parameters. No parameters at all by default.
     */
    default List<List<CommandParameter>> getOverloads() {
        return List.of(List.of());
    }

    /**
     * Whether the client has this command's parameters built in, as it does vanilla's help.
     * The client adds its own to whatever the server sends, so for these the server sends
     * none, or the client shows each one twice.
     */
    default boolean hasClientParameters() {
        return false;
    }

    /**
     * The permission level needed to run this command, as vanilla's: 0 for any player,
     * 2 for changing the game (e.g. game modes), 3 for managing players (e.g. op), 4 for
     * managing the server itself (e.g. stop). Players get 0, or op-permission-level from
     * server.properties if they're an operator; the console can run everything.
     */
    default int getPermissionLevel() {
        return 0;
    }

    /**
     * An API permission node that also gates this command, or an empty string when the command
     * level above is the only requirement. Plugin commands use this so permission manager plugins
     * can allow or deny them without changing the built-in op-level system.
     */
    default String getPermissionNode() {
        return "";
    }

    /**
     * Shows a command's result to the people who watch admin commands, as Cloudburst Nukkit does
     * with its administrative broadcast channel. Each recipient gets the result in their own
     * language: the source sees it alone, the console logs "[source: result]" in the server's
     * language, and other online operators get that line in gray italics in their client language.
     * Only a command's outcome goes through here: errors, usage and information queries stay with
     * the sender so the channel remains worth watching.
     */
    static void broadcastCommandMessage(Netherrack server, CommandSender sender, String key,
                                        String... params) {
        Player sourcePlayer = sender.getPlayer();
        if (sourcePlayer != null) {
            sender.sendText(CommandSender.render(langOf(sourcePlayer), key, params));
        }
        Logger.info(adminLine(Lang.current, sender.getName(), key, params));
        for (Player operator : server.getPlayerManager().getPlayers()) {
            if (operator.getPermission() == Permission.OPERATOR && operator != sourcePlayer) {
                String line = adminLine(langOf(operator), sender.getName(), key, params);
                operator.sendText("\u00a77\u00a7o" + line + "\u00a7r");
            }
        }
    }

    private static Lang langOf(Player player) {
        return Lang.of(player.getLanguageCode());
    }

    private static String adminLine(Lang lang, String source, String key, String[] params) {
        return "[" + source + ": " + CommandSender.render(lang, key, params) + "]";
    }

    void execute(Netherrack server, CommandSender sender, String[] args);

    /**
     * Runs the command, including the name the sender actually typed so plugins can see which
     * alias was used. Built-in commands keep using the simpler overload.
     */
    default void execute(Netherrack server, CommandSender sender, String[] args, String label) {
        execute(server, sender, args);
    }
}
