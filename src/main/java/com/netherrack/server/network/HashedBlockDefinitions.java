package com.netherrack.server.network;

import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

/**
 * Block definitions for the codec under Bedrock's hashed block network IDs (see StartGame's
 * blockNetworkIdsHashed): a block's network id is just the hash of its state, so every id
 * the client sends is accepted and wrapped as-is rather than looked up in a table. The
 * codec needs this to read block ids inside item stacks - e.g. the block item a player
 * is holding when they place something.
 */
public final class HashedBlockDefinitions implements DefinitionRegistry<BlockDefinition> {

    @Override
    public BlockDefinition getDefinition(int hash) {
        return of(hash);
    }

    @Override
    public boolean isRegistered(BlockDefinition definition) {
        return true;
    }

    public static BlockDefinition of(int hash) {
        return new Hashed(hash);
    }

    private record Hashed(int runtimeId) implements BlockDefinition {
        @Override
        public int getRuntimeId() {
            return runtimeId;
        }
    }
}
