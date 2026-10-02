package com.netherrack.server.world;

/**
 * Fills a newly-created chunk with blocks. One instance is shared across a whole world.
 */
public interface WorldGenerator {

    /**
     * Short id saved alongside a level so it can be regenerated with the same generator later.
     */
    String getId();

    void generate(Chunk chunk);

    /**
     * Y level new players should spawn on top of. Defaults to 64, the classic "sea level".
     */
    default int getSpawnY() {
        return 64;
    }
}
