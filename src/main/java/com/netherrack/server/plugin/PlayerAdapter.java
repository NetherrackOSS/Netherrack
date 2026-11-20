package com.netherrack.server.plugin;

import com.netherrack.api.entity.Player;
import com.netherrack.api.world.World;
import com.netherrack.server.network.SessionDisconnect;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

import java.util.UUID;

/**
 * Presents one connected player through the API's Player interface.
 * <p>
 * The player this wraps is still the server's own object; the adapter only keeps plugin code from
 * depending on protocol classes. ServerAdapter keeps one of these per UUID so a plugin that holds
 * onto a Player in an event keeps seeing the same object.
 */
final class PlayerAdapter implements Player {

    private final com.netherrack.server.player.Player player;
    private final ServerAdapter apiServer;

    PlayerAdapter(com.netherrack.server.player.Player player, ServerAdapter apiServer) {
        this.player = player;
        this.apiServer = apiServer;
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public UUID getUuid() {
        return player.getUuid();
    }

    @Override
    public String getXuid() {
        return player.getXuid();
    }

    @Override
    public World getWorld() {
        return apiServer.worldAdapter();
    }

    @Override
    public void sendMessage(String message) {
        TextPacket text = new TextPacket();
        text.setType(TextPacket.Type.SYSTEM);
        text.setSourceName("");
        text.setXuid("");
        text.setMessage(message);
        text.setNeedsTranslation(false);
        player.getSession().sendPacket(text);
    }

    @Override
    public void kick(String reason) {
        SessionDisconnect.disconnect(player.getSession(), reason);
    }

    com.netherrack.server.player.Player serverPlayer() {
        return player;
    }
}
