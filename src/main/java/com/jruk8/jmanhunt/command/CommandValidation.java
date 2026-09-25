package com.jruk8.jmanhunt.command;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;

/**
 * Modifier editor command validation. {@link #validateLine} orchestrates
 * the pure checks over injected data; the suppliers below are the only
 * Bukkit touchpoints, called from click actions on a live server.
 */
public final class CommandValidation {

    private CommandValidation() {
    }

    /**
     * Full line check: the placeholder fatal error first, then (only
     * when the editor setting allows) the unknown root and give item
     * checks. Pure for tests.
     */
    public static Optional<String> validateLine(String raw, boolean validateCommands,
            Set<String> knownRoots, Predicate<String> knownMaterial,
            Collection<String> materialNames) {
        Optional<String> problem = CommandSyntax.error(raw);
        if (problem.isPresent() || !validateCommands) {
            return problem;
        }
        Optional<String> root = CommandSyntax.unknownRoot(raw, knownRoots);
        if (root.isPresent()) {
            return root;
        }
        return CommandSyntax.giveItemCheck(raw, knownMaterial, materialNames);
    }

    /**
     * Live server roots: command map names and aliases, lowercased with
     * namespace prefixes stripped. The live map already unions vanilla,
     * Bukkit, and plugin commands, so no separate vanilla table exists.
     */
    public static Set<String> knownRoots() {
        Set<String> roots = new HashSet<>();
        for (Map.Entry<String, Command> entry
                : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
            addRoot(roots, entry.getKey());
            Command command = entry.getValue();
            if (command == null) {
                continue;
            }
            addRoot(roots, command.getName());
            for (String alias : command.getAliases()) {
                addRoot(roots, alias);
            }
        }
        return roots;
    }

    private static void addRoot(Set<String> roots, String label) {
        if (label == null) {
            return;
        }
        int colon = label.indexOf(':');
        String bare = (colon < 0 ? label : label.substring(colon + 1))
                .toLowerCase(Locale.ROOT);
        if (!bare.isEmpty()) {
            roots.add(bare);
        }
    }

    /** Material registry predicate; matchMaterial handles namespaces and case. */
    public static boolean isKnownMaterial(String token) {
        return Material.matchMaterial(token) != null;
    }

    /** Lowercase material names for did-you-mean hints. */
    public static Set<String> materialNames() {
        Set<String> names = new HashSet<>();
        for (Material material : Material.values()) {
            names.add(material.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
