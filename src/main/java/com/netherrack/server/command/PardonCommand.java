package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.setup.Lang;

import java.util.List;

/**
 * pardon &lt;player&gt;, also available as /unban - removes a ban by name, XUID or UUID. This is
 * Nukkit's PardonCommand naming: the vanilla word is the command and "unban" is the familiar
 * alias. The target may be offline, since the ban list is looked up without loading a world.
 */
public class PardonCommand implements Command {

    @Override
    public String getName() {
        return "pardon";
    }

    @Override
    public List<String> getAliases() {
        return List.of("unban");
    }

    @Override
    public String getDescription() {
        return Lang.current.get("command.description.pardon");
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(CommandParameter.string("player", false, "player")));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendFailure("command.pardon.usage");
            return;
        }

        boolean removed = server.getBans().remove(args[0]);
        sender.sendSuccess(removed ? "command.pardon.success" : "command.pardon.not_banned", args[0]);
    }
}
