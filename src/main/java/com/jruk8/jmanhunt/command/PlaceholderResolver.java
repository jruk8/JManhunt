package com.jruk8.jmanhunt.command;

/**
 * Placeholder expansion behind raw {@code %...%} spans and the
 * {@code <placeholder>} tag alias. Pure seam: the server-backed
 * implementation lives outside this package while tests inject
 * fakes. Unresolvable spans return unchanged, like PlaceholderAPI.
 */
public interface PlaceholderResolver {

    /**
     * Expands every placeholder span in the text for one executor.
     * Never null, never throws for unknown keys.
     */
    String resolve(String text, String playerName);

    /** Resolver that expands nothing. */
    static PlaceholderResolver inert() {
        return (text, playerName) -> text;
    }
}
