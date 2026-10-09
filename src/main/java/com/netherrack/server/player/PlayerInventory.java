package com.netherrack.server.player;

import com.netherrack.server.network.VanillaData;
import com.netherrack.server.util.Logger;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftCreativeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * A player's inventory: 36 main slots (the first 9 are the hotbar), 4 armor slots and the
 * offhand - plus, while the inventory screen is open, the cursor (what the player is
 * moving around) and the 2x2 crafting grid. Items only sit in the crafting grid for now;
 * there are no recipes to craft with yet.
 * <p>
 * Items thrown out of the inventory are collected here, for the caller to take with
 * {@link #takeDropped()} and put into the world.
 * <p>
 * Creative players can also take items from the creative inventory - each one appears in
 * a "created output" slot for the rest of that request, to be moved somewhere real - and
 * destroy items in the creative inventory's bin.
 * <p>
 * The server's copy is the real one (StartGame makes inventories server-authoritative).
 * The client moves items on its side and asks the server to confirm with item stack
 * requests. Each request is applied whole or not at all, and the response either tells
 * the client what the slots it touched now hold, or to undo the request.
 * <p>
 * Every stack has a network id the client names it by. The client doesn't wait for
 * answers between requests, so it can also name a slot an earlier request changed by
 * that request's (negative) id - which is why the ids each recent request left behind
 * are remembered.
 */
public class PlayerInventory {

    public static final int SIZE = 36;
    public static final int HOTBAR_SIZE = 9;
    private static final int ARMOR_SIZE = 4;
    private static final int CRAFTING_SIZE = 4;

    /** The inventory screen's crafting grid is UI slots 28 to 31 (a crafting table's 3x3 grid starts at 32). */
    private static final int CRAFTING_FIRST_SLOT = 28;

    /** How many requests' changes are remembered for later requests to refer back to. */
    private static final int RECENT_REQUESTS = 64;

    private enum Kind { MAIN, ARMOR, OFFHAND, CURSOR, CRAFTING, CREATED }

    /** A slot somewhere in the inventory. */
    private record Place(Kind kind, int index) {
    }

    /** The stack network id a request left in a slot. */
    private record Change(Place place, int netId) {
    }

    private record Recent(int requestId, List<Change> changes) {
    }

    /** Why a request was refused. Only logged - the client just undoes the request. */
    private static class Refused extends Exception {
        Refused(String reason) {
            super(reason);
        }
    }

    private ItemData[] main = emptySlots(SIZE);
    private ItemData[] armor = emptySlots(ARMOR_SIZE);
    private ItemData[] crafting = emptySlots(CRAFTING_SIZE);
    private ItemData offhand = ItemData.AIR;
    private ItemData cursor = ItemData.AIR;
    /** What a creative player took from the creative inventory, for the rest of one request. */
    private ItemData created = ItemData.AIR;

    private int nextNetId = 1;
    private final Deque<Recent> recent = new ArrayDeque<>();
    private final List<ItemData> dropped = new ArrayList<>();

    /** The item in a main inventory slot. */
    public synchronized ItemData get(int slot) {
        return main[slot];
    }

    /** Puts an item in a main inventory slot as a new stack, replacing what was there. */
    public synchronized void set(int slot, ItemData item) {
        main[slot] = item.isNull() ? ItemData.AIR : withCount(item, item.getCount(), nextNetId++);
    }

    /** Uses up one item from a main inventory slot (e.g. a block that was just placed) and returns what's left. */
    public synchronized ItemData useOne(int slot) {
        main[slot] = removed(main[slot], 1);
        return main[slot];
    }

    /**
     * Adds as much of the item as fits into the main inventory, as picking it up does:
     * onto stacks of the same item first, then into empty slots, hotbar first. Returns how
     * many didn't fit.
     */
    public synchronized int add(ItemData item) {
        int max = maxStack(item);
        int left = item.getCount();
        for (int i = 0; i < SIZE && left > 0; i++) {
            if (!main[i].isNull() && sameItem(main[i], item) && main[i].getCount() < max) {
                int moved = Math.min(left, max - main[i].getCount());
                main[i] = main[i].toBuilder().count(main[i].getCount() + moved).build();
                left -= moved;
            }
        }
        for (int i = 0; i < SIZE && left > 0; i++) {
            if (main[i].isNull()) {
                int moved = Math.min(left, max);
                main[i] = withCount(item, moved, nextNetId++);
                left -= moved;
            }
        }
        return left;
    }

    /**
     * Throws some of a main inventory slot out, as pressing Q on the hotbar does - if the
     * slot really holds at least that many of the item the client says it does.
     */
    public synchronized void throwFromSlot(int slot, int itemId, int count) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        ItemData item = main[slot];
        if (item.isNull() || item.getDefinition().getRuntimeId() != itemId || count < 1 || count > item.getCount()) {
            return;
        }
        dropped.add(item.toBuilder().count(count).build());
        main[slot] = removed(item, count);
    }

    /** The items thrown out of the inventory since the last call, for the world to take. */
    public synchronized List<ItemData> takeDropped() {
        List<ItemData> taken = List.copyOf(dropped);
        dropped.clear();
        return taken;
    }

    /**
     * Applies an item stack request if it's valid, and returns the response to send: what
     * the touched slots now hold, or an error that makes the client undo the request.
     * {@code creative} allows taking items from the creative inventory and destroying them.
     */
    public synchronized ItemStackResponse handle(ItemStackRequest request, boolean creative) {
        int requestId = request.getRequestId();
        Snapshot before = new Snapshot();
        List<Change> changes = new ArrayList<>();
        List<ItemStackRequestSlotData> touched = new ArrayList<>();
        try {
            for (ItemStackRequestAction action : request.getActions()) {
                apply(action, requestId, creative, changes, touched);
            }
            checkArmor();
        } catch (Refused refused) {
            before.restore();
            created = ItemData.AIR;
            Logger.debug("Refused item stack request " + requestId + ": " + refused.getMessage());
            return new ItemStackResponse(ItemStackResponseStatus.ERROR, requestId, List.of());
        }

        // Whatever's left of a creative item that wasn't put anywhere is gone once the request ends.
        created = ItemData.AIR;
        recent.addLast(new Recent(requestId, changes));
        if (recent.size() > RECENT_REQUESTS) {
            recent.removeFirst();
        }
        return new ItemStackResponse(ItemStackResponseStatus.OK, requestId, describe(touched));
    }

    /**
     * Puts what's on the cursor and in the crafting grid back into the main inventory, as
     * closing the inventory screen does; what doesn't fit is thrown out. Returns whether
     * anything moved.
     */
    public synchronized boolean returnScreenItems() {
        boolean moved = returnToMain(cursor);
        cursor = ItemData.AIR;
        for (int i = 0; i < CRAFTING_SIZE; i++) {
            moved |= returnToMain(crafting[i]);
            crafting[i] = ItemData.AIR;
        }
        return moved;
    }

    private boolean returnToMain(ItemData item) {
        if (item.isNull()) {
            return false;
        }
        int left = add(item);
        if (left > 0) {
            dropped.add(item.toBuilder().count(left).build());
        }
        return true;
    }

    /**
     * The packets that show the client this whole inventory: the main inventory, offhand
     * and armor, then the cursor and crafting grid in the UI container.
     */
    public synchronized List<BedrockPacket> contentPackets() {
        List<BedrockPacket> packets = new ArrayList<>();
        packets.add(content(ContainerId.INVENTORY, main));
        packets.add(content(ContainerId.OFFHAND, new ItemData[]{offhand}));
        packets.add(content(ContainerId.ARMOR, armor));
        packets.add(uiSlot(0, cursor));
        for (int i = 0; i < CRAFTING_SIZE; i++) {
            packets.add(uiSlot(CRAFTING_FIRST_SLOT + i, crafting[i]));
        }
        return packets;
    }

    private void apply(ItemStackRequestAction action, int requestId, boolean creative, List<Change> changes,
                       List<ItemStackRequestSlotData> touched) throws Refused {
        if (action instanceof TransferItemStackRequestAction transfer) {
            // Take and Place: moving some or all of a stack, onto nothing or onto the same item.
            transfer(transfer.getCount(), transfer.getSource(), transfer.getDestination(), requestId, changes);
            touch(touched, transfer.getSource());
            touch(touched, transfer.getDestination());
        } else if (action instanceof SwapAction swap) {
            Place from = checked(swap.getSource(), requestId, changes);
            Place to = checked(swap.getDestination(), requestId, changes);
            if (from.kind() == Kind.CREATED || to.kind() == Kind.CREATED) {
                throw new Refused("swapping with the created output");
            }
            ItemData moving = get(from);
            set(from, get(to));
            set(to, moving);
            note(changes, from);
            note(changes, to);
            touch(touched, swap.getSource());
            touch(touched, swap.getDestination());
        } else if (action instanceof CraftCreativeAction craft) {
            if (!creative) {
                throw new Refused("only creative players can take from the creative inventory");
            }
            ItemData item = VanillaData.get().creativeItem(craft.getCreativeItemNetworkId());
            if (item == null) {
                throw new Refused("no creative item " + craft.getCreativeItemNetworkId());
            }
            // Until it lands in a real slot, the client names this stack by the request's id.
            created = withCount(item, item.getCount(), requestId);
        } else if (action instanceof DestroyAction destroy) {
            if (!creative) {
                throw new Refused("only creative players can destroy items");
            }
            Place from = checked(destroy.getSource(), requestId, changes);
            ItemData item = get(from);
            if (item.isNull() || destroy.getCount() < 1 || destroy.getCount() > item.getCount()) {
                throw new Refused("can't destroy " + destroy.getCount() + " from a stack of " + item.getCount());
            }
            set(from, removed(item, destroy.getCount()));
            note(changes, from);
            touch(touched, destroy.getSource());
        } else if (action instanceof DropAction drop) {
            Place from = checked(drop.getSource(), requestId, changes);
            ItemData item = get(from);
            if (item.isNull() || drop.getCount() < 1 || drop.getCount() > item.getCount()) {
                throw new Refused("can't drop " + drop.getCount() + " from a stack of " + item.getCount());
            }
            dropped.add(item.toBuilder().count(drop.getCount()).build());
            set(from, removed(item, drop.getCount()));
            note(changes, from);
            touch(touched, drop.getSource());
        } else {
            switch (action.getType()) {
                // Informational only: what the client expects a craft to make, and a tool's
                // wear while mining - nothing to apply.
                case CRAFT_RESULTS_DEPRECATED, MINE_BLOCK -> {
                }
                default -> throw new Refused(action.getType() + " isn't supported yet");
            }
        }
    }

    private void transfer(int count, ItemStackRequestSlotData source, ItemStackRequestSlotData destination,
                          int requestId, List<Change> changes) throws Refused {
        Place from = checked(source, requestId, changes);
        Place to = checked(destination, requestId, changes);
        if (from.equals(to)) {
            throw new Refused("moving a stack onto itself");
        }
        if (to.kind() == Kind.CREATED) {
            throw new Refused("placing into the created output");
        }
        ItemData moving = get(from);
        if (moving.isNull() || count < 1 || count > moving.getCount()) {
            throw new Refused("can't move " + count + " from a stack of " + moving.getCount());
        }

        ItemData there = get(to);
        int max = maxStack(moving);
        ItemData arriving;
        if (there.isNull()) {
            if (count > max) {
                throw new Refused("a stack of " + count + " is too big");
            }
            // A whole stack keeps its id; the part that splits off, or a fresh creative
            // item, is a new stack.
            boolean whole = count == moving.getCount() && moving.getNetId() > 0;
            int netId = whole ? moving.getNetId() : nextNetId++;
            arriving = withCount(moving, count, netId);
        } else {
            if (!sameItem(there, moving)) {
                throw new Refused("the items don't stack");
            }
            int total = there.getCount() + count;
            if (total > max) {
                throw new Refused("a stack of " + total + " is too big");
            }
            arriving = there.toBuilder().count(total).build();
        }

        set(to, arriving);
        set(from, removed(moving, count));
        note(changes, from);
        note(changes, to);
    }

    /**
     * Where a request's slot points, once it's confirmed the stack the client thinks is
     * there is the one the server has: the same stack id (0 for empty), or a request id
     * standing for whatever that request left in the slot.
     */
    private Place checked(ItemStackRequestSlotData slot, int requestId, List<Change> changes) throws Refused {
        Place place = resolve(slot);
        int actual = netId(get(place));
        int claimed = slot.getStackNetworkId();

        Integer expected;
        if (claimed >= 0) {
            expected = claimed;
        } else if (claimed == actual) {
            expected = claimed; // a fresh creative item, which carries the request's id itself
        } else if (claimed == requestId) {
            expected = lastChange(changes, place);
        } else {
            expected = null;
            for (Recent request : recent) {
                if (request.requestId() == claimed) {
                    expected = lastChange(request.changes(), place);
                }
            }
        }

        if (!Objects.equals(expected, actual)) {
            throw new Refused(slot.getContainer() + " slot " + slot.getSlot() + " is stack " + actual
                    + ", not " + claimed);
        }
        return place;
    }

    private static Place resolve(ItemStackRequestSlotData slot) throws Refused {
        int index = slot.getSlot();
        Place place = switch (slot.getContainer()) {
            case HOTBAR, INVENTORY, HOTBAR_AND_INVENTORY -> index < SIZE ? new Place(Kind.MAIN, index) : null;
            case ARMOR -> index < ARMOR_SIZE ? new Place(Kind.ARMOR, index) : null;
            // The offhand is slot 1 on the wire; 0 is accepted too.
            case OFFHAND -> index <= 1 ? new Place(Kind.OFFHAND, 0) : null;
            case CURSOR -> index == 0 ? new Place(Kind.CURSOR, 0) : null;
            case CREATED_OUTPUT -> new Place(Kind.CREATED, 0);
            case CRAFTING_INPUT -> index >= CRAFTING_FIRST_SLOT && index < CRAFTING_FIRST_SLOT + CRAFTING_SIZE
                    ? new Place(Kind.CRAFTING, index - CRAFTING_FIRST_SLOT) : null;
            default -> null;
        };
        if (place == null) {
            throw new Refused("no slot " + index + " in " + slot.getContainer());
        }
        return place;
    }

    /** Armor slots may only hold what's worn there - checked once the whole request has been applied. */
    private void checkArmor() throws Refused {
        for (int slot = 0; slot < ARMOR_SIZE; slot++) {
            ItemData item = armor[slot];
            if (!item.isNull() && armorSlot(item.getDefinition().getIdentifier()) != slot) {
                throw new Refused(item.getDefinition().getIdentifier() + " isn't worn in armor slot " + slot);
            }
        }
    }

    /** Which armor slot an item is worn in: 0 head, 1 chest, 2 legs, 3 feet, or -1 if it isn't worn. */
    private static int armorSlot(String identifier) {
        String name = identifier.startsWith("minecraft:") ? identifier.substring("minecraft:".length()) : identifier;
        if (name.endsWith("_helmet") || name.endsWith("_skull") || name.endsWith("_head") || name.equals("carved_pumpkin")) {
            return 0;
        }
        if (name.endsWith("_chestplate") || name.equals("elytra")) {
            return 1;
        }
        if (name.endsWith("_leggings")) {
            return 2;
        }
        if (name.endsWith("_boots")) {
            return 3;
        }
        return -1;
    }

    /** What the touched slots now hold, grouped by container as the client named them. */
    private List<ItemStackResponseContainer> describe(List<ItemStackRequestSlotData> touched) {
        List<ItemStackResponseContainer> containers = new ArrayList<>();
        for (ItemStackRequestSlotData slot : touched) {
            ItemData item;
            try {
                item = get(resolve(slot));
            } catch (Refused e) {
                continue; // can't happen: every touched slot was resolved while applying
            }
            ItemStackResponseSlot response = new ItemStackResponseSlot(slot.getSlot(), slot.getSlot(),
                    item.isNull() ? 0 : item.getCount(), netId(item), "", 0, "");

            FullContainerName name = containerName(slot);
            ItemStackResponseContainer container = null;
            for (ItemStackResponseContainer existing : containers) {
                if (existing.getContainerName().equals(name)) {
                    container = existing;
                }
            }
            if (container == null) {
                container = new ItemStackResponseContainer(slot.getContainer(), new ArrayList<>(), name);
                containers.add(container);
            }
            container.getItems().add(response);
        }
        return containers;
    }

    private static void touch(List<ItemStackRequestSlotData> touched, ItemStackRequestSlotData slot) {
        if (slot.getContainer() == ContainerSlotType.CREATED_OUTPUT) {
            return; // not a real slot, so there's nothing to tell the client about it
        }
        for (ItemStackRequestSlotData existing : touched) {
            if (existing.getSlot() == slot.getSlot() && containerName(existing).equals(containerName(slot))) {
                return;
            }
        }
        touched.add(slot);
    }

    private static FullContainerName containerName(ItemStackRequestSlotData slot) {
        return slot.getContainerName() != null ? slot.getContainerName() : new FullContainerName(slot.getContainer(), null);
    }

    private void note(List<Change> changes, Place place) {
        if (place.kind() == Kind.CREATED) {
            return;
        }
        changes.add(new Change(place, netId(get(place))));
    }

    private static Integer lastChange(List<Change> changes, Place place) {
        Integer netId = null;
        for (Change change : changes) {
            if (change.place().equals(place)) {
                netId = change.netId();
            }
        }
        return netId;
    }

    private ItemData get(Place place) {
        return switch (place.kind()) {
            case MAIN -> main[place.index()];
            case ARMOR -> armor[place.index()];
            case OFFHAND -> offhand;
            case CURSOR -> cursor;
            case CRAFTING -> crafting[place.index()];
            case CREATED -> created;
        };
    }

    private void set(Place place, ItemData item) {
        switch (place.kind()) {
            case MAIN -> main[place.index()] = item;
            case ARMOR -> armor[place.index()] = item;
            case OFFHAND -> offhand = item;
            case CURSOR -> cursor = item;
            case CRAFTING -> crafting[place.index()] = item;
            case CREATED -> created = item;
        }
    }

    /** Whether two stacks are the same item and could join up. */
    private static boolean sameItem(ItemData a, ItemData b) {
        return a.getDefinition().getRuntimeId() == b.getDefinition().getRuntimeId()
                && a.getDamage() == b.getDamage()
                && Objects.equals(a.getTag(), b.getTag())
                && blockId(a) == blockId(b);
    }

    private static int maxStack(ItemData item) {
        return VanillaData.get().maxStackSize(item);
    }

    private static int blockId(ItemData item) {
        return item.getBlockDefinition() != null ? item.getBlockDefinition().getRuntimeId() : 0;
    }

    private static int netId(ItemData item) {
        return item.isNull() ? 0 : item.getNetId();
    }

    private static ItemData withCount(ItemData item, int count, int netId) {
        return item.toBuilder().count(count).usingNetId(true).netId(netId).build();
    }

    private static ItemData removed(ItemData item, int count) {
        if (item.isNull() || item.getCount() <= count) {
            return ItemData.AIR;
        }
        return item.toBuilder().count(item.getCount() - count).build();
    }

    private static ItemData[] emptySlots(int size) {
        ItemData[] slots = new ItemData[size];
        Arrays.fill(slots, ItemData.AIR);
        return slots;
    }

    private static InventoryContentPacket content(int containerId, ItemData[] slots) {
        InventoryContentPacket packet = new InventoryContentPacket();
        packet.setContainerId(containerId);
        packet.setContents(List.of(slots));
        return packet;
    }

    private static InventorySlotPacket uiSlot(int slot, ItemData item) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(ContainerId.UI);
        packet.setSlot(slot);
        packet.setItem(item);
        return packet;
    }

    /** The inventory's slots as they were before a request, to put back if it's refused. */
    private class Snapshot {
        private final ItemData[] main = PlayerInventory.this.main.clone();
        private final ItemData[] armor = PlayerInventory.this.armor.clone();
        private final ItemData[] crafting = PlayerInventory.this.crafting.clone();
        private final ItemData offhand = PlayerInventory.this.offhand;
        private final ItemData cursor = PlayerInventory.this.cursor;
        private final int droppedCount = PlayerInventory.this.dropped.size();

        void restore() {
            // Whatever the refused request would have thrown out stays in the inventory.
            dropped.subList(droppedCount, dropped.size()).clear();
            PlayerInventory.this.main = main;
            PlayerInventory.this.armor = armor;
            PlayerInventory.this.crafting = crafting;
            PlayerInventory.this.offhand = offhand;
            PlayerInventory.this.cursor = cursor;
        }
    }
}
