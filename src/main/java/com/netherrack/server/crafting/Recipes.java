package com.netherrack.server.crafting;

import com.netherrack.server.network.VanillaData;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.TrimMaterial;
import org.cloudburstmc.protocol.bedrock.data.TrimPattern;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.CraftingDataType;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.MultiRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The crafting recipes, taken as they are from CloudburstMC's data repository
 * (https://github.com/CloudburstMC/Data, bundled as /data/recipes.json for this same
 * protocol version): every recipe the game knows at this version - the crafting table's
 * and the inventory screen's, but also the furnace, campfire, stonecutter, cartography
 * and smithing ones the other screens use, and the built-in multi recipes like map
 * cloning. The whole set goes out, because the client builds every screen's recipe list
 * from this one packet: with cleanRecipes it has dropped its own built-in copy, and a
 * partial set leaves the missing sections with nothing at all (the client chokes on a
 * world without furnace recipes, for one).
 * <p>
 * Only the crafting-table recipes are carried further than the packet: when the client
 * asks to craft, it names the recipe by network id and the server makes sure the grid
 * really holds what that recipe needs. The rest ride along for their screens, which
 * Netherrack doesn't have yet.
 * <p>
 * The recipes are numbered from 1 in file order; the ids need only be unique per packet,
 * which is exactly how every other server software numbers them.
 */
public final class Recipes {

    private static final String RESOURCE = "/data/recipes.json";

    /** A damage value that means "any of this item, however it's colored or charged". */
    private static final int ANY_AUX = 32767;

    /** The tag the smithing recipes carry, as the bundled file has it in their block field. */
    private static final String SMITHING_TABLE = "smithing_table";

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

    /**
     * The smithing screen's registry of armor trims, which must reach the client before
     * the recipes packet: the client resolves the trim recipe's item tags against these
     * lists as it builds its recipe book, and an empty registry at that moment is one of
     * the things it rejects the whole join over. The values are the ones BDS itself sends
     * at this version, pattern and material by pattern and material, in the order it
     * sends them.
     */
    public TrimDataPacket trimPacket() {
        TrimDataPacket trims = new TrimDataPacket();
        String[] patterns = {"ward", "sentry", "snout", "dune", "spire", "tide", "wild", "rib",
                "coast", "shaper", "eye", "vex", "silence", "wayfinder", "raiser", "host",
                "flow", "bolt"};
        for (String pattern : patterns) {
            trims.getPatterns().add(new TrimPattern(
                    "minecraft:" + pattern + "_armor_trim_smithing_template", pattern));
        }
        // The colors are the Bedrock format codes the game's trim palette uses, as BDS
        // carries them - they tint the trim in tooltips, nothing else.
        trims.getMaterials().add(new TrimMaterial("quartz", "§h", "minecraft:quartz"));
        trims.getMaterials().add(new TrimMaterial("iron", "§i", "minecraft:iron_ingot"));
        trims.getMaterials().add(new TrimMaterial("netherite", "§j", "minecraft:netherite_ingot"));
        trims.getMaterials().add(new TrimMaterial("redstone", "§m", "minecraft:redstone"));
        trims.getMaterials().add(new TrimMaterial("copper", "§n", "minecraft:copper_ingot"));
        trims.getMaterials().add(new TrimMaterial("gold", "§p", "minecraft:gold_ingot"));
        trims.getMaterials().add(new TrimMaterial("emerald", "§q", "minecraft:emerald"));
        trims.getMaterials().add(new TrimMaterial("diamond", "§s", "minecraft:diamond"));
        trims.getMaterials().add(new TrimMaterial("lapis", "§t", "minecraft:lapis_lazuli"));
        trims.getMaterials().add(new TrimMaterial("amethyst", "§u", "minecraft:amethyst_shard"));
        trims.getMaterials().add(new TrimMaterial("resin", "§v", "minecraft:resin_brick"));
        return trims;
    }

    /** The recipe a craft request names by network id, or null if there is no such recipe. */
    public Recipe byNetId(int netId) {
        return byNetId.get(netId);
    }

