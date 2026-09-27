package com.jruk8.jmanhunt.command;

/**
 * Tag backends for one dispatch run: stats, flags, placeholders, and
 * roster. Constructed per run from shared services, so resolvers stay
 * pure while managers own every Bukkit call.
 */
public record TagBackends(StatValues stats, FlagStore flags, PlaceholderResolver placeholders,
        RosterValues roster) {

    /** Backends that resolve nothing: inert stats, placeholders, roster. */
    public static TagBackends inert() {
        return new TagBackends(StatValues.inert(), new FlagStore(), PlaceholderResolver.inert(),
                RosterValues.inert());
    }
}
