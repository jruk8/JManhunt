package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Player body and inventory tags: {@code <pstate>} reads sneaking,
 * sprinting, gliding, swimming, and grounded; {@code <pstandingon>}
 * reads the block below the feet; {@code <ptitle>} sends a
 * center-screen title; {@code <pslot>} gets and sets inventory
 * slots by name or raw index; {@code <pmaxhp>} gets and sets
 * max-health id contributions. Reads resolve {@code null} silently
 * for offline or unknown players; writes warn. No Bukkit types:
 * every read and write runs through the context backends.
 */
public final class TagPlayers {

    /** Valid states, canonical upper-case. */
    static final List<String> STATES = List.of("SNEAK", "SPRINT", "GLIDE", "SWIM", "GROUND");

    /** Named slots, canonical upper-case. */
    private static final Set<String> NAMED_SLOTS = Set.of("MAINHAND", "OFFHAND", "HELMET",
            "CHESTPLATE", "LEGGINGS", "BOOTS");

    /**
     * Highest raw player-inventory index: 0-8 hotbar, 9-35 main
     * storage, 36-39 armor, 40 offhand (raw
     * {@code PlayerInventory#getItem} indices).
     */
    static final int MAX_SLOT = 40;

    private TagPlayers() {
    }

    /**
     * Pure player-tag entry for tag evaluation: splits, validates,
     * and applies. Misuse warns plus {@code "null"} ({@code ""}
     * for successful writes).
     */
    static String resolve(String tag, String name, String args, TagContext context) {
        List<String> segments = TagLists.splitTopLevel(args);
        if (!arityOk(name, segments.size())) {
            context.scope().warn("Tag <" + name + "> needs " + arityText(name) + " like "
                    + example(name) + ": " + tag);
            return "null";
        }
        List<String> parsed = new ArrayList<>();
        for (String segment : segments) {
            Optional<String> item = CommandPlaceholders.parsePickItem(segment);
            if (item.isEmpty() && !segment.isBlank()) {
                context.scope().warn("Tag <" + name + "> mixes quotes: " + tag);
                return "null";
            }
            parsed.add(item.orElse(""));
        }
        return switch (name) {
            case "pstate" -> state(tag, parsed, context);
            case "pstandingon" -> standingOn(tag, parsed.get(0), context);
            case "ptitle" -> title(tag, parsed, context);
            case "pmaxhp.set", "pmaxhp.modify" -> maxHealthWrite(tag, name, parsed, context);
            case "pmaxhp.get" -> maxHealthGet(tag, parsed, context);
            case "pmaxhp.clear" -> maxHealthClear(tag, parsed, context);
            default -> slot(tag, parsed, context);
        };
    }

    /** True or false for one body state; unknown states warn plus the valid list. */
    private static String state(String tag, List<String> parsed, TagContext context) {
        if (parsed.get(0).isBlank()) {
            context.scope().warn("Tag <pstate> needs a player like <pstate:Steve,SNEAK>: " + tag);
            return "null";
        }
        String state = parsed.get(1).strip().toUpperCase(Locale.ROOT);
        if (!STATES.contains(state)) {
            context.scope().warn("Tag <pstate> state '" + parsed.get(1).strip() + "' is unknown, "
                    + "valid states: " + String.join(", ", STATES) + ": " + tag);
            return "null";
        }
        Optional<Boolean> active = context.roster().playerState(parsed.get(0).strip(), state);
        if (active.isEmpty()) {
            return "null";
        }
        return active.get() ? "true" : "false";
    }

    /** Upper-case block-below material for one player. */
    private static String standingOn(String tag, String raw, TagContext context) {
        if (raw.isBlank()) {
            context.scope().warn("Tag <pstandingon> needs a player like <pstandingon:Steve>: "
                    + tag);
            return "null";
        }
        return context.roster().standingOn(raw.strip()).orElse("null");
    }

