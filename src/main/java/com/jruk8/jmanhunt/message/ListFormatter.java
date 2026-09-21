package com.jruk8.jmanhunt.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared text-list helpers used by roster, modifier, and win-condition lines.
 */
public final class ListFormatter {
    private ListFormatter() {
    }

    /**
     * Joins names with gray MiniMessage separators and an Oxford-style
     * "and" before the last entry.
     */
    public static String joinOxford(List<String> names) {
        return switch (names.size()) {
            case 0 -> "";
            case 1 -> names.getFirst();
            case 2 -> names.get(0) + " <gray>and</gray> " + names.get(1);
            default -> String.join("<gray>,</gray> ", names.subList(0, names.size() - 1))
                    + "<gray>,</gray> <gray>and</gray> " + names.getLast();
        };
    }

    /**
     * Chunks names into lines of at most {@code perLine} entries joined with
     * ", ". A non-positive {@code perLine} puts every name on one line.
     */
    public static List<String> chunk(List<String> names, int perLine) {
        if (perLine <= 0) {
            return List.of(String.join(", ", names));
        }
        List<String> lines = new ArrayList<>();
        for (int start = 0; start < names.size(); start += perLine) {
            lines.add(String.join(", ", names.subList(start, Math.min(start + perLine, names.size()))));
        }
        return lines;
    }
}
