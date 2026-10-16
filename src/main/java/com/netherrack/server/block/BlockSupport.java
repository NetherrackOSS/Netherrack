package com.netherrack.server.block;

import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which blocks need another to hold them up, and whether it still does: a torch, button,
 * lever, ladder, wall sign or tripwire hook on its side, a plant, carpet or rail on the floor,
 * a lantern or roots from a ceiling, and the top half of a door or tall plant on its bottom
 * half. When the block holding one goes, it breaks too (see BlockBreaking).
 * <p>
 * What can hold a block up follows the game's rules: flowers, saplings and grasses grow in
 * soil (dirt, grass, moss, mud and farmland), crops in farmland, cacti and dead bushes in
 * sand, nether wart in soul sand, and so on. Rails, redstone and coral need a whole top to
 * stand on, buttons, levers, ladders and tripwire hooks a whole face, and signs and banners
 * a solid block.
 */
public final class BlockSupport {

    private static final String UPPER_BLOCK = "upper_block_bit";
    private static final String HEAD_PIECE = "head_piece_bit";

    /** Plants and the like that stand on the block below, by how their names end. */
    private static final List<String> ON_FLOOR_ENDINGS = List.of(
            "_sapling", "_tulip", "_carpet", "_pressure_plate", "rail", "_coral_fan", "_coral", "candle", "candle_cake",
            "_petals", "_standing_sign", "standing_banner", "_repeater", "_comparator", "_crop", "_fungus");

    /** Plants and the like that stand on the block below, by name. */
    private static final Set<String> ON_FLOOR = Set.of(
            "big_dripleaf", "short_grass", "short_dry_grass", "tall_dry_grass", "fern", "deadbush", "bush", "firefly_bush",
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley",
            "wither_rose", "torchflower", "open_eyeblossom", "closed_eyeblossom", "golden_dandelion", "cactus",
            "cactus_flower", "reeds", "bamboo", "bamboo_sapling", "azalea", "flowering_azalea", "nether_sprouts",
            "crimson_roots", "warped_roots", "nether_wart", "wheat", "carrots", "potatoes", "beetroot", "sweet_berry_bush",
            "sea_pickle", "turtle_egg", "snow_layer", "redstone_wire", "cake", "standing_sign", "leaf_litter", "wildflowers",
            "brown_mushroom", "red_mushroom", "flower_pot", "kelp", "seagrass", "melon_stem", "pumpkin_stem",
            "twisting_vines", "waterlily", "frog_spawn");

    /** Blocks that hang from the block above. */
    private static final Set<String> FROM_CEILING = Set.of(
            "hanging_roots", "spore_blossom", "pale_hanging_moss", "cave_vines", "cave_vines_body_with_berries",
            "cave_vines_head_with_berries", "weeping_vines");

    /** The ground most plants grow in: dirt of every kind, grass, moss, mud and farmland. */
    private static final Set<String> SOIL = Set.of(
            "grass_block", "dirt", "coarse_dirt", "podzol", "mycelium", "dirt_with_roots", "moss_block", "pale_moss_block",
            "mud", "muddy_mangrove_roots", "farmland");

    /** Plants that grow in soil and nothing else, besides those named like saplings and tulips. */
    private static final Set<String> IN_SOIL = Set.of(
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley",
            "torchflower", "open_eyeblossom", "closed_eyeblossom", "golden_dandelion", "short_grass", "fern", "bush",
            "firefly_bush", "sweet_berry_bush", "pink_petals", "wildflowers", "sunflower", "lilac", "rose_bush", "peony",
            "tall_grass", "large_fern", "pitcher_plant");

    private static final Set<String> SAND = Set.of("sand", "red_sand", "suspicious_sand");

