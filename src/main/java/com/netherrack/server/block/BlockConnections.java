package com.netherrack.server.block;

import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blocks that take their shape from the blocks around them, which the server works out and
 * keeps in their state: fences, panes and bars reach out to what they join, walls stand
 * tall under a block and raise a post where they end or turn, a fence gate drops when set
 * in a wall, and stairs turn a corner against stairs set across them. Tripwire joins what's
 * beside it and hooks are strung together (see Tripwire), and scaffolding knows how far it
 * is from what holds it up (see Scaffolding).
 */
public final class BlockConnections {

    /** Whole blocks that nothing joins, besides leaves and shulker boxes. */
    private static final Set<String> KEPT_APART = Set.of(
            BlockID.BARRIER, BlockID.PUMPKIN, BlockID.CARVED_PUMPKIN, BlockID.LIT_PUMPKIN,
            BlockID.MELON_BLOCK);

    private BlockConnections() {
    }

    /** A block shaped by the blocks around it, for the blocks that join their neighbours; any other as it is. */
    public static Block joined(World world, Vector3i position, Block block) {
        Block shaped = null;
        if (Scaffolding.is(block)) {
            shaped = Scaffolding.shaped(world, position, block);
        } else if (Tripwire.isWire(block)) {
            shaped = Tripwire.wireShaped(world, position, block);
        } else if (Tripwire.isHook(block)) {
            shaped = Tripwire.hookShaped(world, position, block);
        } else if (block.hasProperty("wall_post_bit")) {
            shaped = wallShape(world, position, block);
        } else if (block.hasProperty("minecraft:corner")) {
            shaped = block.with("minecraft:corner", stairsCorner(world, position, block));
        } else if (isFence(block) || isPane(block)) {
            List<Object> changes = new ArrayList<>();
            for (Face side : Face.AROUND) {
                changes.add("minecraft:connection_" + side.id());
                changes.add(Block.bit(joins(block, side, at(world, side.from(position)))));
            }
            shaped = block.with(changes.toArray());
        } else if (isGate(block)) {
            Face facing = Face.named(block.getProperty("minecraft:cardinal_direction"));
            if (facing != null) {
                Face span = facing.clockwise();
                boolean inWall = isWall(at(world, span.from(position))) || isWall(at(world, span.opposite().from(position)));
                shaped = block.with("in_wall_bit", Block.bit(inWall));
            }
        }
        return shaped != null ? shaped : block;
    }

    private static boolean isFence(Block block) {
        return block != null && block.getIdentifier().endsWith("_fence");
    }

    private static boolean isPane(Block block) {
        return block != null && (block.getIdentifier().endsWith("glass_pane") || block.getIdentifier().endsWith("_bars"));
    }

    private static boolean isWall(Block block) {
        return block != null && block.hasProperty("wall_post_bit");
    }

    private static boolean isGate(Block block) {
        return block != null && block.getIdentifier().endsWith("fence_gate");
    }

    private static boolean keptApart(Block block) {
        String name = block.getIdentifier();
        return name.contains("leaves") || name.endsWith("shulker_box") || KEPT_APART.contains(name);
    }

    /**
     * Whether a fence, pane or wall joins its neighbour on a side: any whole face (unless it's
     * one nothing joins), a fence gate set across a fence or wall, and its own kind - wooden
     * fences one another, nether brick fences one another, and panes, bars and walls one another.
     */
    private static boolean joins(Block block, Face side, Block neighbour) {
        if (neighbour == null || keptApart(neighbour)) {
            return false;
        }
        if (BlockShapes.hasFullFace(neighbour, side.opposite())) {
            return true;
        }
        if (isGate(neighbour)) {
            Face facing = Face.named(neighbour.getProperty("minecraft:cardinal_direction"));
            return !isPane(block) && facing != null && !facing.axis().equals(side.axis());
        }
        if (isFence(block)) {
            return isFence(neighbour)
                    && neighbour.getIdentifier().equals(BlockID.NETHER_BRICK_FENCE) == block.getIdentifier().equals(BlockID.NETHER_BRICK_FENCE);
        }
        return isPane(neighbour) || isWall(neighbour);
    }

