package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * help [page] / help &lt;command&gt; - vanilla's help: every way of writing each command the
 * sender may run, a page at a time, or one command's description and usage.
 */
public class HelpCommand implements Command {

    /** Usage lines on one page of the list. */
    private static final int LINES_PER_PAGE = 7;

    /** Dark green, as vanilla's help header and footer are. */
    private static final String HEADER_COLOR = "\u00a72";

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public List<String> getAliases() {
        return List.of("?");
    }

    /**
     * Vanilla's, as its usage lines show them: a page (needed in that form - plain "help"
     * is the other form, with its command left out), or a command.
     */
    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(
                List.of(CommandParameter.integer("page", false)),
                List.of(CommandParameter.choice("command", true, "CommandName", List.of())));
    }

    @Override
    public boolean hasClientParameters() {
        return true;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        Map<String, Command> usable = usableByName(server.getCommandManager(), sender);
        if (args.length == 1 && !args[0].matches("-?\\d+")) {
            describe(sender, args[0].toLowerCase(), usable);
            return;
        }

        List<String> lines = new ArrayList<>();
        usable.forEach((name, command) -> lines.addAll(usageLines(name, command)));
        int pages = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        // Past the last page shows the last one, however big the number.
        int page = 1;
        if (args.length == 1) {
            page = args[0].length() > 9 ? (args[0].startsWith("-") ? 1 : pages) : Integer.parseInt(args[0]);
            page = Math.max(1, Math.min(pages, page));
        }

        sender.sendColored(HEADER_COLOR, "commands.help.header", String.valueOf(page), String.valueOf(pages));
        for (String line : lines.subList((page - 1) * LINES_PER_PAGE, Math.min(lines.size(), page * LINES_PER_PAGE))) {
            sender.sendText(line);
        }
        sender.sendColored(HEADER_COLOR, "commands.help.footer");
    }

    /** One command's name (and aliases), description and every way of writing it. */
    private static void describe(CommandSender sender, String name, Map<String, Command> usable) {
        Command command = usable.get(name);
        if (command == null) {
            sender.sendFailure("commands.generic.unknown", name);
            return;
        }
        if (command.getAliases().isEmpty()) {
            sender.sendText(command.getName() + ":");
        } else {
            sender.sendSuccess("commands.help.command.aliases", command.getName(), String.join(", ", command.getAliases()));
        }
        sender.sendSuccess(command.getDescription());
        sender.sendSuccess("commands.generic.usage.noparam");
        for (String line : usageLines(command.getName(), command)) {
            sender.sendText("- " + line);
        }
    }

    /** The commands the sender may run, by every name they go by (aliases included), sorted as vanilla's list is. */
    private static Map<String, Command> usableByName(CommandManager commands, CommandSender sender) {
        Map<String, Command> byName = new TreeMap<>();
        for (Command command : commands.getCommands()) {
            if (sender.getPermissionLevel() >= command.getPermissionLevel()) {
                byName.put(command.getName(), command);
                for (String alias : command.getAliases()) {
                    byName.put(alias, command);
                }
            }
        }
        return byName;
    }

    private static List<String> usageLines(String name, Command command) {
        List<String> lines = new ArrayList<>();
        for (List<CommandParameter> overload : command.getOverloads()) {
            StringBuilder line = new StringBuilder("/").append(name);
            for (CommandParameter parameter : overload) {
                line.append(' ').append(parameter.usage());
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
