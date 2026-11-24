package com.netherrack.server.command;

import com.netherrack.server.player.Player;
import com.netherrack.server.setup.Lang;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputMessage;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputType;
import org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket;

/**
 * A player running a command from chat. Replies go back as command output, as vanilla's
 * do: the client words vanilla keys itself and shows failures in red.
 */
public class PlayerCommandSender implements CommandSender {

    private final Player player;
    private final CommandOriginData origin;

    /** @param origin the command request's origin, which the output has to answer to */
    public PlayerCommandSender(Player player, CommandOriginData origin) {
        this.player = player;
        this.origin = origin != null ? origin
                : new CommandOriginData(CommandOriginType.PLAYER, player.getUuid(), "", 0);
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public int getPermissionLevel() {
        return player.getCommandLevel();
    }

    @Override
    public void sendMessage(boolean success, String key, String... params) {
        Lang lang = Lang.of(player.getLanguageCode());
        if (key.startsWith("command.")) {
            send(success, new CommandOutputMessage(success, CommandSender.render(lang, key, params), new String[0]));
            return;
        }
        // Vanilla keys are known by clients, but our custom keys may only exist in our own lang
        // files. For non-default client locales, render locally so custom messages are translated.
        if (!Lang.DEFAULT_CODE.equals(lang.getCode()) && !lang.get(key).equals(key)) {
            send(success, new CommandOutputMessage(success, CommandSender.render(lang, key, params), new String[0]));
            return;
        }
        send(success, new CommandOutputMessage(success, key, params));
    }

    @Override
    public void sendText(String text) {
        // Not a key the client knows, so it shows the text as it stands.
        send(true, new CommandOutputMessage(true, text, new String[0]));
    }

    @Override
    public void sendColored(String color, String key, String... params) {
        Lang lang = Lang.of(player.getLanguageCode());
        if (!Lang.DEFAULT_CODE.equals(lang.getCode()) && !lang.get(key).equals(key)) {
            send(true, new CommandOutputMessage(true, color + CommandSender.render(lang, key, params), new String[0]));
            return;
        }
        // A key written "%key" inside other text is worded by the client in its place, so
        // the color code can go in front of it.
        send(true, new CommandOutputMessage(true, color + "%" + key, params));
    }

    private void send(boolean success, CommandOutputMessage message) {
        CommandOutputPacket output = new CommandOutputPacket();
        output.setCommandOriginData(origin);
        output.setType(CommandOutputType.ALL_OUTPUT);
        output.setSuccessCount(success ? 1 : 0);
        output.getMessages().add(message);
        output.setData("");
        player.getSession().sendPacket(output);
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
