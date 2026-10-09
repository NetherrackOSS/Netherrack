package com.netherrack.server.command;

import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The commands a player's client suggests as they type "/", with each one's description
 * and parameters. Only commands the player's permission level allows are listed, so it's
 * sent again whenever that level changes.
 * <p>
 * Every command is listed as usable by anyone. The server already only lists the commands
 * a player may run, and checks again when one is run - but given its real level (e.g.
 * "admin"), the client shows none of a command's parameters.
 * <p>
 * Commands the client has built in, such as help, are sent without parameters: the client
 * adds its own to the server's. The client's own-device commands, such as gametips, aren't
 * sent at all - only the game's own local worlds run those, a dedicated server can't.
 */
public final class AvailableCommands {

    private AvailableCommands() {
    }

    public static AvailableCommandsPacket forLevel(CommandManager commands, int permissionLevel) {
        AvailableCommandsPacket packet = new AvailableCommandsPacket();
        for (Command command : commands.getCommands()) {
            if (command.getPermissionLevel() <= permissionLevel) {
                packet.getCommands().add(toCommandData(command));
            }
        }
        return packet;
    }

    private static CommandData toCommandData(Command command) {
        // The aliases list holds the command's own name too, as vanilla's does.
        List<String> names = new ArrayList<>();
        names.add(command.getName());
        names.addAll(command.getAliases());
        CommandEnumData aliases = command.getAliases().isEmpty() ? null
                : enumData(capitalized(command.getName()) + "Aliases", names);

        List<CommandOverloadData> overloads = new ArrayList<>();
        List<List<CommandParameter>> sent = command.hasClientParameters() ? List.of() : command.getOverloads();
        for (List<CommandParameter> overload : sent) {
            CommandParamData[] params = new CommandParamData[overload.size()];
            for (int i = 0; i < params.length; i++) {
                params[i] = toParamData(overload.get(i));
            }
            overloads.add(new CommandOverloadData(false, params));
        }

        return new CommandData(command.getName(), command.getDescription(), Set.of(),
                CommandPermission.ANY, aliases, List.of(),
                overloads.toArray(new CommandOverloadData[0]));
    }

    private static CommandParamData toParamData(CommandParameter parameter) {
        CommandParamData data = new CommandParamData();
        data.setName(parameter.name());
        data.setOptional(parameter.optional());
        switch (parameter.kind()) {
            case PLAYER -> data.setType(CommandParam.TARGET);
            case INT -> data.setType(CommandParam.INT);
            case CHOICE -> data.setEnumData(enumData(parameter.typeName(), parameter.choices()));
        }
        return data;
    }

    private static CommandEnumData enumData(String name, List<String> values) {
        Map<String, Set<CommandEnumConstraint>> constrained = new LinkedHashMap<>();
        for (String value : values) {
            constrained.put(value, Set.of());
        }
        return new CommandEnumData(name, constrained, false);
    }

    private static String capitalized(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
