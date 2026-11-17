package com.netherrack.server.crafting;

import com.netherrack.server.network.VanillaData;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crafting recipes, taken as they are from CloudburstMC's data repository
 * (https://github.com/CloudburstMC/Data, bundled as /data/recipes.json for this same
 * protocol version): every recipe a crafting table or the inventory screen's 2x2 grid can
 * make, each shaped (a pattern of item letters) or shapeless (a pinch of ingredients).
 * <p>
 * The packet lists them all for the client, whose recipe book, output prediction and
 * auto-fill are its own work; the server's job is only to make sure a craft really did
 * happen over a grid that holds what the recipe needs - the request names the recipe by
 * network id, the client's own, per-recipe number the packet assigns.
 * <p>
 * Only recipes a crafting table makes are kept: the furnace, stonecutter and smithing
 * recipes in the file belong to screens Netherrack doesn't have yet.
 */
public final class Recipes {

    private static final String RESOURCE = "/data/recipes.json";

    /** A damage value that means "any of this item, however it's colored or charged". */
    private static final int ANY_AUX = 32767;

    /**
     * The vanilla item tags the recipes name as ingredients, with what each holds, as
     * vanilla 1.26 has them (Geyser's item_tags.26_30.json). Only the twelve the bundled
     * recipes actually use are here.
     */
    private static final Map<String, List<String>> TAGS = Map.ofEntries(
            Map.entry("minecraft:coals", List.of("minecraft:coal", "minecraft:charcoal")),
            Map.entry("minecraft:egg", List.of("minecraft:egg", "minecraft:brown_egg", "minecraft:blue_egg")),
            Map.entry("minecraft:logs", List.of(
                    "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log",
                    "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log",
                    "minecraft:cherry_log", "minecraft:pale_oak_log", "minecraft:oak_wood",
                    "minecraft:spruce_wood", "minecraft:birch_wood", "minecraft:jungle_wood",
                    "minecraft:acacia_wood", "minecraft:dark_oak_wood", "minecraft:mangrove_wood",
                    "minecraft:cherry_wood", "minecraft:pale_oak_wood", "minecraft:stripped_oak_log",
                    "minecraft:stripped_spruce_log", "minecraft:stripped_birch_log",
                    "minecraft:stripped_jungle_log", "minecraft:stripped_acacia_log",
                    "minecraft:stripped_dark_oak_log", "minecraft:stripped_mangrove_log",
                    "minecraft:stripped_cherry_log", "minecraft:stripped_pale_oak_log",
                    "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_wood",
                    "minecraft:stripped_birch_wood", "minecraft:stripped_jungle_wood",
                    "minecraft:stripped_acacia_wood", "minecraft:stripped_dark_oak_wood",
                    "minecraft:stripped_mangrove_wood", "minecraft:stripped_cherry_wood",
                    "minecraft:stripped_pale_oak_wood", "minecraft:crimson_stem", "minecraft:warped_stem",
                    "minecraft:crimson_hyphae", "minecraft:warped_hyphae", "minecraft:stripped_crimson_stem",
                    "minecraft:stripped_warped_stem", "minecraft:stripped_crimson_hyphae",
                    "minecraft:stripped_warped_hyphae")),
            Map.entry("minecraft:logs_that_burn", List.of(
                    "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log",
                    "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log",
                    "minecraft:cherry_log", "minecraft:pale_oak_log", "minecraft:oak_wood",
                    "minecraft:spruce_wood", "minecraft:birch_wood", "minecraft:jungle_wood",
                    "minecraft:acacia_wood", "minecraft:dark_oak_wood", "minecraft:mangrove_wood",
                    "minecraft:cherry_wood", "minecraft:pale_oak_wood", "minecraft:stripped_oak_log",
                    "minecraft:stripped_spruce_log", "minecraft:stripped_birch_log",
                    "minecraft:stripped_jungle_log", "minecraft:stripped_acacia_log",
                    "minecraft:stripped_dark_oak_log", "minecraft:stripped_mangrove_log",
                    "minecraft:stripped_cherry_log", "minecraft:stripped_pale_oak_log",
                    "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_wood",
                    "minecraft:stripped_birch_wood", "minecraft:stripped_jungle_wood",
                    "minecraft:stripped_acacia_wood", "minecraft:stripped_dark_oak_wood",
                    "minecraft:stripped_mangrove_wood", "minecraft:stripped_cherry_wood",
                    "minecraft:stripped_pale_oak_wood")),
            Map.entry("minecraft:metal_nuggets", List.of("minecraft:iron_nugget", "minecraft:gold_nugget",
                    "minecraft:copper_nugget")),
            Map.entry("minecraft:mushrooms_for_stew", List.of("minecraft:red_mushroom", "minecraft:brown_mushroom")),
            Map.entry("minecraft:planks", List.of(
                    "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                    "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                    "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:pale_oak_planks",
                    "minecraft:bamboo_planks", "minecraft:crimson_planks", "minecraft:warped_planks")),
            Map.entry("minecraft:soul_fire_base_blocks", List.of("minecraft:soul_sand", "minecraft:soul_soil")),
            Map.entry("minecraft:stone_crafting_materials", List.of("minecraft:cobblestone",
                    "minecraft:cobbled_deepslate", "minecraft:blackstone")),
            Map.entry("minecraft:stone_tool_materials", List.of("minecraft:cobblestone",
                    "minecraft:cobbled_deepslate", "minecraft:blackstone")),
            Map.entry("minecraft:wooden_slabs", List.of(
                    "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:birch_slab",
                    "minecraft:jungle_slab", "minecraft:acacia_slab", "minecraft:dark_oak_slab",
                    "minecraft:mangrove_slab", "minecraft:cherry_slab", "minecraft:pale_oak_slab",
                    "minecraft:bamboo_slab", "minecraft:crimson_slab", "minecraft:warped_slab")),
            Map.entry("minecraft:wool", List.of(
                    "minecraft:white_wool", "minecraft:orange_wool", "minecraft:magenta_wool",
                    "minecraft:light_blue_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
                    "minecraft:pink_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
                    "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:blue_wool",
                    "minecraft:brown_wool", "minecraft:green_wool", "minecraft:red_wool",
                    "minecraft:black_wool")));

