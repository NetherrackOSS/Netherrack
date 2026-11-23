package com.netherrack.server.plugin;

import com.netherrack.api.plugin.Plugin;
import com.netherrack.api.scheduler.TaskHandler;
import com.netherrack.server.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One plugin task, tracked so its owner can cancel it on disable.
 * <p>
 * The task runs on the server tick, while cancellation can arrive from a plugin's own thread. A
 * cancelled task that is already queued simply does nothing when its turn comes. Repeating tasks
 * queue only their next run, so cancellation takes effect before the following tick.
 */
final class PluginTask implements TaskHandler {

    private final PluginScheduler manager;
    private final com.netherrack.server.util.Scheduler tickScheduler;
    private final int id;
    private final Plugin owner;
    private final Runnable body;
    private final int periodTicks;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    PluginTask(PluginScheduler manager, com.netherrack.server.util.Scheduler tickScheduler,
               int id, Plugin owner, Runnable body, int periodTicks) {
        this.manager = manager;
        this.tickScheduler = tickScheduler;
        this.id = id;
        this.owner = owner;
        this.body = body;
        this.periodTicks = periodTicks;
    }

    void run() {
        if (cancelled.get()) {
            return;
        }
        try {
            body.run();
        } catch (RuntimeException e) {
            Logger.error("Error in a task belonging to plugin " + owner.getName() + ": " + e);
        } finally {
            if (periodTicks > 0) {
                if (!cancelled.get()) {
                    tickScheduler.schedule(periodTicks, this::run);
                }
            } else {
                manager.remove(id);
            }
        }
    }

    @Override
    public int getTaskId() {
        return id;
    }

    @Override
    public Plugin getOwner() {
        return owner;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        manager.cancelTask(id);
    }

    void markCancelled() {
        cancelled.set(true);
    }
}
