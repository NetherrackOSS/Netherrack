package com.netherrack.server.block;

import java.util.List;
import java.util.Set;

/**
 * The shapes of blocks, as far as building cares: which a placed block takes the place of,
 * which are whole cubes, and which faces are whole and flat, for torches, buttons, ladders
 * and fences to hold on to. The block data has no shapes, so they go by name: slabs,
 * stairs, fences and the like come in families named alike.
 */
public final class BlockShapes {

    /**
     * Blocks a placed block takes the place of, rather than going beside. Taking the place of
     * one half of tall grass or a large fern takes the other half away too.
     */
    private static final Set<String> REPLACEABLE = Set.of(
            BlockID.AIR,
            BlockID.WATER,
            BlockID.FLOWING_WATER,
            BlockID.LAVA,
            BlockID.FLOWING_LAVA,
            BlockID.FIRE,
            BlockID.SOUL_FIRE,
            BlockID.SHORT_GRASS,
            BlockID.FERN,
            BlockID.TALL_GRASS,
            BlockID.LARGE_FERN,
            BlockID.DEADBUSH,
            BlockID.BUSH,
            BlockID.SHORT_DRY_GRASS,
            BlockID.TALL_DRY_GRASS,
            BlockID.SEAGRASS,
            BlockID.CRIMSON_ROOTS,
            BlockID.WARPED_ROOTS,
            BlockID.NETHER_SPROUTS,
            BlockID.HANGING_ROOTS,
            BlockID.GLOW_LICHEN,
            BlockID.SCULK_VEIN,
            BlockID.VINE,
            BlockID.LEAF_LITTER,
            BlockID.PALE_HANGING_MOSS,
            BlockID.SNOW_LAYER,
            BlockID.STRUCTURE_VOID
    );

    /** A whole block's space. */
    private static final Box WHOLE = new Box(0, 0, 0, 1, 1, 1);

    /** How thick, in sixteenths of a block, a ladder or trapdoor is. */
    private static final int PLATE = 3;

    /** How the names of blocks that aren't whole cubes end. */
    private static final List<String> PARTIAL_ENDINGS = List.of(
            "_slab", "_stairs", "_wall", "fence", "fence_gate", "_pane", "_bars", "door", "button", "pressure_plate",
            "carpet", "rail", "torch", "lantern", "chain", "candle", "cake", "_sign", "banner", "_bed", "_head", "_skull",
            "chest", "anvil", "_shelf", "golem_statue", "lightning_rod", "sapling", "_tulip", "_mushroom", "_fungus",
            "_vines", "_petals", "_bush", "_grass", "_plant", "dripleaf", "_dripleaf_block", "blossom", "_flower",
            "_propagule", "_bud", "_cluster", "coral", "_fan", "_egg", "_spike", "_spawn", "detector");

    /** Blocks that aren't whole cubes and have names of their own. */
    private static final Set<String> PARTIAL_NAMES = Set.of(
            "bed", "bamboo", "bell", "brewing_stand", "cactus", "campfire", "soul_campfire", "cauldron", "composter",
            "conduit", "daylight_detector_inverted", "decorated_pot", "dried_ghast", "enchanting_table", "end_portal_frame",
            "end_rod", "flower_pot", "glow_lichen", "grindstone", "heavy_core", "hopper", "ladder", "lectern", "lever",
            "scaffolding", "sculk_sensor", "calibrated_sculk_sensor", "sculk_shrieker", "sculk_vein", "resin_clump",
            "pointed_dripstone", "sea_pickle", "stonecutter_block", "tripwire_hook", "trip_wire", "waterlily", "web",
            "large_fern", "leaf_litter", "pale_hanging_moss", "wildflowers", "allium", "azure_bluet", "blue_orchid",
            "cornflower", "dandelion", "golden_dandelion", "lily_of_the_valley", "oxeye_daisy", "poppy", "torchflower",
            "wither_rose", "lilac", "peony", "sunflower", "redstone_wire", "snow_layer", "reeds", "wheat", "carrots",
            "potatoes", "beetroot", "nether_wart", "cocoa", "kelp", "frame", "glow_frame");

    /** Whole cubes whose names end like those of a family that isn't. */
    private static final Set<String> FULL_NAMES = Set.of(
            "sea_lantern", "dirt_with_roots", "mangrove_roots", "muddy_mangrove_roots");

    /** The way stairs climb, by their "weirdo_direction". Their tall back is on the side they climb towards. */
    public static final List<Face> STAIRS_CLIMB = List.of(Face.EAST, Face.WEST, Face.SOUTH, Face.NORTH);

    private BlockShapes() {
    }

