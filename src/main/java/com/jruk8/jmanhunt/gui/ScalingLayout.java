package com.jruk8.jmanhunt.gui;

/**
 * Row math for menus that grow with their content.
 *
 * <p>Content fills columns 0 to 7; column 8 holds the back arrow at row
 * {@code (rows - 1) / 2} with filler above and below. Rows clamp to the
 * chest range of 1 to 6, so a scaling menu shows at most 48 entries and
 * anything beyond that stays available through the chat command.
 */
public final class ScalingLayout {

    /** Content columns per row. */
    public static final int CONTENT_COLUMNS = 8;

    /** Largest chest menu. */
    public static final int MAX_ROWS = 6;

    /** Layout token marking the back-arrow slot. */
    public static final char BACK = 'b';

    private ScalingLayout() {
    }

    /** Rows needed for the entry count, clamped to 1 to 6. */
    public static int rowsFor(int entries) {
        return clamp((Math.max(0, entries) + CONTENT_COLUMNS - 1) / CONTENT_COLUMNS);
    }

    /** Back-arrow row for the row count. */
    public static int backRow(int rows) {
        return (clamp(rows) - 1) / 2;
    }

    /** Raw back-arrow slot for the row count. */
    public static int backSlot(int rows) {
        return backRow(rows) * 9 + 8;
    }

    /** Content slots for the row count. */
    public static int capacity(int rows) {
        return clamp(rows) * CONTENT_COLUMNS;
    }

    /** Layout with content columns, the back token, and filler. */
    public static MenuLayout layout(int rows) {
        int clamped = clamp(rows);
        int arrow = backRow(clamped);
        String[] lines = new String[clamped];
        for (int row = 0; row < clamped; row++) {
            lines[row] = "xxxxxxxx" + (row == arrow ? BACK : MenuLayout.FILLER);
        }
        return MenuLayout.parse(lines);
    }

    private static int clamp(int rows) {
        return Math.max(1, Math.min(MAX_ROWS, rows));
    }
}
