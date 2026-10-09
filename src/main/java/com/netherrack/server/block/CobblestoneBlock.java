package com.netherrack.server.block;

/**
 * The cobblestone block ("minecraft:cobblestone" in Bedrock).
 */
public class CobblestoneBlock extends Block {

    public CobblestoneBlock() {
        super("minecraft:cobblestone", 1741778478);
    }

    @Override
    public float getHardness() {
        return 2f;
    }

    @Override
    public boolean requiresTool() {
        return true;
    }
}