    /** Where plants that grow in particular ground can stand, beyond soil where it says so. */
    private static final Map<String, Set<String>> GROUND = Map.ofEntries(
            Map.entry("wheat", Set.of("farmland")),
            Map.entry("carrots", Set.of("farmland")),
            Map.entry("potatoes", Set.of("farmland")),
            Map.entry("beetroot", Set.of("farmland")),
            Map.entry("melon_stem", Set.of("farmland")),
            Map.entry("pumpkin_stem", Set.of("farmland")),
            Map.entry("torchflower_crop", Set.of("farmland")),
            Map.entry("pitcher_crop", Set.of("farmland")),
            Map.entry("nether_wart", Set.of("soul_sand")),
            Map.entry("cactus", Set.of("sand", "red_sand", "suspicious_sand", "cactus")),
            Map.entry("small_dripleaf_block", Set.of("clay", "moss_block", "pale_moss_block")),
            Map.entry("brown_mushroom", Set.of("mycelium", "podzol", "crimson_nylium", "warped_nylium")),
            Map.entry("red_mushroom", Set.of("mycelium", "podzol", "crimson_nylium", "warped_nylium")),
            Map.entry("waterlily", Set.of("water", "flowing_water", "ice", "packed_ice", "blue_ice", "frosted_ice")),
            Map.entry("frog_spawn", Set.of("water", "flowing_water")));

    /** Plants that grow in soil and somewhere else too. */
    private static final Map<String, Set<String>> SOIL_AND = Map.ofEntries(
            Map.entry("crimson_fungus", Set.of("crimson_nylium", "warped_nylium", "soul_soil")),
            Map.entry("warped_fungus", Set.of("crimson_nylium", "warped_nylium", "soul_soil")),
            Map.entry("crimson_roots", Set.of("crimson_nylium", "warped_nylium", "soul_soil")),
            Map.entry("warped_roots", Set.of("crimson_nylium", "warped_nylium", "soul_soil")),
            Map.entry("nether_sprouts", Set.of("crimson_nylium", "warped_nylium", "soul_soil")),
            Map.entry("wither_rose", Set.of("netherrack", "soul_sand", "soul_soil")),
            Map.entry("bamboo", Set.of("sand", "red_sand", "suspicious_sand", "gravel", "suspicious_gravel", "bamboo",
                    "bamboo_sapling")),
            Map.entry("bamboo_sapling", Set.of("sand", "red_sand", "suspicious_sand", "gravel", "suspicious_gravel",
                    "bamboo", "bamboo_sapling")),
            Map.entry("azalea", Set.of("clay")),
            Map.entry("flowering_azalea", Set.of("clay")),
            Map.entry("mangrove_propagule", Set.of("clay")),
            Map.entry("big_dripleaf", Set.of("clay", "big_dripleaf")));

    /** Plants of dry ground, which grow in sand and terracotta as well as soil. */
    private static final Set<String> DRY = Set.of("deadbush", "short_dry_grass", "tall_dry_grass");

    /** Blocks that need a whole top to stand on. */
    private static final Set<String> ON_WHOLE_TOP = Set.of(
            "redstone_wire", "unpowered_repeater", "powered_repeater", "unpowered_comparator", "powered_comparator",
            "leaf_litter", "sea_pickle", "seagrass");

    /** The logs cocoa grows on. */
    private static final Set<String> JUNGLE_LOGS = Set.of(
            "jungle_log", "stripped_jungle_log", "jungle_wood", "stripped_jungle_wood");

    private BlockSupport() {
    }

