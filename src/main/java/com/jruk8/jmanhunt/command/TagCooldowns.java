package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cooldown tags: {@code <pcooldown:player,key,seconds>} gates one
 * player, {@code <gcooldown:key,seconds>} gates match-wide, each
 * with {@code .get} (remaining window) and {@code .reset} (clear)
 * siblings. The set tags stamp only when the gate opens (no stamp
 * or the window elapsed) and yield {@code "true"} then, else
 * {@code "false"} without touching the stamp. Player keys
 * namespace below the lower-cased name; offline or unknown players
 * resolve {@code null} silently. No Bukkit types: the store
 * arrives through the context.
 */
public final class TagCooldowns {

    private TagCooldowns() {
    }

    /** Pure cooldown-op entry for tag evaluation. */
    static String resolve(String tag, String name, String args, TagContext context) {
        List<String> segments = TagLists.splitTopLevel(args);
        if (segments.size() != arity(name)) {
            return warn(tag, name, context, "needs " + arity(name) + " args like "
                    + example(name) + ": " + tag);
        }
        Optional<String> key = CommandPlaceholders.parsePickItem(
                segments.get(isPlayer(name) ? 1 : 0));
        if (key.isEmpty() || key.get().isBlank()) {
            return warn(tag, name, context, "needs a non-blank key: " + tag);
        }
        Optional<String> storeKey = storeKey(tag, name, segments, key.get().strip(), context);
        if (storeKey.isEmpty()) {
            return "null";
        }
        if (name.endsWith(".reset")) {
            context.cooldowns().reset(context.matchId(), storeKey.get());
            return "true";
        }
        Optional<Double> seconds = seconds(tag, name,
                segments.get(segments.size() - 1), context);
        if (seconds.isEmpty()) {
            return "null";
        }
        if (name.endsWith(".get")) {
            return TagMath.formatNumber(context.cooldowns()
                    .remainingSeconds(context.matchId(), storeKey.get(), seconds.get()));
        }
        return context.cooldowns().tryAcquire(context.matchId(), storeKey.get(), seconds.get())
                ? "true" : "false";
    }

    /**
     * Store key for one op: match-wide keys pass through, player
     * keys namespace below the lower-cased name. Blank players warn
     * plus empty; offline or unknown players yield empty silently.
     */
    private static Optional<String> storeKey(String tag, String name, List<String> segments,
            String key, TagContext context) {
        if (!isPlayer(name)) {
            return Optional.of(key);
        }
        Optional<String> player = CommandPlaceholders.parsePickItem(segments.get(0));
        if (player.isEmpty() || player.get().isBlank()) {
            context.scope().warn("Tag <" + name + "> needs a player like <" + name
                    + ":Steve,key>: " + tag);
            return Optional.empty();
        }
        String trimmed = player.get().strip();
        if (context.roster().locationOf(trimmed).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed.toLowerCase(Locale.ROOT) + ":" + key);
    }

    /** Non-negative finite window seconds; misuse warns plus empty. */
    private static Optional<Double> seconds(String tag, String name, String raw,
            TagContext context) {
        Optional<String> item = CommandPlaceholders.parsePickItem(raw);
        if (item.isEmpty() && !raw.isBlank()) {
            context.scope().warn("Tag <" + name + "> mixes quotes: " + tag);
            return Optional.empty();
        }
        double seconds;
        try {
            seconds = Double.parseDouble(item.orElse("").strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag <" + name + "> needs seconds like " + example(name)
                    + ": " + tag);
            return Optional.empty();
        }
        if (!Double.isFinite(seconds) || seconds < 0) {
            context.scope().warn("Tag <" + name + "> needs seconds like " + example(name)
                    + ": " + tag);
            return Optional.empty();
        }
        return Optional.of(seconds);
    }

    private static String warn(String tag, String name, TagContext context, String detail) {
        context.scope().warn("Tag <" + name + "> " + detail);
        return "null";
    }

    /**
     * Edit-time arity for the cooldown ops: top-level split, quote
     * hygiene, plus the per-op shape. Mirrors the runtime warns.
     */
    static Optional<String> syntaxError(String name, String args) {
        int count = arity(name);
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + count + " args like "
                    + example(name) + ".");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != count) {
            return Optional.of("Tag <" + name + "> needs " + count + " args like "
                    + example(name) + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    private static boolean isPlayer(String name) {
        return name.equals("pcooldown") || name.equals("pcooldown.get")
                || name.equals("pcooldown.reset");
    }

    private static int arity(String name) {
        return switch (name) {
            case "pcooldown", "pcooldown.get" -> 3;
            case "pcooldown.reset", "gcooldown", "gcooldown.get" -> 2;
            default -> 1;
        };
    }

    private static String example(String name) {
        return switch (name) {
            case "pcooldown" -> "<pcooldown:player,key,seconds>";
            case "pcooldown.get" -> "<pcooldown.get:player,key,seconds>";
            case "pcooldown.reset" -> "<pcooldown.reset:player,key>";
            case "gcooldown" -> "<gcooldown:key,seconds>";
            case "gcooldown.get" -> "<gcooldown.get:key,seconds>";
            default -> "<gcooldown.reset:key>";
        };
    }
}
