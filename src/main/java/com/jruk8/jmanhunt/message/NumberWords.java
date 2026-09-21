package com.jruk8.jmanhunt.message;

/** Number words for player-facing counts: words below ten, digits above. */
public final class NumberWords {
    private static final String[] WORDS = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    private NumberWords() {
    }

    /** Lowercase number word for 0-9, digits otherwise. Pure for tests. */
    public static String word(int value) {
        return value >= 0 && value < WORDS.length ? WORDS[value] : String.valueOf(value);
    }
}
