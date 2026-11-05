package com.netherrack.server.world;

import com.netherrack.server.NetherrackConfig;
import com.netherrack.server.ServerConfig;
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
     * The flat world layers a new level gets, or null for generated terrain. level-type in
     * server.properties picks between the two ("default" when it isn't set, as on Bedrock
     * Dedicated Server), and level.preset-type in netherrack.yml picks the layers of a flat one.
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
     * A new level's spawn point with its height worked out: standing on the highest block at
     * the spawn's x and z, so new players appear on the ground. With nothing there at all
     * (terrain Netherrack can't generate), it's left as "find the ground" for the game to
     * work out if the world is opened there.
     */
    private static NbtMap withSpawnHeight(NbtMap level, WorldGenerator generator) {
        int x = level.getInt("SpawnX", 0);
        int z = level.getInt("SpawnZ", 0);
        Chunk chunk = new Chunk(x >> 4, z >> 4, generator.getBiome());
        generator.generate(chunk);
        Integer top = chunk.getHighestBlockY(x & 15, z & 15);
        return top == null ? level : level.toBuilder().putInt("SpawnY", top + 1).build();
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
     * The terrain to generate where the world has nothing saved: flat worlds' own layers.
     * Netherrack can't generate other kinds of terrain yet, so those get nothing.
     */
    private static WorldGenerator generatorFor(NbtMap level) {
        int type = level.getInt("Generator", 2);
        if (type != 2) {
            return new EmptyGenerator(switch (type) {
                case 0 -> "old";
                case 1 -> "infinite";
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
