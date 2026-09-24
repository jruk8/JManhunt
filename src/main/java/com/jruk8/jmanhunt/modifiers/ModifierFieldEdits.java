package com.jruk8.jmanhunt.modifiers;

import java.util.Set;
import org.bukkit.Material;

/**
 * Pure field validation for the creator GUI prompts. Each parser returns
 * either a value or a plain-English error the caller shows in chat.
 * Blank-means-clear is a caller decision; these parsers reject blanks so
 * required and optional fields share one strict core. No server needed.
 */
public final class ModifierFieldEdits {
    private ModifierFieldEdits() {
    }

    /** A parsed value, or the error to show when parsing failed. */
    public record Parsed<T>(T value, String error) {
        public boolean ok() {
            return error == null;
        }

        static <T> Parsed<T> ok(T value) {
            return new Parsed<>(value, null);
        }

        static <T> Parsed<T> fail(String error) {
            return new Parsed<>(null, error);
        }
    }

    /** Display name: trimmed, never blank. */
    public static Parsed<String> name(String raw) {
        if (raw == null || raw.isBlank()) {
            return Parsed.fail("Name must not be empty.");
        }
        return Parsed.ok(raw.trim());
    }

    /** Icon material: lenient parse, never air. */
    public static Parsed<Material> item(String raw) {
        if (raw == null || raw.isBlank()) {
            return Parsed.fail("Item must not be empty.");
        }
        Material material = ModifierStore.parseMaterial(raw);
        if (material == null || material == Material.AIR) {
            return Parsed.fail("Unknown item '" + raw.trim() + "'.");
        }
        return Parsed.ok(material);
    }

    /** Modifier or preset id: codec shape, and not taken. */
    public static Parsed<String> id(String raw, Set<String> taken) {
        if (raw == null || raw.isBlank()) {
            return Parsed.fail("Id must not be empty.");
        }
        String id = raw.trim();
        if (!ModifierCodec.validId(id)) {
            return Parsed.fail("Ids use letters, numbers, - and _, up to 64 chars.");
        }
        if (taken.contains(id)) {
            return Parsed.fail("Id '" + id + "' is already taken.");
        }
        return Parsed.ok(id);
    }

    /** Decimal field with an optional inclusive range. */
    public static Parsed<Double> number(String label, String raw, Double min, Double max) {
        if (raw == null || raw.isBlank()) {
            return Parsed.fail(label + " must not be empty.");
        }
        double parsed;
        try {
            parsed = Double.parseDouble(raw.trim());
        } catch (NumberFormatException unparseable) {
            return Parsed.fail(label + " wants a number, got '" + raw.trim() + "'.");
        }
        if (!Double.isFinite(parsed)) {
            return Parsed.fail(label + " wants a number, got '" + raw.trim() + "'.");
        }
        if ((min != null && parsed < min) || (max != null && parsed > max)) {
            return Parsed.fail(label + " wants " + rangeText(min, max) + ".");
        }
        return Parsed.ok(parsed);
    }

    /** Whole-number field with an optional inclusive range. */
    public static Parsed<Long> whole(String label, String raw, Long min, Long max) {
        if (raw == null || raw.isBlank()) {
            return Parsed.fail(label + " must not be empty.");
        }
        long parsed;
        try {
            parsed = Long.parseLong(raw.trim());
        } catch (NumberFormatException unparseable) {
            return Parsed.fail(label + " wants a whole number, got '" + raw.trim() + "'.");
        }
        if ((min != null && parsed < min) || (max != null && parsed > max)) {
            return Parsed.fail(label + " wants " + rangeText(min, max) + ".");
        }
        return Parsed.ok(parsed);
    }

    private static String rangeText(Number min, Number max) {
        if (min != null && max != null) {
            return "between " + min + " and " + max;
        }
        if (min != null) {
            return min + " or above";
        }
        return "at most " + max;
    }
}
