package com.netherrack.server.block;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

/**
 * A block state: a block's namespaced id plus its states (which way stairs face, what
 * color wool is...), the way vanilla stores it in a world's palettes.
 */
public class Block implements BlockID {

    /**
     * The block state version every state of this game version carries in saved worlds:
     * 1.21.60.33's, unchanged up to 1.26.50. Vanilla upgrades states saved with an older
     * version as it loads them.
     */
    public static final int CURRENT_VERSION = 18_168_865;

    private final String identifier;
    private final NbtMap states;
    private final int version;
    private final int blockStateHash;

    /** A block without any states, e.g. "minecraft:cobblestone". */
    public Block(String identifier) {
        this(NbtMap.builder()
                .putString("name", identifier)
                .putCompound("states", NbtMap.EMPTY)
                .build());
    }

    /**
     * @param state the state as vanilla saves it: {"name": ..., "states": {...}, "version": ...}.
     *              Without a version, it's the current one.
     */
    public Block(NbtMap state) {
        this.identifier = state.getString("name");
        this.states = state.getCompound("states", NbtMap.EMPTY);
        this.version = state.getInt("version", CURRENT_VERSION);
        this.blockStateHash = BlockStateHash.of(state);
    }

    /**
     * The Bedrock namespaced identifier, e.g. "minecraft:grass_block".
     */
    public String getIdentifier() {
        return identifier;
    }

    /** The block's states, by name - empty for blocks that have none. */
    public NbtMap getStates() {
        return states;
    }

    /** The block state version this state was saved with, see {@link #CURRENT_VERSION}. */
    public int getVersion() {
        return version;
    }

    /** The state as a world's palette saves it: {"name": ..., "states": {...}, "version": ...}. */
    public NbtMap toNbt() {
        return NbtMap.builder()
                .putString("name", identifier)
                .putCompound("states", states)
                .putInt("version", version)
                .build();
    }

    /**
     * The block's network state hash, as Bedrock's "hashed block network IDs" scheme uses -
     * a value computed from the block's name and states (see BlockStateHash), the same way
     * on both client and server, so they agree on identity without needing to share an
     * identically-ordered palette.
     */
    public int getBlockStateHash() {
        return blockStateHash;
    }

    /** How the block takes to mining: its hardness, the tool that suits it, and whether it's solid. */
    public Mining getMining() {
        return Mining.of(identifier);
    }

    /** Vanilla's hardness value, which sets how long the block takes to break. */
    public double getHardness() {
        return getMining().hardness();
    }

    /** Whether it can be broken outside creative mode - not so for a hardness below 0, like bedrock's. */
    public boolean isBreakable() {
        return getHardness() >= 0;
    }

    /** Whether it's a whole box to bump into, rather than something to walk through, like a flower. */
    public boolean isSolid() {
        return getMining().solid() && !BlockShapes.isReplaceable(this);
    }

    /** A block state property's value: a Byte for a bit, an Integer or a String. Null if it hasn't got that property. */
    public Object getProperty(String property) {
        return states.get(property);
    }

    public boolean hasProperty(String property) {
        return states.containsKey(property);
    }

    /** Whether a bit property is set. */
    public boolean isSet(String property) {
        return states.get(property) instanceof Byte value && value == 1;
    }

    /**
     * The same block with some properties changed, or null if the game has no such state.
     *
     * @param changes property names and values, in turn: a Byte for a bit (see {@link #bit}),
     *                an Integer or a String
     */
    public Block with(Object... changes) {
        NbtMapBuilder changed = states.toBuilder();
        for (int i = 0; i < changes.length; i += 2) {
            changed.put((String) changes[i], changes[i + 1]);
        }
        return Blocks.find(identifier, changed.build());
    }

    /** A bit property's value, which block states keep as a byte. */
    public static Byte bit(boolean set) {
        return (byte) (set ? 1 : 0);
    }

    @Override
    public String toString() {
        return identifier;
    }
}
