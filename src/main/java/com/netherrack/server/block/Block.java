package com.netherrack.server.block;

/**
 * A minimal representation of a block type. There's no world/chunk system yet,
 * so this just models the identity of a block (its Bedrock namespaced id).
 */
public class Block {

    private final String identifier;
    private final int blockStateHash;

    /**
     * @param blockStateHash the block's network state hash, as Bedrock's "hashed block
     *                       network IDs" scheme uses - a value computed from the block's
     *                       NBT state (name + properties), the same way on both client and
     *                       server, so they agree on identity without needing to share an
     *                       identically-ordered palette. These are extracted/known values,
     *                       not something computed here - see Blocks.java for sourcing.
     */
    public Block(String identifier, int blockStateHash) {
        this.identifier = identifier;
        this.blockStateHash = blockStateHash;
    }

    /**
     * The Bedrock namespaced identifier, e.g. "minecraft:grass_block".
     */
    public String getIdentifier() {
        return identifier;
    }

    public int getBlockStateHash() {
        return blockStateHash;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