    /**
     * For a block another holds up, the side the block holding it is on; null for a block
     * that stands by itself. The top half of a door or plant is held by its bottom half,
     * which {@link #otherHalf} follows.
     */
    public static Face heldFrom(Block block) {
        if (isTwoTall(block) && !block.isSet(UPPER_BLOCK)) {
            return Face.DOWN;
        }
        Object torch = block.getProperty("torch_facing_direction");
        if (torch != null) {
            return "top".equals(torch) ? Face.DOWN : Face.named(torch);
        }
        // A lever names the face it points out of, or the axis it lies along on a floor or ceiling.
        if (block.getProperty("lever_direction") instanceof String lever) {
            if (lever.startsWith("up")) {
                return Face.DOWN;
            }
            if (lever.startsWith("down")) {
                return Face.UP;
            }
            Face pointing = Face.named(lever);
            return pointing != null ? pointing.opposite() : null;
        }
        // Amethyst buds grow out of the face they name.
        Face growing = Face.named(block.getProperty("minecraft:block_face"));
        if (growing != null) {
            return growing.opposite();
        }
        String name = block.getIdentifier();
        String bare = bare(name);
        Face pointing = block.getProperty("facing_direction") instanceof Integer facing ? Face.of(facing) : null;
        if (name.endsWith("button") || bare.equals("ladder") || name.endsWith("wall_sign") || bare.equals("wall_banner")
                || bare.equals("frame") || bare.equals("glow_frame")) {
            return pointing != null ? pointing.opposite() : null;
        }
        if (name.endsWith("_coral_wall_fan") && block.getProperty("coral_direction") instanceof Integer direction) {
            Face facing = BlockPlacement.CORAL_WALL_ORDER.get(Math.floorMod(direction, 4));
            return facing.opposite();
        }
        if (bare.equals("tripwire_hook") || bare.equals("cocoa")) {
            Face facing = block.getProperty("direction") instanceof Integer direction ? Face.ofLegacyIndex(direction) : null;
            // A hook names the way it faces out of its block; cocoa names the log it hangs from.
            return facing == null || bare.equals("cocoa") ? facing : facing.opposite();
        }
        if (bare.equals("bell")) {
            Object attachment = block.getProperty("attachment");
            if ("standing".equals(attachment)) {
                return Face.DOWN;
            }
            if ("hanging".equals(attachment)) {
                return Face.UP;
            }
            Face facing = block.getProperty("direction") instanceof Integer direction ? Face.ofLegacyIndex(direction) : null;
            return "side".equals(attachment) && facing != null ? facing.opposite() : null;
        }
        if (name.endsWith("_hanging_sign")) {
            return block.isSet("hanging") ? Face.UP : null;
        }
        if ((name.endsWith("lantern") && block.hasProperty("hanging")) || bare.equals("pointed_dripstone")
                || bare.equals("sulfur_spike") || bare.equals("mangrove_propagule")) {
            return block.isSet("hanging") ? Face.UP : Face.DOWN;
        }
        if (FROM_CEILING.contains(bare)) {
            return Face.UP;
        }
        if (ON_FLOOR.contains(bare)) {
            return Face.DOWN;
        }
        for (String ending : ON_FLOOR_ENDINGS) {
            if (name.endsWith(ending)) {
                return Face.DOWN;
            }
        }
        return null;
    }

    /**
     * Whether the block at holderPosition holds up this block on its face: the face of the
     * holder it's on, so UP for a block standing on it.
     */
    public static boolean holdsUp(World world, Vector3i holderPosition, Face face, Block block) {
        Block holder = world.getBlock(holderPosition.getX(), holderPosition.getY(), holderPosition.getZ());
        String name = block.getIdentifier();
        String bare = bare(name);
        if (block.hasProperty("torch_facing_direction")) {
            return face == Face.UP ? BlockShapes.holdsTorchOnTop(holder) : BlockShapes.hasFullFace(holder, face);
        }
        if (name.endsWith("button") || bare.equals("lever") || bare.equals("ladder") || bare.equals("tripwire_hook")
                || name.endsWith("_coral_wall_fan") || block.hasProperty("minecraft:block_face")) {
            return BlockShapes.hasFullFace(holder, face);
        }
        if (holder == null) {
            return false;
        }
        if (face == Face.UP) {
            return standsOn(world, holderPosition, holder, block);
        }
        if (face == Face.DOWN) {
            return hangsFrom(holder, block);
        }
        if (bare.equals("cocoa")) {
            return JUNGLE_LOGS.contains(bare(holder.getIdentifier()));
        }
        if (bare.equals("bell")) {
            return BlockShapes.hasFullFace(holder, face);
        }
        if (name.endsWith("wall_sign") || bare.equals("wall_banner")) {
            return holder.isSolid();
        }
        return !BlockShapes.isReplaceable(holder);
    }

