package com.netherrack.server.command;

import com.netherrack.server.util.Logger;

/** The server console, which may run every command and whose replies go to the log. */
public final class ConsoleCommandSender implements CommandSender {

    public static final ConsoleCommandSender INSTANCE = new ConsoleCommandSender();

    private ConsoleCommandSender() {
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public int getPermissionLevel() {
        return 4;
    }

    @Override
    public void sendMessage(boolean success, String key, String... params) {
        Logger.info(CommandSender.render(key, params));
    }

    @Override
    public void sendText(String text) {
        Logger.info(text);
    }

    @Override
    public void sendColored(String color, String key, String... params) {
        Logger.info(CommandSender.render(key, params));
    }
}
