package com.netherrack.server.network;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.BlockItems;
import com.netherrack.server.block.BlockStateHash;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla data Netherrack needs to speak Bedrock's protocol correctly, sourced from
 * PMMP's BedrockData project (https://github.com/pmmp/BedrockData, CC0). The codec this
 * project targets is Bedrock_v2225 (1.26.60) - that has to match the real client's wire
 * protocol exactly, it's not a free choice. The bundled data below is NOT an exact match
 * for that version though, since BedrockData doesn't have a tag that far ahead yet:
 * <p>
 * - entity_identifiers.nbt: from the "bedrock-1.26.30" tag, the closest available. A
 *   couple of patches behind 1.26.50, so some newer entities may be missing.
 * - biome_definitions.nbt: still from the older "bedrock-1.21.70" tag - BedrockData
 *   stopped shipping a pre-built raw-NBT biome file in more recent tags (JSON only), and
 *   converting that JSON into the right NBT shape hasn't been done yet. This one's the
 *   most likely to be visibly stale of the three. The protocol no longer takes this NBT
 *   as-is either (CompressedBiomeDefinitionListPacket was dropped from the codec), so it's
 *   converted into the structured BiomeDefinitionListPacket form on load.
 * <p>
 * The item, creative inventory and data-driven block data is from Dragonfly instead
 * (https://github.com/df-mc/dragonfly, MIT - see data/LICENSE-dragonfly.txt), at commit
 * 4c7b5074be94, which targeted 1.26.50 exactly at the time - now one step behind 1.26.60
 * too, the same way the two files above are. Converted from Dragonfly's
 * vanilla_items.nbt, creative_items.nbt, block_states.nbt and data_driven_blocks.nbt into
 * the same single-compound network NBT form as the files above:
 * <p>
 * - item_definitions.nbt: {"items": [{name, id, version, component_based, max_stack,
 *   components?, block?}]} - every vanilla item's network id, version, stack size,
 *   components (for data-driven items) and, for block items, the block state it places
 *   ({name, states}). Clients no longer have a built-in item list; this is what fills it.
 * - creative_items.nbt: {"groups": [{category, name, icon, icon_block?}], "items":
 *   [{item, meta, group, block?, nbt?}]} - the creative inventory, in display order.
 * - data_driven_blocks.nbt: {"blocks": [{name, properties}]} - the vanilla blocks that
 *   are defined in the vanilla behavior pack rather than built into the client (the wool
 *   and concrete slabs and stairs, shelf mushrooms...). Behavior packs are the server's,
 *   so the client only knows these blocks if StartGame sends their definitions.
 * <p>
 * block_palette.nbt: every vanilla block state of 1.26.50 exactly (now also one step behind
 * 1.26.60), from CloudburstMC's Data project (https://github.com/CloudburstMC/Data,
 * commit 659ce1e2, Apache 2.0 - see
 * data/LICENSE-CloudburstMC.txt), unchanged: gzipped big-endian NBT, {"blocks": [{name,
 * states, version, network_id...}]}. It's what says which states exist - a fence's
 * connections, stairs' corners - and each block's default state. StartGame's blockPalette
 * field is set from it, though that field isn't actually transmitted at all for this
 * protocol version (confirmed by reading the active serializer chain) - harmless to still
 * set, just inert.
 * <p>
 * Block identity on the wire uses Bedrock's hashed block network IDs scheme (see
 * Block.getBlockStateHash()), not ordinal positions in this palette - that's why
 * getRuntimeId() below is unused.
 */
public final class VanillaData {

    private static final String BLOCK_STATES_RESOURCE = "/data/block_palette.nbt";
    private static final String ENTITY_IDENTIFIERS_RESOURCE = "/data/entity_identifiers.nbt";
    private static final String BIOME_DEFINITIONS_RESOURCE = "/data/biome_definitions.nbt";
    private static final String ITEM_DEFINITIONS_RESOURCE = "/data/item_definitions.nbt";
    private static final String CREATIVE_ITEMS_RESOURCE = "/data/creative_items.nbt";
    private static final String DATA_DRIVEN_BLOCKS_RESOURCE = "/data/data_driven_blocks.nbt";

    private static volatile VanillaData instance;