    /**
     * Sends one title: player, title, subtitle, then optional stay,
     * fade-in, and fade-out in seconds (defaults 2.0, 0.4, 0.4).
     * Bad timing warns plus null with nothing sent; offline warns
     * plus null; success returns empty.
     */
    private static String title(String tag, List<String> parsed, TagContext context) {
        if (parsed.get(0).isBlank()) {
            context.scope().warn("Tag <ptitle> needs a player like <ptitle:Steve,hi,sub>: "
                    + tag);
            return "null";
        }
        double[] timing = {2.0, 0.4, 0.4};
        String[] labels = {"stay", "in", "out"};
        for (int index = 3; index < parsed.size(); index++) {
            Optional<Double> seconds = timing(tag, labels[index - 3], parsed.get(index), context);
            if (seconds.isEmpty()) {
                return "null";
            }
            timing[index - 3] = seconds.get();
        }
        boolean sent = context.playerSinks().title(parsed.get(0).strip(), parsed.get(1),
                parsed.get(2), timing[0], timing[1], timing[2]);
        if (!sent) {
            context.scope().warn("Tag <ptitle> player '" + parsed.get(0).strip()
                    + "' is offline: " + tag);
            return "null";
        }
        return "";
    }

    /** One non-negative finite timing value in seconds; misuse warns plus empty. */
    private static Optional<Double> timing(String tag, String label, String raw,
            TagContext context) {
        double seconds;
        try {
            seconds = Double.parseDouble(raw.strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag <ptitle> " + label + " '" + raw.strip()
                    + "' is not seconds: " + tag);
            return Optional.empty();
        }
        if (!Double.isFinite(seconds) || seconds < 0) {
            context.scope().warn("Tag <ptitle> " + label + " '" + raw.strip()
                    + "' is not seconds: " + tag);
            return Optional.empty();
        }
        return Optional.of(seconds);
    }

    /**
     * Gets ({@code <pslot:player,slot>}) or sets
     * ({@code <pslot:player,slot,newitem>}) one slot. Gets read
     * {@code [MATERIAL, qty]} with null for empty or offline; sets
     * take a {@code [material, qty]} list or a bare material (qty
     * 1), warn plus null with no change on invalid, and return
     * empty.
     */
    private static String slot(String tag, List<String> parsed, TagContext context) {
        if (parsed.get(0).isBlank()) {
            context.scope().warn("Tag <pslot> needs a player like <pslot:Steve,helmet>: " + tag);
            return "null";
        }
        Optional<RosterValues.InventorySlot> slot = parseSlot(tag, parsed.get(1), context);
        if (slot.isEmpty()) {
            return "null";
        }
        if (parsed.size() == 2) {
            Optional<RosterValues.SlotContent> held =
                    context.roster().slotItem(parsed.get(0).strip(), slot.get());
            if (held.isEmpty()) {
                return "null";
            }
            return TagLists.format(List.of(held.get().material(),
                    Integer.toString(held.get().qty())));
        }
        Optional<SetValue> value = parseSetValue(tag, parsed.get(2), context);
        if (value.isEmpty()) {
            return "null";
        }
        boolean set = context.playerSinks().setSlot(parsed.get(0).strip(), slot.get(),
                value.get().material(), value.get().qty());
        if (!set) {
            context.scope().warn("Tag <pslot> player '" + parsed.get(0).strip()
                    + "' is offline or the item '" + value.get().material()
                    + "' is unknown: " + tag);
            return "null";
        }
        return "";
    }

