package com.netherrack.server.block;

/**
 * The grass block. Bedrock's identifier for this is "minecraft:grass_block" - NOT
 * "minecraft:grass", which was the identifier in older Bedrock versions before it got
 * renamed to match Java Edition (sending the old name meant this block was silently
 * falling back to air, since nothing in the current vanilla data matched it).
 */
public class GrassBlock extends Block {

    public GrassBlock() {
        super(GRASS_BLOCK);
    }
}