    private final NbtList<NbtMap> blockPalette;
    private final NbtMap entityIdentifiers;
    private final BiomeDefinitions biomeDefinitions;
    private final List<ItemDefinition> itemDefinitions;
    private final DefinitionRegistry<ItemDefinition> itemRegistry;
    private final Map<Integer, Integer> maxStackByItem = new HashMap<>();
    private final Map<Integer, NbtMap> placedBlockByItem = new HashMap<>();
    private final Map<Integer, Integer> placedBlockHashByItem = new HashMap<>();
    // Every block state known by its network hash, and each block's default state by name.
    private final Map<Integer, NbtMap> blockStatesByHash = new HashMap<>();
    private final Map<String, NbtMap> defaultBlockStates = new HashMap<>();
    private final Map<String, String> itemByPlacedBlock = new HashMap<>();
    private final List<CreativeItemGroup> creativeGroups = new ArrayList<>();
    private final List<CreativeItemData> creativeItems = new ArrayList<>();
    private final List<BlockPropertyData> dataDrivenBlocks = new ArrayList<>();
    private final Map<String, Integer> runtimeIdsByName = new HashMap<>();

    private VanillaData(NbtList<NbtMap> blockPalette, NbtMap entityIdentifiers, BiomeDefinitions biomeDefinitions,
                        NbtMap items, NbtMap creative, NbtMap dataDriven) {
        this.blockPalette = blockPalette;
        this.entityIdentifiers = entityIdentifiers;
        this.biomeDefinitions = biomeDefinitions;
        this.itemDefinitions = toItemDefinitions(items);
        this.itemRegistry = SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(itemDefinitions).build();

        // Several palette entries share a name (one per combination of states); the first
        // stands in as the default, unless the block's item says which state it places.
        for (NbtMap state : blockPalette) {
            blockStatesByHash.put(BlockStateHash.of(state), state);
            defaultBlockStates.putIfAbsent(state.getString("name"), state);
        }
        for (NbtMap item : items.getList("items", NbtType.COMPOUND)) {
            int id = item.getInt("id");
            maxStackByItem.put(id, item.getInt("max_stack"));
            NbtMap block = item.getCompound("block", null);
            if (block != null) {
                int hash = BlockStateHash.of(block);
                placedBlockByItem.put(id, block);
                placedBlockHashByItem.put(id, hash);
                blockStatesByHash.put(hash, block);
                defaultBlockStates.put(block.getString("name"), block);
                // An item named like its block wins over others that place it too.
                String blockName = block.getString("name");
                String itemName = item.getString("name");
                if (!itemByPlacedBlock.containsKey(blockName) || itemName.equals(blockName)) {
                    itemByPlacedBlock.put(blockName, itemName);
                }
            }
        }
        for (NbtMap item : creative.getList("items", NbtType.COMPOUND)) {
            NbtMap block = item.getCompound("block", null);
            if (block != null) {
                blockStatesByHash.putIfAbsent(BlockStateHash.of(block), block);
            }
        }
        for (NbtMap group : creative.getList("groups", NbtType.COMPOUND)) {
            creativeGroups.add(new CreativeItemGroup(
                    CreativeItemCategory.values()[group.getInt("category")],
                    group.getString("name"),
                    creativeStack(group.getString("icon"), group.getCompound("icon_block", null), 0, null, 1)));
        }
        // The client takes items from the creative inventory by these network ids, from 1.
        for (NbtMap item : creative.getList("items", NbtType.COMPOUND)) {
            ItemData stack = creativeStack(item.getString("item"), item.getCompound("block", null),
                    item.getInt("meta"), item.getCompound("nbt", null), 1);
            if (!stack.isNull()) {
                creativeItems.add(new CreativeItemData(stack, creativeItems.size() + 1, item.getInt("group")));
            }
        }
        for (NbtMap block : dataDriven.getList("blocks", NbtType.COMPOUND)) {
            dataDrivenBlocks.add(new BlockPropertyData(block.getString("name"), block.getCompound("properties")));
        }

        for (int i = 0; i < blockPalette.size(); i++) {
            String name = blockPalette.get(i).getString("name");
            // Several entries can share a name (different block states); the first one
            // is good enough for blocks we don't have any states for, like ours.
            runtimeIdsByName.putIfAbsent(name, i);
        }
    }

    public static VanillaData get() {
        VanillaData result = instance;
        if (result == null) {
            synchronized (VanillaData.class) {
                result = instance;
                if (result == null) {
                    instance = result = load();
                }
            }
        }
        return result;
    }

    public NbtList<NbtMap> getBlockPalette() {
        return blockPalette;
    }

    public NbtMap getEntityIdentifiers() {
        return entityIdentifiers;
    }

