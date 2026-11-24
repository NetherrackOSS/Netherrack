package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.world.World;

import java.util.List;
import java.util.Locale;

/**
 * weather clear|rain|thunder [duration] - changes the world weather. Duration is accepted for
 * Nukkit command compatibility but Netherrack currently keeps the chosen weather until changed.
 */
public class WeatherCommand implements Command {

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(
                CommandParameter.choice("type", false, "WeatherType", List.of("clear", "rain", "thunder")),
                CommandParameter.integer("duration", true)));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendFailure("commands.generic.usage", "/weather <clear|rain|thunder> [duration]");
            return;
        }
        World world = server.getWorld();
        if (world == null) {
            sender.sendFailure("commands.generic.exception");
            return;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        boolean raining;
        boolean thundering;
        String key;
        switch (type) {
            case "clear" -> {
                raining = false;
                thundering = false;
                key = "commands.weather.clear";
            }
            case "rain" -> {
                raining = true;
                thundering = false;
                key = "commands.weather.rain";
            }
            case "thunder" -> {
                raining = true;
                thundering = true;
                key = "commands.weather.thunder";
            }
            default -> {
                sender.sendFailure("commands.generic.usage", "/weather <clear|rain|thunder> [duration]");
                return;
            }
        }
        if (args.length == 2) {
            try {
                Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendFailure("commands.generic.num.invalid", args[1]);
                return;
            }
        }
        world.setRaining(raining);
        world.setThundering(thundering);
        server.getPlayerManager().broadcastWeather(raining, thundering);
        Command.broadcastCommandMessage(server, sender, key);
    }
}
