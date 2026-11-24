package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.world.World;

import java.util.List;
import java.util.Locale;

/**
 * time set|add|query ... - shows or changes the world time. Nukkit-style names (day, night,
 * noon, midnight, sunrise, sunset) are accepted, as are raw tick values.
 */
public class TimeCommand implements Command {

    @Override
    public String getName() {
        return "time";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(
                List.of(CommandParameter.choice("mode", false, "TimeModeQuery", List.of("query")),
                        CommandParameter.choice("value", true, "TimeQuery", List.of("daytime", "gametime", "day"))),
                List.of(CommandParameter.choice("mode", false, "TimeModeSet", List.of("set")),
                        CommandParameter.choice("value", false, "TimeName", List.of("day", "night", "noon", "midnight", "sunrise", "sunset"))),
                List.of(CommandParameter.choice("mode", false, "TimeModeSet", List.of("set")),
                        CommandParameter.integer("value", false)),
                List.of(CommandParameter.choice("mode", false, "TimeModeAdd", List.of("add")),
                        CommandParameter.integer("value", false)));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendFailure("commands.generic.usage", "/time <set|add|query> <value>");
            return;
        }
        World world = server.getWorld();
        if (world == null) {
            sender.sendFailure("commands.generic.exception");
            return;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "query" -> query(sender, world, args);
            case "set" -> set(server, sender, world, args);
            case "add" -> add(server, sender, world, args);
            default -> sender.sendFailure("commands.generic.usage", "/time <set|add|query> <value>");
        }
    }

    private static void query(CommandSender sender, World world, String[] args) {
        String value = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "daytime";
        int time = world.getTime();
        switch (value) {
            case "day", "daytime" -> sender.sendSuccess("commands.time.query", String.valueOf(time));
            case "gametime" -> sender.sendSuccess("commands.time.query.gametime", String.valueOf(time));
            default -> sender.sendSuccess("commands.time.query", String.valueOf(time));
        }
    }

    private static void set(Netherrack server, CommandSender sender, World world, String[] args) {
        if (args.length < 2) {
            sender.sendFailure("commands.generic.usage", "/time set <day|night|noon|midnight|sunrise|sunset|value>");
            return;
        }
        Integer value = parseTime(args[1]);
        if (value == null) {
            sender.sendFailure("commands.generic.usage", "/time set <day|night|noon|midnight|sunrise|sunset|value>");
            return;
        }
        world.setTime(value);
        server.getPlayerManager().broadcastTime(world.getTime());
        Command.broadcastCommandMessage(server, sender, "commands.time.set",
                String.valueOf(world.getTime()));
    }

    private static void add(Netherrack server, CommandSender sender, World world, String[] args) {
        if (args.length < 2) {
            sender.sendFailure("commands.generic.usage", "/time add <value>");
            return;
        }
        Integer amount = parseInt(args[1]);
        if (amount == null) {
            sender.sendFailure("commands.generic.num.invalid", args[1]);
            return;
        }
        world.setTime(world.getTime() + amount);
        server.getPlayerManager().broadcastTime(world.getTime());
        Command.broadcastCommandMessage(server, sender, "commands.time.added", String.valueOf(amount));
    }

    private static Integer parseTime(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "day" -> 1000;
            case "night" -> 13000;
            case "noon" -> 6000;
            case "midnight" -> 18000;
            case "sunrise" -> 23000;
            case "sunset" -> 12000;
            default -> parseInt(value);
        };
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
