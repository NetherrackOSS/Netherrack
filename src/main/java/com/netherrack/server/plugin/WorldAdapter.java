package com.netherrack.server.plugin;

import com.netherrack.api.world.BlockPosition;
import com.netherrack.api.world.BlockSnapshot;
import com.netherrack.api.world.World;
import com.netherrack.server.Netherrack;
import com.netherrack.server.block.Block;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.network.VanillaData;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;

/**
 * The loaded world seen through the API's small World interface.
 * <p>
 * Blocks cross the boundary as immutable snapshots rather than the server's live Block objects.
 * Setting one resolves its state hash back through the vanilla data, falling back to the block's
 * default state when that state isn't known.
 */
final class WorldAdapter implements World {

    private final Netherrack server;

    WorldAdapter(Netherrack server) {
        this.server = server;
    }

    @Override
    public String getName() {
        return serverWorld().getName();
    }

    @Override
    public long getSeed() {
        return serverWorld().getSeed();
    }

    @Override
    public BlockPosition getSpawn() {
        Vector3i spawn = serverWorld().getSpawn();
        return new BlockPosition(spawn.getX(), spawn.getY(), spawn.getZ());
    }

    @Override
    public BlockSnapshot getBlock(int x, int y, int z) {
        return snapshot(serverWorld().getBlock(x, y, z));
    }

    @Override
    public void setBlock(int x, int y, int z, BlockSnapshot block) {
        serverWorld().setBlock(x, y, z, resolve(block));
    }

    @Override
    public void save() {
        serverWorld().save();
    }

    private com.netherrack.server.world.World serverWorld() {
        com.netherrack.server.world.World world = server.getWorld();
        if (world == null) {
            throw new IllegalStateException("No world is loaded yet");
        }
        return world;
    }

    private static BlockSnapshot snapshot(Block block) {
        return block == null ? null
                : new BlockSnapshot(block.getIdentifier(), block.getBlockStateHash());
    }

    private static Block resolve(BlockSnapshot block) {
        if (block == null) {
            return Blocks.AIR;
        }
        NbtMap state = VanillaData.get().blockState(block.getStateHash());
        Block resolved = state != null && block.getIdentifier().equals(state.getString("name"))
                ? Blocks.of(state)
                : Blocks.byName(block.getIdentifier());
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown block " + block.getIdentifier());
        }
        return resolved;
    }
}