    private Recipes() {
        long start = System.currentTimeMillis();
        int shaped = 0;
        int shapeless = 0;
        int containers = 0;
        int multi = 0;
        int smithing = 0;
        int skipped = 0;
        Set<String> usedIds = new HashSet<>();

        Map<String, Object> root;
        try {
            root = JsonUtil.parseJson(read());
        } catch (JoseException e) {
            throw new IllegalStateException("Failed to parse " + RESOURCE, e);
        }
        // Recipes are numbered from 1, in file order. The file's own "type" numbers are
        // the CraftingDataType ordinals, which is also the list each kind goes out in:
        // shaped, shapeless, multi, container-preserving shapeless, and the two smithing
        // kinds. The furnace, campfire, stonecutter and cartography recipes ride in the
        // shapeless list, tagged by the crafting block they belong to.
        int netId = 0;
        for (Object object : (List<?>) root.get("recipes")) {
            Map<String, Object> entry = cast(object);
            int type = ((Number) entry.get("type")).intValue();
            netId++;
            if (type == CraftingDataType.SHAPED.ordinal()) {
                if (shaped(entry, netId)) {
                    shaped++;
                } else {
                    skipped++;
                }
            } else if (type == CraftingDataType.SHAPELESS.ordinal()) {
                if (shapeless(entry, netId)) {
                    shapeless++;
                } else {
                    skipped++;
                }
            } else if (type == CraftingDataType.SHULKER_BOX.ordinal()) {
                if (container(entry, netId)) {
                    containers++;
                } else {
                    skipped++;
                }
            } else if (type == CraftingDataType.MULTI.ordinal()) {
                packet.getMultiData().add(MultiRecipeData.of(
                        UUID.fromString((String) entry.get("uuid")), netId));
                multi++;
            } else if (type == CraftingDataType.SMITHING_TRANSFORM.ordinal()) {
                if (smithingTransform(entry, netId, usedIds)) {
                    smithing++;
                } else {
                    skipped++;
                }
            } else if (type == CraftingDataType.SMITHING_TRIM.ordinal()) {
                if (smithingTrim(entry, netId, usedIds)) {
                    smithing++;
                } else {
                    skipped++;
                }
            } else {
                skipped++;
            }
        }
        Logger.info("Loaded " + (shaped + shapeless + containers + multi + smithing) + " crafting recipes ("
                + shaped + " shaped, " + shapeless + " shapeless, " + containers + " container-preserving, "
                + multi + " multi, " + smithing + " smithing)"
                + (skipped > 0 ? ", " + skipped + " skipped, for items this version doesn't have" : "")
                + " in " + (System.currentTimeMillis() - start) + "ms");
        // The client has its own built-in copy of vanilla's recipes, numbered from 1 just
        // like these are. Left alone, the two lists merge and every network id collides -
        // the client would craft with an id that means a different recipe here, and every
        // craft would be refused. This flag tells it to drop its own list and use ours.
        packet.setCleanRecipes(true);
    }

    /** A shaped recipe: the pattern in the file, both as the packet carries it and as the server checks it. */
    private boolean shaped(Map<String, Object> entry, int netId) {
        String id = (String) entry.get("id");
        ItemData result = result(entry);
        if (result == null) {
            return false;
        }
        List<String> shape = (List<String>) entry.get("shape");
        int height = shape.size();
        int width = shape.get(0).length();

        // The pattern flattened row by row, an empty descriptor where the shape has a space.
        List<ItemDescriptorWithCount> ingredients = new ArrayList<>();
        Map<String, Ingredient> keys = new LinkedHashMap<>();
        for (Map.Entry<String, Object> letter : cast(entry.get("input")).entrySet()) {
            Ingredient ingredient = ingredient(cast(letter.getValue()));
            if (ingredient == null) {
                return false; // an ingredient no item of this version matches
            }
            keys.put(letter.getKey(), ingredient);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char letter = shape.get(y).charAt(x);
                ingredients.add(letter == ' ' ? ItemDescriptorWithCount.EMPTY : keys.get(String.valueOf(letter)).wire());
            }
        }

