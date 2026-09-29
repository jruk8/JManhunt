package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Inventory tag behind {@code <phasitem:player,item,qty>}: true when
 * the named online player holds at least qty of the item in storage
 * contents. Reads through the context roster; managers own every
 * Bukkit call. Always resolves "true" or "false" so {@code == true}
 * comparisons stay total.
 */
public final class TagItems {

    private TagItems() {
    }

    /**
     * Normalizes a material key: trims, strips one optional
     * {@code minecraft:} prefix (any case), and uppercases for the
     * enum lookup. Shared by every countItem implementation.
     */
    public static String normalizeMaterialKey(String raw) {
        String key = raw.strip();
        if (key.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            key = key.substring("minecraft:".length());
        }
        return key.toUpperCase(Locale.ROOT);
    }

    /**
     * Edit-time shape for {@code <phasitem>}: a player, an item, plus
     * an optional count, quotes parsed. Mirrors the runtime warns.
     */
    static Optional<String> syntaxError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player, an item, "
                    + "and an optional count.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 3) {
            return Optional.of("Tag <" + name + "> needs a player, an item, "
                    + "and an optional count.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves {@code <phasitem:player,item,qty>}. Qty defaults to 1;
     * a malformed qty, an offline player, or an unknown item warns
     * and yields "false".
     */
    static String hasItem(String tag, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 3) {
            context.scope().warn("Tag <phasitem> needs a player, an item, "
                    + "and an optional count: " + tag);
            return "false";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(1));
        if (name.isEmpty() || item.isEmpty() || name.get().isBlank() || item.get().isBlank()) {
            context.scope().warn("Tag <phasitem> has a malformed player or item: " + tag);
            return "false";
        }
        int qty = 1;
        if (parts.size() == 3) {
            Optional<String> raw = CommandPlaceholders.parsePickItem(parts.get(2));
            if (raw.isEmpty()) {
                context.scope().warn("Tag <phasitem> has a malformed count: " + tag);
                return "false";
            }
            try {
                qty = Integer.parseInt(raw.get().strip());
            } catch (NumberFormatException invalid) {
                context.scope().warn("Tag <phasitem> count '" + raw.get().strip()
                        + "' is not a number: " + tag);
                return "false";
            }
            if (qty <= 0) {
                context.scope().warn("Tag <phasitem> count must be positive: " + tag);
                return "false";
            }
        }
        Optional<Integer> count = context.roster().countItem(name.get().strip(),
                item.get().strip());
        if (count.isEmpty()) {
            context.scope().warn("Tag <phasitem> player '" + name.get().strip()
                    + "' is offline or the item is unknown: " + tag);
            return "false";
        }
        return count.get() >= qty ? "true" : "false";
    }
}
