package com.jruk8.jmanhunt.message;

/** Singular/plural picks by count. */
public final class Plurals {

    private Plurals() {
    }

    /** Singular when count is exactly 1, else plural. Pure. */
    public static String pick(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }
}