    public BiomeDefinitions getBiomeDefinitions() {
        return biomeDefinitions;
    }

    /** Every vanilla item, in the form ItemComponentPacket sends them to the client. */
    public List<ItemDefinition> getItemDefinitions() {
        return itemDefinitions;
    }

    /**
     * Some of an item by name, carrying the block it places if it's a block item. Air if
     * there's no such item.
     */
    public ItemData item(String name, int count) {
        ItemDefinition definition = itemRegistry.getDefinition(name);
        if (definition == null) {
            return ItemData.AIR;
        }
        ItemData.Builder builder = ItemData.builder().definition(definition).count(count);
        Integer blockHash = placedBlockHashByItem.get(definition.getRuntimeId());
        if (blockHash != null) {
            builder.blockDefinition(HashedBlockDefinitions.of(blockHash));
        }
        return builder.build();
    }

    /**
     * Some of the item named the same as a block, e.g. for a broken block's drop. Air if
     * the block has no item of its own.
     */
    public ItemData blockItem(Block block, int count) {
        return item(block.getIdentifier(), count);
    }

    /** The most of an item one stack holds: 64 for most, 16 or 1 for some. */
    public int maxStackSize(ItemData item) {
        return maxStackByItem.getOrDefault(item.getDefinition().getRuntimeId(), 64);
    }

    /**
     * The block state an item places ({"name", "states"}): the one the stack carries, such
     * as which color of wool or a slab's half, or else the item's usual one. Null if it
     * isn't a block item.
     */
    public NbtMap placedBlock(ItemData item) {
        if (item.getBlockDefinition() != null) {
            NbtMap carried = blockStatesByHash.get(item.getBlockDefinition().getRuntimeId());
            if (carried != null) {
                return carried;
            }
        }
        NbtMap usual = placedBlockByItem.get(item.getDefinition().getRuntimeId());
        if (usual != null) {
            return usual;
        }
        // Doors, beds, seeds, signs and the like don't say in the item data which block they place.
        String block = BlockItems.blockPlacedBy(item.getDefinition().getIdentifier());
        return block != null ? defaultBlockStates.get(block) : null;
    }

    /**
     * The name of the item that places a block, e.g. "minecraft:repeater" for
     * "minecraft:unpowered_repeater" - the block's own name if no item says it places it.
     */
    public String itemPlacing(String blockName) {
        String other = BlockItems.itemPlacing(blockName);
        if (other != null) {
            return other;
        }
        return itemByPlacedBlock.getOrDefault(blockName, blockName);
    }

    /** The block state ({"name", "states"}) with this network hash, or null if it's none this version knows. */
    public NbtMap blockState(int hash) {
        return blockStatesByHash.get(hash);
    }

    /**
     * A block's default state by its identifier, e.g. for the layers of a flat world, or
     * just the name with no states if it's no block this version knows.
     */
    public NbtMap defaultBlockState(String name) {
        NbtMap state = defaultBlockStates.get(name);
        if (state != null) {
            return state;
        }
        return NbtMap.builder().putString("name", name).putCompound("states", NbtMap.EMPTY).build();
    }

    public List<CreativeItemGroup> getCreativeGroups() {
        return creativeGroups;
    }

    public List<CreativeItemData> getCreativeItems() {
        return creativeItems;
    }

    /** A full stack of the creative inventory item with this network id, or null if there's none. */
    public ItemData creativeItem(int netId) {
        if (netId < 1 || netId > creativeItems.size()) {
            return null;
        }
        ItemData item = creativeItems.get(netId - 1).getItem();
        return item.toBuilder().count(maxStackSize(item)).build();
    }

    /** The definitions of the vanilla blocks the client only knows if StartGame sends them. */
    public List<BlockPropertyData> getDataDrivenBlocks() {
        return dataDrivenBlocks;
    }

    private ItemData creativeStack(String name, NbtMap block, int meta, NbtMap nbt, int count) {
        ItemDefinition definition = itemRegistry.getDefinition(name);
        if (definition == null) {
            return ItemData.AIR;
        }
        ItemData.Builder builder = ItemData.builder().definition(definition).damage(meta).count(count);
        if (block != null) {
            builder.blockDefinition(HashedBlockDefinitions.of(BlockStateHash.of(block)));
        }
        if (nbt != null) {
            builder.tag(nbt);
        }
        return builder.build();
    }

    /** The same items as a lookup the codec uses to decode item stacks in client packets. */
    public DefinitionRegistry<ItemDefinition> getItemRegistry() {
        return itemRegistry;
    }

