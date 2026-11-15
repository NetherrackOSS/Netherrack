package com.netherrack.server.world;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.Blocks;

/**
 * Generates the kind of terrain the game's "infinite" worlds have: rolling hills, valleys,
 * lakes and the sea, dirt and grass over stone, sandy shores, a jagged bedrock floor and
 * scattered oak trees.
 * <p>
 * The height of the land comes from three layered Perlin noise fields (see PerlinNoise,
 * whose octave structure follows CloudburstMC Nukkit's terrain generator): a broad one
 * that lifts continents or sinks them into the sea, a middle one for hills and valleys,
 * and a fine one for little bumps and dips. Because every height is worked out from the
 * world coordinates alone, chunks fit the ones next to them however they're ordered.
 */
public class TerrainGenerator implements WorldGenerator {

    /** Where the water stands: columns lower than this hold a lake or the sea. */
    private static final int SEA_LEVEL = 62;

    private final Block bedrock;
    private final Block stone;
    private final Block dirt;
    private final Block grass;
    private final Block sand;
    private final Block gravel;
    private final Block water;
    private final Block oakLog;
    private final Block oakLeaves;

    /** Broad continents and seas, a few hundred blocks across. */
    private final PerlinNoise continent;
    /** Hills and valleys, tens of blocks across, bigger on high ground. */
    private final PerlinNoise hills;
    /** Little bumps and dips, a few blocks across. */
    private final PerlinNoise detail;

    private final long seed;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.bedrock = Blocks.byName("minecraft:bedrock");
        this.stone = Blocks.byName("minecraft:stone");
        this.dirt = Blocks.byName("minecraft:dirt");
        this.grass = Blocks.byName("minecraft:grass_block");
        this.sand = Blocks.byName("minecraft:sand");
        this.gravel = Blocks.byName("minecraft:gravel");
        this.water = Blocks.byName("minecraft:water");
        this.oakLog = Blocks.byName("minecraft:oak_log");
        this.oakLeaves = Blocks.byName("minecraft:oak_leaves");
        this.continent = new PerlinNoise(seed, 2, 1 / 260.0, 0.5);
        this.hills = new PerlinNoise(seed + 0x9E3779B9L, 3, 1 / 60.0, 0.5);
        this.detail = new PerlinNoise(seed + 0x515F2A31L, 2, 1 / 40.0, 0.5);
    }

    /** "terrain", the kind of ground it lays down. */
    @Override
    public String getId() {
        return "terrain";
    }

    @Override
    public void generate(Chunk chunk) {
        int baseX = chunk.getChunkX() << 4;
        int baseZ = chunk.getChunkZ() << 4;

        // The column of ground in every square, from the bedrock floor up to the surface,
        // with water standing where the land dips below the sea.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surface = surfaceY(baseX + x, baseZ + z);
                for (int y = Chunk.MIN_Y; y <= surface; y++) {
                    if (y < Chunk.MIN_Y + 5) {
                        chunk.setBlock(x, y, z, bedrockOf(baseX + x, y, baseZ + z, y == Chunk.MIN_Y));
                    } else if (y == surface) {
                        chunk.setBlock(x, y, z, topOf(baseX + x, surface, baseZ + z));
                    } else if (y >= surface - 3) {
                        chunk.setBlock(x, y, z, surface <= SEA_LEVEL + 1 ? sand : dirt);
                    } else {
                        chunk.setBlock(x, y, z, stone);
                    }
                }
                for (int y = surface + 1; y <= SEA_LEVEL; y++) {
                    chunk.setBlock(x, y, z, water);
                }
            }
        }

        // Trees grow on the grass, away from the chunk's edge so their leaves always land
        // in the chunk they were planted in.
        for (int x = 2; x < 14; x++) {
            for (int z = 2; z < 14; z++) {
                int surface = surfaceY(baseX + x, baseZ + z);
                if (surface > SEA_LEVEL && chunk.getBlock(x, surface, z) == grass
                        && Math.floorMod(hash(baseX + x, surface, baseZ + z), 1000) < 15) {
                    growOak(chunk, x, surface + 1, z, baseX + x, baseZ + z);
                }
            }
        }
    }

    /** The height the ground stands at in one column of the world. */
    private int surfaceY(int x, int z) {
        double broad = continent.sample(x, z);
        double relief = hills.sample(x, z);
        double fine = detail.sample(x, z);
        // High ground rolls much more than low ground, but hills rise everywhere. The
        // samples only ever span a little more than half their range, so the multipliers
        // are sized for what they actually deliver - these were tuned over a spread of
        // regions to keep a few lakes about, land from the shore up to proper hills.
        double height = SEA_LEVEL + 7 + broad * 22
                + (relief * 36 + fine * 10) * (0.5 + 0.5 * Math.max(0, broad));
        return Math.max(Chunk.MIN_Y + 8, Math.min(120, (int) Math.round(height)));
    }

    /** The bottom of the world: always bedrock, with a thinning jagged layer above it. */
    private Block bedrockOf(int x, int y, int z, boolean bottom) {
        int layer = y - Chunk.MIN_Y;
        return bottom || layer == 1 || Math.floorMod(hash(x, y, z), layer) == 0 ? bedrock : stone;
    }

    /**
     * What the ground wears on top: grass on dry land, sand on the shore and sea floor,
     * the sand patchy with gravel out at the bottom of deep water.
     */
    private Block topOf(int x, int surface, int z) {
        if (surface > SEA_LEVEL + 1) {
            return grass;
        }
        if (surface < SEA_LEVEL - 3 && Math.floorMod(hash(x, surface, z), 5) == 0) {
            return gravel;
        }
        return sand;
    }

    /** An oak tree at this position in the chunk: a trunk of logs under a leafy canopy. */
    private void growOak(Chunk chunk, int localX, int baseY, int localZ, int worldX, int worldZ) {
        int height = 4 + (hash(worldX, baseY, worldZ) & 1);
        int top = baseY + height;
        // The canopy first, so the trunk can be pushed through it afterwards.
        for (int y = top - 2; y <= top + 1; y++) {
            int reach = y > top - 1 ? 1 : 2;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (y == top + 1 && Math.abs(dx) + Math.abs(dz) > 1) {
                        continue; // the crown is a plus shape, not a block
                    }
                    if (reach == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2
                            && Math.floorMod(hash(worldX + dx, y, worldZ + dz), 2) == 0) {
                        continue; // the wide layers lose a corner here and there
                    }
                    if (chunk.getBlock(localX + dx, y, localZ + dz) == null) {
                        chunk.setBlock(localX + dx, y, localZ + dz, oakLeaves);
                    }
                }
            }
        }
        for (int y = baseY; y < top; y++) {
            chunk.setBlock(localX, y, localZ, oakLog);
        }
        // Nothing grows through the trunk's base.
        chunk.setBlock(localX, baseY - 1, localZ, dirt);
    }

    /** A random number for this exact position, steady however often it's asked. */
    private int hash(int x, int y, int z) {
        long mixed = seed ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 2533594825263L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) mixed;
    }
}
