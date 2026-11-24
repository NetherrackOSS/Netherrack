package com.netherrack.server.command;

import com.netherrack.server.Netherrack;
import com.netherrack.server.player.Player;
import org.cloudburstmc.math.vector.Vector3f;

import java.util.List;

/**
 * tp &lt;destination&gt; / tp &lt;victim&gt; &lt;destination&gt; / tp &lt;x&gt; &lt;y&gt; &lt;z&gt; /
 * tp &lt;victim&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; - teleports a player, themselves by default, to
 * another player or to a position. Coordinates are where the player's feet go, as in vanilla,
 * and may be written relative to where they are, e.g. "~ ~5 ~-2".
 */
public class TpCommand implements Command {

    private static final String USAGE =
            "/tp <destination> | <victim> <destination> | <x> <y> <z> | <victim> <x> <y> <z>";

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public List<List<CommandParameter>> getOverloads() {
        return List.of(
                List.of(CommandParameter.player("destination", false)),
                List.of(CommandParameter.player("victim", false), CommandParameter.player("destination", false)),
                List.of(CommandParameter.coordinate("x", false, 'x'),
                        CommandParameter.coordinate("y", false, 'y'),
                        CommandParameter.coordinate("z", false, 'z')),
                List.of(CommandParameter.player("victim", false),
                        CommandParameter.coordinate("x", false, 'x'),
                        CommandParameter.coordinate("y", false, 'y'),
                        CommandParameter.coordinate("z", false, 'z')));
    }

    @Override
    public int getPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(Netherrack server, CommandSender sender, String[] args) {
        switch (args.length) {
            case 1 -> {
                Player target = sender.getPlayer();
                if (target == null) {
                    sender.sendFailure("commands.generic.usage", USAGE);
                    return;
                }
                Player destination = find(server, sender, args[0]);
                if (destination == null) {
                    sender.sendFailure("commands.generic.player.notFound");
                    return;
                }
                toPlayer(server, sender, target, destination);
            }
            case 2 -> {
                Player target = find(server, sender, args[0]);
                Player destination = find(server, sender, args[1]);
                if (target == null || destination == null) {
                    sender.sendFailure("commands.generic.player.notFound");
                    return;
                }
                toPlayer(server, sender, target, destination);
            }
            case 3 -> {
                Player target = sender.getPlayer();
                if (target == null) {
                    sender.sendFailure("commands.generic.usage", USAGE);
                    return;
                }
                toCoordinates(server, sender, target, args, 0);
            }
            case 4 -> {
                Player target = find(server, sender, args[0]);
                if (target == null) {
                    sender.sendFailure("commands.generic.player.notFound");
                    return;
                }
                toCoordinates(server, sender, target, args, 1);
            }
            default -> sender.sendFailure("commands.generic.usage", USAGE);
        }
    }

    private static void toPlayer(Netherrack server, CommandSender sender, Player target, Player destination) {
        server.getPlayerManager().teleport(target, destination.getPosition());

        String key = "commands.tp.success";
        String[] params = {target.getUsername(), destination.getUsername()};
        if (target != sender.getPlayer()) {
            target.sendTranslation(key, params);
        }
        Command.broadcastCommandMessage(server, sender, key, params);
    }

    private static void toCoordinates(Netherrack server, CommandSender sender, Player target, String[] args, int first) {
        Vector3f position = parse(target, args, first);
        if (position == null) {
            sender.sendFailure("commands.generic.usage", USAGE);
            return;
        }
        server.getPlayerManager().teleport(target, position);

        Vector3f feet = position.sub(0, Player.EYE_HEIGHT, 0);
        String[] params = {target.getUsername(), number(feet.getX()), number(feet.getY()), number(feet.getZ())};
        String key = "commands.tp.success.coordinates";
        if (target != sender.getPlayer()) {
            target.sendTranslation(key, params);
        }
        Command.broadcastCommandMessage(server, sender, key, params);
    }

    /** The position the three coordinates name, at eye height, or null if one isn't a number. */
    private static Vector3f parse(Player target, String[] args, int first) {
        Vector3f feet = target.getFeetPosition();
        Double x = axis(args[first], feet.getX());
        Double y = axis(args[first + 1], feet.getY());
        Double z = axis(args[first + 2], feet.getZ());
        if (x == null || y == null || z == null) {
            return null;
        }
        return Vector3f.from(x, y + Player.EYE_HEIGHT, z);
    }

    /** One coordinate: an absolute number, or "~" for where the player is, or "~n" relative to it. */
    private static Double axis(String text, float current) {
        if (text.startsWith("~")) {
            String offset = text.substring(1);
            if (offset.isEmpty()) {
                return (double) current;
            }
            try {
                return current + Double.parseDouble(offset);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Player find(Netherrack server, CommandSender sender, String name) {
        return name.equalsIgnoreCase("@s") ? sender.getPlayer() : server.getPlayerManager().getPlayer(name);
    }

    /** A number as a command message shows it, without trailing zeros. */
    private static String number(float value) {
        return String.valueOf(Math.round(value * 100f) / 100f);
    }
}
