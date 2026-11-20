package com.netherrack.server.plugin;

import com.netherrack.api.world.BlockPosition;
import com.netherrack.api.world.BlockSnapshot;
import com.netherrack.api.world.World;
import com.netherrack.server.Netherrack;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * The loaded world seen through the API's small World interface.
 * <p>
 * Blocks cross the boundary as immutable snapshots rather than the server's live Block objects.
 * Snapshots carry the block's exact vanilla properties, so setting one restores the state a plugin
 * read earlier instead of just its default variant. Changes are broadcast to clients that have the
 * chunk, using the same path as ordinary placement and breaking.
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
        return BlockSnapshots.snapshot(serverWorld().getBlock(x, y, z));
    }

    @Override
    public void setBlock(int x, int y, int z, BlockSnapshot block) {
        com.netherrack.server.block.Block resolved = BlockSnapshots.resolve(block);
        serverWorld().setBlock(x, y, z, resolved);
        // The same broadcast placement and breaking use, so clients with this chunk see the
        // change immediately instead of keeping their predicted/old state until they reload.
        server.getPlayerManager().broadcastBlock(Vector3i.from(x, y, z), resolved);
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
}