        Recipe server = null;
        String block = (String) entry.get("block");
        if ("crafting_table".equals(block) || "deprecated".equals(block)) {
            // The crafting_table ones are the ordinary recipes; the deprecated ones carry
            // recipes nothing else lists (the barrel, for one) and are just as valid. Only
            // these can be asked for over a grid, so only these get a server-side check.
            Ingredient[] cells = new Ingredient[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    char letter = shape.get(y).charAt(x);
                    if (letter != ' ') {
                        cells[y * width + x] = keys.get(String.valueOf(letter));
                    }
                }
            }
            server = new Shaped(netId, id, width, height, cells, result);
            byNetId.put(netId, server);
        }
        packet.getShapedData().add(ShapedRecipeData.shaped(id, width, height, ingredients, List.of(result),
                uuid(id, netId), block, priority(entry), netId, true, RecipeUnlockingRequirement.INVALID));
        return true;
    }

    /** A shapeless recipe: a pinch of ingredients in any arrangement. */
    private boolean shapeless(Map<String, Object> entry, int netId) {
        String id = (String) entry.get("id");
        ItemData result = result(entry);
        if (result == null) {
            return false;
        }
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        for (Object object : (List<?>) entry.get("input")) {
            ItemDescriptorWithCount descriptor = descriptor(cast(object));
            if (descriptor == null) {
                return false; // an ingredient no item of this version matches
            }
            descriptors.add(descriptor);
        }

        String block = (String) entry.get("block");
        if ("crafting_table".equals(block) || "deprecated".equals(block)) {
            // Only crafting-table recipes can be asked for over a grid; the furnace,
            // campfire, stonecutter and cartography ones ride along for their screens.
            List<Ingredient> ingredients = new ArrayList<>();
            for (Object object : (List<?>) entry.get("input")) {
                Ingredient ingredient = ingredient(cast(object));
                if (ingredient == null) {
                    return false;
                }
                ingredients.add(ingredient);
            }
            byNetId.put(netId, new Shapeless(netId, id, ingredients, result));
        }
        packet.getShapelessData().add(ShapelessRecipeData.shapeless(id, descriptors, List.of(result),
                uuid(id, netId), block, priority(entry), netId, RecipeUnlockingRequirement.INVALID));
        return true;
    }

    /**
     * A container-preserving shapeless recipe: a shapeless recipe that carries whatever
     * was inside the item it remakes (dyeing a bundle keeps its contents), sent in the
     * packet's user-data list. The client only uses them over screens Netherrack doesn't
     * have, so they go out for the book and nothing checks them server-side.
     */
    private boolean container(Map<String, Object> entry, int netId) {
        String id = (String) entry.get("id");
        ItemData result = result(entry);
        if (result == null) {
            return false;
        }
        List<ItemDescriptorWithCount> descriptors = new ArrayList<>();
        for (Object object : (List<?>) entry.get("input")) {
            ItemDescriptorWithCount descriptor = descriptor(cast(object));
            if (descriptor == null) {
                return false;
            }
            descriptors.add(descriptor);
        }
        packet.getShapelessUserData().add(ShapelessRecipeData.shulkerBox(id, descriptors, List.of(result),
                uuid(id, netId), (String) entry.get("block"), priority(entry), netId));
        return true;
    }

    /**
     * A smithing-table upgrade: template, base item and material in, the upgraded item
     * out. The bundled file leaves these unnamed, so they get one from what they make -
     * the name only has to be unique within the packet.
     */
    private boolean smithingTransform(Map<String, Object> entry, int netId, Set<String> usedIds) {
        Map<String, Object> input = cast(entry.get("input"));
        ItemDescriptorWithCount template = descriptor(cast(input.get("template")));
        ItemDescriptorWithCount base = descriptor(cast(input.get("base")));
        ItemDescriptorWithCount addition = descriptor(cast(input.get("addition")));
        ItemData result = output(entry);
        if (template == null || base == null || addition == null || result == null) {
            return false;
        }
        String id = uniqueId("minecraft:smithing_netherite_" + smithingBaseName(name(base)), usedIds);
        // BDS tags smithing recipes with no block at all; the bundled file says
        // "smithing_table". The empty tag is what BDS puts on the wire, so keep it.
        packet.getSmithingTransformData().add(SmithingTransformRecipeData.of(id, template, base, addition,
                result, "", netId));
        return true;
    }

    /**
     * A smithing-table armor trim: template, armor and trim material in, no item out. The
     * bundled file leaves these unnamed, so they get the id BDS itself sends. The data
     * class's factory takes base, addition, template in that order even though the wire
     * carries template first - the order of the arguments below is what lands on the wire.
     */
    private boolean smithingTrim(Map<String, Object> entry, int netId, Set<String> usedIds) {
        Map<String, Object> input = cast(entry.get("input"));
        ItemDescriptorWithCount template = descriptor(cast(input.get("template")));
        ItemDescriptorWithCount base = descriptor(cast(input.get("base")));
        ItemDescriptorWithCount addition = descriptor(cast(input.get("addition")));
        if (template == null || base == null || addition == null) {
            return false;
        }
        // The client rejects a smithing trim recipe whose trim registry is empty, so the
        // trim data packet is sent first (see trimPacket). The id is the one BDS itself
        // sends, from its dumped wire data; the bundled file leaves it unnamed.
        String id = uniqueId("minecraft:smithing_armor_trim", usedIds);
        packet.getSmithingTrimData().add(SmithingTrimRecipeData.of(id, base, addition, template,
                "", netId));
        return true;
    }

    /**
     * The wire descriptor for one raw ingredient: the item tag it names, or the item
     * itself with its damage value. The item's real identifier has to go on the wire too,
     * since that's what the client matches against. Null if this version lacks the item.
     */
    private ItemDescriptorWithCount descriptor(Map<String, Object> raw) {
        String tag = (String) raw.get("itemTag");
        if (tag != null) {
            return new ItemDescriptorWithCount(new ItemTagDescriptor(tag), 1);
        }
        ItemDefinition definition = definition((String) raw.get("id"));
        if (definition == null) {
            return null;
        }
        int aux = ((Number) raw.getOrDefault("auxValue", 0)).intValue();
        // The codec writes the identifier string on the wire for DefaultDescriptor, so it
        // has to be the real item name - an empty string leaves the client with an ingredient
        // that matches nothing. The wildcard 32767 from the data file is already the value
        // BDS puts on the wire, so it is passed through unchanged.
        return new ItemDescriptorWithCount(new DefaultDescriptor(definition, aux, definition.getIdentifier()), 1);
    }

    private static String name(ItemDescriptorWithCount descriptor) {
        return descriptor.getDescriptor() instanceof DefaultDescriptor defaultDescriptor
                ? defaultDescriptor.getItemId().getIdentifier().replaceFirst("minecraft:", "")
                : "unknown";
    }

    /**
     * The recipe-id tail for a smithing upgrade's base item: BDS names them after what is
     * being upgraded, without the diamond it starts from - a diamond axe becomes
     * "minecraft:smithing_netherite_axe". Anything else keeps its whole name.
     */
    private static String smithingBaseName(String base) {
        return base.startsWith("diamond_") ? base.substring("diamond_".length()) : base;
    }

    private static String uniqueId(String base, Set<String> usedIds) {
        String id = base;
        for (int suffix = 2; !usedIds.add(id); suffix++) {
            id = base + "_" + suffix;
        }
        return id;
    }

    /**
     * A recipe's own identifier. It has to differ per recipe even where two share a name:
     * the four furnace recipes for one food (furnace, smoker, campfire, soul campfire) all
     * carry the same id, and a client keying its book by this uuid would see one recipe in
     * place of four. BDS numbers them apart the same way.
     */
    private static UUID uuid(String id, int netId) {
        return UUID.nameUUIDFromBytes((id + "#" + netId).getBytes(StandardCharsets.UTF_8));
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
        return resultItem(cast(((List<?>) entry.get("output")).get(0)));
    }

    /** What a recipe makes, from an output that may be a single entry or a one-entry list. */
    private ItemData output(Map<String, Object> entry) {
        Object raw = entry.get("output");
        return resultItem(cast(raw instanceof List<?> list ? list.get(0) : raw));
    }

    /** One raw output entry as an item, or null if this version doesn't have it. */
    private ItemData resultItem(Map<String, Object> raw) {
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
     * Whether the stacks a request consumed cover exactly {@code crafts} sets of this
     * recipe's ingredients. Shift-clicking sends consumes from the inventory rather than
     * from a laid-out grid, so the server checks them against its own idea of the recipe
     * instead of trusting the ingredient list the client included in the request.
     */
    private static boolean consumeMatches(List<Ingredient> expected, List<ItemData> consumed, int crafts) {
        if (crafts < 1 || expected.isEmpty() || consumed == null) {
            return false;
        }
        List<ItemData> left = usable(consumed);
        for (Ingredient ingredient : expected) {
            if (take(left, crafts, ingredient::matches) > 0) {
                return false;
            }
        }
        return left.isEmpty();
    }

    private static List<ItemData> usable(List<ItemData> consumed) {
        List<ItemData> left = new ArrayList<>();
        for (ItemData item : consumed) {
            if (!item.isNull() && item.getCount() > 0) {
                left.add(item);
            }
        }
        return left;
    }

    /** Takes up to {@code needed} matching items, returning what's still needed. */
    private static int take(List<ItemData> slots, int needed, java.util.function.Predicate<ItemData> matcher) {
        for (int i = 0; i < slots.size() && needed > 0; ) {
            ItemData item = slots.get(i);
            if (!matcher.test(item)) {
                i++;
                continue;
            }
            int taken = Math.min(needed, item.getCount());
            needed -= taken;
            if (taken == item.getCount()) {
                slots.remove(i);
            } else {
                slots.set(i, item.toBuilder().count(item.getCount() - taken).build());
                i++;
            }
        }
        return needed;
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
        private final List<Ingredient> ingredients;

        Recipe(int netId, String id, ItemData result, List<Ingredient> ingredients) {
            this.netId = netId;
            this.id = id;
            this.result = result;
            this.ingredients = List.copyOf(ingredients);
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

        /** Whether stacks taken straight from the inventory cover {@code crafts} of this recipe. */
        public boolean matches(List<ItemData> consumed, int crafts) {
            return consumeMatches(ingredients, consumed, crafts);
        }
    }

    /** A shaped recipe: a pattern of ingredients, row by row, with empty cells where the shape has spaces. */
    static final class Shaped extends Recipe {

        private final int width;
        private final int height;
        private final Ingredient[] cells;

        Shaped(int netId, String id, int width, int height, Ingredient[] cells, ItemData result) {
            super(netId, id, result, ingredientList(cells));
            this.width = width;
            this.height = height;
            this.cells = cells;
        }

        private static List<Ingredient> ingredientList(Ingredient[] cells) {
            List<Ingredient> ingredients = new ArrayList<>();
            for (Ingredient cell : cells) {
                if (cell != null) {
                    ingredients.add(cell);
                }
            }
            return ingredients;
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
    }

    /** A shapeless recipe: a pinch of ingredients in any arrangement. */
    static final class Shapeless extends Recipe {

        private final List<Ingredient> ingredients;

        Shapeless(int netId, String id, List<Ingredient> ingredients, ItemData result) {
            super(netId, id, result, ingredients);
            this.ingredients = ingredients;
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
            ItemDefinition definition = definition(names.get(0));
            return new ItemDescriptorWithCount(new DefaultDescriptor(definition, aux, definition.getIdentifier()), 1);
        }
    }
}
