package com.netherrack.server.network;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.world.Chunk;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * Encodes a Chunk into the raw bytes LevelChunkPacket carries in its "data" field - the
 * library doesn't do this part for you, there's no chunk/block-storage helper in Protocol,
 * so this follows Bedrock's own binary chunk format by hand:
 * <p>
 * Every sub-chunk in the dimension, bottom to top -> one biome-storage section per
 * sub-chunk -> border block count (always 0).
 * <p>
 * Per sub-chunk: version byte (9, the modern format with an explicit Y index) -> storage
 * layer count (always 2: blocks, then the "liquid" layer for waterlogging, which is all
 * air for now) -> signed Y index -> each block-storage layer (palette + bit-packed indices).
 * <p>
 * Sending only up to the highest non-empty sub-chunk, with zero-layer empty ones, parses
 * fine but real clients disconnected with a generic "Block" error on it. Real clients accept
 * the full-height, always-two-layer shape (confirmed by comparing against what AllayMC, a
 * working server on the same protocol library, puts on the wire), so that's what's sent.
 */
public final class ChunkEncoder {

    /** The overworld spans Y -64 to 319, i.e. sub-chunks -4 to 19. */
    private static final int MIN_SUB_CHUNK_Y = -4;
    private static final int MAX_SUB_CHUNK_Y = 19;

    /** What the LevelChunkPacket carrying {@link #encodeChunk(Chunk)} must set as subChunksLength. */
    public static final int SUB_CHUNK_COUNT = MAX_SUB_CHUNK_Y - MIN_SUB_CHUNK_Y + 1;

    /** Legacy numeric id for the "plains" biome - used uniformly, since there's no terrain variety yet. */
    private static final int PLAINS_BIOME_ID = 1;

    private ChunkEncoder() {
    }

    public static ByteBuf encodeChunk(Chunk chunk) {
        ByteBuf buf = Unpooled.buffer();

        for (int y = MIN_SUB_CHUNK_Y; y <= MAX_SUB_CHUNK_Y; y++) {
            writeSubChunk(buf, chunk, y);
        }

        for (int y = MIN_SUB_CHUNK_Y; y <= MAX_SUB_CHUNK_Y; y++) {
            writeBiomeSection(buf);
        }

        buf.writeByte(0); // border blocks: none

        // Block entities (NBT compounds) would follow here; there are none yet, so nothing
        // more is written - readers for this format read compounds until the buffer ends.

        return buf;
    }

    private static void writeSubChunk(ByteBuf buf, Chunk chunk, int subChunkY) {
        buf.writeByte(9); // sub-chunk format version
        buf.writeByte(2); // storage layer count
        buf.writeByte(subChunkY);

        writeBlockLayer(buf, chunk, subChunkY);
        writeAirLayer(buf);
    }

    /** The liquid layer: nothing is waterlogged yet, so it's a single-entry air palette. */
    private static void writeAirLayer(ByteBuf buf) {
        buf.writeByte((0 << 1) | 1);
        VarInts.writeInt(buf, Blocks.AIR.getBlockStateHash());
    }

    private static void writeBlockLayer(ByteBuf buf, Chunk chunk, int subChunkY) {
        int airHash = Blocks.AIR.getBlockStateHash();

        // One block state hash per of the 4096 positions in this sub-chunk, in x/z/y order
        // (index = (x << 8) | (z << 4) | y), matching how the client indexes it. These are
        // Bedrock's "hashed block network IDs", not palette-ordinal runtime IDs - see
        // Block.getBlockStateHash().
        int[] blockHashes = new int[4096];
        java.util.Map<Integer, Integer> paletteIndexByHash = new java.util.LinkedHashMap<>();
        paletteIndexByHash.put(airHash, 0);

        int worldYBase = subChunkY * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    Block block = chunk.getBlock(x, worldYBase + y, z);
                    int hash = block != null ? block.getBlockStateHash() : airHash;
                    int index = (x << 8) | (z << 4) | y;
                    blockHashes[index] = paletteIndexByHash.computeIfAbsent(hash, id -> paletteIndexByHash.size());
                }
            }
        }

        int paletteSize = paletteIndexByHash.size();
        int bitsPerBlock = bitsNeededFor(paletteSize);

        buf.writeByte((bitsPerBlock << 1) | 1); // low bit set = palette is a runtime-ID palette

        writePackedIndices(buf, blockHashes, bitsPerBlock);

        // With 0 bits per block the palette size isn't written - it's implicitly 1.
        if (bitsPerBlock != 0) {
            VarInts.writeInt(buf, paletteSize);
        }
        for (int hash : paletteIndexByHash.keySet()) {
            VarInts.writeInt(buf, hash);
        }
    }

    private static void writeBiomeSection(ByteBuf buf) {
        // Uniform single biome (plains) for the whole sub-chunk: bitsPerBlock 0 means
        // "every position is palette entry 0", so no index array or palette size is written.
        buf.writeByte((0 << 1) | 1);
        VarInts.writeInt(buf, PLAINS_BIOME_ID);
    }

    /**
     * Packs `indices` (each < 2^bitsPerBlock) into 32-bit little-endian words, each word
     * holding as many indices as fit, consecutive indices never split across a word boundary
     * (any leftover bits in a word are padding and ignored).
     */
    private static void writePackedIndices(ByteBuf buf, int[] indices, int bitsPerBlock) {
        if (bitsPerBlock == 0) {
            return; // single-entry palette: nothing to pack, every index is implicitly 0
        }

        int indicesPerWord = 32 / bitsPerBlock;
        int wordCount = (indices.length + indicesPerWord - 1) / indicesPerWord;

        int pos = 0;
        for (int w = 0; w < wordCount; w++) {
            int word = 0;
            for (int i = 0; i < indicesPerWord && pos < indices.length; i++, pos++) {
                word |= (indices[pos] & ((1 << bitsPerBlock) - 1)) << (i * bitsPerBlock);
            }
            buf.writeIntLE(word);
        }
    }

    /**
     * Smallest of Bedrock's allowed bits-per-block sizes (0,1,2,3,4,5,6,8,16) that can
     * represent `paletteSize` distinct values.
     */
    private static int bitsNeededFor(int paletteSize) {
        if (paletteSize <= 1) {
            return 0;
        }
        int[] allowed = {1, 2, 3, 4, 5, 6, 8, 16};
        for (int bits : allowed) {
            if ((1 << bits) >= paletteSize) {
                return bits;
            }
        }
        return 16;
    }
}
