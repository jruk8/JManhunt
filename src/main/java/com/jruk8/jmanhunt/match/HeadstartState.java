package com.jruk8.jmanhunt.match;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable per-side headstart state; the countdown ticks in GameManager. */
public final class HeadstartState {
    private boolean armed;
    private int remaining;
    private BukkitTask task;
    private final Map<UUID, Location> returnPoints = new HashMap<>();

    /** Armed when configured: the side will be held once its countdown begins. */
    public boolean armed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
    }

    public int remaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    /** Non-null while the side is held in spectator with a live countdown. */
    public BukkitTask task() {
        return task;
    }

    public void setTask(BukkitTask task) {
        this.task = task;
    }

    public Map<UUID, Location> returnPoints() {
        return returnPoints;
    }
}