    /**
     * Overwrites ({@code <pmaxhp.set:player,id,amount>}) or adds to
     * ({@code <pmaxhp.modify:player,id,amount>}) one max-health id
     * contribution. Blank player or id, and non-finite amounts,
     * warn plus null; offline warns plus null; success returns
     * empty.
     */
    private static String maxHealthWrite(String tag, String name, List<String> parsed,
            TagContext context) {
        if (parsed.get(0).isBlank()) {
            context.scope().warn("Tag <" + name + "> needs a player like <" + name
                    + ":Steve,boost,4>: " + tag);
            return "null";
        }
        if (parsed.get(1).isBlank()) {
            context.scope().warn("Tag <" + name + "> needs an id like <" + name
                    + ":Steve,boost,4>: " + tag);
            return "null";
        }
        Optional<Double> amount = maxHealthAmount(tag, name, parsed.get(2), context);
        if (amount.isEmpty()) {
            return "null";
        }
        boolean done = name.equals("pmaxhp.set")
                ? context.playerSinks().setMaxHealth(parsed.get(0).strip(), parsed.get(1).strip(),
                        amount.get())
                : context.playerSinks().modifyMaxHealth(parsed.get(0).strip(),
                        parsed.get(1).strip(), amount.get());
        if (!done) {
            context.scope().warn("Tag <" + name + "> player '" + parsed.get(0).strip()
                    + "' is offline: " + tag);
            return "null";
        }
        return "";
    }

    /**
     * Reads ({@code <pmaxhp.get:player,id>}) one max-health id
     * contribution, 0 when never set. Blank player or id warns
     * plus null; offline warns plus null.
     */
    private static String maxHealthGet(String tag, List<String> parsed, TagContext context) {
        if (parsed.get(0).isBlank()) {
            context.scope().warn(
                    "Tag <pmaxhp.get> needs a player like <pmaxhp.get:Steve,boost>: " + tag);
            return "null";
        }
        if (parsed.get(1).isBlank()) {
            context.scope().warn(
                    "Tag <pmaxhp.get> needs an id like <pmaxhp.get:Steve,boost>: " + tag);
            return "null";
        }
        Optional<Double> value = context.playerSinks().getMaxHealth(parsed.get(0).strip(),
                parsed.get(1).strip());
        if (value.isEmpty()) {
            context.scope().warn("Tag <pmaxhp.get> player '" + parsed.get(0).strip()
                    + "' is offline: " + tag);
            return "null";
        }
        return TagMath.formatNumber(value.get());
    }

    /**
     * Deletes ({@code <pmaxhp.clear:player,id>}) one max-health id,
     * or every id when the id is omitted. Blank player warns plus
     * null; offline warns plus null; success returns empty.
     */
    private static String maxHealthClear(String tag, List<String> parsed, TagContext context) {
        if (parsed.get(0).isBlank()) {
            context.scope().warn(
                    "Tag <pmaxhp.clear> needs a player like <pmaxhp.clear:Steve,boost>: "
                            + tag);
            return "null";
        }
        String id = parsed.size() > 1 ? parsed.get(1).strip() : null;
        if (id != null && id.isBlank()) {
            context.scope().warn(
                    "Tag <pmaxhp.clear> needs an id like <pmaxhp.clear:Steve,boost>: "
                            + tag);
            return "null";
        }
        boolean done = context.playerSinks().clearMaxHealth(parsed.get(0).strip(), id);
        if (!done) {
            context.scope().warn("Tag <pmaxhp.clear> player '" + parsed.get(0).strip()
                    + "' is offline: " + tag);
            return "null";
        }
        return "";
    }

    /** One finite amount; misuse warns plus empty. */
    private static Optional<Double> maxHealthAmount(String tag, String name, String raw,
            TagContext context) {
        double amount;
        try {
            amount = Double.parseDouble(raw.strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag <" + name + "> amount '" + raw.strip()
                    + "' is not a number: " + tag);
            return Optional.empty();
        }
        if (!Double.isFinite(amount)) {
            context.scope().warn("Tag <" + name + "> amount '" + raw.strip()
                    + "' is not a number: " + tag);
            return Optional.empty();
        }
        return Optional.of(amount);
    }

    /** One set value: upper-case material plus whole qty. */
    private record SetValue(String material, int qty) {
    }

