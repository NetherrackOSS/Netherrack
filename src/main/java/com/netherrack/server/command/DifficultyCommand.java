package com.netherrack.server.command;

import com.netherrack.server.Netherrack;

import java.util.List;
import java.util.Locale;

/**
 * difficulty [peaceful|easy|normal|hard|0-3] - shows or changes the world difficulty.
 */
public class DifficultyCommand implements Command {

    @Override
    public String getName() {
        return "difficulty";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(List.of(
                CommandParameter.choice("difficulty", true, "Difficulty",
                        List.of("peaceful", "easy", "normal", "hard", "p", "e", "n", "h", "0", "1", "2", "3"))));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendSuccess("commands.difficulty.query", Netherrack.difficultyName(server.getDifficulty()));
            return;
        }
        if (args.length != 1) {
            sender.sendFailure("commands.generic.usage", "/difficulty <peaceful|easy|normal|hard>");
            return;
        }
        Integer difficulty = parseDifficulty(args[0]);
        if (difficulty == null) {
            sender.sendFailure("commands.generic.usage", "/difficulty <peaceful|easy|normal|hard>");
            return;
        }
        server.setDifficulty(difficulty);
        server.getPlayerManager().broadcastDifficulty(difficulty);
        sender.sendSuccess("commands.difficulty.success", Netherrack.difficultyName(difficulty));
    }

    private static Integer parseDifficulty(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "peaceful", "p", "0" -> 0;
            case "easy", "e", "1" -> 1;
            case "normal", "n", "2" -> 2;
            case "hard", "h", "3" -> 3;
            default -> null;
        };
    }
}
