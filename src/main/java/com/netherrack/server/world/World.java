package com.netherrack.server.world;

import com.netherrack.server.block.Block;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.storage.WorldStorage;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single world/dimension. Chunks are loaded the first time they're asked for: from the
 * world's storage if it has them, otherwise from the generator. They stay in memory until
 * no player is near them any more ({@link #unloadChunksExcept}), and {@link #save()} writes
 * whatever has changed in them back to the storage. The world's storage also keeps each
 * player's data, see {@link #loadPlayer}.
 */
public class World {

    private final String name;
    private final WorldGenerator generator;
    private final WorldStorage storage;
    private final Path levelDatFile;
    private final int spawnX;
    private final Integer spawnY;
    private final int spawnZ;
    private final int generatorType;
    private final long seed;
    // Concurrent since each player's packets are handled on their own network thread,
    // so two players can ask for (and load) chunks at the same time.
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private volatile int time;
    private volatile boolean raining;
    private volatile boolean thundering;
    private volatile int difficulty;
    private volatile boolean levelDatDirty;

    /**
     * @param storage    where the world's chunks are saved, or null to keep them in memory only
     * @param levelDatFile where the world's level.dat lives, or null when it should not be rewritten
     * @param level      the world's level.dat, for its spawn point, terrain type, seed and state
     */
    public World(String name, WorldGenerator generator, WorldStorage storage, Path levelDatFile, NbtMap level) {
        this.name = name;
        this.generator = generator;
        this.storage = storage;
        this.levelDatFile = levelDatFile;
        this.spawnX = level.getInt("SpawnX", 0);
        this.spawnZ = level.getInt("SpawnZ", 0);
        // Vanilla saves 32767 until it has worked out where the ground is.
        int savedSpawnY = level.getInt("SpawnY", Short.MAX_VALUE);
        this.spawnY = savedSpawnY >= Chunk.MIN_Y && savedSpawnY <= Chunk.MAX_Y ? savedSpawnY : null;
        this.generatorType = level.getInt("Generator", 2);
        this.seed = level.getLong("RandomSeed", 0L);
        this.time = normalizeTime(level.getLong("Time", 0L));
        this.raining = level.getByte("rainLevel", (byte) 0) != 0 || level.getBoolean("raining", false);
        this.thundering = level.getByte("lightningLevel", (byte) 0) != 0 || level.getBoolean("thundering", false);
        this.difficulty = clampDifficulty(level.getInt("Difficulty", 1));
        // Legacy worlds don't carry the time/weather keys Netherrack now persists.
        this.levelDatDirty = !level.containsKey("Time") || !level.containsKey("Difficulty")
                || (!level.containsKey("rainLevel") && !level.containsKey("raining"));
    }

    /** The world's name, as players see it. */
    public String getName() {
        return name;
    }

    public WorldGenerator getGenerator() {
        return generator;
    }

    /** Vanilla's number for the world's kind of terrain: 0 old, 1 infinite, 2 flat. */
    public int getGeneratorType() {
        return generatorType;
    }

    public long getSeed() {
        return seed;
    }

    /** The world time in ticks, in the 0-23999 day cycle range. */
    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        int normalized = normalizeTime(time);
        if (this.time == normalized) {
            return;
        }
        this.time = normalized;
        this.levelDatDirty = true;
    }

    public boolean isRaining() {
        return raining;
    }

    public void setRaining(boolean raining) {
        if (this.raining == raining) {
            return;
        }
        this.raining = raining;
        this.levelDatDirty = true;
    }

    public boolean isThundering() {
        return thundering;
    }

    public void setThundering(boolean thundering) {
        if (this.thundering == thundering) {
            return;
        }
        this.thundering = thundering;
        this.levelDatDirty = true;
    }

    /** 0 peaceful, 1 easy, 2 normal, 3 hard. */
    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        int clamped = clampDifficulty(difficulty);
        if (this.difficulty == clamped) {
            return;
        }
        this.difficulty = clamped;
        this.levelDatDirty = true;
    }

    public int getSpawnX() {
        return spawnX;
    }

    public int getSpawnZ() {
        return spawnZ;
    }

    /**
     * The world spawn: the block new players appear in, standing in the middle of it. Its
     * height is level.dat's if that has one, or else on top of the highest block there -
     * vanilla saves 32767 until it has worked the height out.
     */
    public Vector3i getSpawn() {
        if (spawnY != null) {
            return Vector3i.from(spawnX, spawnY, spawnZ);
        }
        Integer top = getChunk(spawnX >> 4, spawnZ >> 4).getHighestBlockY(spawnX & 15, spawnZ & 15);
        return Vector3i.from(spawnX, top != null ? top + 1 : generator.getSpawnY(), spawnZ);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(chunkKey(chunkX, chunkZ), key -> loadOrGenerate(chunkX, chunkZ));
    }

    public Block getBlock(int x, int y, int z) {
        return getChunk(x >> 4, z >> 4).getBlock(x & 15, y, z & 15);
    }

    public void setBlock(int x, int y, int z, Block block) {
        getChunk(x >> 4, z >> 4).setBlock(x & 15, y, z & 15, block);
    }

    /** The block entity of the block at a position - a bed's color, a banner's patterns - or null if it has none. */
    public NbtMap getBlockEntity(Vector3i position) {
        return getChunk(position.getX() >> 4, position.getZ() >> 4)
                .getBlockEntity(position.getX(), position.getY(), position.getZ());
    }

    /**
     * Gives the block at a position its block entity, which says what it is ("id") and what
     * it keeps; where it is ("x", "y", "z") is added here. Setting the block afterwards takes
     * it away again.
     */
    public void setBlockEntity(Vector3i position, NbtMap entity) {
        NbtMapBuilder placed = entity.toBuilder();
        placed.putInt("x", position.getX());
        placed.putInt("y", position.getY());
        placed.putInt("z", position.getZ());
        getChunk(position.getX() >> 4, position.getZ() >> 4).setBlockEntity(placed.build());
    }

    /** Whether a chunk is in memory: loaded or generated, and not unloaded since. */
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return chunks.containsKey(chunkKey(chunkX, chunkZ));
    }

    /** How many chunks are in memory. */
    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /**
     * Saves and lets go of every chunk in memory but those in {@code keep} (by
     * {@link #chunkKey}) - the ones players are still near. A chunk that can't be saved
     * stays, to be tried again next time. Returns how many were unloaded.
     */
    public synchronized int unloadChunksExcept(Set<Long> keep) {
        int unloaded = 0;
        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            if (keep.contains(entry.getKey())) {
                continue;
            }
            Chunk chunk = entry.getValue();
            // Held while it's saved and dropped, so no change can slip in between the two.
            synchronized (chunk) {
                if (chunk.hasChanges()) {
                    if (storage == null || closed) {
                        continue; // nowhere to keep its changes but memory
                    }
                    try {
                        storage.save(chunk);
                    } catch (IOException e) {
                        Logger.error("Could not save chunk " + chunk.getChunkX() + ", " + chunk.getChunkZ()
                                + " of \"" + name + "\", so it stays loaded: " + e.getMessage());
                        continue;
                    }
                }
                chunks.remove(entry.getKey(), chunk);
            }
            unloaded++;
        }
        return unloaded;
    }

    /** Writes every chunk's changes to the world's storage, and returns how many chunks that was. */
    public synchronized int save() {
        if (storage == null || closed) {
            return 0;
        }
        int saved = 0;
        for (Chunk chunk : chunks.values()) {
            if (!chunk.hasChanges()) {
                continue;
            }
            try {
                storage.save(chunk);
                saved++;
            } catch (IOException e) {
                Logger.error("Could not save chunk " + chunk.getChunkX() + ", " + chunk.getChunkZ()
                        + " of \"" + name + "\": " + e.getMessage());
            }
        }
        return saved;
    }

    /**
     * What was saved of the player with this UUID, or null if they're new here. Data that
     * can't be read counts as new, so they start afresh, and it's replaced when they're saved.
     */
    public NbtMap loadPlayer(UUID uuid) {
        if (storage == null || closed) {
            return null;
        }
        try {
            return storage.loadPlayer(uuid);
        } catch (IOException e) {
            Logger.error("Could not read the saved data of player " + uuid + " in \"" + name
                    + "\", so they start afresh: " + e.getMessage());
            return null;
        }
    }

    /** The UUIDs of players with saved data in this world, used for offline player lookups. */
    public List<UUID> playerUuids() {
        if (storage == null || closed) {
            return List.of();
        }
        try {
            return storage.playerUuids();
        } catch (IOException e) {
            Logger.error("Could not list the saved players in \"" + name + "\": " + e.getMessage());
            return List.of();
        }
    }

    /** Saves a player's data, as made by PlayerData. Does nothing once the world is closed. */
    public synchronized void savePlayer(UUID uuid, NbtMap data) {
        if (storage == null || closed) {
            return;
        }
        try {
            storage.savePlayer(uuid, data);
        } catch (IOException e) {
            Logger.error("Could not save player " + uuid + " in \"" + name + "\": " + e.getMessage());
        }
    }

    /** Saves the world and closes its storage. Anything changed afterwards isn't saved. */
    public synchronized void close() {
        if (closed) {
            return;
        }
        save();
        closed = true;
        if (storage != null) {
            try {
                storage.close();
            } catch (IOException e) {
                Logger.error("Could not close the storage of \"" + name + "\": " + e.getMessage());
            }
        }
    }

    /** Writes the level.dat fields Netherrack owns when they changed. */
    public synchronized void saveLevelDat() {
        if (levelDatFile == null || closed || !levelDatDirty) {
            return;
        }
        try {
            NbtMap level = LevelDat.read(levelDatFile);
            NbtMap updated = level.toBuilder()
                    .putLong("Time", (long) time)
                    .putInt("Difficulty", difficulty)
                    .putFloat("rainLevel", raining ? 1.0f : 0.0f)
                    .putInt("rainTime", raining ? 12000 : 0)
                    .putFloat("lightningLevel", thundering ? 1.0f : 0.0f)
                    .putInt("lightningTime", thundering ? 12000 : 0)
                    .build();
            LevelDat.write(levelDatFile, updated);
            levelDatDirty = false;
        } catch (IOException e) {
            Logger.error("Could not save level.dat for \"" + name + "\": " + e.getMessage());
        }
    }

    private Chunk loadOrGenerate(int chunkX, int chunkZ) {
        boolean saveable = storage != null && !closed;
        if (saveable) {
            try {
                Chunk saved = storage.load(chunkX, chunkZ, generator.getBiome());
                if (saved != null) {
                    return saved;
                }
            } catch (RuntimeException e) {
                Logger.error("Could not read chunk " + chunkX + ", " + chunkZ + " of \"" + name + "\", so it's "
                        + "generated instead, and won't be saved: " + e.getMessage());
                saveable = false;
            }
        }
        Chunk chunk = new Chunk(chunkX, chunkZ, generator.getBiome());
        generator.generate(chunk);
        chunk.clearChanges();
        if (!saveable && storage != null) {
            chunk.markReadOnly();
        }
        return chunk;
    }

    private static int normalizeTime(long time) {
        int normalized = (int) (time % 24000L);
        return normalized < 0 ? normalized + 24000 : normalized;
    }

    private static int clampDifficulty(int difficulty) {
        return Math.max(0, Math.min(3, difficulty));
    }

    /** A chunk's coordinates as one number, as the world keys its chunks by. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
