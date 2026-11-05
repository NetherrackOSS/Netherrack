package com.netherrack.server.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;

import java.util.List;

/**
 * Bedrock pipeline for channels that arrive over NetherNet instead of RakNet.
 * <p>
 * CloudburstMC's {@link BedrockServerInitializer} is written for RakNet: it reads the
 * {@code RAK_PROTOCOL_VERSION} channel option and puts a {@code FrameIdCodec} in front of the
 * batch codec. NetherNet has neither of those. Its channel config does not carry RakNet
 * options, and the data channel already carries the raw batch body (no {@code 0xFE} frame id).
 * <p>
 * Above the frame codec the rest is identical to RakNet protocol 11: initial traffic is
 * uncompressed, then compression becomes prefixed ZLib, and packets use the v3 (varint)
 * framing. NetherNet therefore reuses Cloudburst's batch and packet codecs unchanged.
 */
public abstract class NetherNetServerInitializer extends BedrockServerInitializer {

    /**
     * NetherNet's batching, compression and packet framing follow RakNet protocol 11.
     */
    public static final int NETHERNET_CODEC_VERSION = 11;

    private static final NetherNetFrameCodec FRAME_CODEC = new NetherNetFrameCodec();

    @Override
    protected void preInitChannel(Channel channel) throws Exception {
        channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.CLIENT_BOUND);

        // NetherNet hands us the batch directly, so this only wraps/unwraps the ByteBuf into
        // a BedrockBatchWrapper; there is no RakNet frame id to add or strip.
        channel.pipeline().addLast(NetherNetFrameCodec.NAME, FRAME_CODEC);

        CompressionStrategy compression = BedrockChannelInitializer.getCompression(
                PacketCompressionAlgorithm.ZLIB, NETHERNET_CODEC_VERSION, true);
        channel.pipeline().addLast(CompressionCodec.NAME, new CompressionCodec(compression, false));
    }

    @Override
    protected void initPacketCodec(Channel channel) {
        channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
    }

    @Override
    protected BedrockPeer createPeer(Channel channel) {
        return new BedrockPeer(channel, this::createSession) {
            /**
             * The Bedrock connection layer still asks the transport for this when changing
             * compression. There is no RakNet option on a NetherNet channel, so report the
             * protocol-equivalent version instead of unboxing null.
             */
            @Override
            public int getRakVersion() {
                return NETHERNET_CODEC_VERSION;
            }
        };
    }

    /**
     * Moves a compressed batch between NetherNet's data channel and the Bedrock batch codecs.
     * NetherNet's payload is the batch body exactly, with no frame id.
     */
    @Sharable
    public static final class NetherNetFrameCodec
            extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {

        public static final String NAME = "nethernet-frame-codec";

        @Override
        protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) {
            if (msg.getCompressed() == null) {
                throw new IllegalStateException("Bedrock batch was not compressed");
            }
            out.add(msg.getCompressed().retainedSlice());
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
            if (!msg.isReadable()) {
                return;
            }
            out.add(BedrockBatchWrapper.newInstance(msg.readRetainedSlice(msg.readableBytes()), null));
        }
    }
}
