package com.netherrack.server.world;

import com.netherrack.server.NetherrackConfig;
import com.netherrack.server.ServerConfig;
import com.netherrack.server.block.Block;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.storage.WorldStorage;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Opens a world from disk, or creates a brand new one if it doesn't exist yet. Worlds are
 * kept the way Bedrock Dedicated Server keeps them, in worlds/&lt;level-name&gt;/:
 * <ul>
 *   <li>level.dat: the world's settings (see LevelDat);</li>
 *   <li>levelname.txt: its name as players see it;</li>
 *   <li>db/: its chunks, in a LevelDB database (see WorldStorage).</li>
 * </ul>
 * So a world folder from Bedrock Dedicated Server, or one exported from the game, can be
 * dropped in there, and a world Netherrack made opens in the game.
 */
public class WorldManager {

    /** Where worlds are kept, as Bedrock Dedicated Server keeps them. */
    public static final Path WORLDS_DIRECTORY = Path.of("worlds");

    private static final String LEVEL_DAT = "level.dat";
    private static final String LEVEL_NAME_FILE = "levelname.txt";
    private static final String DATABASE_DIRECTORY = "db";
    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private final ServerConfig config;
    private final NetherrackConfig settings;

    /**
     * @param config   where a new level's type comes from
     * @param settings where a new flat level's preset comes from
     */
    public WorldManager(ServerConfig config, NetherrackConfig settings) {
        this.config = config;
        this.settings = settings;
    }

    /** @throws IOException if the world can't be read, or a new one can't be written */
    public World loadOrCreate(String levelName) throws IOException {
        Path directory = WORLDS_DIRECTORY.resolve(levelName);
        Path levelDat = directory.resolve(LEVEL_DAT);

        NbtMap level;
        WorldGenerator generator;
        if (Files.exists(levelDat)) {
            level = LevelDat.read(levelDat);
            generator = generatorFor(level);
        } else {
            Logger.warn(Lang.current.get("server.level_not_found", levelName));
            level = newLevel(levelName, newLevelLayers());
            generator = generatorFor(level);
            level = withSpawnHeight(level, generator);
            Files.createDirectories(directory);
            LevelDat.write(levelDat, level);
            Files.writeString(directory.resolve(LEVEL_NAME_FILE), levelName, StandardCharsets.UTF_8);
        }

        Logger.info(Lang.current.get("server.loading_level", levelName, generator.getId()));
        if (generator instanceof EmptyGenerator) {
            Logger.warn(Lang.current.get("server.generator_unsupported", generator.getId()));
        }

        WorldStorage storage = WorldStorage.open(directory.resolve(DATABASE_DIRECTORY));
        return new World(displayName(directory, level, levelName), generator, storage, level);
    }

    /**
     * The flat world layers a new level gets, or null for generated terrain: level-type in
     * server.properties picks between the two ("default", the terrain generator, when it
     * isn't set, as on Bedrock Dedicated Server), and level.preset-type in netherrack.yml
     * picks the layers of a flat one.
     */
    private FlatPreset newLevelLayers() {
        String type = config.get("level-type", "default").trim();
        if (type.equalsIgnoreCase("default")) {
            return null;
        }
        if (!type.equalsIgnoreCase("flat")) {
            Logger.warn("Unknown level-type \"" + type + "\" in server.properties, so the new level is flat."
                    + " Expected flat or default.");
        }
        String presetName = settings.getString("level.preset-type");
        FlatPreset preset = FlatPreset.fromName(presetName);
        if (preset == null) {
            Logger.warn("Unknown level.preset-type \"" + presetName + "\" in netherrack.yml, so the new level is "
                    + FlatPreset.CLASSIC_FLAT.getDisplayName() + ".");
            return FlatPreset.CLASSIC_FLAT;
        }
        return preset;
    }

