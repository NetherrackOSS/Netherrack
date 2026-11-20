package com.netherrack.server.plugin;

import com.netherrack.api.plugin.Plugin;
import com.netherrack.api.scheduler.Scheduler;
import com.netherrack.api.scheduler.TaskHandler;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Presents the server's tick scheduler to plugins with ids, repeating tasks and cancellation.
 * <p>
 * The underlying scheduler already runs everything on the single tick thread; this layer only adds
 * the bookkeeping plugins need. Tasks are keyed by a process-wide id and remember their owner, so
 * disabling a plugin cancels all of its work before its jar is closed.
 */
public final class PluginScheduler implements Scheduler {

    private final com.netherrack.server.util.Scheduler tickScheduler;
    private final AtomicInteger nextId = new AtomicInteger();
    private final ConcurrentMap<Integer, PluginTask> tasks = new ConcurrentHashMap<>();

    public PluginScheduler(com.netherrack.server.util.Scheduler tickScheduler) {
        this.tickScheduler = tickScheduler;
    }

    @Override
    public TaskHandler runTask(Plugin plugin, Runnable task) {
        return schedule(plugin, task, 1, 0);
    }

    @Override
    public TaskHandler runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        return schedule(plugin, task, delayTicks, 0);
    }

    @Override
    public TaskHandler runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("a repeating task needs a positive period");
        }
        return schedule(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void cancelTask(int taskId) {
        PluginTask task = tasks.remove(taskId);
        if (task != null) {
            task.markCancelled();
        }
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        for (PluginTask task : List.copyOf(tasks.values())) {
            if (task.getOwner() == plugin) {
                cancelTask(task.getTaskId());
            }
        }
    }

    /** Cancels everything, used when the server is shutting down. */
    public void cancelAll() {
        for (int id : List.copyOf(tasks.keySet())) {
            cancelTask(id);
        }
    }

    void remove(int taskId) {
        tasks.remove(taskId);
    }

    private TaskHandler schedule(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (plugin == null || task == null) {
            throw new IllegalArgumentException("a task needs both an owner and a body");
        }
        int id = nextId.incrementAndGet();
        PluginTask scheduled = new PluginTask(this, tickScheduler, id, plugin, task,
                (int) Math.min(Integer.MAX_VALUE, periodTicks));
        tasks.put(id, scheduled);
        tickScheduler.schedule((int) Math.min(Integer.MAX_VALUE, Math.max(1, delayTicks)), scheduled::run);
        return scheduled;
    }
}
