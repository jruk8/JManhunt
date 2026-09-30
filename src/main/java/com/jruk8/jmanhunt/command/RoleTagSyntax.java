package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Edit-time validation for the role tags ({@code <rflag>},
 * {@code <rmessage>}, {@code <rsound>}): arity, quote hygiene, plus
 * the HUNTER/SPEEDRUNNER/ALL head. Mirrors the runtime warns. No
 * Bukkit types.
 */
final class RoleTagSyntax {

    private RoleTagSyntax() {
    }

    /** Role message shape: a role plus literal preset text, quotes parsed. */
    static Optional<String> messageError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a role and a text.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 2) {
            return Optional.of("Tag <" + name + "> needs a role and a text.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return headError(name, parts.get(0));
    }

    /** Role sound shape: a role plus an id, pitch, and volume, quotes parsed. */
    static Optional<String> soundError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a role plus an id, pitch, and volume.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 4) {
            return Optional.of("Tag <" + name + "> needs a role plus an id, pitch, and volume.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return headError(name, parts.get(0));
    }

    /** Role flag shape: a role, a name, plus an optional value, quotes parsed. */
    static Optional<String> flagError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a role, a name, plus an optional value.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 2 || parts.size() > 3) {
            return Optional.of("Tag <" + name + "> needs a role, a name, plus an optional value.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        Optional<String> role = headError(name, parts.get(0));
        if (role.isPresent()) {
            return role;
        }
        if (FlagStore.parseName(parts.get(1)).isEmpty()) {
            return Optional.of("Tag <" + name + "> needs a name.");
        }
        return Optional.empty();
    }

    /**
     * Role head value: HUNTER, SPEEDRUNNER, or ALL. Like {@code <win>}
     * the role must be literal here; nested tags resolve at runtime
     * but the editor cannot see through them.
     */
    private static Optional<String> headError(String name, String head) {
        String role = CommandPlaceholders.parsePickItem(head).orElse("").strip()
                .toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER") && !role.equals("ALL")) {
            return Optional.of("Tag <" + name + "> needs HUNTER, SPEEDRUNNER, or ALL.");
        }
        return Optional.empty();
    }
}
