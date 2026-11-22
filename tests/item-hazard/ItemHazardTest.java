package com.netherrack.server.entity;

import com.netherrack.server.block.Block;
import com.netherrack.server.block.BlockID;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.world.World;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Plain-Java checks for the item side of lava and fire damage. The fake world answers every
 * block lookup with one hazard block, which is enough because the damage code only cares what
 * block the item's own position occupies.
 */
public final class ItemHazardTest {

    private static int failures;

    public static void main(String[] args) {
        lavaDestroysRegularItems();
        fireBurnsRegularItems();
        afterburnContinuesInAir();
        netheriteWithstandsLavaBriefly();
        airLeavesItemsAlone();
        managerRemovesBurningItems();
        unloadedItemsStillExpire();
        unloadedVoidItemsDisappear();

        System.out.println(failures == 0
                ? "All item hazard checks passed."
                : failures + " check(s) failed.");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void lavaDestroysRegularItems() {
        FakeWorld lava = new FakeWorld(BlockID.LAVA);
        ItemEntity item = item("minecraft:stick");
        check("lava destroys item", item.applyHazards(lava), true);
        check("lava leaves no health", item.getHealth(), 0);
        check("lava ignites item", item.isBurning(), true);

        FakeWorld flowing = new FakeWorld(BlockID.FLOWING_LAVA);
        ItemEntity inFlowing = item("minecraft:stick");
        check("flowing lava destroys item", inFlowing.applyHazards(flowing), true);
    }

    private static void fireBurnsRegularItems() {
        FakeWorld fire = new FakeWorld(BlockID.FIRE);
        ItemEntity item = item("minecraft:stick");
        check("first fire tick survives", item.applyHazards(fire), false);
        check("fire and afterburn damage", item.getHealth(), 3);
        check("fire ignites item", item.isBurning(), true);
        check("second fire tick survives", item.applyHazards(fire), false);
        check("fire re-ignites each tick", item.getHealth(), 1);
        check("third fire tick destroys", item.applyHazards(fire), true);

        FakeWorld soulFire = new FakeWorld(BlockID.SOUL_FIRE);
        ItemEntity inSoulFire = item("minecraft:stick");
        inSoulFire.applyHazards(soulFire);
        check("soul fire damages like fire", inSoulFire.getHealth(), 3);
    }

    private static void afterburnContinuesInAir() {
        FakeWorld world = new FakeWorld(BlockID.FIRE);
        ItemEntity item = item("minecraft:stick");
        item.applyHazards(world);
        world.set("minecraft:air");

        int ticks = 0;
        boolean destroyed = false;
        while (!destroyed && ticks < 100) {
            destroyed = item.applyHazards(world);
            ticks++;
        }
        check("afterburn eventually destroys", destroyed, true);
        check("afterburn lasts eight seconds", ticks, 60);
    }

    private static void netheriteWithstandsLavaBriefly() {
        FakeWorld lava = new FakeWorld(BlockID.LAVA);
        ItemEntity item = item("minecraft:netherite_pickaxe");
        check("netherite is fireproof", item.isFireproof(), true);
        check("first lava tick survives", item.applyHazards(lava), false);
        check("one lava hit lands", item.getHealth(), 1);
        check("fireproof item still shows flames", item.isBurning(), true);

        for (int i = 0; i < 20; i++) {
            check("netherite survives burn tick " + i, item.applyHazards(lava), false);
        }
        boolean destroyed = false;
        int extra = 0;
        while (!destroyed && extra < 100) {
            destroyed = item.applyHazards(lava);
            extra++;
        }
        check("netherite burns out in lava", destroyed, true);
        check("netherite burns out after 20 more ticks", extra, 20);
    }

    private static void airLeavesItemsAlone() {
        FakeWorld air = new FakeWorld("minecraft:air");
        ItemEntity item = item("minecraft:stick");
        for (int i = 0; i < 100; i++) {
            item.applyHazards(air);
        }
        check("air leaves health alone", item.getHealth(), 5);
        check("air does not ignite", item.isBurning(), false);
    }

    private static void managerRemovesBurningItems() {
        FakeWorld lava = new FakeWorld(BlockID.LAVA);
        ItemEntities entities = new ItemEntities(lava, new PlayerManager());
        Map<Long, ItemEntity> items = addItem(entities, item("minecraft:stick"));

        entities.tick();
        check("manager removes burning item", items.isEmpty(), true);
    }

    private static void unloadedItemsStillExpire() {
        FakeWorld air = new FakeWorld("minecraft:air");
        air.setLoaded(false);
        ItemEntities entities = new ItemEntities(air, new PlayerManager());
        ItemEntity item = item("minecraft:stick");
        for (int i = 0; i < 5999; i++) {
            item.age();
        }
        Map<Long, ItemEntity> items = addItem(entities, item);

        entities.tick();
        check("unloaded item still expires", items.isEmpty(), true);
    }

    private static void unloadedVoidItemsDisappear() {
        FakeWorld air = new FakeWorld("minecraft:air");
        air.setLoaded(false);
        ItemEntities entities = new ItemEntities(air, new PlayerManager());
        Map<Long, ItemEntity> items = addItem(entities, item("minecraft:stick", -200));

        entities.tick();
        check("unloaded void item is removed", items.isEmpty(), true);
    }

    private static Map<Long, ItemEntity> addItem(ItemEntities entities, ItemEntity item) {
        try {
            Field field = ItemEntities.class.getDeclaredField("items");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, ItemEntity> items = (Map<Long, ItemEntity>) field.get(entities);
            items.put(item.getEntityId(), item);
            return items;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ItemEntity item(String id) {
        return item(id, 4);
    }

    private static ItemEntity item(String id, double y) {
        return new ItemEntity(1, ItemData.builder()
                .definition(new SimpleItemDefinition(id, 1, false))
                .count(1)
                .build(), Vector3f.from(0.5, y, 0.5), Vector3f.ZERO, 0);
    }

    private static final class FakeWorld extends World {
        private Block block;
        private boolean loaded = true;

        FakeWorld(String identifier) {
            super("test", null, null, NbtMap.builder().build());
            this.block = new Block(identifier);
        }

        void set(String identifier) {
            this.block = new Block(identifier);
        }

        void setLoaded(boolean loaded) {
            this.loaded = loaded;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return block;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return loaded;
        }
    }

    private static void check(String name, Object actual, Object expected) {
        if (!java.util.Objects.equals(actual, expected)) {
            failures++;
            System.out.println("FAIL " + name + ": expected <" + expected + "> but got <"
                    + actual + ">");
        } else {
            System.out.println("ok   " + name);
        }
    }
}
