package com.netherrack.server.network;

import com.netherrack.server.Netherrack;
import com.netherrack.server.ServerConfig;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.util.Logger;
import com.netherrack.server.util.NettyLogBridge;
import com.netherrack.server.world.World;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.cloudburstmc.netty.channel.nethernet.NetherNetServerChannel;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPServerSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.PongData;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.netty.util.nethernet.PlayerInfo;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Wraps CloudburstMC's NetherNet (WebRTC, over Netty) transport so the rest of Netherrack
 * doesn't need to touch the transport/Netty layer directly.
 * <p>
 * Replaces the old RakNet-based transport: as of Bedrock 1.26.60, Mojang removed RakNet
 * from the client entirely, and every connection now goes over NetherNet instead - ICE +
 * DTLS + SCTP data channels, negotiated through an HTTP "signaling" exchange rather than a
 * raw UDP handshake. The actual game data still flows as Bedrock packets once a NetherNet
 * channel is up, the same way it did over RakNet; only the transport underneath changed.
 * <p>
 * Credit: https://github.com/CloudburstMC/Network (NetherNet transport, "nethernet" branch -
 *         not yet merged to Network's main line, so its API may still move)
 *         https://github.com/CloudburstMC/Protocol (Bedrock protocol version/codec info)
 */
public class NetherNetServer {

    private final ServerConfig config;

    private EventLoopGroup eventLoopGroup;
    private Channel channel;

    public NetherNetServer(ServerConfig config) {
        this.config = config;
    }

    public void start(World world, PlayerManager players, Netherrack netherrack) {
        String ip = config.get("server-ip", "0.0.0.0");
        int port = config.getInt("server-port", 19132);
        int maxPlayers = config.getInt("max-players", 20);

        // A persistent identity for the server (an EC key pair), so it presents the same
        // one across restarts instead of a fresh, unrecognized one each time. Stored next
        // to server.properties; created automatically on first run.
        OperatorIdentity identity;
        try {
            identity = OperatorIdentity.fromPemOrCreate(
                    new File("nethernet-identity.pem"), config.get("server-name", "Netherrack Server"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load or create the NetherNet server identity", e);
        }

        NetherNetHTTPServerSignaling signaling = new NetherNetHTTPServerSignaling.Builder()
                .setIdentity(identity)
                // Requires a real Xbox-issued token from connecting clients. NetherNet has no
                // RakNet-style ping/pong of its own to authenticate against, so this is what
                // actually establishes who's connecting - without it, any client could present
                // any identity.
                .setTokenTrust(TokenTrust.MINECRAFT_AUTH)
                // Netherrack has no TLS certificate management yet. The real client falls back
                // to plaintext HTTP signaling when the server doesn't require TLS - see
                // Network's docs/external-signaling notes - so this keeps a direct-connect
                // server workable without needing a certificate.
                .setRequiresTls(false)
                .setMotd(buildMotd(world, maxPlayers))
                .build();

        eventLoopGroup = new NioEventLoopGroup();

        ChannelFuture future = new ServerBootstrap()
                .channelFactory(NetherNetChannelFactory.server(signaling))
                .group(eventLoopGroup)
                .handler(new ChannelInitializer<NetherNetServerChannel>() {
                    @Override
                    protected void initChannel(NetherNetServerChannel ch) {
                        Logger.info("NetherNet server channel initialized.");
                    }
                })
                .childHandler(new NetherNetServerInitializer() {
                    @Override
                    protected void initSession(BedrockServerSession session) {
                        Channel ch = session.getPeer().getChannel();

                        if (players.getPlayers().size() >= maxPlayers) {
                            session.disconnect("Server is full");
                            return;
                        }

                        PlayerInfo info = ch.attr(NetherNetChildChannel.PLAYER_INFO).get();
                        InetSocketAddress remote = info != null
                                ? info.remoteAddress()
                                : (InetSocketAddress) session.getSocketAddress();
                        Logger.info("Incoming NetherNet connection from "
                                + remote.getAddress().getHostAddress() + ":" + remote.getPort());

                        // NetherNet installs the transport identity binding itself when it
                        // accepts the player (see NetherNetServerChannel.acceptConnection), so
                        // installing it here would throw "Identity binding already installed".
                        // NetherrackPacketHandler's LoginPacket handling checks that binding
                        // with TransportIdentityBinding.mismatch(...) before accepting a login.

                        // Logs every packet in and out of this session (at trace level, so
                        // only visible with debug logging on in netherrack.yml).
                        session.setLogging(NettyLogBridge.isDebug());

                        NetherrackPacketHandler handler = new NetherrackPacketHandler(session, world, players, netherrack);
                        session.setPacketHandler(handler);

                        ch.closeFuture().addListener(f -> {
                            handler.onDisconnect();
                            Logger.info(handler.getDisplayName() + " disconnected.");
                        });
                    }
                })
                .bind(new InetSocketAddress(ip, port));

        future.awaitUninterruptibly();

        if (!future.isSuccess()) {
            Logger.error("Failed to bind NetherNet server on " + ip + ":" + port + ": " + future.cause());
            throw new RuntimeException(future.cause());
        }

        channel = future.channel();
    }

    public void stop() {
        if (channel != null) {
            // Bounded, so a connection that won't close can't keep the server from stopping.
            channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
    }

    /**
     * The server info shown in the client's server list - NetherNet's structured equivalent
     * of the semicolon-delimited MOTD string RakNet's advertisement used.
     */
    private PongData buildMotd(World world, int maxPlayers) {
        return new PongData.Builder()
                .setServerName(config.get("server-name", "Netherrack Server"))
                .setProtocol(Bedrock_v2225.CODEC.getProtocolVersion())
                .setVersion(Bedrock_v2225.CODEC.getMinecraftVersion())
                .setLevelName(config.get("level-name", "Bedrock level"))
                .setPlayerCount(0)
                .setMaxPlayerCount(maxPlayers)
                // transportLayer, connectionType and dataVersion are left at PongData's own
                // defaults (see PongData.DEFAULT) - this project doesn't yet know the exact
                // wire values the real client expects for these specifically, and they aren't
                // things we can verify without a client to test against.
                .build();
    }
}
