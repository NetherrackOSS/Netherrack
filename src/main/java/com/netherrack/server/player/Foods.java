package com.netherrack.server.player;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Map;
import java.util.Set;

/**
 * What eating an item does: how many hunger points it restores, and how much saturation
 * that comes with. Values match vanilla's.
 * <p>
 * Items not listed here aren't food; the client won't offer to eat them, so the server
 * only ever sees a consume request for one of these.
 */
public final class Foods {

    /**
     * @param nutrition          hunger points restored
     * @param saturationModifier saturation restored is this times the nutrition, doubled -
     *                           vanilla's formula (food * modifier * 2)
     */
    public record Food(int nutrition, float saturationModifier) {
    }

    /** Foods that may be eaten even when the hunger bar is full, as in vanilla. */
    private static final Set<String> ALWAYS_EDIBLE = Set.of(
            "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple",
            "minecraft:chorus_fruit",
            "minecraft:honey_bottle");

    private static final Map<String, Food> FOODS = Map.ofEntries(
            Map.entry("minecraft:apple", new Food(4, 0.3f)),
            Map.entry("minecraft:baked_potato", new Food(5, 0.6f)),
            Map.entry("minecraft:beef", new Food(3, 0.3f)),
            Map.entry("minecraft:beetroot", new Food(1, 0.6f)),
            Map.entry("minecraft:beetroot_soup", new Food(6, 0.6f)),
            Map.entry("minecraft:bread", new Food(5, 0.6f)),
            Map.entry("minecraft:carrot", new Food(3, 0.6f)),
            Map.entry("minecraft:chicken", new Food(2, 0.3f)),
            Map.entry("minecraft:chorus_fruit", new Food(4, 0.3f)),
            Map.entry("minecraft:cod", new Food(2, 0.1f)),
            Map.entry("minecraft:cooked_beef", new Food(8, 0.8f)),
            Map.entry("minecraft:cooked_chicken", new Food(6, 0.6f)),
            Map.entry("minecraft:cooked_cod", new Food(5, 0.6f)),
            Map.entry("minecraft:cooked_mutton", new Food(6, 0.8f)),
            Map.entry("minecraft:cooked_porkchop", new Food(8, 0.8f)),
            Map.entry("minecraft:cooked_rabbit", new Food(5, 0.6f)),
            Map.entry("minecraft:cooked_salmon", new Food(6, 0.8f)),
            Map.entry("minecraft:cookie", new Food(2, 0.1f)),
            Map.entry("minecraft:dried_kelp", new Food(1, 0.3f)),
            Map.entry("minecraft:enchanted_golden_apple", new Food(4, 1.2f)),
            Map.entry("minecraft:glow_berries", new Food(2, 0.1f)),
            Map.entry("minecraft:golden_apple", new Food(4, 1.2f)),
            Map.entry("minecraft:golden_carrot", new Food(6, 1.2f)),
            Map.entry("minecraft:honey_bottle", new Food(6, 0.1f)),
            Map.entry("minecraft:melon_slice", new Food(2, 0.3f)),
            Map.entry("minecraft:mushroom_stew", new Food(6, 0.6f)),
            Map.entry("minecraft:mutton", new Food(2, 0.3f)),
            Map.entry("minecraft:porkchop", new Food(3, 0.3f)),
            Map.entry("minecraft:potato", new Food(1, 0.3f)),
            Map.entry("minecraft:pufferfish", new Food(1, 0.1f)),
            Map.entry("minecraft:pumpkin_pie", new Food(8, 0.3f)),
            Map.entry("minecraft:rabbit", new Food(3, 0.3f)),
            Map.entry("minecraft:rabbit_stew", new Food(10, 0.6f)),
            Map.entry("minecraft:rotten_flesh", new Food(4, 0.1f)),
            Map.entry("minecraft:salmon", new Food(2, 0.1f)),
            Map.entry("minecraft:spider_eye", new Food(2, 0.8f)),
            Map.entry("minecraft:suspicious_stew", new Food(6, 0.6f)),
            Map.entry("minecraft:sweet_berries", new Food(2, 0.1f)),
            Map.entry("minecraft:tropical_fish", new Food(1, 0.1f)));

    private Foods() {
    }

    /** What eating an item does, or null if it isn't food. */
    public static Food of(ItemData item) {
        if (item == null || item.isNull() || item.getDefinition() == null) {
            return null;
        }
        return FOODS.get(item.getDefinition().getIdentifier());
    }

    /** Whether an item may be eaten even with a full hunger bar. */
    public static boolean alwaysEdible(ItemData item) {
        return item != null && !item.isNull() && item.getDefinition() != null
                && ALWAYS_EDIBLE.contains(item.getDefinition().getIdentifier());
    }
}
