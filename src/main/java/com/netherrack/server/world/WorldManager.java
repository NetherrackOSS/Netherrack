package com.netherrack.server.world;

import com.netherrack.server.setup.Lang;
import com.netherrack.server.util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds the level directory for a world on disk, or generates a brand new one if it
 * doesn't exist yet. There's no chunk data persistence yet (nothing about block edits
 * is saved between restarts) - all this currently tracks is which generator a level
 * was created with, via a small marker file, so a restart doesn't silently switch
 * generators on an existing level.
 */
public class WorldManager {

    private static final String MARKER_FILE = "netherrack.generator";

    public World loadOrCreate(String levelName) {
        Path levelDir = Path.of(levelName);
        Path marker = levelDir.resolve(MARKER_FILE);

        if (Files.isDirectory(levelDir) && Files.exists(marker)) {
            String generatorId = readGeneratorId(marker);
            Logger.info(Lang.current.get("server.loading_level", levelName, generatorId));
            return new World(levelName, resolveGenerator(generatorId));
        }

        Logger.warn(Lang.current.get("server.level_not_found", levelName));
        WorldGenerator generator = new SuperflatGenerator();
        World world = new World(levelName, generator);
        createLevelDirectory(levelDir, marker, generator.getId(), world);
        return world;
    }

    private void createLevelDirectory(Path levelDir, Path marker, String generatorId, World world) {
        try {
            Files.createDirectories(levelDir);
            Files.writeString(marker, generatorId);
            LevelDatWriter.write(levelDir, world);
        } catch (IOException e) {
            Logger.error("Failed to create level directory \"" + levelDir + "\": " + e.getMessage());
        }
    }

    private String readGeneratorId(Path marker) {
        try {
            return Files.readString(marker).trim();
        } catch (IOException e) {
            Logger.warn("Failed to read the generator marker for this level, defaulting to superflat: " + e.getMessage());
            return "superflat";
        }
    }

    private WorldGenerator resolveGenerator(String id) {
        if ("superflat".equals(id)) {
            return new SuperflatGenerator();
        }
        Logger.warn("Unknown generator \"" + id + "\", defaulting to superflat.");
        return new SuperflatGenerator();
    }
}
