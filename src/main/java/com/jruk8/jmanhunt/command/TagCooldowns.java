package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;

/**
 * Cooldown tags: {@code <cooldown:key,seconds>} stamps and gates,
 * {@code <cooldown.get:key,seconds>} reads the remaining window,
 * and {@code <cooldown.reset:key>} clears. Keys are plain strings;
 * compose per-player keys with {@code <p>} (see the docs). No
 * Bukkit types: the store arrives through the context.
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
        Optional<String> key = CommandPlaceholders.parsePickItem(segments.get(0));
        if (key.isEmpty() || key.get().isBlank()) {
            return warn(tag, name, context, "needs a non-blank key: " + tag);
        }
        if (name.equals("cooldown.reset")) {
            context.cooldowns().reset(context.matchId(), key.get().strip());
            return "true";
        }
        Optional<Double> seconds = seconds(tag, name, segments.get(1), context);
        if (seconds.isEmpty()) {
            return "null";
        }
        if (name.equals("cooldown.get")) {
            return TagMath.formatNumber(context.cooldowns()
                    .remainingSeconds(context.matchId(), key.get().strip(), seconds.get()));
        }
        return context.cooldowns().tryAcquire(context.matchId(), key.get().strip(), seconds.get())
                ? "true" : "false";
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
            context.scope().warn("Tag <" + name + "> needs seconds like <" + name + ":key,5>: "
                    + tag);
            return Optional.empty();
        }
        if (!Double.isFinite(seconds) || seconds < 0) {
            context.scope().warn("Tag <" + name + "> needs seconds like <" + name + ":key,5>: "
                    + tag);
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

    private static int arity(String name) {
        return name.equals("cooldown.reset") ? 1 : 2;
    }

    private static String example(String name) {
        return switch (name) {
            case "cooldown.get" -> "<cooldown.get:key,seconds>";
            case "cooldown.reset" -> "<cooldown.reset:key>";
            default -> "<cooldown:key,seconds>";
        };
    }
}
