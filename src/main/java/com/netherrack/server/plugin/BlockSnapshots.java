package com.netherrack.server.plugin;

import com.netherrack.api.world.BlockSnapshot;
import com.netherrack.server.block.Block;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.network.VanillaData;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts between the server's live block states and the API's immutable snapshots.
 * <p>
 * NBT keeps bit properties as bytes and the API exposes them as booleans, so a snapshot built for
 * a plugin can be handed straight back to {@link WorldAdapter#setBlock} without losing the exact
 * vanilla state. The state hash is used as a fallback for snapshots that carry only an identifier
 * and a hash, as the first snapshots did.
 */
final class BlockSnapshots {

    private BlockSnapshots() {
    }

    static BlockSnapshot snapshot(Block block) {
        if (block == null) {
            return null;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> state : block.getStates().entrySet()) {
            Object value = state.getValue();
            if (value instanceof Byte bit) {
                properties.put(state.getKey(), bit != 0);
            } else if (value instanceof Integer || value instanceof String) {
                properties.put(state.getKey(), value);
            }
        }
        return new BlockSnapshot(block.getIdentifier(), block.getBlockStateHash(), properties);
    }

    static Block resolve(BlockSnapshot snapshot) {
        if (snapshot == null) {
            return Blocks.AIR;
        }
        if (!snapshot.getStates().isEmpty()) {
            NbtMapBuilder properties = NbtMap.builder();
            for (Map.Entry<String, Object> state : snapshot.getStates().entrySet()) {
                String name = state.getKey();
                Object value = state.getValue();
                if (value instanceof Boolean bit) {
                    properties.putByte(name, (byte) (bit ? 1 : 0));
                } else if (value instanceof Integer number) {
                    properties.putInt(name, number);
                } else if (value instanceof String text) {
                    properties.putString(name, text);
                }
            }
            Block exact = Blocks.find(snapshot.getIdentifier(), properties.build());
            if (exact != null) {
                return exact;
            }
        }

        NbtMap state = VanillaData.get().blockState(snapshot.getStateHash());
        if (state != null && snapshot.getIdentifier().equals(state.getString("name"))) {
            return Blocks.of(state);
        }
        Block fallback = Blocks.byName(snapshot.getIdentifier());
        if (fallback == null) {
            throw new IllegalArgumentException("Unknown block " + snapshot.getIdentifier());
        }
        return fallback;
    }
}
