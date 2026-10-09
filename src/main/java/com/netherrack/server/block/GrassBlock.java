package com.netherrack.server.block;

/**
 * The grass block. Bedrock's identifier for this is "minecraft:grass_block" - NOT
 * "minecraft:grass", which was the identifier in older Bedrock versions before it got
 * renamed to match Java Edition (sending the old name meant this block was silently
 * falling back to air, since nothing in the current vanilla data matched it).
 */
public class GrassBlock extends Block {

    public GrassBlock() {
        // Hash exceeds Integer.MAX_VALUE as an unsigned value - the network field is a
        // signed 32-bit int, so this is the same bit pattern via a long-to-int cast
        // rather than a literal (3727763636 alone won't compile, it overflows int).
        super("minecraft:grass_block", (int) 3727763636L);
    }

    @Override
    public float getHardness() {
        return 0.6f;
    }
}
