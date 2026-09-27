package com.jruk8.jmanhunt.command;

import org.bukkit.Location;

/**
 * Location primitives for JMHScript: canonical 6-element lists
 * shared by respawn event args and the location tags.
 */
public final class TagLocations {

    private TagLocations() {
    }

    /**
     * Canonical location list {@code [x, y, z, pitch, yaw,
     * dimension]}: coords via {@link TagMath#formatNumber}, pitch
     * before yaw, dimension as the world name passed in. The name
     * stays a parameter (instead of reading the location world) so
     * callers with a bare location stay unit-testable.
     */
    public static String formatLocation(Location location, String worldName) {
        return "[" + TagMath.formatNumber(location.getX()) + ", "
                + TagMath.formatNumber(location.getY()) + ", "
                + TagMath.formatNumber(location.getZ()) + ", "
                + TagMath.formatNumber(location.getPitch()) + ", "
                + TagMath.formatNumber(location.getYaw()) + ", "
                + worldName + "]";
    }
}