    /**
     * The network runtime ID for a block identifier (e.g. "minecraft:grass"), i.e. its
     * position in the block palette. Falls back to air's runtime ID (logging a warning)
     * if the identifier isn't found, rather than throwing mid-chunk-encode.
     */
    public int getRuntimeId(String identifier) {
        Integer id = runtimeIdsByName.get(identifier);
        if (id != null) {
            return id;
        }
        Logger.warn("No block palette entry for \"" + identifier + "\", falling back to air.");
        return runtimeIdsByName.getOrDefault("minecraft:air", 0);
    }

    private static VanillaData load() {
        NbtList<NbtMap> blockPalette = readPalette(BLOCK_STATES_RESOURCE);
        NbtMap entityIdentifiers = readSingleCompound(ENTITY_IDENTIFIERS_RESOURCE);
        BiomeDefinitions biomeDefinitions = toBiomeDefinitions(readSingleCompound(BIOME_DEFINITIONS_RESOURCE));
        return new VanillaData(blockPalette, entityIdentifiers, biomeDefinitions,
                readSingleCompound(ITEM_DEFINITIONS_RESOURCE),
                readSingleCompound(CREATIVE_ITEMS_RESOURCE),
                readSingleCompound(DATA_DRIVEN_BLOCKS_RESOURCE));
    }

    private static List<ItemDefinition> toItemDefinitions(NbtMap root) {
        List<ItemDefinition> definitions = new ArrayList<>();
        for (NbtMap item : root.getList("items", NbtType.COMPOUND)) {
            definitions.add(new SimpleItemDefinition(
                    item.getString("name"),
                    item.getInt("id"),
                    ItemVersion.from(item.getInt("version")),
                    item.getBoolean("component_based"),
                    item.getCompound("components", NbtMap.EMPTY)));
        }
        return List.copyOf(definitions);
    }

    /**
     * Converts the legacy biome NBT (one compound per biome name) into the structured form
     * BiomeDefinitionListPacket carries. The id is left null: that field is only for custom
     * biomes, the client already knows vanilla biomes' numeric ids by name. The legacy
     * "height" field is what the structured form calls "scale".
     */
    private static BiomeDefinitions toBiomeDefinitions(NbtMap legacy) {
        Map<String, BiomeDefinitionData> definitions = new LinkedHashMap<>();
        legacy.forEach((name, value) -> {
            NbtMap biome = (NbtMap) value;
            Color waterColor = new Color(
                    biome.getFloat("waterColorR"), biome.getFloat("waterColorG"),
                    biome.getFloat("waterColorB"), biome.getFloat("waterColorA"));
            definitions.put(name, new BiomeDefinitionData(
                    null,
                    biome.getFloat("temperature"),
                    biome.getFloat("downfall"),
                    biome.getFloat("red_spores"),
                    biome.getFloat("blue_spores"),
                    biome.getFloat("ash"),
                    biome.getFloat("white_ash"),
                    0f, // foliage snow: not in the legacy data
                    biome.getFloat("depth"),
                    biome.getFloat("height"),
                    waterColor,
                    biome.getBoolean("rain"),
                    biome.getList("tags", NbtType.STRING),
                    null));
        });
        return new BiomeDefinitions(definitions);
    }

    @SuppressWarnings("unchecked")
    private static NbtMap readSingleCompound(String resource) {
        try (InputStream in = openResource(resource);
             var reader = NbtUtils.createNetworkReader(in)) {
            return (NbtMap) reader.readTag();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resource, e);
        }
    }

    /**
     * Every block state in the palette, as {"name", "states", "version"} - the palette's
     * other fields (its own ids and hashes) are left out.
     */
    private static NbtList<NbtMap> readPalette(String resource) {
        List<NbtMap> states = new ArrayList<>();
        try (var reader = NbtUtils.createGZIPReader(openResource(resource))) {
            NbtMap root = (NbtMap) reader.readTag();
            for (NbtMap entry : root.getList("blocks", NbtType.COMPOUND)) {
                states.add(NbtMap.builder()
                        .putString("name", entry.getString("name"))
                        .putCompound("states", entry.getCompound("states"))
                        .putInt("version", entry.getInt("version"))
                        .build());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resource, e);
        }
        return new NbtList<>(NbtType.COMPOUND, states);
    }

    private static InputStream openResource(String resource) {
        InputStream in = VanillaData.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing bundled resource: " + resource);
        }
        return in;
    }
}
