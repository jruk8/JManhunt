package com.jruk8.jmanhunt.match.prestart;

import lombok.Setter;
import org.bukkit.Location;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable per-side headstart state; the countdown ticks in the shared ticker. */
public final class HeadstartState {
    @Setter
    private boolean armed;
    @Setter
    private int remaining;
    private final Map<UUID, Location> returnPoints = new HashMap<>();

    /** Armed when configured: the side will be held once its countdown begins. */
    public boolean armed() {
        return armed;
    }

    public int remaining() {
        return remaining;
    }

    public Map<UUID, Location> returnPoints() {
        return returnPoints;
    }
}
