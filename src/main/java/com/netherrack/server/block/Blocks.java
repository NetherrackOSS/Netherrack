package com.netherrack.server.block;

/**
 * Registry of the blocks Netherrack currently knows about. This will grow
 * alongside the world/chunk system; for now it just holds identity data.
 */
public final class Blocks {

    public static final GrassBlock GRASS_BLOCK = new GrassBlock();
    public static final CobblestoneBlock COBBLESTONE = new CobblestoneBlock();
    public static final AirBlock AIR = new AirBlock();

    private Blocks() {
    }
}
