package com.netherrack.server.command;

import java.util.List;

/**
 * One parameter of a command, for players' command suggestions and the help command's
 * usage lines: a player, a whole number, an item, coordinates, or a choice from a fixed
 * list (such as game modes).
 *
 * @param typeName what the usage line calls its type, e.g. "target", "item" or "x"
 * @param choices  the values to choose from; empty unless it's a choice
 */
public record CommandParameter(String name, boolean optional, Kind kind, String typeName, List<String> choices) {

    public enum Kind { PLAYER, INT, STRING, COORD_X, COORD_Y, COORD_Z, CHOICE }

    public static CommandParameter player(String name, boolean optional) {
        return new CommandParameter(name, optional, Kind.PLAYER, "target", List.of());
    }

    public static CommandParameter integer(String name, boolean optional) {
        return new CommandParameter(name, optional, Kind.INT, "int", List.of());
    }

    /** Free text the server reads itself, such as an item's name. */
    public static CommandParameter string(String name, boolean optional, String typeName) {
        return new CommandParameter(name, optional, Kind.STRING, typeName, List.of());
    }

    /** An item's name, e.g. "stone" or "minecraft:diamond_sword". */
    public static CommandParameter item(String name, boolean optional) {
        return string(name, optional, "item");
    }

    /** One of a position's three numbers, which may also be written relative to the player, e.g. "~1". */
    public static CommandParameter coordinate(String name, boolean optional, char axis) {
        Kind kind = switch (Character.toLowerCase(axis)) {
            case 'x' -> Kind.COORD_X;
            case 'y' -> Kind.COORD_Y;
            case 'z' -> Kind.COORD_Z;
            default -> throw new IllegalArgumentException("Not an axis: " + axis);
        };
        return new CommandParameter(name, optional, kind, String.valueOf(Character.toLowerCase(axis)), List.of());
    }

    public static CommandParameter choice(String name, boolean optional, String choicesName, List<String> choices) {
        return new CommandParameter(name, optional, Kind.CHOICE, choicesName, choices);
    }

    /** As vanilla's usage lines write it: "&lt;name: type&gt;", or "[name: type]" if it's optional. */
    public String usage() {
        return (optional ? "[" : "<") + name + ": " + typeName + (optional ? "]" : ">");
    }
}
