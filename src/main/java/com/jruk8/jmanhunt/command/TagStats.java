package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Stat tags behind {@code <pstat>} and {@code <gstat>}. Keys match
 * case-blindly; unknown keys are tag errors listing the valid ones.
 * Values come from the context backend, so this stays pure while the
 * match package owns every Bukkit read.
 */
public final class TagStats {

    /** Player keys: live vitals plus match counters. */
    public static final List<String> PSTAT_KEYS =
            List.of("health", "hunger", "mobs-killed", "achievements-gained");

    /** Global keys: the match clock plus the world clock. */
    public static final List<String> GSTAT_KEYS = List.of("duration", "daytime");

    private TagStats() {
    }

    /** Normalizes a stat key: trimmed and case-blind. */
    public static String normalizeKey(String key) {
        return key.strip().toLowerCase(Locale.ROOT);
    }

    /** True when the normalized key names a player stat. */
    public static boolean isPlayerKey(String normalized) {
        return PSTAT_KEYS.contains(normalized);
    }

    /** True when the normalized key names a global stat. */
    public static boolean isGlobalKey(String normalized) {
        return GSTAT_KEYS.contains(normalized);
    }

    /**
     * Resolves {@code <pstat:player,key>}. Offline players warn and
     * yield empty; missing counters read 0 through the backend.
     */
    static String player(String tag, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <pstat> needs a player plus a key: " + tag);
            return "";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        Optional<String> key = CommandPlaceholders.parsePickItem(parts.get(1));
        if (name.isEmpty() || key.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <pstat> has a malformed player or key: " + tag);
            return "";
        }
        String normalized = normalizeKey(key.get());
        if (!isPlayerKey(normalized)) {
            context.scope().warn("Unknown <pstat> key '" + key.get().strip() + "'. Valid keys: "
                    + String.join(", ", PSTAT_KEYS) + ": " + tag);
            return "";
        }
        Optional<String> value = context.statValues().player(name.get().strip(), normalized);
        if (value.isEmpty()) {
            context.scope().warn(
                    "Tag <pstat> player '" + name.get().strip() + "' is offline: " + tag);
            return "";
        }
        return value.get();
    }

    /**
     * Resolves {@code <gstat:key>}. Duration needs a live match and
     * daytime a loaded world; either missing warns and yields 0 so
     * math around the tag stays total.
     */
    static String global(String tag, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <gstat> needs one key: " + tag);
            return "";
        }
        Optional<String> key = CommandPlaceholders.parsePickItem(parts.get(0));
        if (key.isEmpty()) {
            context.scope().warn("Tag <gstat> has a malformed key: " + tag);
            return "";
        }
        String normalized = normalizeKey(key.get());
        if (!isGlobalKey(normalized)) {
            context.scope().warn("Unknown <gstat> key '" + key.get().strip() + "'. Valid keys: "
                    + String.join(", ", GSTAT_KEYS) + ": " + tag);
            return "";
        }
        Optional<String> value = context.statValues().global(normalized);
        if (value.isEmpty()) {
            String need = normalized.equals("duration") ? "a live match" : "a loaded world";
            context.scope().warn("Tag <gstat> needs " + need + ": " + tag);
            return "0";
        }
        return value.get();
    }
}
