package com.netherrack.server.player;

import com.netherrack.server.block.BlockStateHash;
import com.netherrack.server.block.Blocks;
import com.netherrack.server.network.HashedBlockDefinitions;
import com.netherrack.server.network.VanillaData;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.Chunk;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A player as the world saves them, in vanilla's own layout and names (those of a
 * single-player world's "~local_player"):
 * <ul>
 *   <li>Pos: x, y, z - y at eye height, as players' positions are on the network;</li>
 *   <li>Rotation: yaw, then pitch, in degrees;</li>
 *   <li>PlayerGameMode: the game mode's number (0 survival, 1 creative, 2 adventure, 6 spectator);</li>
 *   <li>SelectedInventorySlot: the hotbar slot they had selected;</li>
 *   <li>Inventory: the 36 main slots, hotbar first, each item with its "Slot";</li>
 *   <li>Armor: helmet, chestplate, leggings, boots;</li>
 *   <li>Offhand: one item;</li>
 *   <li>PlayerUIItems: what was left on a screen - the cursor (UI slot 0), the inventory
 *   screen's crafting grid (28 to 31) and the crafting table's (32 to 40);</li>
 *   <li>Attributes: health, hunger, saturation and exhaustion, each with its "Name", "Base",
 *   "Current" and "Max", as vanilla keeps them;</li>
 *   <li>Air and Fire: the air left, in ticks, and how long the player is still burning.</li>
 * </ul>
 * An item is {"Name", "Count", "Damage", "WasPickedUp"}, plus the "Block" a block item
 * places ({"name", "states", "version"}) and its "tag" (enchantments, a custom name...) if
 * it has them. An empty slot is an item with no name and a count of 0.
 */
public final class PlayerData {

    private PlayerData() {
    }

    /** Everything to save of a player. */
    public static NbtMap save(Player player) {
        PlayerInventory.Contents contents = player.getInventory().getContents();
        Vector3f position = player.getPosition();
        Vector3f rotation = player.getRotation(); // pitch, yaw, head yaw

        List<NbtMap> screen = new ArrayList<>();
        if (!contents.cursor().isNull()) {
            screen.add(item(contents.cursor(), 0));
        }
        for (int i = 0; i < contents.crafting().size(); i++) {
            if (!contents.crafting().get(i).isNull()) {
                screen.add(item(contents.crafting().get(i), PlayerInventory.CRAFTING_FIRST_SLOT + i));
            }
        }
        for (int i = 0; i < contents.table().size(); i++) {
            if (!contents.table().get(i).isNull()) {
                screen.add(item(contents.table().get(i), PlayerInventory.TABLE_FIRST_SLOT + i));
            }
        }

        PlayerSurvival survival = player.getSurvival();
        // A player who saved while dead comes back alive: the death itself isn't saved.
        float health = survival == null || survival.isDead() ? PlayerSurvival.MAX_HEALTH : survival.getHealth();
        int food = survival != null ? survival.getFood() : PlayerSurvival.MAX_FOOD;
        float saturation = survival != null ? survival.getSaturation() : PlayerSurvival.START_SATURATION;
        float exhaustion = survival != null ? survival.getExhaustion() : 0;
        int air = survival != null ? survival.getAir() : PlayerSurvival.MAX_AIR;
        int fire = survival != null ? survival.getFireTicks() : 0;

        return NbtMap.builder()
                .putString("identifier", "minecraft:player")
                .putList("Pos", NbtType.FLOAT, List.of(position.getX(), position.getY(), position.getZ()))
                .putList("Rotation", NbtType.FLOAT, List.of(rotation.getY(), rotation.getX()))
                .putInt("DimensionId", 0)
                .putInt("PlayerGameMode", player.getGameMode().ordinal())
                .putInt("SelectedInventorySlot", player.getSelectedSlot())
                .putList("Inventory", NbtType.COMPOUND, slots(contents.main()))
                .putList("Armor", NbtType.COMPOUND, slots(contents.armor()))
                .putList("Offhand", NbtType.COMPOUND, slots(List.of(contents.offhand())))
                .putList("PlayerUIItems", NbtType.COMPOUND, screen)
                .putList("Attributes", NbtType.COMPOUND, attributes(health, food, saturation, exhaustion))
                .putShort("Air", (short) air)
                .putShort("Fire", (short) fire)
                .build();
    }

