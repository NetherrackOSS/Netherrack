package com.netherrack.server.world;

import com.netherrack.server.block.Blocks;

/**
 * Generates a superflat world that's just a single layer of grass blocks and nothing
 * else (no dirt/stone/bedrock underneath). Netherrack only has grass and cobblestone
 * implemented so far, and cobblestone doesn't naturally generate, so a one-block-thick
 * grass layer is the only terrain that makes sense right now.
 */
public class SuperflatGenerator implements WorldGenerator {

    /**
     * The Y level the single grass layer sits on.
     */
    public static final int LAYER_Y = 4;

    @Override
    public String getId() {
        return "superflat";
    }

    @Override
    public int getSpawnY() {
        return LAYER_Y + 1;
    }

    @Override
    public void generate(Chunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlock(x, LAYER_Y, z, Blocks.GRASS_BLOCK);
            }
        }
    }
}
