package com.netherrack.server.world;

import com.netherrack.server.block.Block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A 16x16 column of blocks, identified by chunk coordinates (block coordinates divided by 16).
 * Storage is a sparse map keyed by local position rather than a dense 3D array - worlds are
 * simple right now (a superflat single layer), so most of a chunk is empty air. This can be
 * swapped for proper per-subchunk block-palette storage once real terrain generation exists.
 */
public class Chunk {

    private final int chunkX;
    private final int chunkZ;
    // Concurrent since players break and place blocks from their own network threads,
    // while other threads may be encoding the chunk to send.
    private final Map<Integer, Block> blocks = new ConcurrentHashMap<>();

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * @param localX 0-15, x within this chunk
     * @param y      world y
     * @param localZ 0-15, z within this chunk
     */
    public Block getBlock(int localX, int y, int localZ) {
        return blocks.get(index(localX, y, localZ));
    }

    public void setBlock(int localX, int y, int localZ, Block block) {
        int index = index(localX, y, localZ);
        if (block == null) {
            blocks.remove(index);
        } else {
            blocks.put(index, block);
        }
    }

    private static int index(int localX, int y, int localZ) {
        return (localX & 15) | ((localZ & 15) << 4) | (y << 8);
    }
}
