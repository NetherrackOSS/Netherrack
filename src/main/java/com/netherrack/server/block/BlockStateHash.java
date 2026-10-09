package com.netherrack.server.block;

import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.TreeMap;

/**
 * Computes a block state's network id under Bedrock's hashed block network ids (see
 * StartGame's blockNetworkIdsHashed): the 32-bit FNV-1a hash of the state as
 * little-endian NBT, {"name": ..., "states": {...}}, with the states sorted by name and no
 * version field. The client hashes its own block states the same way, so both sides agree
 * on every block without a palette being sent.
 */
public final class BlockStateHash {

    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private BlockStateHash() {
    }

    /** @param state a {"name": ..., "states": {...}} compound */
    public static int of(NbtMap state) {
        NbtMapBuilder sortedStates = NbtMap.builder();
        new TreeMap<>(state.getCompound("states", NbtMap.EMPTY)).forEach(sortedStates::put);
        NbtMap hashed = NbtMap.builder()
                .putString("name", state.getString("name"))
                .putCompound("states", sortedStates.build())
                .build();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NBTOutputStream writer = NbtUtils.createWriterLE(bytes)) {
            writer.writeTag(hashed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int hash = FNV_OFFSET_BASIS;
        for (byte b : bytes.toByteArray()) {
            hash ^= b & 0xff;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