    /** Whether a block can stand on the block below it, ground: the right soil for a plant, a whole top for a rail... */
    private static boolean standsOn(World world, Vector3i groundPosition, Block ground, Block block) {
        String bare = bare(block.getIdentifier());
        String under = bare(ground.getIdentifier());
        if (isTwoTall(block) && bare.endsWith("door")) {
            return BlockShapes.hasFullFace(ground, Face.UP);
        }
        Set<String> only = GROUND.get(bare);
        if (only != null) {
            // Mushrooms grow on any whole block in the dark; there's no light to go by yet, so on any.
            return only.contains(under) || (bare.endsWith("_mushroom") && BlockShapes.isFullCube(ground));
        }
        Set<String> alsoOn = SOIL_AND.get(bare);
        if (alsoOn != null) {
            return SOIL.contains(under) || alsoOn.contains(under);
        }
        if (IN_SOIL.contains(bare) || bare.endsWith("_tulip") || bare.endsWith("_sapling")) {
            return SOIL.contains(under);
        }
        if (DRY.contains(bare)) {
            return SOIL.contains(under) || SAND.contains(under) || isTerracotta(under);
        }
        switch (bare) {
            case "reeds" -> {
                // Sugar cane grows up from more of itself, or from soil or sand beside water.
                return under.equals("reeds")
                        || ((SOIL.contains(under) || SAND.contains(under)) && besideWater(world, groundPosition));
            }
            case "cactus_flower" -> {
                return under.equals("cactus") || under.equals("farmland") || BlockShapes.hasFullFace(ground, Face.UP);
            }
            case "kelp", "twisting_vines" -> {
                return under.equals(bare) || BlockShapes.hasFullFace(ground, Face.UP);
            }
            case "snow_layer" -> {
                if (under.equals("snow_layer")) {
                    return Integer.valueOf(7).equals(ground.getProperty("height"));
                }
                return !under.equals("ice") && !under.equals("packed_ice") && !under.equals("barrier")
                        && BlockShapes.hasFullFace(ground, Face.UP);
            }
            case "flower_pot", "bell" -> {
                return BlockShapes.holdsTorchOnTop(ground);
            }
            case "cake", "standing_sign", "standing_banner" -> {
                return ground.isSolid();
            }
            default -> {
                // Anything else below.
            }
        }
        if (ON_WHOLE_TOP.contains(bare) || bare.endsWith("rail") || bare.endsWith("_coral") || bare.endsWith("_coral_fan")) {
            return BlockShapes.hasFullFace(ground, Face.UP);
        }
        if (bare.endsWith("candle") || bare.endsWith("_pressure_plate") || bare.endsWith("lantern")) {
            return BlockShapes.holdsTorchOnTop(ground);
        }
        if (bare.endsWith("candle_cake") || bare.endsWith("_standing_sign")) {
            return ground.isSolid();
        }
        return !BlockShapes.isReplaceable(ground);
    }

    /** Whether a block can hang from the block above it, ceiling. */
    private static boolean hangsFrom(Block ceiling, Block block) {
        String bare = bare(block.getIdentifier());
        String above = bare(ceiling.getIdentifier());
        boolean wholeUnderside = BlockShapes.hasFullFace(ceiling, Face.DOWN);
        return switch (bare) {
            case "hanging_roots", "spore_blossom" -> wholeUnderside;
            case "cave_vines", "cave_vines_body_with_berries", "cave_vines_head_with_berries" ->
                    above.startsWith("cave_vines") || wholeUnderside;
            case "weeping_vines" -> above.equals("weeping_vines") || wholeUnderside;
            case "pale_hanging_moss" -> above.equals("pale_hanging_moss") || above.endsWith("leaves") || wholeUnderside;
            case "mangrove_propagule" -> above.equals("mangrove_leaves");
            default -> bare.endsWith("lantern") || bare.equals("bell") ? holdsCenterBelow(ceiling)
                    : !BlockShapes.isReplaceable(ceiling);
        };
    }

    /** Whether a lantern or bell can hang from the middle of a block's underside: a whole one, or a chain, fence, wall or bars. */
    static boolean holdsCenterBelow(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getIdentifier();
        return BlockShapes.hasFullFace(block, Face.DOWN) || name.endsWith("chain") || name.endsWith("fence")
                || name.endsWith("_wall") || name.endsWith("bars") || name.endsWith("glass_pane");
    }

