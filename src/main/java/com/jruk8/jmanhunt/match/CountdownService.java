package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.core.TaskScheduler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shared per-second countdown ticker: headstarts, autostart, the
 * pre-start must-hit wait, and join and respawn holds all count
 * through here instead of running private timer forks. Ticks carry
 * the whole seconds remaining; done fires once at zero.
 */
public final class CountdownService {
    /**
     * Shared announcement ladder, whole seconds remaining: 10h40m
     * down to the final 3, 2, 1.
     */
    public static final Set<Integer> LADDER = Set.of(38400, 19200, 9600, 4800, 2400, 1200,
            600, 300, 150, 90, 60, 30, 15, 10, 3, 2, 1);

    /** True when the remaining seconds sit on the ladder. */
    public static boolean onLadder(int remainingSeconds) {
        return remainingSeconds > 0 && LADDER.contains(remainingSeconds);
    }

    /**
     * Announces one tick for the key: the first tick always
     * announces (the initial mark), ladder marks within two seconds
     * of it are skipped, then the plain ladder runs. Returns true
     * when this tick is a broadcast mark. Marks clear with cancel.
     */
    public boolean pollMark(Object key, int remainingSeconds) {
        if (remainingSeconds <= 0) {
            return false;
        }
        Integer initial = marks.get(key);
        if (initial == null) {
            marks.put(key, remainingSeconds);
            return true;
        }
        if (!settled.contains(key)) {
            if (!onLadder(remainingSeconds) || Math.abs(remainingSeconds - initial) <= 2) {
                return false;
            }
            settled.add(key);
            return true;
        }
        return onLadder(remainingSeconds);
    }

    private static final class Countdown {
        BukkitTask task;
        int remaining;
        final IntConsumer tick;
        final Runnable done;

        Countdown(int remaining, IntConsumer tick, Runnable done) {
            this.remaining = remaining;
            this.tick = tick;
            this.done = done;
        }
    }

    private final TaskScheduler tasks;
    private final Map<Object, Countdown> countdowns = new HashMap<>();
    private final Map<Object, Integer> marks = new HashMap<>();
    private final Set<Object> settled = new HashSet<>();

    public CountdownService(TaskScheduler tasks) {
        this.tasks = tasks;
    }

    /**
     * Starts a countdown under the key, replacing any previous one.
     * Tick fires at once with the full total (the initial mark),
     * then every second with the whole seconds remaining; done
     * fires once at zero. A non-positive total runs done at once
     * without scheduling. A tick that cancels its own key stops
     * the countdown before the timer is scheduled.
     */
    public void start(Object key, int totalSeconds, IntConsumer tick, Runnable done) {
        cancel(key);
        if (totalSeconds <= 0) {
            done.run();
            return;
        }
        Countdown countdown = new Countdown(totalSeconds, tick, done);
        countdowns.put(key, countdown);
        tick.accept(totalSeconds);
        if (countdowns.get(key) != countdown) {
            return;
        }
        countdown.task = tasks.runTimer(() -> tick(key), 20L, 20L);
    }

    private void tick(Object key) {
        Countdown countdown = countdowns.get(key);
        if (countdown == null) {
            return;
        }
        countdown.remaining--;
        if (countdown.remaining <= 0) {
            cancel(key);
            countdown.done.run();
            return;
        }
        countdown.tick.accept(countdown.remaining);
    }

    /** Cancels the countdown under the key. False when none ran. */
    public boolean cancel(Object key) {
        marks.remove(key);
        settled.remove(key);
        Countdown countdown = countdowns.remove(key);
        if (countdown == null) {
            return false;
        }
        if (countdown.task != null) {
            countdown.task.cancel();
        }
        return true;
    }

    /** True while a countdown runs under the key. */
    public boolean running(Object key) {
        return countdowns.containsKey(key);
    }
}
