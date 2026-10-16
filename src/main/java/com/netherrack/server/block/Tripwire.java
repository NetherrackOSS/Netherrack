package com.netherrack.server.block;

import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tripwire and the hooks it's strung between. Tripwire joins the tripwire beside it and the
 * hooks facing it. Two hooks facing each other, with tripwire all the way between them and
 * at most {@link #REACH} blocks apart, are strung together: they and the tripwire between
 * are attached. Something walking into attached tripwire presses it, and the hooks at its
 * ends click down - powered - until nothing presses any of it.
 */
public final class Tripwire {

    /** How far apart, at most, two hooks can be and still be strung together. */
    public static final int REACH = 41;

    private static final String ATTACHED = "attached_bit";
    private static final String POWERED = "powered_bit";

    private Tripwire() {
    }

    public static boolean isWire(Block block) {
        return block != null && block.getIdentifier().equals(BlockID.TRIP_WIRE);
    }

    public static boolean isHook(Block block) {
        return block != null && block.getIdentifier().equals(BlockID.TRIPWIRE_HOOK);
    }

    /** Tripwire joined to the tripwire and hooks beside it, and attached if it's strung between two hooks. */
    static Block wireShaped(World world, Vector3i position, Block wire) {
        List<Object> changes = new ArrayList<>();
        for (Face side : Face.AROUND) {
            changes.add("minecraft:connection_" + side.id());
            changes.add(Block.bit(wireJoins(side, at(world, side.from(position)))));
        }
        changes.add(ATTACHED);
        changes.add(Block.bit(strungThrough(world, position)));
        Block shaped = wire.with(changes.toArray());
        return shaped != null ? shaped : wire;
    }

    /** A hook attached if it's strung to another, and powered if, as well, some of the tripwire between is pressed. */
    static Block hookShaped(World world, Vector3i position, Block hook) {
        Face facing = facingOf(hook);
        boolean strung = false;
        boolean pressed = false;
        if (facing != null) {
            for (int distance = 1; distance <= REACH; distance++) {
                Block along = at(world, offset(position, facing, distance));
                if (isWire(along)) {
                    pressed |= along.isSet(POWERED);
                    continue;
                }
                strung = isHook(along) && facingOf(along) == facing.opposite() && distance > 1;
                break;
            }
        }
        Block shaped = hook.with(ATTACHED, Block.bit(strung), POWERED, Block.bit(strung && pressed));
        return shaped != null ? shaped : hook;
    }

    /**
     * The sound a hook makes as it changes: a click as it's pressed down or let go, or as it's
     * strung to another hook or parted from it. Null if it makes none.
     */
    static SoundEvent hookSound(Block before, Block after) {
        boolean wasPowered = before.isSet(POWERED);
        boolean powered = after.isSet(POWERED);
        boolean wasAttached = before.isSet(ATTACHED);
        boolean attached = after.isSet(ATTACHED);
        if (powered && !wasPowered) {
            return SoundEvent.POWER_ON;
        }
        if (!powered && wasPowered) {
            return SoundEvent.POWER_OFF;
        }
        if (attached && !wasAttached) {
            return SoundEvent.ATTACH;
        }
        if (!attached && wasAttached) {
            return SoundEvent.DETACH;
        }
        return null;
    }

    /**
     * The tripwire and hooks along the lines out from a position, up to the hooks at their
     * ends - which a change there can string together, part, or press.
     */
    static List<Vector3i> alongLines(World world, Vector3i position) {
        List<Vector3i> along = new ArrayList<>();
        for (Face side : Face.AROUND) {
            for (int distance = 1; distance <= REACH; distance++) {
                Vector3i next = offset(position, side, distance);
                Block block = at(world, next);
                if (isHook(block)) {
                    along.add(next);
                    break;
                }
                if (!isWire(block)) {
                    break;
                }
                along.add(next);
            }
        }
        return along;
    }

    /** Tripwire pressed or let go, or null if that's no change. */
    public static Block pressed(Block wire, boolean pressed) {
        return wire.isSet(POWERED) == pressed ? null : wire.with(POWERED, Block.bit(pressed));
    }

    public static boolean isPressed(Block wire) {
        return wire.isSet(POWERED);
    }

    /**
     * Whether a box from bottom to top, inside the block space at blockY, touches tripwire
     * there: the low line of attached tripwire, or anywhere in its lower half otherwise.
     */
    public static boolean touches(Block wire, double bottom, double top, int blockY) {
        double lowest = blockY + (wire.isSet(ATTACHED) ? 1 / 16.0 : 0);
        double highest = blockY + (wire.isSet(ATTACHED) ? 2.5 / 16 : 0.5);
        return bottom < highest && top > lowest;
    }

    /** Whether tripwire joins its neighbour on a side: more tripwire, or a hook facing it. */
    private static boolean wireJoins(Face side, Block neighbour) {
        return isWire(neighbour) || (isHook(neighbour) && facingOf(neighbour) == side.opposite());
    }

    /** Whether the tripwire at a position runs between two hooks facing each other, close enough to be strung. */
    private static boolean strungThrough(World world, Vector3i position) {
        for (Face side : List.of(Face.EAST, Face.SOUTH)) {
            int ahead = toEnd(world, position, side);
            int behind = toEnd(world, position, side.opposite());
            if (ahead > 0 && behind > 0 && ahead + behind <= REACH
                    && facingOf(at(world, offset(position, side, ahead))) == side.opposite()
                    && facingOf(at(world, offset(position, side.opposite(), behind))) == side) {
                return true;
            }
        }
        return false;
    }

    /** How far along the tripwire from a position the hook that ends it is, or 0 if it ends otherwise. */
    private static int toEnd(World world, Vector3i position, Face side) {
        for (int distance = 1; distance <= REACH; distance++) {
            Block block = at(world, offset(position, side, distance));
            if (isHook(block)) {
                return distance;
            }
            if (!isWire(block)) {
                return 0;
            }
        }
        return 0;
    }

    /** The way a hook faces: out of the block it's on, along the tripwire it can be strung to. */
    private static Face facingOf(Block hook) {
        return hook != null && hook.getProperty("direction") instanceof Integer direction
                ? Face.ofLegacyIndex(direction) : null;
    }

    private static Vector3i offset(Vector3i position, Face side, int distance) {
        return position.add(side.dx() * distance, 0, side.dz() * distance);
    }

    private static Block at(World world, Vector3i position) {
        return world.getBlock(position.getX(), position.getY(), position.getZ());
    }
}
