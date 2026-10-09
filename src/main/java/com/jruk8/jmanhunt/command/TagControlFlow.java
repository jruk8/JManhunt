package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Control-flow spans behind {@link TagExpressions}. */
public final class TagControlFlow {
    private TagControlFlow() {
    }

    /**
     * One {@code <if>} span: offsets plus the raw args between the
     * root colon and the balancing {@code >}. End is exclusive.
     */
    public record IfSpan(int start, int end, String args) {
    }

    /**
     * Finds {@code <if>} spans, whose conditions may hold nested tags
     * that the plain innermost-tag scan cannot see. Quotes nest by
     * alternation and only unquoted brackets count toward depth.
     * Spans that never balance are skipped.
     */
    public static List<IfSpan> findIfSpans(String line) {
        List<IfSpan> spans = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            int open = line.indexOf('<', index);
            if (open < 0) {
                return spans;
            }
            IfSpan span = ifSpanAt(line, open);
            if (span != null) {
                spans.add(span);
            }
            index = open + 1;
        }
        return spans;
    }

    static IfSpan ifSpanAt(String line, int open) {
        int cursor = open + 1;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        int rootEnd = cursor;
        while (rootEnd < line.length() && TagExpressions.isRootChar(line.charAt(rootEnd))) {
            rootEnd++;
        }
        if (!line.substring(cursor, rootEnd).equalsIgnoreCase("if")) {
            return null;
        }
        cursor = rootEnd;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= line.length() || (line.charAt(cursor) != ':'
                && line.charAt(cursor) != '>')) {
            return null;
        }
        int argsStart = cursor + 1;
        Integer end = TagExpressions.spanEnd(line, open);
        if (end == null) {
            return null;
        }
        return new IfSpan(open, end + 1, line.substring(argsStart, end));
    }

    public static boolean hasNestedIf(String args) {
        return !nestedIfStarts(args).isEmpty();
    }

    /**
     * Relative offsets of nested {@code <if} openers: the same
     * case-insensitive match as {@link #hasNestedIf}, kept as one
     * implementation so laziness checks cannot drift from it.
     */
    public static List<Integer> nestedIfStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = lower.indexOf("<if", from);
            if (at < 0) {
                return starts;
            }
            int after = at + 3;
            if (after >= lower.length()) {
                starts.add(at);
                return starts;
            }
            char next = lower.charAt(after);
            if (next == ':' || next == '>' || Character.isWhitespace(next)) {
                starts.add(at);
            }
            from = after;
        }
    }

    public static boolean isExit(String line) {
        return stripSlash(line).strip().equals("exit");
    }

    public static boolean isExitMisuse(String line) {
        String stripped = stripSlash(line).strip();
        if (stripped.equals("exit")) {
            return false;
        }
        int end = stripped.indexOf(' ');
        int tab = stripped.indexOf('\t');
        if (tab >= 0 && (end < 0 || tab < end)) {
            end = tab;
        }
        String first = end < 0 ? stripped : stripped.substring(0, end);
        return first.equals("exit");
    }

    static String stripSlash(String line) {
        String stripped = line.strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    /**
     * Dispatch text for a parsed command line. Tag-only lines evaluate
     * to nothing (a bare {@code <pflag:...>} set, {@code <gmessage:...>},
     * ...), and dispatching blank text crashes the server dispatcher,
     * so blank lines resolve empty and the caller skips them quietly.
     * One leading slash is dropped; surrounding whitespace is trimmed.
     */
    public static Optional<String> dispatchableLine(String parsed) {
        String line = stripSlash(parsed);
        return line.isEmpty() ? Optional.empty() : Optional.of(line);
    }

    /**
     * True when a dispatchable line is exactly the engine null
     * literal: trimmed, case-sensitive lowercase. Callers warn and
     * skip dispatch instead of sending {@code null} to the console
     * dispatcher, which would only raise unknown-command noise.
     */
    public static boolean isPureNull(String line) {
        return line.strip().equals("null");
    }
}
