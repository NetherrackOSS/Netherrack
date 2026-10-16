package com.netherrack.server.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Items that place blocks without saying which in the item data: those that place a block of
 * another name - crops by their seeds, signs by the wood they're made of, redstone dust by
 * redstone - and those that place the block of their own name but carry no block state,
 * like doors, beds and cauldrons. The other way round, it's what such blocks drop.
 */
public final class BlockItems {

    /** Items that place a block of another name. */
    private static final Map<String, String> PLACES = new HashMap<>();

    /** Items that place the block of their own name, though they carry no block state. */
    private static final Set<String> PLACES_ITSELF = Set.of(
            BlockID.CAULDRON,
            BlockID.FLOWER_POT,
            BlockID.HOPPER,
            BlockID.CAKE,
            BlockID.CAMPFIRE,
            BlockID.SOUL_CAMPFIRE,
            BlockID.BREWING_STAND,
            BlockID.FRAME,
            BlockID.GLOW_FRAME,
            BlockID.NETHER_WART,
            BlockID.SHELF_MUSHROOM,
            BlockID.NETHER_SPROUTS,
            BlockID.KELP,
            BlockID.BED,
            BlockID.STRAW_BED
    );

    /** Blocks that drop an item of another name: the reverse of PLACES, and a few more. */
    private static final Map<String, String> ITEM_PLACING = new HashMap<>();

    static {
        places("wheat_seeds", "wheat");
        places("pumpkin_seeds", "pumpkin_stem");
        places("melon_seeds", "melon_stem");
        places("beetroot_seeds", "beetroot");
        places("torchflower_seeds", "torchflower_crop");
        places("pitcher_pod", "pitcher_crop");
        places("potato", "potatoes");
        places("carrot", "carrots");
        places("sweet_berries", "sweet_berry_bush");
        places("glow_berries", "cave_vines");
        places("cocoa_beans", "cocoa");
        places("sugar_cane", "reeds");
        places("redstone", "redstone_wire");
        places("repeater", "unpowered_repeater");
        places("comparator", "unpowered_comparator");
        places("string", "trip_wire");
        places("banner", "standing_banner");
        // Oak and dark oak signs are named differently from the rest.
        places("oak_sign", "standing_sign");
        places("dark_oak_sign", "darkoak_standing_sign");
        for (String wood : List.of("spruce", "birch", "jungle", "acacia", "mangrove", "poplar", "cherry", "pale_oak",
                "bamboo", "crimson", "warped")) {
            places(wood + "_sign", wood + "_standing_sign");
        }

        PLACES.forEach((item, block) -> ITEM_PLACING.put(block, item));
        ITEM_PLACING.put("minecraft:powered_repeater", "minecraft:repeater");
        ITEM_PLACING.put("minecraft:powered_comparator", "minecraft:comparator");
        ITEM_PLACING.put("minecraft:wall_banner", "minecraft:banner");
        ITEM_PLACING.put("minecraft:wall_sign", "minecraft:oak_sign");
        ITEM_PLACING.put("minecraft:darkoak_wall_sign", "minecraft:dark_oak_sign");
        PLACES.forEach((item, block) -> {
            if (block.endsWith("_standing_sign")) {
                ITEM_PLACING.put(block.replace("_standing_sign", "_wall_sign"), item);
            }
        });
    }

    private BlockItems() {
    }

    private static void places(String item, String block) {
        PLACES.put("minecraft:" + item, "minecraft:" + block);
    }

    /**
     * The block an item places when the item data doesn't say: another block's name, its own
     * name (doors, hanging signs, beds, cauldrons...), or null if it places nothing.
     */
    public static String blockPlacedBy(String item) {
        String other = PLACES.get(item);
        if (other != null) {
            return other;
        }
        if (item.endsWith("_door") || item.endsWith("_hanging_sign") || PLACES_ITSELF.contains(item)) {
            return item;
        }
        return null;
    }

    /** The item a block came from, when it isn't the one of its own name - or null when it is. */
    public static String itemPlacing(String block) {
        return ITEM_PLACING.get(block);
    }
}
