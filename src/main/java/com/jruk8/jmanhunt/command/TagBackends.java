package com.jruk8.jmanhunt.command;

/**
 * Tag backends for one dispatch run: stats, flags, and placeholders.
 * Constructed per run from shared services, so resolvers stay pure
 * while managers own every Bukkit call.
 */
public record TagBackends(StatValues stats, FlagStore flags, PlaceholderResolver placeholders) {

    /** Backends that resolve nothing: inert stats and placeholders. */
    public static TagBackends inert() {
        return new TagBackends(StatValues.inert(), new FlagStore(), PlaceholderResolver.inert());
    }
}
