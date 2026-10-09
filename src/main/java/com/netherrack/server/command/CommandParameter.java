package com.netherrack.server.command;

import java.util.List;

/**
 * One parameter of a command, for players' command suggestions and the help command's
 * usage lines: a player, a whole number, or a choice from a fixed list (such as game
 * modes).
 *
 * @param typeName what the usage line calls its type, e.g. "target" or "GameMode"
 * @param choices  the values to choose from; empty unless it's a choice
 */
public record CommandParameter(String name, boolean optional, Kind kind, String typeName, List<String> choices) {

    public enum Kind { PLAYER, INT, CHOICE }

    public static CommandParameter player(String name, boolean optional) {
        return new CommandParameter(name, optional, Kind.PLAYER, "target", List.of());
    }

    public static CommandParameter integer(String name, boolean optional) {
        return new CommandParameter(name, optional, Kind.INT, "int", List.of());
    }

    public static CommandParameter choice(String name, boolean optional, String choicesName, List<String> choices) {
        return new CommandParameter(name, optional, Kind.CHOICE, choicesName, choices);
    }

    /** As vanilla's usage lines write it: "&lt;name: type&gt;", or "[name: type]" if it's optional. */
    public String usage() {
        return (optional ? "[" : "<") + name + ": " + typeName + (optional ? "]" : ">");
    }
}