    private static volatile Recipes instance;

    private final Map<Integer, Recipe> byNetId = new HashMap<>();
    private final CraftingDataPacket packet = new CraftingDataPacket();

    public static Recipes get() {
        Recipes result = instance;
        if (result == null) {
            synchronized (Recipes.class) {
                if (instance == null) {
                    instance = new Recipes();
                }
                result = instance;
            }
        }
        return result;
    }

    /** The packet that tells the client every recipe, sent as the player joins. */
    public CraftingDataPacket packet() {
        return packet;
    }

    /** The recipe a craft request names by network id, or null if there is no such recipe. */
    public Recipe byNetId(int netId) {
        return byNetId.get(netId);
    }

    private Recipes() {
        long start = System.currentTimeMillis();
        int shaped = 0;
        int shapeless = 0;
        int skipped = 0;

        Map<String, Object> root;
        try {
            root = JsonUtil.parseJson(read());
        } catch (JoseException e) {
            throw new IllegalStateException("Failed to parse " + RESOURCE, e);
        }
        for (Object object : (List<?>) root.get("recipes")) {
            Map<String, Object> entry = cast(object);
            int type = ((Number) entry.get("type")).intValue();
            String block = (String) entry.get("block");
            // The crafting_table ones are the ordinary recipes; the deprecated ones carry
            // recipes nothing else lists (the barrel, for one) and are just as valid.
            if ((type != 0 && type != 1)
                    || (!"crafting_table".equals(block) && !"deprecated".equals(block))) {
                continue;
            }

            Recipe recipe = type == 1 ? shapedRecipe(entry) : shapelessRecipe(entry);
            if (recipe == null) {
                skipped++;
                continue;
            }
            byNetId.put(recipe.netId(), recipe);
            // The packet carries shaped and shapeless recipes in separate lists - the
            // client reads them apart, not as one.
            if (recipe instanceof Shaped shapedRecipe) {
                packet.getShapedData().add((ShapedRecipeData) shapedRecipe.wire());
            } else {
                packet.getShapelessData().add((ShapelessRecipeData) ((Shapeless) recipe).wire());
            }
            if (recipe instanceof Shaped) {
                shaped++;
            } else {
                shapeless++;
            }
        }
        Logger.info("Loaded " + shaped + " shaped and " + shapeless + " shapeless crafting recipes"
                + (skipped > 0 ? " (" + skipped + " skipped, for items this version doesn't have)" : "")
                + " in " + (System.currentTimeMillis() - start) + "ms");
        // The client has its own built-in copy of vanilla's recipes, numbered from 1 just
        // like these are. Left alone, the two lists merge and every network id collides -
        // the client would craft with an id that means a different recipe here, and every
        // craft would be refused. This flag tells it to drop its own list and use ours.
        packet.setCleanRecipes(true);
    }

