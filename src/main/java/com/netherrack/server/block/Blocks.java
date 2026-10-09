package com.netherrack.server.block;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of the blocks Netherrack currently knows about. This will grow
 * alongside the world/chunk system; for now it just holds identity data.
 * <p>
 * Only a few blocks have their own class. Any other block state - one placed from the
 * creative inventory, say - is a plain {@link Block} made on first use by {@link #of}.
 */
public final class Blocks {

    public static final GrassBlock GRASS_BLOCK = new GrassBlock();
    public static final CobblestoneBlock COBBLESTONE = new CobblestoneBlock();
    public static final AirBlock AIR = new AirBlock();

    private static final List<Block> ALL = List.of(GRASS_BLOCK, COBBLESTONE, AIR);
    private static final Map<String, Block> BY_IDENTIFIER =
            ALL.stream().collect(Collectors.toMap(Block::getIdentifier, Function.identity()));
    private static final Map<Integer, Block> BY_HASH = new ConcurrentHashMap<>(
            ALL.stream().collect(Collectors.toMap(Block::getBlockStateHash, Function.identity())));

    private Blocks() {
    }

    /** The block with this identifier (e.g. "minecraft:cobblestone"), or null if Netherrack doesn't have it. */
    public static Block byIdentifier(String identifier) {
        return BY_IDENTIFIER.get(identifier);
    }

    /**
     * The block for a block state, by its identifier and network hash (see BlockStateHash):
     * Netherrack's own class if it has one for that state, otherwise a plain block.
     */
    public static Block of(String identifier, int hash) {
        return BY_HASH.computeIfAbsent(hash, h -> new Block(identifier, h));
    }
}