    /**
     * The level.dat of a new world, flat with these layers or else infinite. Vanilla fills
     * in anything it needs that isn't here (game rules and the like) when it opens the world.
     */
    private static NbtMap newLevel(String levelName, FlatPreset flat) {
        List<Integer> version = gameVersion();
        return NbtMap.builder()
                .putString("LevelName", levelName)
                .putInt("StorageVersion", LevelDat.STORAGE_VERSION)
                .putInt("NetworkVersion", Bedrock_v2225.CODEC.getProtocolVersion())
                .putList("lastOpenedWithVersion", NbtType.INT, version)
                .putList("MinimumCompatibleClientVersion", NbtType.INT, version)
                .putString("InventoryVersion", Bedrock_v2225.CODEC.getMinecraftVersion())
                .putInt("WorldVersion", 1) // made since 1.18: y -64 to 319
                .putInt("GameType", 0) // survival
                .putInt("Generator", flat != null ? 2 : 1) // flat or infinite
                // Infinite worlds the game makes have flat layers saved too: Classic Flat's.
                .putString("FlatWorldLayers", (flat != null ? flat : FlatPreset.CLASSIC_FLAT).toJson())
                .putInt("SpawnX", 0)
                .putInt("SpawnY", Short.MAX_VALUE) // "find the ground", until withSpawnHeight has
                .putInt("SpawnZ", 0)
                .putLong("RandomSeed", new Random().nextLong()) // as the game picks one, flat or not
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

    /**
     * A new level's spawn point with its height worked out: standing on the highest block
     * of a column that doesn't end in water. A terrain world's origin can sit in the
     * middle of the sea, so when the spawn's own column is underwater, the search walks
     * out in ever wider rings to the nearest one that isn't. With nothing at all generated
     * (terrain Netherrack can't fill), the spawn stays where it is with no height, for
     * the game to work out if the world is opened there.
     */
    private static NbtMap withSpawnHeight(NbtMap level, WorldGenerator generator) {
        int x = level.getInt("SpawnX", 0);
        int z = level.getInt("SpawnZ", 0);
        int[] dry = drySpawn(generator, x, z);
        if (dry == null) {
            return level;
        }
        return level.toBuilder()
                .putInt("SpawnX", dry[0])
                .putInt("SpawnY", dry[2] + 1)
                .putInt("SpawnZ", dry[1])
                .build();
    }

    /**
     * The nearest column to the start that ends on dry ground, as {x, z, top y} - the
     * start itself when it qualifies, else the closest in ever wider rings, or null when
     * the whole search stays underwater or nothing at all was generated to stand on.
     */
    private static int[] drySpawn(WorldGenerator generator, int x, int z) {
        Chunk start = new Chunk(x >> 4, z >> 4, generator.getBiome());
        generator.generate(start);
        Integer top = start.getHighestBlockY(x & 15, z & 15);
        if (top == null) {
            return null; // nothing generated here, so nothing will be nearby either
        }
        if (!isWater(start, x & 15, z & 15, top)) {
            return new int[]{x, z, top};
        }
        for (int ring = 16; ring <= 512; ring += 16) {
            for (int dx = -ring; dx <= ring; dx += 8) {
                for (int dz = -ring; dz <= ring; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue; // the ring's edge - its inside was searched before
                    }
                    Chunk chunk = new Chunk((x + dx) >> 4, (z + dz) >> 4, generator.getBiome());
                    generator.generate(chunk);
                    Integer found = chunk.getHighestBlockY((x + dx) & 15, (z + dz) & 15);
                    if (found != null && !isWater(chunk, (x + dx) & 15, (z + dz) & 15, found)) {
                        return new int[]{x + dx, z + dz, found};
                    }
                }
            }
        }
        return null;
    }

    /** Whether the highest block of a column is water, i.e. the column is underwater. */
    private static boolean isWater(Chunk chunk, int localX, int localZ, int top) {
        Block block = chunk.getBlock(localX, top, localZ);
        return block != null && block.getIdentifier().equals("minecraft:water");
    }

    /** The game version as level.dat lists it: e.g. 1.26.60 is [1, 26, 60, 0, 0]. */
    private static List<Integer> gameVersion() {
        List<Integer> version = new ArrayList<>();
        for (String part : Bedrock_v2225.CODEC.getMinecraftVersion().split("\\.")) {
            version.add(Integer.parseInt(part));
        }
        while (version.size() < 5) {
            version.add(0);
        }
        return version;
    }

    /**
     * The terrain to generate where the world has nothing saved: flat worlds' own layers,
     * or - for the "infinite" worlds the game and vanilla servers make - rolling terrain
     * from the world's seed (see TerrainGenerator). Other kinds of world get nothing.
     */
    private static WorldGenerator generatorFor(NbtMap level) {
        int type = level.getInt("Generator", 2);
        if (type == 1) {
            return new TerrainGenerator(level.getLong("RandomSeed", 0));
        }
        if (type != 2) {
            return new EmptyGenerator(switch (type) {
                case 0 -> "old";
                default -> "type " + type;
            });
        }
        String layers = level.getString("FlatWorldLayers", "");
        if (layers.isBlank()) {
            // Vanilla's own flat world, for a flat world that doesn't say which layers it has.
            layers = FlatPreset.CLASSIC_FLAT.toJson();
        }
        try {
            return new FlatGenerator(layers, level.getInt("SpawnX", 0), level.getInt("SpawnZ", 0));
        } catch (IllegalArgumentException e) {
            Logger.warn("This flat world's layers can't be read (" + e.getMessage() + "), so nothing is generated.");
            return new EmptyGenerator("flat");
        }
    }

    /** The world's name as players see it: levelname.txt's, else level.dat's, else the folder's. */
    private static String displayName(Path directory, NbtMap level, String levelName) {
        try {
            String text = Files.readString(directory.resolve(LEVEL_NAME_FILE), StandardCharsets.UTF_8);
            // Vanilla writes the name alone; a byte order mark or a newline after it is tolerated.
            String name = text.replace(String.valueOf(BYTE_ORDER_MARK), "").lines().findFirst().orElse("").trim();
            if (!name.isEmpty()) {
                return name;
            }
        } catch (IOException e) {
            // No levelname.txt: fall back to level.dat's name.
        }
        String name = level.getString("LevelName", "").trim();
        return name.isEmpty() ? levelName : name;
    }
}
