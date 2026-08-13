package com.jruk8.jmanhunt;

import java.util.function.BiConsumer;

/**
 * Parses raw string values against the current config value type so that
 * scalar settings (booleans, ints, floats/doubles, strings, enums) can be
 * edited in-game without a type-safe config framework.
 *
 * Known limitation: there is no schema validation. Strings and enum-like
 * values are stored verbatim; only booleans and numbers are validated against
 * the current type in config.yml.
 */
public final class SettingValueParser {
    private SettingValueParser() {
    }

    /**
     * Parses {@code raw} against the type of {@code current} and hands the
     * typed result to {@code consumer}. Returns true when the value parsed
     * successfully and was applied.
     */
    public static boolean parse(Object current, String raw, BiConsumer<Object, Object> consumer) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (current instanceof Boolean) {
            return parseBoolean(current, trimmed, consumer);
        }
        if (current instanceof Integer) {
            return parseInteger(current, trimmed, consumer);
        }
        if (current instanceof Long) {
            return parseLong(current, trimmed, consumer);
        }
        if (current instanceof Double) {
            return parseDouble(current, trimmed, consumer);
        }
        if (current instanceof Float) {
            return parseFloat(current, trimmed, consumer);
        }
        // Strings and enum-like values are accepted verbatim (no type safety).
        if (current instanceof String) {
            consumer.accept(current, trimmed);
            return true;
        }
        // Unsupported types (lists, maps, etc.) cannot be edited in-game.
        return false;
    }

    private static boolean parseBoolean(Object current, String trimmed, BiConsumer<Object, Object> consumer) {
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            consumer.accept(current, Boolean.parseBoolean(trimmed));
            return true;
        }
        return false;
    }

    private static boolean parseInteger(Object current, String trimmed, BiConsumer<Object, Object> consumer) {
        try {
            consumer.accept(current, Integer.parseInt(trimmed));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean parseLong(Object current, String trimmed, BiConsumer<Object, Object> consumer) {
        try {
            consumer.accept(current, Long.parseLong(trimmed));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean parseDouble(Object current, String trimmed, BiConsumer<Object, Object> consumer) {
        try {
            consumer.accept(current, Double.parseDouble(trimmed));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean parseFloat(Object current, String trimmed, BiConsumer<Object, Object> consumer) {
        try {
            consumer.accept(current, Float.parseFloat(trimmed));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}