    /**
     * Parses a slot token: a known name (case-blind) or a raw index
     * 0 through {@link #MAX_SLOT}. Anything else warns plus empty.
     */
    private static Optional<RosterValues.InventorySlot> parseSlot(String tag, String raw,
            TagContext context) {
        String token = raw.strip().toUpperCase(Locale.ROOT);
        if (NAMED_SLOTS.contains(token)) {
            return Optional.of(new RosterValues.InventorySlot.Named(token));
        }
        int index;
        try {
            index = Integer.parseInt(raw.strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag <pslot> slot '" + raw.strip() + "' is unknown, use "
                    + "mainhand, offhand, helmet, chestplate, leggings, boots, or 0-"
                    + MAX_SLOT + ": " + tag);
            return Optional.empty();
        }
        if (index < 0 || index > MAX_SLOT) {
            context.scope().warn("Tag <pslot> slot '" + raw.strip() + "' is out of range 0-"
                    + MAX_SLOT + ": " + tag);
            return Optional.empty();
        }
        return Optional.of(new RosterValues.InventorySlot.Index(index));
    }

    /**
     * Parses a set value: a {@code [material, qty]} list sets both,
     * a bare material sets qty 1. Bad shapes and non-integer qtys
     * warn plus empty.
     */
    private static Optional<SetValue> parseSetValue(String tag, String raw, TagContext context) {
        if (!TagLists.isList(raw)) {
            if (raw.isBlank()) {
                context.scope().warn("Tag <pslot> needs an item like <pslot:Steve,helmet,"
                        + "IRON_HELMET>: " + tag);
                return Optional.empty();
            }
            return Optional.of(new SetValue(raw.strip().toUpperCase(Locale.ROOT), 1));
        }
        List<String> items = TagLists.parse(raw);
        if (items.size() != 2 || items.get(0).isBlank() || items.get(1).isBlank()) {
            context.scope().warn("Tag <pslot> needs [material, qty] like "
                    + "<pslot:Steve,helmet,[IRON_HELMET,1]>: " + tag);
            return Optional.empty();
        }
        int qty;
        try {
            qty = Integer.parseInt(items.get(1).strip());
        } catch (NumberFormatException invalid) {
            context.scope().warn("Tag <pslot> qty '" + items.get(1).strip()
                    + "' is not a number: " + tag);
            return Optional.empty();
        }
        return Optional.of(new SetValue(items.get(0).strip().toUpperCase(Locale.ROOT), qty));
    }

    /**
     * Edit-time arity for the player tags: top-level split, quote
     * hygiene, plus the per-tag shape. Mirrors the runtime warns.
     */
    static Optional<String> opError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + arityText(name) + " like "
                    + example(name) + ".");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (!arityOk(name, parts.size())) {
            return Optional.of("Tag <" + name + "> needs " + arityText(name) + " like "
                    + example(name) + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    private static boolean arityOk(String name, int count) {
        return switch (name) {
            case "pstate" -> count == 2;
            case "pstandingon" -> count == 1;
            case "ptitle" -> count >= 3 && count <= 6;
            case "pmaxhp.set", "pmaxhp.modify" -> count == 3;
            case "pmaxhp.get" -> count == 2;
            case "pmaxhp.clear" -> count == 1 || count == 2;
            default -> count == 2 || count == 3;
        };
    }

    private static String arityText(String name) {
        return switch (name) {
            case "pstate" -> "2 args";
            case "pstandingon" -> "1 arg";
            case "ptitle" -> "3 to 6 args";
            case "pmaxhp.set", "pmaxhp.modify" -> "3 args";
            case "pmaxhp.get" -> "2 args";
            case "pmaxhp.clear" -> "1 or 2 args";
            default -> "2 or 3 args";
        };
    }

    private static String example(String name) {
        return switch (name) {
            case "pstate" -> "<pstate:player,state>";
            case "pstandingon" -> "<pstandingon:player>";
            case "ptitle" -> "<ptitle:player,title,subtitle>";
            case "pmaxhp.set" -> "<pmaxhp.set:player,id,amount>";
            case "pmaxhp.modify" -> "<pmaxhp.modify:player,id,amount>";
            case "pmaxhp.get" -> "<pmaxhp.get:player,id>";
            case "pmaxhp.clear" -> "<pmaxhp.clear:player,id>";
            default -> "<pslot:player,slot>";
        };
    }
}
