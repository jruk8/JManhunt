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
        return joinOxford(names, "<gray>,</gray>", "<gray>and</gray>");
    }

    /**
     * Oxford join with custom comma and "and" separators. Two names use
     * "a and b"; three or more use the Oxford comma.
     */
    public static String joinOxford(List<String> names, String comma, String and) {
        return switch (names.size()) {
            case 0 -> "";
            case 1 -> names.getFirst();
            case 2 -> names.get(0) + " " + and + " " + names.get(1);
            default -> String.join(comma + " ", names.subList(0, names.size() - 1))
                    + comma + " " + and + " " + names.getLast();
        };
    }

    /**
     * Oxford join showing at most {@code maxShown} names with an "and n
     * more" tail when names overflow. A non-positive {@code maxShown}
     * renders the overflow alone with {@code overflowOnlyWord} ("5
     * dead"); shown names tail with {@code moreWord} ("a, b, and 3
     * more"). An empty list renders "".
     */
    public static String joinOxfordTruncated(List<String> names, int maxShown, String comma,
            String and, String moreWord, String overflowOnlyWord) {
        if (names.isEmpty()) {
            return "";
        }
        int shown = Math.max(0, Math.min(maxShown, names.size()));
        int hidden = names.size() - shown;
        if (hidden <= 0) {
            return joinOxford(names, comma, and);
        }
        if (shown == 0) {
            return hidden + " " + overflowOnlyWord;
        }
        List<String> head = new ArrayList<>(names.subList(0, shown));
        head.add(hidden + " " + moreWord);
        return joinOxford(head, comma, and);
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
