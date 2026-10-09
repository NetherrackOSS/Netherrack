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

    /** Vanilla's hardness value, which sets how long the block takes to break. */
    public float getHardness() {
        return 0;
    }

    /** Whether breaking it by hand is the slow, no-drop kind (e.g. stone without a pickaxe). */
    public boolean requiresTool() {
        return false;
    }

    /**
     * How many ticks breaking this by hand takes, by vanilla's formula: hardness times 1.5
     * seconds, or times 5 when the block needs a tool. Only used to animate other
     * players' view of the cracks - the breaking player's own client times the break.
     */
    public int getHandBreakTicks() {
        float seconds = getHardness() * (requiresTool() ? 5f : 1.5f);
        return Math.max(1, Math.round(seconds * 20));
    }

    @Override
    public String toString() {
        return identifier;
    }
}
