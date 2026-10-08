package com.netherrack.server.network;

import com.netherrack.server.ServerConfig;
import com.netherrack.server.util.Logger;
import com.netherrack.server.util.NettyLogBridge;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import com.netherrack.server.world.World;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wraps CloudburstMC's RakNet (Netty) transport so the rest of Netherrack doesn't
 * need to touch the transport/Netty layer directly.
 * <p>
 * Credit: https://github.com/CloudburstMC/Network (RakNet transport)
 *         https://github.com/CloudburstMC/Protocol (Bedrock protocol version/codec info)
 */
public class RakNetServer {

    private final ServerConfig config;
    private final long guid = ThreadLocalRandom.current().nextLong();

    private EventLoopGroup eventLoopGroup;
    private Channel channel;

    public RakNetServer(ServerConfig config) {
        this.config = config;
    }

    public void start(World world) {
        String ip = config.get("server-ip", "0.0.0.0");
        int port = config.getInt("server-port", 19132);

        eventLoopGroup = new NioEventLoopGroup();

        ChannelFuture future = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .group(eventLoopGroup)
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, new int[]{Bedrock_v2193.CODEC.getRaknetProtocolVersion()})
                .option(RakChannelOption.RAK_GUID, guid)
                .option(RakChannelOption.RAK_MAX_CONNECTIONS, config.getInt("max-players", 20))
                .option(RakChannelOption.RAK_ADVERTISEMENT, Unpooled.wrappedBuffer(buildAdvertisement()))
                .handler(new ChannelInitializer<RakServerChannel>() {
                    @Override
                    protected void initChannel(RakServerChannel ch) {
                        Logger.info("RakNet server channel initialized.");
                    }
                })
                .childHandler(new BedrockServerInitializer() {
                    @Override
                    protected void initSession(BedrockServerSession session) {
                        InetSocketAddress remote = (InetSocketAddress) session.getSocketAddress();
                        Logger.info("Incoming RakNet connection from " + remote.getAddress().getHostAddress() + ":" + remote.getPort());

                        // Logs every packet in and out of this session (at trace level, so
                        // only visible with debug=true).
                        session.setLogging(NettyLogBridge.isDebug());

                        NetherrackPacketHandler handler = new NetherrackPacketHandler(session, world);
                        session.setPacketHandler(handler);

                        session.getPeer().getChannel().closeFuture().addListener(f ->
                                Logger.info(handler.getDisplayName() + " disconnected."));
                    }
                })
                .bind(new InetSocketAddress(ip, port));

        future.awaitUninterruptibly();

        if (!future.isSuccess()) {
            Logger.error("Failed to bind RakNet server on " + ip + ":" + port + ": " + future.cause());
            throw new RuntimeException(future.cause());
        }

        channel = future.channel();
    }

    public void stop() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
    }

    /**
     * Builds an "unconnected pong" advertisement string in the format Bedrock clients expect,
     * e.g. MCPE;Netherrack Server;<protocol>;<version>;<players>;<max players>;<guid>;<name>;...
     */
    private byte[] buildAdvertisement() {
        String motd = config.get("server-name", "Netherrack Server");
        String levelName = config.get("level-name", "world");
        int maxPlayers = config.getInt("max-players", 20);

        String advertisement = new StringJoiner(";", "", ";")
                .add("MCPE")
                .add(motd)
                .add(Integer.toString(Bedrock_v2193.CODEC.getProtocolVersion()))
                .add(Bedrock_v2193.CODEC.getMinecraftVersion())
                .add("0")
                .add(Integer.toString(maxPlayers))
                .add(Long.toUnsignedString(guid))
                .add(levelName)
                .add(config.get("gamemode", "survival"))
                .add("1")
                .add(Integer.toString(config.getInt("server-port", 19132)))
                .add(Integer.toString(config.getInt("server-port", 19132)))
                .toString();

        return advertisement.getBytes(StandardCharsets.UTF_8);
    }
}
