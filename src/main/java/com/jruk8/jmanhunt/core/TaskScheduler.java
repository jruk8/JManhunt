package com.jruk8.jmanhunt.core;

import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Narrow Bukkit edge for scheduling and Plugin instance access.
 * Leaf services take this instead of the plugin; only the plugin
 * implements it.
 */
public interface TaskScheduler {
    /** Run on the main thread next tick. */
    default BukkitTask run(Runnable task) {
        return Bukkit.getScheduler().runTask(plugin(), task);
    }

    /** Run on the main thread after a delay. */
    default BukkitTask runLater(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin(), task, delayTicks);
    }

    /** Run on the main thread on a timer. */
    default BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin(), task, delayTicks, periodTicks);
    }

    /** Run on the main thread on a timer with task access. */
    default void runTimer(Consumer<BukkitTask> task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin(), task, delayTicks, periodTicks);
    }

    /** Run off the main thread. */
    default BukkitTask runAsync(Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin(), task);
    }

    /** Plugin instance for Bukkit APIs that demand one. */
    Plugin plugin();
}
