package com.netherrack.server.block;

/**
 * Registry of the blocks Netherrack currently knows about. This will grow
 * alongside the world/chunk system; for now it just holds identity data.
 */
public final class Blocks {

    public static final GrassBlock GRASS_BLOCK = new GrassBlock();
    public static final CobblestoneBlock COBBLESTONE = new CobblestoneBlock();

    /**
     * Not a placeable block, just the fallback used for empty chunk positions when
     * encoding - every other position in a chunk that isn't explicitly set by a generator.
     */
    public static final Block AIR = new Block("minecraft:air", (int) 3690217760L);

    private Blocks() {
    }
}
