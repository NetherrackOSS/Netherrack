package com.netherrack.server.entity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Hands out entity ids. Every entity in the world - players and dropped items alike -
 * needs its own, since clients look all of them up by the same id. Netherrack uses each
 * id as both the entity's runtime and unique id.
 */
public final class EntityIds {

    private static final AtomicLong NEXT = new AtomicLong(1);

    private EntityIds() {
    }

    public static long next() {
        return NEXT.getAndIncrement();
    }
}