    /**
     * Whether a block can stay where it is: whatever holds it up is still there, its other
     * half too for a door, bed or tall plant, and nothing solid stands beside a cactus.
     */
    public static boolean survives(World world, Vector3i position, Block block) {
        return standsAt(world, position, block) && hasOtherHalf(world, position, block);
    }

    /**
     * Whether a block could stand at a position: as {@link #survives}, except a door, bed or
     * tall plant needn't have its other half yet - for a block about to be placed.
     */
    public static boolean standsAt(World world, Vector3i position, Block block) {
        Face holder = heldFrom(block);
        if (holder != null && !holdsUp(world, holder.from(position), holder.opposite(), block)) {
            return false;
        }
        if (block.getIdentifier().equals(BlockID.CACTUS)) {
            for (Face side : Face.AROUND) {
                Vector3i beside = side.from(position);
                Block neighbour = world.getBlock(beside.getX(), beside.getY(), beside.getZ());
                if (neighbour != null && neighbour.isSolid()) {
                    return false;
                }
            }
        }
        if (Scaffolding.is(block)) {
            return Scaffolding.distance(world, position) < Scaffolding.MAX_DISTANCE;
        }
        return true;
    }

    /** Whether a door, bed or tall plant still has its other half; true for any other block. */
    private static boolean hasOtherHalf(World world, Vector3i position, Block block) {
        Vector3i other = otherHalf(block, position);
        if (other == null) {
            return true;
        }
        Block half = world.getBlock(other.getX(), other.getY(), other.getZ());
        if (half == null || !half.getIdentifier().equals(block.getIdentifier())) {
            return false;
        }
        String part = block.hasProperty(HEAD_PIECE) ? HEAD_PIECE : UPPER_BLOCK;
        return half.isSet(part) != block.isSet(part);
    }

    /** Where the other half of a door, bed or tall plant is, or null for a block that's one block. */
    public static Vector3i otherHalf(Block block, Vector3i position) {
        if (isTwoTall(block)) {
            return block.isSet(UPPER_BLOCK) ? Face.DOWN.from(position) : Face.UP.from(position);
        }
        if (!block.hasProperty(HEAD_PIECE)) {
            return null;
        }
        // A bed's foot points at its head.
        Face pointing = block.getProperty("direction") instanceof Integer direction ? Face.ofLegacyIndex(direction) : null;
        if (pointing == null) {
            return null;
        }
        return block.isSet(HEAD_PIECE) ? pointing.opposite().from(position) : pointing.from(position);
    }

    /**
     * Whether a block is two blocks tall, its halves told apart by "upper_block_bit": a door,
     * tall plant or small dripleaf. Pale moss carpet uses the bit for something else, and a
     * pitcher crop only grows a top half later.
     */
    public static boolean isTwoTall(Block block) {
        String name = block.getIdentifier();
        return block.hasProperty(UPPER_BLOCK) && !name.equals(BlockID.PALE_MOSS_CARPET)
                && !name.equals(BlockID.PITCHER_CROP);
    }

    private static boolean isTerracotta(String bare) {
        return bare.equals("hardened_clay") || (bare.endsWith("_terracotta") && !bare.endsWith("glazed_terracotta"));
    }

    /** Whether there's water beside a block, as sugar cane needs beside its ground. */
    private static boolean besideWater(World world, Vector3i position) {
        for (Face side : Face.AROUND) {
            Vector3i beside = side.from(position);
            Block block = world.getBlock(beside.getX(), beside.getY(), beside.getZ());
            if (block != null && (block.getIdentifier().equals(BlockID.WATER)
                    || block.getIdentifier().equals(BlockID.FLOWING_WATER)
                    || block.getIdentifier().equals(BlockID.FROSTED_ICE))) {
                return true;
            }
        }
        return false;
    }

    /** A block's name without "minecraft:". */
    private static String bare(String name) {
        return name.substring(name.indexOf(':') + 1);
    }
}
