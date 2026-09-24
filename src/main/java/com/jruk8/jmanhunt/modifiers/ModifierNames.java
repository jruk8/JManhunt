package com.jruk8.jmanhunt.modifiers;

import java.util.Locale;
import java.util.Set;

/**
 * Display-name numbering and id derivation shared by modifier and
 * preset creation, import, and rename flows.
 */
public final class ModifierNames {

    private ModifierNames() {
    }

    /**
     * Kebab-case id from a display name: lowercase, runs of
     * non-letters-or-digits become one hyphen, edges trimmed. Pure.
     */
    public static String kebab(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /**
     * First free display name: the base itself, else the base with
     * " {n}" for the next available n. Pure for tests.
     */
    public static String uniqueName(String base, Set<String> taken) {
        if (!taken.contains(base)) {
            return base;
        }
        int counter = 2;
        while (taken.contains(base + " " + counter)) {
            counter++;
        }
        return base + " " + counter;
    }
}
