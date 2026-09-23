package com.jruk8.jmanhunt.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scrollable line window over a flat entry list.
 *
 * <p>Entries are grouped into lines of {@code columns} items and the window
 * shows {@code visibleRows} lines starting at a clamped line offset. Pages are
 * jump presets only: the offset stays the single source of truth, so the
 * current page is derived from the visible rows.
 *
 * @param <T> entry type
 */
public final class ContentWindow<T> {

    private final int columns;
    private final int visibleRows;
    private List<T> entries = List.of();
    private int lineOffset;

    /**
     * @param columns entries per line, at least one
     * @param visibleRows lines shown at once, at least one
     */
    public ContentWindow(int columns, int visibleRows) {
        if (columns < 1) {
            throw new IllegalArgumentException("Content window needs at least one column.");
        }
        if (visibleRows < 1) {
            throw new IllegalArgumentException("Content window needs at least one visible row.");
        }
        this.columns = columns;
        this.visibleRows = visibleRows;
    }

    /**
     * Replaces the entries and clamps the offset into range. Null entries
     * are allowed as alignment padding and render as empty slots.
     */
    public void setEntries(List<T> entries) {
        this.entries = entries == null
                ? List.of() : Collections.unmodifiableList(new ArrayList<>(entries));
        lineOffset = Math.min(lineOffset, maxOffset());
    }

    /** Entries visible at the current offset, in order. */
    public List<T> visibleEntries() {
        int from = lineOffset * columns;
        int to = Math.min(entries.size(), from + pageSize());
        if (from >= entries.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(entries.subList(from, to)));
    }

    /**
     * Scrolls by whole lines.
     *
     * @param delta lines to move, negative scrolls up
     * @return true when the offset moved
     */
    public boolean scrollLine(int delta) {
        int next = Math.max(0, Math.min(maxOffset(), lineOffset + delta));
        if (next == lineOffset) {
            return false;
        }
        lineOffset = next;
        return true;
    }

    /** Jumps the offset to the given page, clamped into range. */
    public void setPage(int page) {
        lineOffset = Math.max(0, Math.min(maxOffset(), page * visibleRows));
    }

    /** Current line offset. */
    public int lineOffset() {
        return lineOffset;
    }

    /** Total lines across all entries. */
    public int lineCount() {
        return (entries.size() + columns - 1) / columns;
    }

    /** Highest valid line offset. */
    public int maxOffset() {
        return Math.max(0, lineCount() - visibleRows);
    }

    /** Entries shown at once. */
    public int pageSize() {
        return columns * visibleRows;
    }

    /** Page count, at least one. */
    public int pageCount() {
        return Math.max(1, (entries.size() + pageSize() - 1) / pageSize());
    }

    /** Current page derived from the line offset. */
    public int currentPage() {
        return lineOffset / visibleRows;
    }
}