    /**
     * The corner stairs turn, from the stairs around them of the same half: stairs whose tall
     * back meets stairs set across them round an outer corner, and stairs whose low step meets
     * them fill an inner one - neither where it would break a straight run beside them.
     */
    private static String stairsCorner(World world, Vector3i position, Block stairs) {
        Face climb = climbOf(stairs);
        if (climb == null) {
            return "none";
        }
        Face behind = climbOf(sameHalf(world, climb.from(position), stairs));
        if (behind != null && !behind.axis().equals(climb.axis())
                && !climb.equals(climbOf(sameHalf(world, behind.opposite().from(position), stairs)))) {
            return behind == climb.counterClockwise() ? "outer_left" : "outer_right";
        }
        Face front = climbOf(sameHalf(world, climb.opposite().from(position), stairs));
        if (front != null && !front.axis().equals(climb.axis())
                && !climb.equals(climbOf(sameHalf(world, front.from(position), stairs)))) {
            return front == climb.counterClockwise() ? "inner_left" : "inner_right";
        }
        return "none";
    }

    /** The stairs at a position, if they're the same way up as these. */
    private static Block sameHalf(World world, Vector3i position, Block stairs) {
        Block block = at(world, position);
        return block != null && block.hasProperty("upside_down_bit")
                && block.isSet("upside_down_bit") == stairs.isSet("upside_down_bit") ? block : null;
    }

    private static Face climbOf(Block block) {
        if (block == null || !(block.getProperty("weirdo_direction") instanceof Integer direction)
                || direction < 0 || direction >= BlockShapes.STAIRS_CLIMB.size()) {
            return null;
        }
        return BlockShapes.STAIRS_CLIMB.get(direction);
    }

    /**
     * A wall's sides and post. A side it joins on is tall when the block above covers it, and
     * short otherwise. The post rises where it stands alone, ends or turns, or where a wall
     * post stands on it; along a straight run, only under a torch.
     */
    private static Block wallShape(World world, Vector3i position, Block wall) {
        Block above = at(world, Face.UP.from(position));
        Map<Face, String> sides = new EnumMap<>(Face.class);
        for (Face side : Face.AROUND) {
            String connection;
            if (!joins(wall, side, at(world, side.from(position)))) {
                connection = "none";
            } else if (BlockShapes.isFullCube(above) || (isWall(above)
                    && !"none".equals(above.getProperty("wall_connection_type_" + side.id())))) {
                connection = "tall";
            } else {
                connection = "short";
            }
            sides.put(side, connection);
        }
        boolean north = !"none".equals(sides.get(Face.NORTH));
        boolean south = !"none".equals(sides.get(Face.SOUTH));
        boolean east = !"none".equals(sides.get(Face.EAST));
        boolean west = !"none".equals(sides.get(Face.WEST));
        boolean post;
        if (isWall(above) && above.isSet("wall_post_bit")) {
            post = true;
        } else if ((!north && !south && !east && !west) || north != south || east != west) {
            post = true;
        } else if (("tall".equals(sides.get(Face.NORTH)) && "tall".equals(sides.get(Face.SOUTH)))
                || ("tall".equals(sides.get(Face.EAST)) && "tall".equals(sides.get(Face.WEST)))) {
            post = false;
        } else {
            post = above != null && "top".equals(above.getProperty("torch_facing_direction"));
        }
        List<Object> changes = new ArrayList<>();
        sides.forEach((side, connection) -> {
            changes.add("wall_connection_type_" + side.id());
            changes.add(connection);
        });
        changes.add("wall_post_bit");
        changes.add(Block.bit(post));
        return wall.with(changes.toArray());
    }

    private static Block at(World world, Vector3i position) {
        return world.getBlock(position.getX(), position.getY(), position.getZ());
    }
}
