package com.jruk8.jmanhunt.match.prestart;

import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable per-side headstart state; the countdown ticks in GameManager. */
public final class HeadstartState {
    @Setter
    private boolean armed;
    @Setter
    private int remaining;
    @Setter
    private BukkitTask task;
    private final Map<UUID, Location> returnPoints = new HashMap<>();

    /** Armed when configured: the side will be held once its countdown begins. */
    public boolean armed() {
        return armed;
    }

    public int remaining() {
        return remaining;
    }

    /** Non-null while the side is held in spectator with a live countdown. */
    public BukkitTask task() {
        return task;
    }

    public Map<UUID, Location> returnPoints() {
        return returnPoints;
    }
}
