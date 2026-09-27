package com.jruk8.jmanhunt.compass;

import org.bukkit.Location;
import java.util.UUID;

/** One snapshotted live location, written only by refresh events. */
public record CompassSnapshot(UUID id, Location location) {
}
