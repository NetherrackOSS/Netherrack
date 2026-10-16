package com.netherrack.server.block;

import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Using a block by clicking it: a lever flips, a button presses in (and pops back out a
 * moment later), and doors, trapdoors and fence gates open or shut - a door's two halves
 * together, and a gate away from the player. Iron doors and trapdoors only open by
 * redstone, so a hand does nothing to them.
 */
public final class BlockUse {

    private static final String OPEN = "open_bit";
    private static final String PRESSED = "button_pressed_bit";
    private static final String CARDINAL = "minecraft:cardinal_direction";

    /** How long a button stays pressed, in ticks: a second for stone ones, half as long again for wooden ones. */
    private static final int STONE_BUTTON_TICKS = 20;
    private static final int WOODEN_BUTTON_TICKS = 30;

    private static final Set<String> OPENED_BY_REDSTONE = Set.of(BlockID.IRON_DOOR, BlockID.IRON_TRAPDOOR);

    /**
     * Blocks that open a screen, or do something of their own, when clicked: containers,
     * workstations, beds, bells, cake, note blocks, item frames, signs and the like. Netherrack
     * doesn't do these yet, but clicking one never places the held block beside it, unless
     * the player sneaks.
     */
    private static final List<String> OPENS_ENDINGS = List.of("chest", "shulker_box", "furnace", "smoker", "anvil",
            "_shelf", "standing_sign", "wall_sign", "_hanging_sign", "command_block", "candle_cake");
    private static final Set<String> OPENS = Set.of("barrel", "crafting_table", "cartography_table", "smithing_table",
            "loom", "stonecutter_block", "grindstone", "enchanting_table", "brewing_stand", "beacon", "hopper", "dispenser",
            "dropper", "crafter", "bed", "straw_bed", "bell", "cake", "noteblock", "frame", "glow_frame", "decorated_pot",
            "dragon_egg", "structure_block", "jigsaw", "lectern", "composter", "jukebox", "respawn_anchor", "lodestone");

    /**
     * What using a block did: where blocks changed, the sound it made (by the block now at the
     * first position), and for a pressed button, how many ticks until it pops back out.
     */
    public record Used(List<Vector3i> changed, SoundEvent sound, Integer releaseIn) {
    }

    private BlockUse() {
    }

    /** Whether clicking this block uses it, rather than placing the held item against it. */
    public static boolean isUsable(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getIdentifier();
        return name.equals(BlockID.LEVER) || name.endsWith("button")
                || (!OPENED_BY_REDSTONE.contains(name)
                && (name.endsWith("_door") || name.endsWith("trapdoor") || name.endsWith("fence_gate")));
    }

    /** Whether clicking this block does something of its own, which places nothing, whether or not Netherrack does it yet. */
    public static boolean opensOnClick(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getIdentifier();
        if (OPENS.contains(name.substring(name.indexOf(':') + 1))) {
            return true;
        }
        for (String ending : OPENS_ENDINGS) {
            if (name.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Uses the block at a position as a player facing this way does by clicking it. Null for
     * a block that isn't usable; a button that's already pressed changes nothing.
     */
    public static Used use(World world, Vector3i position, Face facing) {
        Block block = at(world, position);
        if (!isUsable(block)) {
            return null;
        }
        String name = block.getIdentifier();
        if (name.equals(BlockID.LEVER)) {
            boolean on = !block.isSet(OPEN);
            return change(world, position, block.with(OPEN, Block.bit(on)), on ? SoundEvent.POWER_ON : SoundEvent.POWER_OFF);
        }
        if (name.endsWith("button")) {
            if (block.isSet(PRESSED)) {
                return new Used(List.of(), null, null);
            }
            Used used = change(world, position, block.with(PRESSED, Block.bit(true)), SoundEvent.BUTTON_CLICK_ON);
            int ticks = name.endsWith("stone_button") ? STONE_BUTTON_TICKS : WOODEN_BUTTON_TICKS;
            return new Used(used.changed(), used.sound(), ticks);
        }
        boolean opening = !block.isSet(OPEN);
        if (name.endsWith("fence_gate")) {
            // A gate swings away from the player, so one opened from its far side turns round first.
            boolean turnsRound = opening && facing.opposite().id().equals(block.getProperty(CARDINAL));
            Block changed = block.with(OPEN, Block.bit(opening),
                    CARDINAL, turnsRound ? facing.id() : block.getProperty(CARDINAL));
            return change(world, position, changed, opening ? SoundEvent.FENCE_GATE_OPEN : SoundEvent.FENCE_GATE_CLOSE);
        }
        boolean trapdoor = name.endsWith("trapdoor");
        SoundEvent sound = trapdoor
                ? (opening ? SoundEvent.TRAPDOOR_OPEN : SoundEvent.TRAPDOOR_CLOSE)
                : (opening ? SoundEvent.DOOR_OPEN : SoundEvent.DOOR_CLOSE);
        Used used = change(world, position, block.with(OPEN, Block.bit(opening)), sound);
        // A door's other half opens with it.
        Vector3i other = BlockSupport.otherHalf(block, position);
        Block half = other != null ? at(world, other) : null;
        if (half != null && half.getIdentifier().equals(name)) {
            Block opened = half.with(OPEN, Block.bit(opening));
            if (opened != null) {
                world.setBlock(other.getX(), other.getY(), other.getZ(), opened);
                List<Vector3i> changed = new ArrayList<>(used.changed());
                changed.add(other);
                return new Used(changed, sound, null);
            }
        }
        return used;
    }

    /** Lets a button pop back out, if it's still pressed in. Returns whether it was. */
    public static boolean releaseButton(World world, Vector3i position) {
        Block button = at(world, position);
        if (button == null || !button.getIdentifier().endsWith("button") || !button.isSet(PRESSED)) {
            return false;
        }
        Block released = button.with(PRESSED, Block.bit(false));
        if (released == null) {
            return false;
        }
        world.setBlock(position.getX(), position.getY(), position.getZ(), released);
        return true;
    }

    private static Used change(World world, Vector3i position, Block changed, SoundEvent sound) {
        if (changed == null) {
            return new Used(List.of(), null, null);
        }
        world.setBlock(position.getX(), position.getY(), position.getZ(), changed);
        return new Used(List.of(position), sound, null);
    }

    private static Block at(World world, Vector3i position) {
        return world.getBlock(position.getX(), position.getY(), position.getZ());
    }
}
