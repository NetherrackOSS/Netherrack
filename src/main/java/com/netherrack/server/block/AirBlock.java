package com.netherrack.server.block;

/**
 * Air - not a placeable block, just the fallback used for empty chunk positions when
 * encoding (every position in a chunk that isn't explicitly set by a generator).
 */
public class AirBlock extends Block {

    public AirBlock() {
        // Hash exceeds Integer.MAX_VALUE as an unsigned value - this is the same bit
        // pattern via a long-to-int cast rather than a literal (3690217760 alone won't
        // compile, it overflows int).
        super("minecraft:air", (int) 3690217760L);
    }
}
