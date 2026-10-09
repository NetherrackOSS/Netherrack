package com.netherrack.server.network;

import com.netherrack.server.block.Block;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla data Netherrack needs to speak Bedrock's protocol correctly, sourced from
 * PMMP's BedrockData project (https://github.com/pmmp/BedrockData, CC0). The codec this
 * project targets is Bedrock_v2193 (1.26.50) - that has to match the real client's wire
 * protocol exactly, it's not a free choice. The bundled data below is NOT an exact match
 * for that version though, since BedrockData doesn't have a tag that far ahead yet:
 * <p>
 * - canonical_block_states.nbt / entity_identifiers.nbt: from the "bedrock-1.26.30" tag,
 *   the closest available. A couple of patches behind 1.26.50, so some newer blocks or
 *   entities may be missing or show as the unknown/error texture client-side.
 * - biome_definitions.nbt: still from the older "bedrock-1.21.70" tag - BedrockData
 *   stopped shipping a pre-built raw-NBT biome file in more recent tags (JSON only), and
 *   converting that JSON into the right NBT shape hasn't been done yet. This one's the
 *   most likely to be visibly stale of the three. The protocol no longer takes this NBT
 *   as-is either (CompressedBiomeDefinitionListPacket was dropped from the codec), so it's
 *   converted into the structured BiomeDefinitionListPacket form on load.
 * - item_definitions.nbt: every vanilla item's network id, version and (for data-driven
 *   items) components, converted from the "bedrock-1.26.30" tag's required_item_list.json
 *   into the same network NBT form as the files above ({"items": [{name, id, version,
 *   component_based, components?}]}), so it can be read without a JSON library. Clients
 *   no longer have a built-in item list; this is what fills it.
 * <p>
 * canonical_block_states.nbt specifically: every vanilla block state, in a fixed order.
 * StartGame's blockPalette field is set from this, though it turns out that field isn't
 * actually transmitted at all for this protocol version (confirmed by reading the active
 * serializer chain) - harmless to still set, just inert.
 * <p>
 * Block identity on the wire now uses Bedrock's hashed block network IDs scheme instead
 * (see Block.getBlockStateHash()), not ordinal positions in this palette - that's why
 * getRuntimeId() below is unused. Hashing avoids needing this file's ordering to exactly
 * match the real client's version-for-version, which ordinal IDs required and which an
 * older BedrockData snapshot (1.26.30 vs the client's actual 1.26.50) couldn't guarantee.
 */
public final class VanillaData {

    private static final String BLOCK_STATES_RESOURCE = "/data/canonical_block_states.nbt";
    private static final String ENTITY_IDENTIFIERS_RESOURCE = "/data/entity_identifiers.nbt";
    private static final String BIOME_DEFINITIONS_RESOURCE = "/data/biome_definitions.nbt";
    private static final String ITEM_DEFINITIONS_RESOURCE = "/data/item_definitions.nbt";

    private static volatile VanillaData instance;

    private final NbtList<NbtMap> blockPalette;
    private final NbtMap entityIdentifiers;
    private final BiomeDefinitions biomeDefinitions;
    private final List<ItemDefinition> itemDefinitions;
    private final DefinitionRegistry<ItemDefinition> itemRegistry;
    private final Map<String, Integer> runtimeIdsByName = new HashMap<>();

    private VanillaData(NbtList<NbtMap> blockPalette, NbtMap entityIdentifiers, BiomeDefinitions biomeDefinitions,
                        List<ItemDefinition> itemDefinitions) {
        this.blockPalette = blockPalette;
        this.entityIdentifiers = entityIdentifiers;
        this.biomeDefinitions = biomeDefinitions;
        this.itemDefinitions = itemDefinitions;
        this.itemRegistry = SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(itemDefinitions).build();

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

    /** One or more of the item that places a block, e.g. for a broken block's drop. */
    public ItemData blockItem(Block block, int count) {
        return ItemData.builder()
                .definition(itemRegistry.getDefinition(block.getIdentifier()))
                .blockDefinition(HashedBlockDefinitions.of(block.getBlockStateHash()))
                .count(count)
                .build();
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
        NbtList<NbtMap> blockPalette = readRepeatedCompounds(BLOCK_STATES_RESOURCE);
        NbtMap entityIdentifiers = readSingleCompound(ENTITY_IDENTIFIERS_RESOURCE);
        BiomeDefinitions biomeDefinitions = toBiomeDefinitions(readSingleCompound(BIOME_DEFINITIONS_RESOURCE));
        List<ItemDefinition> itemDefinitions = toItemDefinitions(readSingleCompound(ITEM_DEFINITIONS_RESOURCE));
        return new VanillaData(blockPalette, entityIdentifiers, biomeDefinitions, itemDefinitions);
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

    @SuppressWarnings("unchecked")
    private static NbtList<NbtMap> readRepeatedCompounds(String resource) {
        List<NbtMap> entries = new ArrayList<>();
        try (PushbackInputStream in = new PushbackInputStream(openResource(resource));
             var reader = NbtUtils.createNetworkReader(in)) {
            int next;
            while ((next = in.read()) != -1) {
                in.unread(next);
                entries.add((NbtMap) reader.readTag());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resource, e);
        }
        return new NbtList<>(NbtType.COMPOUND, entries);
    }

    private static InputStream openResource(String resource) {
        InputStream in = VanillaData.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing bundled resource: " + resource);
        }
        return in;
    }
}
