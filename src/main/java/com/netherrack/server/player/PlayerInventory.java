package com.netherrack.server.player;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Arrays;
import java.util.List;

/**
 * A player's main inventory: 36 slots, the first 9 of which are the hotbar. The server's
 * copy is the real one - StartGame makes inventories server-authoritative, so the client
 * shows what it's sent and asks before changing anything.
 * <p>
 * Every stack gets its own network id, which the client uses to refer to it when it
 * asks to change it.
 */
public class PlayerInventory {

    public static final int SIZE = 36;
    public static final int HOTBAR_SIZE = 9;

    private final ItemData[] slots = new ItemData[SIZE];
    private int nextNetId = 1;

    public PlayerInventory() {
        Arrays.fill(slots, ItemData.AIR);
    }

    public synchronized ItemData get(int slot) {
        return slots[slot];
    }

    public synchronized void set(int slot, ItemData item) {
        slots[slot] = item.isNull() ? ItemData.AIR : item.toBuilder().usingNetId(true).netId(nextNetId++).build();
    }

    /** Uses up one item from the slot (e.g. a block that was just placed) and returns what's left. */
    public synchronized ItemData useOne(int slot) {
        ItemData item = slots[slot];
        if (item.isNull()) {
            return item;
        }
        slots[slot] = item.getCount() > 1 ? item.toBuilder().count(item.getCount() - 1).build() : ItemData.AIR;
        return slots[slot];
    }

    public synchronized List<ItemData> getContents() {
        return List.of(slots);
    }
}
