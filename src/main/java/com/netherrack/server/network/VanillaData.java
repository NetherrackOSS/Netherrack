package com.netherrack.server.network;

import com.netherrack.server.util.Logger;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
 *   most likely to be visibly stale of the three.
 * <p>
 * canonical_block_states.nbt specifically: every vanilla block state, in a fixed order.
 * The index a block ends up at here IS its network "runtime ID" - both server and client
 * build the same table from the same file, so they agree on IDs without the server having
 * to send one per block.
 * <p>
 * Netherrack only has two blocks implemented, but the full palette still has to be sent
 * (and in the exact order the client expects) since runtime IDs are positions in it, not
 * ids we get to choose ourselves.
 */
public final class VanillaData {

    private static final String BLOCK_STATES_RESOURCE = "/data/canonical_block_states.nbt";
    private static final String ENTITY_IDENTIFIERS_RESOURCE = "/data/entity_identifiers.nbt";
    private static final String BIOME_DEFINITIONS_RESOURCE = "/data/biome_definitions.nbt";

    private static volatile VanillaData instance;

    private final NbtList<NbtMap> blockPalette;
    private final NbtMap entityIdentifiers;
    private final NbtMap biomeDefinitions;
    private final Map<String, Integer> runtimeIdsByName = new HashMap<>();

    private VanillaData(NbtList<NbtMap> blockPalette, NbtMap entityIdentifiers, NbtMap biomeDefinitions) {
        this.blockPalette = blockPalette;
        this.entityIdentifiers = entityIdentifiers;
        this.biomeDefinitions = biomeDefinitions;

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

    public NbtMap getBiomeDefinitions() {
        return biomeDefinitions;
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
        NbtMap biomeDefinitions = readSingleCompound(BIOME_DEFINITIONS_RESOURCE);
        return new VanillaData(blockPalette, entityIdentifiers, biomeDefinitions);
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
