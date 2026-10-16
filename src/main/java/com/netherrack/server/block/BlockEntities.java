package com.netherrack.server.block;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.List;

/**
 * The block entities blocks keep when they're placed, for blocks whose looks don't all fit in
 * their state: a bed's color, a banner's color and patterns, and which way a head on the
 * floor is turned. Their items carry the color in their damage value, and a banner its
 * patterns in its tag. The client makes a plain block entity of its own for any other block
 * that needs one, like a chest.
 */
public final class BlockEntities {

    private BlockEntities() {
    }

    /** The block entity a block keeps once placed from this item, or null if it keeps none of its own. */
    public static NbtMap forPlaced(Block block, ItemData item, Placer placer) {
        String name = block.getIdentifier();
        if (name.equals(BlockID.BED)) {
            NbtMapBuilder bed = named("Bed");
            bed.putByte("color", (byte) item.getDamage());
            return bed.build();
        }
        if (name.equals(BlockID.STANDING_BANNER) || name.equals(BlockID.WALL_BANNER)) {
            NbtMapBuilder banner = named("Banner");
            banner.putInt("Base", item.getDamage());
            NbtMap tag = item.getTag();
            if (tag != null) {
                banner.putInt("Type", tag.getInt("Type", 0));
                List<NbtMap> patterns = tag.getList("Patterns", NbtType.COMPOUND, List.of());
                if (!patterns.isEmpty()) {
                    banner.putList("Patterns", NbtType.COMPOUND, patterns);
                }
            }
            return banner.build();
        }
        if (name.endsWith("_head") || name.endsWith("_skull")) {
            // A head on a wall faces out of it; one on the floor turns to the player in sixteen steps.
            boolean onFloor = Integer.valueOf(Face.UP.ordinal()).equals(block.getProperty("facing_direction"));
            int steps = onFloor ? Math.floorMod(Math.round(placer.yaw() * 16f / 360f), 16) : 0;
            NbtMapBuilder skull = named("Skull");
            skull.putFloat("Rotation", steps * 22.5f);
            return skull.build();
        }
        return null;
    }

    /**
     * The item a block drops, given what its block entity kept: a bed's or banner's color,
     * and a banner's patterns. The item as it is for any other block.
     */
    public static ItemData dropFor(ItemData item, NbtMap entity) {
        if (entity == null || item.isNull()) {
            return item;
        }
        switch (entity.getString("id", "")) {
            case "Bed" -> {
                return item.toBuilder().damage(entity.getByte("color")).build();
            }
            case "Banner" -> {
                ItemData.Builder banner = item.toBuilder().damage(entity.getInt("Base"));
                List<NbtMap> patterns = entity.getList("Patterns", NbtType.COMPOUND, List.of());
                int type = entity.getInt("Type", 0);
                if (!patterns.isEmpty() || type != 0) {
                    NbtMapBuilder tag = NbtMap.builder();
                    if (type != 0) {
                        tag.putInt("Type", type);
                    }
                    if (!patterns.isEmpty()) {
                        tag.putList("Patterns", NbtType.COMPOUND, patterns);
                    }
                    banner.tag(tag.build());
                }
                return banner.build();
            }
            default -> {
                return item;
            }
        }
    }

    private static NbtMapBuilder named(String id) {
        NbtMapBuilder entity = NbtMap.builder();
        entity.putString("id", id);
        entity.putBoolean("isMovable", true);
        return entity;
    }
}
