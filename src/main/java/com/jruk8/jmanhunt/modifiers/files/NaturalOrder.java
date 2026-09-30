package com.jruk8.jmanhunt.modifiers.files;

import java.util.Comparator;

/**
 * Windows-Explorer style natural ordering for mod file and directory
 * names: case-insensitive, digit runs compared numerically ({@code mod2}
 * sorts before {@code mod10}). Pure and total: ties fall back to a
 * raw case-sensitive pass, then to shorter first.
 */
public final class NaturalOrder {

    private NaturalOrder() {
    }

    /** Comparator form for sorting name lists. Pure. */
    public static Comparator<String> comparator() {
        return NaturalOrder::compare;
    }

    /**
     * Natural comparison of two non-null names. Pure.
     */
    public static int compare(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            char leftChar = left.charAt(i);
            char rightChar = right.charAt(j);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int digits = compareDigitRuns(left, i, right, j);
                if (digits != 0) {
                    return digits;
                }
                i = runEnd(left, i);
                j = runEnd(right, j);
                continue;
            }
            int letters = Character.compare(lower(leftChar), lower(rightChar));
            if (letters != 0) {
                return letters;
            }
            i++;
            j++;
        }
        if (i < left.length()) {
            return 1;
        }
        if (j < right.length()) {
            return -1;
        }
        int raw = left.compareTo(right);
        if (raw != 0) {
            return raw;
        }
        return Integer.compare(left.length(), right.length());
    }

    private static int compareDigitRuns(String left, int i, String right, int j) {
        int leftEnd = runEnd(left, i);
        int rightEnd = runEnd(right, j);
        int leftStart = stripZeros(left, i, leftEnd);
        int rightStart = stripZeros(right, j, rightEnd);
        int leftLen = leftEnd - leftStart;
        int rightLen = rightEnd - rightStart;
        if (leftLen != rightLen) {
            return Integer.compare(leftLen, rightLen);
        }
        for (int k = 0; k < leftLen; k++) {
            int result = Character.compare(left.charAt(leftStart + k), right.charAt(rightStart + k));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int runEnd(String text, int start) {
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int stripZeros(String text, int start, int end) {
        int first = start;
        while (first + 1 < end && text.charAt(first) == '0') {
            first++;
        }
        return first;
    }

    private static char lower(char value) {
        return Character.toLowerCase(value);
    }
}
