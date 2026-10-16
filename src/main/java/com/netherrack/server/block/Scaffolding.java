package com.netherrack.server.block;

import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * Scaffolding, which reaches out sideways from what holds it up, and keeps how far in its
 * "stability": 0 on a block with a whole top, the same as the scaffolding it stands on, or
 * one more than the nearest scaffolding beside it. Seven out, it can't stay, and breaks.
 * Scaffolding looks at its distance again a tick after something around it changes, so a
 * column whose bottom goes falls one block a tick.
 * <p>
 * Placing scaffolding against scaffolding builds onto it rather than beside it: past the
 * scaffolding the way the clicked face looks, into the first space beyond. Clicking a top
 * builds the column up, and clicking a side reaches out that way.
 */
public final class Scaffolding {

    /** How far scaffolding reaches out from what holds it up, plus one: at this distance, it can't stay. */
    public static final int MAX_DISTANCE = 7;

    private static final String STABILITY = "stability";

    private Scaffolding() {
    }

    public static boolean is(Block block) {
        return block != null && block.getIdentifier().equals(BlockID.SCAFFOLDING);
    }

    /** How far the scaffolding at a position is from what holds it up, or MAX_DISTANCE if nothing does. */
    public static int distance(World world, Vector3i position) {
        Block below = at(world, Face.DOWN.from(position));
        int distance = MAX_DISTANCE;
        if (is(below)) {
            distance = stabilityOf(below);
        } else if (BlockShapes.hasFullFace(below, Face.UP)) {
            return 0;
        }
        for (Face side : Face.AROUND) {
            Block beside = at(world, side.from(position));
            if (is(beside)) {
                distance = Math.min(distance, stabilityOf(beside) + 1);
            }
        }
        return distance;
    }

    /** Scaffolding at a position with its distance brought up to date. */
    public static Block shaped(World world, Vector3i position, Block scaffolding) {
        Block shaped = scaffolding.with(STABILITY, Math.min(distance(world, position), MAX_DISTANCE));
        return shaped != null ? shaped : scaffolding;
    }

    /**
     * Whether scaffolding placed against a face of the block at clicked builds onto
     * scaffolding: the clicked block is some, or the block beyond the face is - as when a
     * player standing in scaffolding clicks the ground under it.
     */
    public static boolean buildsOn(World world, Vector3i clicked, Face face) {
        return is(at(world, clicked)) || is(at(world, face.from(clicked)));
    }

    /**
     * Where scaffolding built onto scaffolding goes: the first space past the scaffolding
     * beyond the clicked face - straight beyond it for an underside, as for any block. Null
     * if reaching out sideways passes more scaffolding than it can reach across.
     */
    public static Vector3i placement(World world, Vector3i clicked, Face face) {
        Vector3i target = face.from(clicked);
        if (face == Face.DOWN) {
            return target;
        }
        int passed = 0;
        while (is(at(world, target))) {
            if (face.isHorizontal() && ++passed >= MAX_DISTANCE) {
                return null;
            }
            target = face.from(target);
        }
        return target;
    }

    private static int stabilityOf(Block scaffolding) {
        return scaffolding.getProperty(STABILITY) instanceof Integer stability ? stability : MAX_DISTANCE;
    }

    private static Block at(World world, Vector3i position) {
        return world.getBlock(position.getX(), position.getY(), position.getZ());
    }
}
