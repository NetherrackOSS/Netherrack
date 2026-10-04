package com.netherrack.server.world;

import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a level.dat file: the small NBT file every Bedrock world folder has, describing
 * the world itself (name, generator, spawn, etc.) - separate from the actual per-chunk
 * block data, which Netherrack doesn't persist to disk yet (worlds are regenerated fresh
 * from the generator every time a chunk is first needed).
 * <p>
 * Format: an 8-byte header (storage version, then payload length, both int32 little-endian),
 * followed by the level data itself as a single uncompressed little-endian NBT compound.
 * The client never reads this directly in multiplayer - it's server-side bookkeeping, and
 * what makes the folder look/behave like a real Bedrock world save if someone opens it.
 */
public final class LevelDatWriter {

    private static final int STORAGE_VERSION = 9;

    private LevelDatWriter() {
    }

    public static void write(Path levelDir, World world) throws IOException {
        NbtMap levelData = buildLevelData(world);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (NBTOutputStream nbtOut = NbtUtils.createWriterLE(payload)) {
            nbtOut.writeTag(levelData);
        }

        byte[] payloadBytes = payload.toByteArray();
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(STORAGE_VERSION);
        header.putInt(payloadBytes.length);

        Path levelDat = levelDir.resolve("level.dat");
        try (OutputStream out = Files.newOutputStream(levelDat)) {
            out.write(header.array());
            out.write(payloadBytes);
        }
    }

    private static NbtMap buildLevelData(World world) {
        int spawnY = world.getSpawnY();

        // The standard Bedrock superflat layer description: one layer of grass and
        // nothing below it, matching what SuperflatGenerator actually generates.
        String flatWorldLayers = "{\"biome_id\":1,\"block_layers\":["
                + "{\"block_name\":\"minecraft:grass\",\"count\":1}"
                + "],\"encoding_version\":6,\"structure_options\":null}";

        return NbtMap.builder()
                .putString("LevelName", world.getName())
                .putInt("StorageVersion", STORAGE_VERSION)
                .putInt("NetworkVersion", 2193)
                .putInt("GameType", 0) // survival
                .putInt("Generator", 2) // flat
                .putString("FlatWorldLayers", flatWorldLayers)
                .putInt("SpawnX", 0)
                .putInt("SpawnY", spawnY)
                .putInt("SpawnZ", 0)
                .putLong("RandomSeed", 0L)
                .putLong("Time", 0L)
                .putLong("currentTick", 0L)
                .putLong("LastPlayed", System.currentTimeMillis() / 1000L)
                .putInt("Difficulty", 1) // easy
                .putByte("commandsEnabled", (byte) 1)
                .putByte("hasBeenLoadedInCreative", (byte) 0)
                .putByte("immutableWorld", (byte) 0)
                .putByte("isCreatedInEditor", (byte) 0)
                .build();
    }
}
