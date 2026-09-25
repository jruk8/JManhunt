package com.jruk8.jmanhunt.command;

/**
 * Ordinal formatter and command list display names for edit feedback.
 * Pure for tests.
 */
public final class Ordinal {

    private Ordinal() {
    }

    /**
     * 1-based position with its suffix: 1st, 2nd, 3rd, 4th, with the
     * 11th, 12th, and 13th teens overriding the one/two/three rule.
     */
    public static String format(int index) {
        int teens = index % 100;
        if (teens >= 11 && teens <= 13) {
            return index + "th";
        }
        return switch (index % 10) {
            case 1 -> index + "st";
            case 2 -> index + "nd";
            case 3 -> index + "rd";
            default -> index + "th";
        };
    }

    /**
     * Chat-facing command list name. List ids are already friendly, so
     * this maps every list to itself; the single display point if that
     * ever changes.
     */
    public static String listName(String list) {
        return list == null ? "console" : list;
    }
}
