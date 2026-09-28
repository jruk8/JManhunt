package com.jruk8.jmanhunt.command;

import org.bukkit.Location;
import java.util.List;
import java.util.Optional;

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

    /**
     * {@code <plocation:player>}: the online player's location as a
     * canonical 6-element list. Offline or unknown players warn plus
     * {@code "null"}.
     */
    static String plocation(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <plocation> needs a player like <plocation:Steve>: " + tag);
            return "null";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <plocation> needs a player like <plocation:Steve>: " + tag);
            return "null";
        }
        Optional<Location> location = context.roster().locationOf(name.get().strip());
        if (location.isEmpty() || location.get().getWorld() == null) {
            context.scope().warn("Tag <plocation> found no online player '"
                    + name.get().strip() + "': " + tag);
            return "null";
        }
        return formatLocation(location.get(), location.get().getWorld().getName());
    }

    /**
     * {@code <distance:loc1,loc2>}: 3D Euclidean distance on x, y, z
     * only (extra elements ignored, so full primitives work;
     * pitch/yaw ignored). Two full primitives in different
     * dimensions warn plus {@code "null"}; short lists carry no
     * dimension and always compare. Non-lists and non-numeric
     * coords warn plus {@code "null"}.
     */
    static String distance(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <distance> needs two locations like "
                    + "<distance:loc1,loc2>: " + tag);
            return "null";
        }
        Optional<double[]> first = coords(parts.get(0));
        Optional<double[]> second = coords(parts.get(1));
        if (first.isEmpty() || second.isEmpty()) {
            context.scope().warn("Tag <distance> needs numeric x, y, z in both lists: " + tag);
            return "null";
        }
        Optional<String> firstDimension = dimension(parts.get(0));
        Optional<String> secondDimension = dimension(parts.get(1));
        if (firstDimension.isPresent() && secondDimension.isPresent()
                && !firstDimension.get().equals(secondDimension.get())) {
            context.scope().warn("Tag <distance> needs both locations in the same dimension, got '"
                    + firstDimension.get() + "' and '" + secondDimension.get() + "': " + tag);
            return "null";
        }
        double dx = first.get()[0] - second.get()[0];
        double dy = first.get()[1] - second.get()[1];
        double dz = first.get()[2] - second.get()[2];
        return TagMath.formatNumber(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** Sixth list item (dimension); empty when the list is not a full primitive. */
    private static Optional<String> dimension(String segment) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        List<String> elements = TagLists.parse(item.get());
        if (elements.size() < 6) {
            return Optional.empty();
        }
        return Optional.of(elements.get(5).strip());
    }

    /** First three list items as doubles; empty when missing or non-numeric. */
    private static Optional<double[]> coords(String segment) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        List<String> elements = TagLists.parse(item.get());
        if (elements.size() < 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new double[]{
                Double.parseDouble(elements.get(0)),
                Double.parseDouble(elements.get(1)),
                Double.parseDouble(elements.get(2))});
        } catch (NumberFormatException unmatched) {
            return Optional.empty();
        }
    }
}
