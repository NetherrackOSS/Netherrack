package com.netherrack.server.network;

import com.netherrack.server.util.ColorCodes;
import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;

/**
 * Records why a session ended and turns that reason into one plain console line.
 * <p>
 * A new BedrockSession starts with the generic "disconnect.lost" reason, and a transport close only
 * replaces it when it is still that default. A server kick is sent as a DisconnectPacket, so its
 * message would otherwise be gone by the time the channel's close listener prints the logout line.
 * Recording the reason on the session before disconnecting keeps that line useful for both client
 * quits and server kicks.
 */
public final class SessionDisconnect {

    private SessionDisconnect() {
    }

    /** Sends the kick and remembers it as the session's reason before the channel closes. */
    public static void disconnect(BedrockServerSession session, CharSequence reason) {
        String text = reason == null ? "Disconnected" : reason.toString();
        session.setDisconnectReason(text);
        session.disconnect(text);
    }

    /** The reason for this session, in words an operator can read in the console. */
    public static String describe(BedrockServerSession session) {
        CharSequence raw = session.getDisconnectReason();
        return friendly(raw == null ? "" : raw.toString());
    }

    private static String friendly(String reason) {
        String oneLine = ColorCodes.singleLine(reason).trim();
        if (oneLine.isEmpty()) {
            return "Disconnected";
        }
        return switch (oneLine) {
            case BedrockDisconnectReasons.DISCONNECTED, BedrockDisconnectReasons.UNKNOWN -> "Disconnected";
            case BedrockDisconnectReasons.CLOSED -> "Connection closed";
            case BedrockDisconnectReasons.REMOVED -> "Removed";
            case BedrockDisconnectReasons.TIMEOUT -> "Timed out";
            default -> oneLine;
        };
    }
}