    /** Whether a placed block takes this one's place (null is air). Snow does only while it's one layer deep. */
    public static boolean isReplaceable(Block block) {
        if (block == null) {
            return true;
        }
        String name = block.getIdentifier();
        return REPLACEABLE.contains(name)
                && !(name.equals(BlockID.SNOW_LAYER) && !Integer.valueOf(0).equals(block.getProperty("height")));
    }

    /**
     * The part of a block's space, from 0 to 1 along each axis, that players bump into, and
     * so mustn't be in when it's placed: all of it for most solid blocks, the half a slab
     * fills, the thin plate of a ladder or closed trapdoor, and the low top of a carpet or
     * bed. Null for a block players pass through, like a flower, or scaffolding, which they
     * climb inside.
     */
    public static Box collisionBox(Block block) {
        if (block == null || !block.isSolid() || Scaffolding.is(block)) {
            return null;
        }
        String name = block.getIdentifier();
        if (name.endsWith("_slab") && !name.contains("double")) {
            return "top".equals(block.getProperty("minecraft:vertical_half")) ? sixteenths(0, 8, 0, 16, 16, 16)
                    : sixteenths(0, 0, 0, 16, 8, 16);
        }
        if (name.endsWith("carpet")) {
            return sixteenths(0, 0, 0, 16, 1, 16);
        }
        if (name.equals(BlockID.BED) || name.equals(BlockID.STRAW_BED)) {
            return sixteenths(0, 0, 0, 16, 9, 16);
        }
        if (name.endsWith("trapdoor") && !block.isSet("open_bit")) {
            return block.isSet("upside_down_bit") ? sixteenths(0, 16 - PLATE, 0, 16, 16, 16)
                    : sixteenths(0, 0, 0, 16, PLATE, 16);
        }
        if (name.equals(BlockID.LADDER) && block.getProperty("facing_direction") instanceof Integer facing
                && Face.of(facing) != null && Face.of(facing).isHorizontal()) {
            // Against the wall behind it, which is on the side it faces away from.
            return switch (Face.of(facing).opposite()) {
                case NORTH -> sixteenths(0, 0, 0, 16, 16, PLATE);
                case SOUTH -> sixteenths(0, 0, 16 - PLATE, 16, 16, 16);
                case WEST -> sixteenths(0, 0, 0, PLATE, 16, 16);
                default -> sixteenths(16 - PLATE, 0, 0, 16, 16, 16);
            };
        }
        return WHOLE;
    }

    private static Box sixteenths(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Box(minX / 16.0, minY / 16.0, minZ / 16.0, maxX / 16.0, maxY / 16.0, maxZ / 16.0);
    }

    /** A box within a block's space, each side from 0 to 1. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    /**
     * Whether a block is a whole cube, which can hold things like torches on any side.
     * Double slabs are; blocks a placed block replaces, like air and short grass, aren't.
     */
    public static boolean isFullCube(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getIdentifier().substring(block.getIdentifier().indexOf(':') + 1);
        if (FULL_NAMES.contains(name) || name.contains("double")) {
            return true;
        }
        if (isReplaceable(block) || PARTIAL_NAMES.contains(name)) {
            return false;
        }
        for (String ending : PARTIAL_ENDINGS) {
            if (name.endsWith(ending)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a face of a block is whole and flat, so torches, buttons, levers and ladders can
     * be fixed to it. Every face of a whole cube is. A slab's is on the side it fills; stairs
     * have a whole bottom (or top, upside down), a whole back while straight, and whole sides
     * on both the back and the turn when they fill an inner corner.
     */
    public static boolean hasFullFace(Block block, Face face) {
        if (isFullCube(block)) {
            return true;
        }
        if (block == null) {
            return false;
        }
        Object half = block.getProperty("minecraft:vertical_half");
        if ("top".equals(half)) {
            return face == Face.UP;
        }
        if ("bottom".equals(half)) {
            return face == Face.DOWN;
        }
        if (!(block.getProperty("weirdo_direction") instanceof Integer direction)
                || direction < 0 || direction >= STAIRS_CLIMB.size()) {
            return false;
        }
        Face climb = STAIRS_CLIMB.get(direction);
        boolean upsideDown = block.isSet("upside_down_bit");
        if (face == (upsideDown ? Face.UP : Face.DOWN)) {
            return true;
        }
        Object corner = block.getProperty("minecraft:corner");
        if (corner == null || "none".equals(corner)) {
            return face == climb;
        }
        if ("inner_left".equals(corner)) {
            return face == climb || face == climb.counterClockwise();
        }
        if ("inner_right".equals(corner)) {
            return face == climb || face == climb.clockwise();
        }
        return false;
    }

    /** Whether a torch can stand on top of a block: one with a whole top can hold it, and so can a fence or wall. */
    public static boolean holdsTorchOnTop(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getIdentifier();
        return hasFullFace(block, Face.UP) || name.endsWith("fence") || name.endsWith("_wall");
    }
}
