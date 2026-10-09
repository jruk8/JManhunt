package com.jruk8.jmanhunt.command;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.Comparator;
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
     * Canonical location list {@code [x, y, z, world, pitch, yaw]}:
     * coords via {@link TagMath#formatNumber}, world as the
     * normalized alias (see {@link #worldAlias}), never the raw
     * name. The location must have a world; callers null-check
     * first. Shared by the respawn event arg producer.
     */
    public static String formatLocation(Location location) {
        return "[" + TagMath.formatNumber(location.getX()) + ", "
                + TagMath.formatNumber(location.getY()) + ", "
                + TagMath.formatNumber(location.getZ()) + ", "
                + worldAlias(location.getWorld().getEnvironment().name(),
                        location.getWorld().getName()) + ", "
                + TagMath.formatNumber(location.getPitch()) + ", "
                + TagMath.formatNumber(location.getYaw()) + "]";
    }

    /**
     * Pure teleport target behind {@code <pteleport>},
     * {@code <rteleport>}, and {@code <gteleport>}: coords plus a
     * world ref, with null angles meaning keep the player's view.
     * No Bukkit types.
     */
    public record TeleportRequest(double x, double y, double z, String worldRef,
            Float pitch, Float yaw) {
        /** Canonical list shape, angles only when present. */
        public String format() {
            String base = "[" + TagMath.formatNumber(x) + ", " + TagMath.formatNumber(y)
                    + ", " + TagMath.formatNumber(z) + ", " + worldRef;
            if (pitch == null || yaw == null) {
                return base + "]";
            }
            return base + ", " + TagMath.formatNumber(pitch) + ", "
                    + TagMath.formatNumber(yaw) + "]";
        }
    }

    /**
     * Parses one location literal to a teleport target:
     * {@code [x, y, z, world]} or
     * {@code [x, y, z, world, pitch, yaw]}, pitch before yaw like
     * {@link #formatLocation}. Anything else yields empty; callers
     * warn.
     */
    public static Optional<TeleportRequest> parseTeleport(String raw) {
        if (!TagLists.isList(raw)) {
            return Optional.empty();
        }
        return teleportFromElements(TagLists.parse(raw));
    }

    /**
     * Validates already-split location elements to a teleport
     * target: 4 or 6 elements, finite numbers, a non-blank world.
     * Anything else yields empty; callers warn.
     */
    public static Optional<TeleportRequest> teleportFromElements(List<String> elements) {
        if (elements.size() != 4 && elements.size() != 6) {
            return Optional.empty();
        }
        Optional<Double> x = teleportNumber(elements.get(0));
        Optional<Double> y = teleportNumber(elements.get(1));
        Optional<Double> z = teleportNumber(elements.get(2));
        Optional<String> world = CommandPlaceholders.parsePickItem(elements.get(3));
        if (x.isEmpty() || y.isEmpty() || z.isEmpty() || world.isEmpty()
                || world.get().isBlank()) {
            return Optional.empty();
        }
        if (elements.size() == 4) {
            return Optional.of(new TeleportRequest(x.get(), y.get(), z.get(),
                    world.get().strip(), null, null));
        }
        Optional<Double> pitch = teleportNumber(elements.get(4));
        Optional<Double> yaw = teleportNumber(elements.get(5));
        if (pitch.isEmpty() || yaw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TeleportRequest(x.get(), y.get(), z.get(),
                world.get().strip(), pitch.get().floatValue(), yaw.get().floatValue()));
    }

    /** One finite location number, unquoted; empty when not a number. */
    private static Optional<Double> teleportNumber(String raw) {
        Optional<String> item = CommandPlaceholders.parsePickItem(raw);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        try {
            double value = Double.parseDouble(item.get().strip());
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    /**
     * Normalized world alias for primitives and {@code <pworld>}:
     * NETHER environments read {@code nether}, THE_END reads
     * {@code end} whatever the pooled name, and everything else
     * reads the raw world name. Keyed on environment, never on
     * name, so the configurable end base name needs no special
     * case. Pure SSOT for tests.
     */
    public static String worldAlias(String environmentName, String worldName) {
        if ("NETHER".equalsIgnoreCase(environmentName)) {
            return "nether";
        }
        if ("THE_END".equalsIgnoreCase(environmentName)) {
            return "end";
        }
        return worldName;
    }

    /**
     * {@code <plocation:player>}: the online player's location as a
     * canonical 6-element list. Offline or unknown players resolve
     * {@code "null"} silently (routine runtime state, not a script
     * error); only blank or malformed args warn.
     */
    static String plocation(String tag, String name, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <" + name + "> needs a player like <" + name + ":Steve>: "
                    + tag);
            return "null";
        }
        Optional<String> player = CommandPlaceholders.parsePickItem(parts.get(0));
        if (player.isEmpty() || player.get().isBlank()) {
            context.scope().warn("Tag <" + name + "> needs a player like <" + name + ":Steve>: "
                    + tag);
            return "null";
        }
        Optional<Location> location = context.roster().locationOf(player.get().strip());
        if (location.isEmpty() || location.get().getWorld() == null) {
            return "null";
        }
        return formatLocation(location.get());
    }

    /**
     * {@code <pworld:player>} (alias {@code <world:player>}): the
     * player's world as the normalized alias, through the same
     * helper as every primitive world slot. Offline or unknown
     * players resolve {@code "null"} silently; only blank or
     * malformed args warn.
     */
    static String playerWorld(String tag, String name, String args, TagContext context) {
        Optional<Location> spot = playerSpot(tag, args, name, context);
        if (spot.isEmpty()) {
            return "null";
        }
        Location location = spot.get();
        if (location.getWorld() == null) {
            return "null";
        }
        return worldAlias(location.getWorld().getEnvironment().name(),
                location.getWorld().getName());
    }

    /**
     * {@code <px:player>}, {@code <py>}, {@code <pz>},
     * {@code <pyaw>}, {@code <ppitch>}: one coordinate of the online
     * player. Offline or unknown players resolve {@code "null"}
     * silently; only blank or malformed args warn.
     */
    static String playerCoord(String tag, String name, String args, TagContext context) {
        Optional<Location> spot = playerSpot(tag, args, name, context);
        if (spot.isEmpty()) {
            return "null";
        }
        Location location = spot.get();
        if (location.getWorld() == null) {
            return "null";
        }
        return switch (name) {
            case "px" -> TagMath.formatNumber(location.getX());
            case "py" -> TagMath.formatNumber(location.getY());
            case "pz" -> TagMath.formatNumber(location.getZ());
            case "pyaw" -> TagMath.formatNumber(location.getYaw());
            default -> TagMath.formatNumber(location.getPitch());
        };
    }

    /**
     * One player arg resolved to a live location: blank or malformed
     * args warn, offline or unknown players stay silent. Both
     * read {@code "null"}.
     */
    private static Optional<Location> playerSpot(String tag, String args, String root,
            TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <" + root + "> needs a player like <" + root + ":Steve>: "
                    + tag);
            return Optional.empty();
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <" + root + "> needs a player like <" + root + ":Steve>: "
                    + tag);
            return Optional.empty();
        }
        Optional<Location> location = context.roster().locationOf(name.get().strip());
        if (location.isEmpty()) {
            return Optional.empty();
        }
        return location;
    }


    /**
     * {@code <distance:loc1,loc2>}: 3D Euclidean distance on x, y, z
     * only (extra elements ignored, so full primitives work;
     * pitch/yaw ignored). Null, blank, and unparseable sides resolve
     * {@code "null"} silently, as do two full primitives in different
     * dimensions; short lists carry no dimension and always compare.
     * Non-null malformed sides warn plus {@code "null"}.
     */
    static String distance(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <distance> needs two locations like "
                    + "<distance:loc1,loc2>: " + tag);
            return "null";
        }
        if (isNullish(parts.get(0)) || isNullish(parts.get(1))) {
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
            return "null";
        }
        double dx = first.get()[0] - second.get()[0];
        double dy = first.get()[1] - second.get()[1];
        double dz = first.get()[2] - second.get()[2];
        return TagMath.formatNumber(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * True when a distance side is blank, literal null, or fails pick
     * parsing: silent {@code "null"}, never a warning.
     */
    private static boolean isNullish(String segment) {
        if (segment == null || segment.isBlank()) {
            return true;
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        return item.isEmpty() || item.get().isBlank() || item.get().strip().equalsIgnoreCase("null");
    }

    /**
     * {@code <overlap-players:origin,role,radius,max>}: names within
     * radius blocks (3D Euclidean, at or under) of a location
     * primitive origin, filtered to HUNTER, SPEEDRUNNER, or ALL
     * (both), capped at max, nearest-first with name order breaking
     * ties. A 4-plus-element origin scopes to its world alias (index
     * 3); 3-element origins match all worlds. Pitch and yaw never
     * matter. Empty matches read {@code []}.
     */
    static String overlapPlayers(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 4) {
            context.scope().warn("Tag <overlap-players> needs an origin, a role, a radius, "
                    + "and a max like <overlap-players:[0, 64, 0],HUNTER,10,5>: " + tag);
            return "null";
        }
        Optional<double[]> origin = originCoords(tag, parts.get(0), "overlap-players", context);
        Optional<String> role = FlagStore.parseRole(tag, "overlap-players", parts.get(1),
                context.scope());
        Optional<Double> radius = proximityRadius(tag, parts.get(2), "overlap-players", context);
        Optional<Integer> max = proximityMax(tag, parts.get(3), "overlap-players", context);
        if (origin.isEmpty() || role.isEmpty() || radius.isEmpty() || max.isEmpty()) {
            return "null";
        }
        return selectProximity(origin.get(), originWorld(parts.get(0)), role.get(),
                radius.get(), max.get(), null, context);
    }

    /**
     * {@code <nearby-players:player,role,radius,max>}: like
     * overlap-players with a player's live location as the origin,
     * scoped to their world, never including the command sender.
     * Offline or unknown players resolve {@code "null"} silently.
     */
    static String nearbyPlayers(String tag, String args, String senderName, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 4) {
            context.scope().warn("Tag <nearby-players> needs a player, a role, a radius, "
                    + "and a max like <nearby-players:Steve,HUNTER,10,5>: " + tag);
            return "null";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <nearby-players> needs a player like "
                    + "<nearby-players:Steve,HUNTER,10,5>: " + tag);
            return "null";
        }
        Optional<Location> spot = context.roster().locationOf(name.get().strip());
        if (spot.isEmpty() || spot.get().getWorld() == null) {
            return "null";
        }
        Optional<String> role = FlagStore.parseRole(tag, "nearby-players", parts.get(1),
                context.scope());
        Optional<Double> radius = proximityRadius(tag, parts.get(2), "nearby-players", context);
        Optional<Integer> max = proximityMax(tag, parts.get(3), "nearby-players", context);
        if (role.isEmpty() || radius.isEmpty() || max.isEmpty()) {
            return "null";
        }
        Location origin = spot.get();
        return selectProximity(new double[] {origin.getX(), origin.getY(), origin.getZ()},
                Optional.of(worldAlias(origin.getWorld().getEnvironment().name(),
                        origin.getWorld().getName())),
                role.get(), radius.get(), max.get(), senderName, context);
    }

    /**
     * Shared proximity scan: role filter, optional sender exclusion,
     * optional world scope, radius cap, nearest-first order, max cap.
     */
    private static String selectProximity(double[] origin, Optional<String> scopeWorld,
            String role, double radius, int max, String excludeName, TagContext context) {
        List<ScoredName> hits = new ArrayList<>();
        for (RosterValues.NearbyParticipant candidate : context.roster().nearbyParticipants()) {
            if (!proximityRoleMatches(role, candidate.role())) {
                continue;
            }
            if (excludeName != null && candidate.name().equalsIgnoreCase(excludeName)) {
                continue;
            }
            if (scopeWorld.isPresent() && !worldAlias(candidate.environment(), candidate.world())
                    .equals(scopeWorld.get())) {
                continue;
            }
            double dx = candidate.x() - origin[0];
            double dy = candidate.y() - origin[1];
            double dz = candidate.z() - origin[2];
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > radius) {
                continue;
            }
            hits.add(new ScoredName(candidate.name(), distance));
        }
        hits.sort(Comparator.comparingDouble(ScoredName::distance)
                .thenComparing(ScoredName::name, String.CASE_INSENSITIVE_ORDER));
        return TagLists.format(hits.stream().limit(max).map(ScoredName::name).toList());
    }

    /** One proximity hit for nearest-first ordering. */
    private record ScoredName(String name, double distance) {
    }

    /** True when a candidate role falls under a proximity filter. */
    private static boolean proximityRoleMatches(String filter, String candidateRole) {
        if (filter.equalsIgnoreCase("ALL")) {
            return candidateRole.equalsIgnoreCase("HUNTER")
                    || candidateRole.equalsIgnoreCase("SPEEDRUNNER");
        }
        return candidateRole.equalsIgnoreCase(filter);
    }

    /** First three origin elements as doubles; warns on anything less numeric. */
    private static Optional<double[]> originCoords(String tag, String segment, String root,
            TagContext context) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        if (item.isEmpty()) {
            context.scope().warn("Tag <" + root + "> needs a location origin with numeric "
                    + "x, y, z: " + tag);
            return Optional.empty();
        }
        List<String> elements = TagLists.parse(item.get());
        if (elements.size() < 3) {
            context.scope().warn("Tag <" + root + "> needs a location origin with numeric "
                    + "x, y, z: " + tag);
            return Optional.empty();
        }
        try {
            return Optional.of(new double[] {
                    Double.parseDouble(elements.get(0).strip()),
                    Double.parseDouble(elements.get(1).strip()),
                    Double.parseDouble(elements.get(2).strip())});
        } catch (NumberFormatException unmatched) {
            context.scope().warn("Tag <" + root + "> needs a location origin with numeric "
                    + "x, y, z: " + tag);
            return Optional.empty();
        }
    }

    /** Origin world scope (index 3); empty for 3-element origins. */
    private static Optional<String> originWorld(String segment) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        List<String> elements = TagLists.parse(item.get());
        if (elements.size() < 4) {
            return Optional.empty();
        }
        return Optional.of(elements.get(3).strip());
    }

    /** Radius at or above 0; warns on anything else (including NaN). */
    private static Optional<Double> proximityRadius(String tag, String segment, String root,
            TagContext context) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        try {
            double radius = item.isPresent() ? Double.parseDouble(item.get().strip())
                    : Double.NaN;
            if (!(radius >= 0)) {
                throw new NumberFormatException("radius");
            }
            return Optional.of(radius);
        } catch (NumberFormatException unmatched) {
            context.scope().warn("Tag <" + root + "> needs a radius at or above 0: " + tag);
            return Optional.empty();
        }
    }

    /** Positive whole max; warns on anything else. */
    private static Optional<Integer> proximityMax(String tag, String segment, String root,
            TagContext context) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        try {
            int max = item.isPresent() ? Integer.parseInt(item.get().strip()) : 0;
            if (max <= 0) {
                throw new NumberFormatException("max");
            }
            return Optional.of(max);
        } catch (NumberFormatException unmatched) {
            context.scope().warn("Tag <" + root + "> needs a positive whole max: " + tag);
            return Optional.empty();
        }
    }

    /** Fourth list item (world alias); empty when the list is not a full primitive. */
    private static Optional<String> dimension(String segment) {
        Optional<String> item = CommandPlaceholders.parsePickItem(segment);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        List<String> elements = TagLists.parse(item.get());
        if (elements.size() < 6) {
            return Optional.empty();
        }
        return Optional.of(elements.get(3).strip());
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
