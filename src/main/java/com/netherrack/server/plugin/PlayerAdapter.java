package com.netherrack.server.plugin;

import com.netherrack.api.Server;
import com.netherrack.api.command.CommandSender;
import com.netherrack.api.entity.GameMode;
import com.netherrack.api.entity.Player;
import com.netherrack.api.event.player.PlayerActionBarEvent;
import com.netherrack.api.event.player.PlayerPopupEvent;
import com.netherrack.api.event.player.PlayerToastEvent;
import com.netherrack.api.form.Form;
import com.netherrack.api.permission.PermissionAttachment;
import com.netherrack.api.permission.PermissionState;
import com.netherrack.api.plugin.Plugin;
import com.netherrack.api.world.Location;
import com.netherrack.api.world.World;
import com.netherrack.server.network.SessionDisconnect;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Presents one connected player through the API's Player interface.
 * <p>
 * The player this wraps is still the server's own object; the adapter only keeps plugin code from
 * depending on protocol classes. ServerAdapter keeps one of these per UUID so a plugin that holds
 * onto a Player in an event keeps seeing the same object.
 */
final class PlayerAdapter implements Player, CommandSender {

    private final com.netherrack.server.player.Player player;
    private final ServerAdapter apiServer;
    private final PermissionSubject permissions;
    // Forms are opened and answered on the player's network thread. Keeping the open one here
    // means Nukkit-style form callbacks can live on the form itself without leaking protocol types.
    private final Map<Integer, Form> openForms = new HashMap<>();
    private boolean formOpen;

    PlayerAdapter(com.netherrack.server.player.Player player, ServerAdapter apiServer) {
        this.player = player;
        this.apiServer = apiServer;
        this.permissions = new PermissionSubject(
                () -> player.getPermission() == com.netherrack.server.player.Permission.OPERATOR,
                apiServer.permissionManager());
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
    public Location getLocation() {
        Vector3f feet = player.getFeetPosition();
        Vector3f rotation = player.getRotation();
        // Bedrock keeps rotation as (pitch, yaw, head yaw); the API uses yaw then pitch.
        return new Location(feet.getX(), feet.getY(), feet.getZ(), rotation.getY(), rotation.getX());
    }

    @Override
    public void teleport(Location location) {
        Objects.requireNonNull(location, "location");
        // API locations are at the feet; every movement packet and ChunkView uses eye height.
        Vector3f eyes = Vector3f.from(location.getX(),
                location.getY() + com.netherrack.server.player.Player.EYE_HEIGHT, location.getZ());
        Vector3f rotation = Vector3f.from(location.getPitch(), location.getYaw(), location.getYaw());
        apiServer.server().getPlayerManager().teleport(player, eyes, rotation);
    }

    @Override
    public GameMode getGameMode() {
        return gameMode(player.getGameMode());
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        Objects.requireNonNull(gameMode, "gameMode");
        // Go through PlayerManager so spectators are hidden and shown like /gamemode does.
        apiServer.server().getPlayerManager().changeGameMode(player, gameType(gameMode));
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
    public void sendActionBar(String message) {
        sendActionBar(message, 1, 0, 1);
    }

    @Override
    public void sendActionBar(String message, int fadeInTime, int stayTime, int fadeOutTime) {
        PlayerActionBarEvent event = EventBridge.playerActionBar(apiServer.server(), player, message,
                fadeInTime, stayTime, fadeOutTime);
        if (event.isCancelled()) {
            return;
        }
        SetTitlePacket title = new SetTitlePacket();
        title.setType(SetTitlePacket.Type.ACTIONBAR);
        title.setText(event.getMessage());
        title.setFadeInTime(event.getFadeInTime());
        title.setStayTime(event.getStayTime());
        title.setFadeOutTime(event.getFadeOutTime());
        // The codec writes these optional identifiers as strings, so null defaults fail to encode.
        title.setXuid("");
        title.setPlatformOnlineId("");
        title.setFilteredTitleText("");
        player.getSession().sendPacket(title);
    }

    @Override
    public void sendToast(String title, String content) {
        PlayerToastEvent event = EventBridge.playerToast(apiServer.server(), player, title, content);
        if (event.isCancelled()) {
            return;
        }
        ToastRequestPacket toast = new ToastRequestPacket();
        toast.setTitle(event.getTitle());
        toast.setContent(event.getContent());
        player.getSession().sendPacket(toast);
    }

    @Override
    public void sendPopup(String message) {
        PlayerPopupEvent event = EventBridge.playerPopup(apiServer.server(), player, message);
        if (event.isCancelled()) {
            return;
        }
        TextPacket text = new TextPacket();
        text.setType(TextPacket.Type.POPUP);
        text.setSourceName("");
        text.setXuid("");
        text.setPlatformChatId("");
        text.setMessage(event.getMessage());
        text.setNeedsTranslation(false);
        player.getSession().sendPacket(text);
    }

    @Override
    public int showForm(Form form) {
        Objects.requireNonNull(form, "form");
        // The client itself only renders one modal form at a time. Refusing the second request
        // keeps our id-to-form state from getting out of step with what the player can answer.
        if (formOpen || !openForms.isEmpty()) {
            return -1;
        }
        int formId = apiServer.nextFormId();
        ModalFormRequestPacket request = new ModalFormRequestPacket();
        request.setFormId(formId);
        request.setFormData(form.toJson());
        openForms.put(formId, form);
        formOpen = true;
        player.getSession().sendPacket(request);
        return formId;
    }

    /** Answers the one open form, returning it so EventBridge can announce the response. */
    Form completeForm(int formId, String data, boolean closed) {
        Form form = openForms.remove(formId);
        if (form == null) {
            return null;
        }
        formOpen = false;
        form.handleResponse(this, data, closed);
        return form;
    }

    /** Drops form state without running callbacks, for a player who has disconnected. */
    void discardForm() {
        openForms.clear();
        formOpen = false;
    }

    @Override
    public void kick(String reason) {
        SessionDisconnect.disconnect(player.getSession(), reason);
    }

    @Override
    public Server getServer() {
        return apiServer;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public PermissionState getPermissionValue(String node) {
        return permissions.getPermissionValue(node);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return permissions.addAttachment(plugin);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        permissions.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        permissions.recalculatePermissions();
    }

    private static GameMode gameMode(GameType type) {
        return switch (type) {
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR, SURVIVAL_VIEWER, CREATIVE_VIEWER -> GameMode.SPECTATOR;
            case SURVIVAL, DEFAULT -> GameMode.SURVIVAL;
        };
    }

    private static GameType gameType(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> GameType.SURVIVAL;
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
        };
    }

    com.netherrack.server.player.Player serverPlayer() {
        return player;
    }
}