    private static List<NbtMap> attributes(float health, int food, float saturation, float exhaustion) {
        return List.of(
                attribute("minecraft:health", PlayerSurvival.MAX_HEALTH, health, PlayerSurvival.MAX_HEALTH),
                attribute("minecraft:player.hunger", PlayerSurvival.MAX_FOOD, food, PlayerSurvival.MAX_FOOD),
                attribute("minecraft:player.saturation", PlayerSurvival.MAX_FOOD, saturation, PlayerSurvival.MAX_FOOD),
                attribute("minecraft:player.exhaustion", 0, exhaustion, 5));
    }

    private static NbtMap attribute(String name, float base, float current, float max) {
        return NbtMap.builder()
                .putString("Name", name)
                .putFloat("Base", base)
                .putFloat("Current", current)
                .putFloat("Max", max)
                .build();
    }

    /**
     * What a returning player gets back.
     *
     * @param position     where they were, at eye height - or null if that's nowhere they can
     *                     be put back (below the world, say), so they start at the spawn instead
     * @param rotation     (pitch, yaw, head yaw)
     * @param gameMode     their game mode, or null to give them the server's default
     * @param selectedSlot the hotbar slot they had selected
     */
    public record Saved(Vector3f position, Vector3f rotation, GameType gameMode, int selectedSlot,
                        PlayerInventory.Contents inventory, Stats stats) {
    }

    /** The survival numbers a returning player gets back. */
    public record Stats(float health, int food, float saturation, float exhaustion, int air, int fireTicks) {

        public static Stats defaults() {
            return new Stats(PlayerSurvival.MAX_HEALTH, PlayerSurvival.MAX_FOOD,
                    PlayerSurvival.START_SATURATION, 0, PlayerSurvival.MAX_AIR, 0);
        }
    }

    public static Saved read(NbtMap data) {
        Vector3f position = null;
        List<Float> pos = data.getList("Pos", NbtType.FLOAT, List.of());
        if (pos.size() == 3 && pos.stream().allMatch(Float::isFinite) && pos.get(1) >= Chunk.MIN_Y) {
            position = Vector3f.from(pos.get(0), pos.get(1), pos.get(2));
        }

        Vector3f rotation = Vector3f.ZERO;
        List<Float> rot = data.getList("Rotation", NbtType.FLOAT, List.of());
        if (rot.size() == 2 && rot.stream().allMatch(Float::isFinite)) {
            rotation = Vector3f.from(rot.get(1), rot.get(0), rot.get(0));
        }

        GameType gameMode = switch (data.getInt("PlayerGameMode", GameType.DEFAULT.ordinal())) {
            case 0 -> GameType.SURVIVAL;
            case 1 -> GameType.CREATIVE;
            case 2 -> GameType.ADVENTURE;
            case 6 -> GameType.SPECTATOR;
            default -> null; // 5 is vanilla's "the world's default"
        };

        ItemData[] main = new ItemData[PlayerInventory.SIZE];
        Arrays.fill(main, ItemData.AIR);
        for (NbtMap entry : data.getList("Inventory", NbtType.COMPOUND, List.of())) {
            int slot = entry.getByte("Slot", (byte) -1);
            if (slot >= 0 && slot < main.length) {
                main[slot] = item(entry);
            }
        }
        List<ItemData> armor = new ArrayList<>();
        for (NbtMap entry : data.getList("Armor", NbtType.COMPOUND, List.of())) {
            armor.add(item(entry));
        }
        List<NbtMap> offhand = data.getList("Offhand", NbtType.COMPOUND, List.of());
        ItemData cursor = ItemData.AIR;
        ItemData[] crafting = new ItemData[PlayerInventory.CRAFTING_SIZE];
        ItemData[] table = new ItemData[PlayerInventory.TABLE_SIZE];
        Arrays.fill(crafting, ItemData.AIR);
        Arrays.fill(table, ItemData.AIR);
        for (NbtMap entry : data.getList("PlayerUIItems", NbtType.COMPOUND, List.of())) {
            int slot = entry.getByte("Slot", (byte) -1) & 0xFF;
            if (slot == 0) {
                cursor = item(entry);
            } else if (slot >= PlayerInventory.CRAFTING_FIRST_SLOT
                    && slot < PlayerInventory.CRAFTING_FIRST_SLOT + crafting.length) {
                crafting[slot - PlayerInventory.CRAFTING_FIRST_SLOT] = item(entry);
            } else if (slot >= PlayerInventory.TABLE_FIRST_SLOT
                    && slot < PlayerInventory.TABLE_FIRST_SLOT + table.length) {
                table[slot - PlayerInventory.TABLE_FIRST_SLOT] = item(entry);
            }
        }

        return new Saved(position, rotation, gameMode, data.getInt("SelectedInventorySlot", 0),
                new PlayerInventory.Contents(List.of(main), armor,
                        offhand.isEmpty() ? ItemData.AIR : item(offhand.get(0)), cursor,
                        List.of(crafting), List.of(table)),
                stats(data));
    }