    private String read() {
        try (InputStream in = Recipes.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + RESOURCE, e);
        }
    }

    private Recipe shapedRecipe(Map<String, Object> entry) {
        String id = (String) entry.get("id");
        ItemData result = result(entry);
        if (result == null) {
            return null;
        }
        List<String> shape = (List<String>) entry.get("shape");
        int height = shape.size();
        int width = shape.get(0).length();
        Map<String, Ingredient> keys = new LinkedHashMap<>();
        for (Map.Entry<String, Object> letter : cast(entry.get("input")).entrySet()) {
            Ingredient ingredient = ingredient(cast(letter.getValue()));
            if (ingredient == null) {
                return null; // an ingredient no item of this version matches
            }
            keys.put(letter.getKey(), ingredient);
        }

        // The pattern flattened row by row, a null cell where the shape has a space.
        Ingredient[] cells = new Ingredient[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char letter = shape.get(y).charAt(x);
                if (letter != ' ') {
                    cells[y * width + x] = keys.get(String.valueOf(letter));
                }
            }
        }
        return new Shaped(byNetId.size() + 1, id, width, height, cells, result, priority(entry));
    }

    private Recipe shapelessRecipe(Map<String, Object> entry) {
        String id = (String) entry.get("id");
        ItemData result = result(entry);
        if (result == null) {
            return null;
        }
        List<Ingredient> ingredients = new ArrayList<>();
        for (Object object : (List<?>) entry.get("input")) {
            Ingredient ingredient = ingredient(cast(object));
            if (ingredient == null) {
                return null; // an ingredient no item of this version matches
            }
            ingredients.add(ingredient);
        }
        return new Shapeless(byNetId.size() + 1, id, ingredients, result, priority(entry));
    }

    /** An ingredient: the items that satisfy it, whether by tag or by name, and the damage value wanted. */
    private Ingredient ingredient(Map<String, Object> raw) {
        String tag = (String) raw.get("itemTag");
        List<String> names;
        if (tag != null) {
            names = TAGS.get(tag);
            if (names == null) {
                Logger.warn("A recipe uses item tag " + tag + ", which Netherrack doesn't know.");
                return null;
            }
        } else {
            names = List.of((String) raw.get("id"));
        }
        for (String name : names) {
            if (definition(name) == null) {
                return null;
            }
        }
        int aux = ((Number) raw.getOrDefault("auxValue", 0)).intValue();
        return new Ingredient(names, tag, aux);
    }

    /** What the recipe makes: the item named, with its count, color or tag, or null if this version doesn't have it. */
    private ItemData result(Map<String, Object> entry) {
        Map<String, Object> raw = cast(((List<?>) entry.get("output")).get(0));
        ItemData item = VanillaData.get().item((String) raw.get("id"),
                ((Number) raw.getOrDefault("count", 1)).intValue());
        if (item.isNull()) {
            return null;
        }
        ItemData.Builder builder = item.toBuilder();
        int damage = ((Number) raw.getOrDefault("damage", 0)).intValue();
        if (damage != 0) {
            builder.damage(damage);
        }
        String nbt = (String) raw.get("nbt_b64");
        if (nbt != null) {
            NbtMap tag = tag(Base64.getDecoder().decode(nbt));
            if (tag != null) {
                builder.tag(tag);
            }
        }
        return builder.build();
    }

    /** A saved output's little-endian NBT, as banners, fireworks and the like carry. */
    private NbtMap tag(byte[] bytes) {
        try {
            return (NbtMap) NbtUtils.createReaderLE(new ByteArrayInputStream(bytes), false, false).readTag();
        } catch (Exception e) {
            Logger.warn("A recipe's saved item tag couldn't be read, so it's plain: " + e);
            return null;
        }
    }

    private static ItemDefinition definition(String name) {
        return VanillaData.get().getItemRegistry().getDefinition(name);
    }

    private static int priority(Map<String, Object> entry) {
        return ((Number) entry.getOrDefault("priority", 0)).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object object) {
        return (Map<String, Object>) object;
    }

    /**
     * A recipe: what it's called, its number on the wire, what it makes, and whether a
     * grid's contents are exactly it laid out. The shaped ones match either way round -
     * laid out left-to-right or mirrored, as vanilla accepts both.
     */
    public abstract static class Recipe {

        private final int netId;
        private final String id;
        private final ItemData result;

        Recipe(int netId, String id, ItemData result) {
            this.netId = netId;
            this.id = id;
            this.result = result;
        }

        public int netId() {
            return netId;
        }

        public String id() {
            return id;
        }

        /** What crafting this recipe makes. */
        public ItemData result() {
            return result;
        }

        /** Whether a crafting grid (4 slots for the 2x2, 9 for the 3x3) holds exactly this recipe. */
        public abstract boolean matches(ItemData[] grid);

        /** The recipe as the CraftingDataPacket carries it, for the client's recipe book. */
        abstract RecipeData wire();
    }

    /** A shaped recipe: a pattern of ingredients, row by row, with empty cells where the shape has spaces. */
    static final class Shaped extends Recipe {

        private final int width;
        private final int height;
        private final Ingredient[] cells;
        private final int priority;

        Shaped(int netId, String id, int width, int height, Ingredient[] cells, ItemData result, int priority) {
            super(netId, id, result);
            this.width = width;
            this.height = height;
            this.cells = cells;
            this.priority = priority;
        }

        @Override
        public boolean matches(ItemData[] grid) {
            int side = grid.length == 4 ? 2 : 3;
            int minX = side;
            int maxX = -1;
            int minY = side;
            int maxY = -1;
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    if (!grid[y * side + x].isNull()) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0 || maxX - minX + 1 != width || maxY - minY + 1 != height) {
                return false;
            }
            // Vanilla takes a shaped recipe laid out either way round, so both orientations match.
            return cellsMatch(grid, side, minX, minY, false) || cellsMatch(grid, side, minX, minY, true);
        }

        private boolean cellsMatch(ItemData[] grid, int side, int minX, int minY, boolean mirrored) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Ingredient cell = cells[y * width + (mirrored ? width - 1 - x : x)];
                    ItemData item = grid[(minY + y) * side + minX + x];
                    if (cell == null ? !item.isNull() : (item.isNull() || !cell.matches(item))) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        RecipeData wire() {
            List<ItemDescriptorWithCount> ingredients = new ArrayList<>();
            for (Ingredient cell : cells) {
                ingredients.add(cell == null ? ItemDescriptorWithCount.EMPTY : cell.wire());
            }
            return ShapedRecipeData.shaped(id(), width, height, ingredients, List.of(result()),
                    UUID.nameUUIDFromBytes(id().getBytes(StandardCharsets.UTF_8)), "crafting_table",
                    priority, netId(), true, RecipeUnlockingRequirement.INVALID);
        }
    }

    /** A shapeless recipe: a pinch of ingredients in any arrangement. */
    static final class Shapeless extends Recipe {

        private final List<Ingredient> ingredients;
        private final int priority;

        Shapeless(int netId, String id, List<Ingredient> ingredients, ItemData result, int priority) {
            super(netId, id, result);
            this.ingredients = ingredients;
            this.priority = priority;
        }

        @Override
        public boolean matches(ItemData[] grid) {
            List<Ingredient> left = new ArrayList<>(ingredients);
            for (ItemData item : grid) {
                if (item.isNull()) {
                    continue;
                }
                Ingredient found = null;
                for (Ingredient ingredient : left) {
                    if (ingredient.matches(item)) {
                        found = ingredient;
                        break;
                    }
                }
                if (found == null) {
                    return false; // something in the grid the recipe doesn't want
                }
                left.remove(found);
            }
            return left.isEmpty();
        }

        @Override
        RecipeData wire() {
            List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
            for (Ingredient ingredient : ingredients) {
                descriptors.add(ingredient.wire());
            }
            return ShapelessRecipeData.shapeless(id(), descriptors, List.of(result()),
                    UUID.nameUUIDFromBytes(id().getBytes(StandardCharsets.UTF_8)), "crafting_table",
                    priority, netId(), RecipeUnlockingRequirement.INVALID);
        }
    }

    /**
     * What one grid cell of a recipe wants: one of a set of items (named, or everything
     * under a tag), optionally of a certain kind by its damage value - 32767 meaning any.
     */
    private record Ingredient(List<String> names, String tag, int aux) {

        boolean matches(ItemData item) {
            return names.contains(item.getDefinition().getIdentifier())
                    && (aux == ANY_AUX || item.getDamage() == aux);
        }

        ItemDescriptorWithCount wire() {
            if (tag != null) {
                return new ItemDescriptorWithCount(new ItemTagDescriptor(tag), 1);
            }
            return new ItemDescriptorWithCount(new DefaultDescriptor(definition(names.get(0)), aux, ""), 1);
        }
    }
}
