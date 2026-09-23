package com.jruk8.jmanhunt.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structure-string layout for a chest menu.
 *
 * <p>Each row is nine cells after whitespace is removed. The token {@code x}
 * marks a content slot; every other character is a static or filler slot whose
 * meaning is supplied by the caller. Rows that contain {@code x} must all hold
 * the same count so scrolling moves whole lines; rows without {@code x} are
 * skipped when grouping content lines.
 */
public final class MenuLayout {

    /** Layout token marking a scrollable content slot. */
    public static final char CONTENT = 'x';

    /** Layout token marking a filler pane slot. */
    public static final char FILLER = '#';

    private static final int CELLS_PER_ROW = 9;

    private final List<String> rows;
    private final List<Integer> contentSlots;
    private final int contentColumns;

    private MenuLayout(List<String> rows, List<Integer> contentSlots, int contentColumns) {
        this.rows = rows;
        this.contentSlots = contentSlots;
        this.contentColumns = contentColumns;
    }

    /**
     * Parses layout rows such as {@code "m#######p"}.
     *
     * @param rows one or more nine-cell rows
     * @return the parsed layout
     * @throws IllegalArgumentException when a row is malformed or content rows disagree
     */
    public static MenuLayout parse(String... rows) {
        if (rows == null || rows.length == 0) {
            throw new IllegalArgumentException("Menu layout needs at least one row.");
        }
        List<String> normalized = new ArrayList<>(rows.length);
        for (int index = 0; index < rows.length; index++) {
            String row = rows[index] == null ? "" : rows[index].replaceAll("\\s", "");
            if (row.length() != CELLS_PER_ROW) {
                throw new IllegalArgumentException("Menu layout row " + index
                        + " must have exactly 9 cells after removing spaces, got " + row.length()
                        + ": '" + rows[index] + "'.");
            }
            normalized.add(row);
        }
        int columns = -1;
        for (String row : normalized) {
            int count = 0;
            for (int cell = 0; cell < row.length(); cell++) {
                if (row.charAt(cell) == CONTENT) {
                    count++;
                }
            }
            if (count == 0) {
                continue;
            }
            if (columns == -1) {
                columns = count;
            } else if (columns != count) {
                throw new IllegalArgumentException(
                        "Menu layout content rows must all hold the same number of x cells.");
            }
        }
        List<Integer> contentSlots = new ArrayList<>();
        for (int row = 0; row < normalized.size(); row++) {
            String cells = normalized.get(row);
            for (int cell = 0; cell < cells.length(); cell++) {
                if (cells.charAt(cell) == CONTENT) {
                    contentSlots.add(row * CELLS_PER_ROW + cell);
                }
            }
        }
        int resolvedColumns = columns == -1 ? 0 : columns;
        return new MenuLayout(Collections.unmodifiableList(normalized),
                Collections.unmodifiableList(contentSlots), resolvedColumns);
    }

    /** Number of chest rows in this layout. */
    public int rowCount() {
        return rows.size();
    }

    /** Total slot count (rows times nine). */
    public int size() {
        return rows.size() * CELLS_PER_ROW;
    }

    /** Raw slot indexes holding the given token, in row-major order. */
    public List<Integer> slotsFor(char token) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            String cells = rows.get(row);
            for (int cell = 0; cell < cells.length(); cell++) {
                if (cells.charAt(cell) == token) {
                    slots.add(row * CELLS_PER_ROW + cell);
                }
            }
        }
        return slots;
    }

    /** Content slots in row-major order. */
    public List<Integer> contentSlots() {
        return contentSlots;
    }

    /** Content cells per content row, or zero when the layout has no content. */
    public int contentColumns() {
        return contentColumns;
    }

    /** Number of rows that contain at least one content cell. */
    public int contentRows() {
        if (contentColumns == 0) {
            return 0;
        }
        return contentSlots.size() / contentColumns;
    }

    /**
     * Line-relative content index for a raw slot, or {@code -1} when the slot
     * is not a content slot. Each content row is one line.
     */
    public int contentIndex(int slot) {
        int row = slot / CELLS_PER_ROW;
        int cell = slot % CELLS_PER_ROW;
        if (row < 0 || row >= rows.size() || cell < 0 || cell >= CELLS_PER_ROW) {
            return -1;
        }
        if (rows.get(row).charAt(cell) != CONTENT) {
            return -1;
        }
        int line = 0;
        for (int scanned = 0; scanned < row; scanned++) {
            if (rows.get(scanned).indexOf(CONTENT) >= 0) {
                line++;
            }
        }
        int column = 0;
        String cells = rows.get(row);
        for (int scanned = 0; scanned < cell; scanned++) {
            if (cells.charAt(scanned) == CONTENT) {
                column++;
            }
        }
        return line * contentColumns + column;
    }
}
