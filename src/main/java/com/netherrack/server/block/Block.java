package com.netherrack.server.block;

/**
 * A minimal representation of a block type. There's no world/chunk system yet,
 * so this just models the identity of a block (its Bedrock namespaced id).
 */
public class Block {

    private final String identifier;

    public Block(String identifier) {
        this.identifier = identifier;
    }

    /**
     * The Bedrock namespaced identifier, e.g. "minecraft:grass".
     */
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