    /** Reads the health, hunger, air and fire a player was saved with. */
    private static Stats stats(NbtMap data) {
        Stats defaults = Stats.defaults();
        float health = defaults.health();
        int food = defaults.food();
        float saturation = defaults.saturation();
        float exhaustion = defaults.exhaustion();

        for (NbtMap attribute : data.getList("Attributes", NbtType.COMPOUND, List.of())) {
            String name = attribute.getString("Name", "");
            float current = attribute.getFloat("Current", Float.NaN);
            if (!Float.isFinite(current)) {
                continue;
            }
            switch (name) {
                case "minecraft:health" -> health = current;
                case "minecraft:player.hunger" -> food = Math.round(current);
                case "minecraft:player.saturation" -> saturation = current;
                case "minecraft:player.exhaustion" -> exhaustion = current;
                default -> {
                }
            }
        }

        if (health <= 0) {
            health = PlayerSurvival.MAX_HEALTH; // died, then left before respawning
        }
        health = Math.max(0.01f, Math.min(health, PlayerSurvival.MAX_HEALTH));
        food = Math.max(0, Math.min(food, PlayerSurvival.MAX_FOOD));
        saturation = Math.max(0, Math.min(saturation, food));
        exhaustion = Math.max(0, Math.min(exhaustion, 4));
        int air = (data.getShort("Air", (short) PlayerSurvival.MAX_AIR)) & 0xFFFF;
        int fire = (data.getShort("Fire", (short) 0)) & 0xFFFF;
        return new Stats(health, food, saturation, exhaustion,
                Math.min(air, PlayerSurvival.MAX_AIR), Math.min(fire, 1000));
    }

    private static List<NbtMap> slots(List<ItemData> items) {
        List<NbtMap> slots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            slots.add(item(items.get(i), i));
        }
        return slots;
    }

    private static NbtMap item(ItemData item, int slot) {
        NbtMapBuilder builder = NbtMap.builder();
        if (item.isNull() || item.getDefinition() == null) {
            builder.putByte("Count", (byte) 0)
                    .putShort("Damage", (short) 0)
                    .putString("Name", "")
                    .putByte("WasPickedUp", (byte) 0);
        } else {
            builder.putByte("Count", (byte) item.getCount())
                    .putShort("Damage", (short) item.getDamage())
                    .putString("Name", item.getDefinition().getIdentifier())
                    .putByte("WasPickedUp", (byte) 0);
            if (item.getBlockDefinition() != null) {
                NbtMap state = VanillaData.get().blockState(item.getBlockDefinition().getRuntimeId());
                if (state != null) {
                    builder.putCompound("Block", Blocks.of(state).toNbt());
                }
            }
            if (item.getTag() != null) {
                builder.putCompound("tag", item.getTag());
            }
        }
        return builder.putByte("Slot", (byte) slot).build();
    }

    /** A saved item, or air for an empty slot - or for an item this version doesn't have. */
    private static ItemData item(NbtMap saved) {
        String name = saved.getString("Name", "");
        int count = saved.getByte("Count", (byte) 0) & 0xFF;
        if (name.isEmpty() || count == 0) {
            return ItemData.AIR;
        }
        ItemDefinition definition = VanillaData.get().getItemRegistry().getDefinition(name);
        if (definition == null) {
            Logger.warn("A saved " + name + " isn't an item this version has, so it's gone.");
            return ItemData.AIR;
        }
        // The block state it carries if it saved one, else the item's usual one.
        ItemData usual = VanillaData.get().item(name, count);
        ItemData.Builder builder = usual.toBuilder().damage(saved.getShort("Damage", (short) 0));
        NbtMap block = saved.getCompound("Block", null);
        if (block != null && block.containsKey("name")) {
            builder.blockDefinition(HashedBlockDefinitions.of(BlockStateHash.of(block)));
        }
        NbtMap tag = saved.getCompound("tag", null);
        if (tag != null) {
            builder.tag(tag);
        }
        return builder.build();
    }
}
