package com.netherrack.server.block;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of the blocks Netherrack currently knows about. This will grow
 * alongside the world/chunk system; for now it just holds identity data.
 */
public final class Blocks {

    public static final GrassBlock GRASS_BLOCK = new GrassBlock();
    public static final CobblestoneBlock COBBLESTONE = new CobblestoneBlock();
    public static final AirBlock AIR = new AirBlock();

    private static final List<Block> ALL = List.of(GRASS_BLOCK, COBBLESTONE, AIR);
    private static final Map<String, Block> BY_IDENTIFIER =
            ALL.stream().collect(Collectors.toMap(Block::getIdentifier, Function.identity()));

    private Blocks() {
    }

    /** The block with this identifier (e.g. "minecraft:cobblestone"), or null if Netherrack doesn't have it. */
    public static Block byIdentifier(String identifier) {
        return BY_IDENTIFIER.get(identifier);
    }
}
