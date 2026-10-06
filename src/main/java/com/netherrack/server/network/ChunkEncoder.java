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
 * so this follows Bedrock's own binary sub-chunk format by hand:
 * <p>
 * Per sub-chunk: version byte (9, the modern format with an explicit Y index, so sub-chunks
 * don't have to be contiguous from the bottom of the world) -> signed Y index -> storage
 * layer count (always 1 here, no "waterlogged" second layer) -> one block-storage layer
 * (palette + bit-packed indices) -> one biome-storage layer (same shape, biome IDs instead
 * of block runtime IDs).
 * <p>
 * This is the single riskiest hand-written part of Netherrack's protocol support - it was
 * written from protocol documentation/knowledge rather than verified against a real client,
 * since there's no way to test it in this environment. If a client disconnects or renders
 * something clearly wrong on joining, this is the first place to look.
 */
public final class ChunkEncoder {

    /** Legacy numeric id for the "plains" biome - used uniformly, since there's no terrain variety yet. */
    private static final int PLAINS_BIOME_ID = 1;

    private ChunkEncoder() {
    }

    /**
     * @param subChunkY the subchunk's Y index (world Y divided by 16, floored)
     */
    public static ByteBuf encodeSubChunk(Chunk chunk, int subChunkY) {
        ByteBuf buf = Unpooled.buffer();

        buf.writeByte(9); // sub-chunk format version
        buf.writeByte(subChunkY);
        buf.writeByte(1); // one block-storage layer (no second "waterlogged" layer)

        writeBlockLayer(buf, chunk, subChunkY);
        writeBiomeLayer(buf);

        VarInts.writeUnsignedInt(buf, 0); // border blocks: none

        // Block entities (NBT compounds) would follow here; there are none yet, so nothing
        // more is written - readers for this format read compounds until the buffer ends.

        return buf;
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

        VarInts.writeInt(buf, paletteSize);
        for (int hash : paletteIndexByHash.keySet()) {
            VarInts.writeInt(buf, hash);
        }
    }

    private static void writeBiomeLayer(ByteBuf buf) {
        // Uniform single biome (plains) for the whole sub-chunk: bitsPerBlock 0 means
        // "every position is palette entry 0", so no index array is written at all.
        buf.writeByte(0 << 1);
        VarInts.writeInt(buf, 1);
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
