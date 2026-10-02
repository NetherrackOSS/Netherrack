package com.netherrack.server.world;

import com.netherrack.server.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * A single world/dimension. Chunks are generated on demand (the first time they're
 * asked for) via the configured generator and kept in memory. There's no chunk/block
 * persistence yet - edits aren't saved to disk, only the fact that a level with a
 * given generator exists (see WorldManager).
 */
public class World {

    private final String name;
    private final WorldGenerator generator;
    private final Map<Long, Chunk> chunks = new HashMap<>();

    public World(String name, WorldGenerator generator) {
        this.name = name;
        this.generator = generator;
    }

    public String getName() {
        return name;
    }

    public WorldGenerator getGenerator() {
        return generator;
    }

    public int getSpawnY() {
        return generator.getSpawnY();
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(chunkKey(chunkX, chunkZ), key -> {
            Chunk chunk = new Chunk(chunkX, chunkZ);
            generator.generate(chunk);
            return chunk;
        });
    }

    public Block getBlock(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).getBlock(x & 15, y, z & 15);
    }

    public void setBlock(int x, int y, int z, Block block) {
        getChunk(x >> 4, z >> 4).setBlock(x & 15, y, z & 15, block);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
