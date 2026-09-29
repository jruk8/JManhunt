package com.jruk8.jmanhunt.command;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Run-local function scope for validation: which def names a line
 * may call without an unknown-tag warning. Collection mirrors
 * {@link TagFunctions#define} (identifier shape only, never
 * builtins) and grouping mirrors the runtime function tables:
 * player, hunter, and speedrunner share one executor context
 * while every other list runs alone.
 */
public final class TagFunctionScope {
    private static final Pattern FUNCTION_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private TagFunctionScope() {
    }

    /**
     * Lowercase function names a {@code <def:name,...>} actually stores
     * across the given lines.
     */
    public static Set<String> definedFunctions(Collection<String> lines) {
        Set<String> names = new HashSet<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            for (String body : CommandSyntax.tagBodies(line)) {
                int separator = body.indexOf(':');
                String name = (separator < 0 ? body : body.substring(0, separator))
                        .trim().toLowerCase(Locale.ROOT);
                if (!"def".equals(name) || separator < 0) {
                    continue;
                }
                List<String> parts =
                        TagLists.splitTopLevel(body.substring(separator + 1));
                if (parts.size() < 2 || parts.get(0).isBlank()) {
                    continue;
                }
                String function = parts.get(0).strip();
                if (!FUNCTION_NAME.matcher(function).matches()) {
                    continue;
                }
                String key = function.toLowerCase(Locale.ROOT);
                if (!TagFunctions.isBuiltin(key)) {
                    names.add(key);
                }
            }
        }
        return names;
    }

    /**
     * Lists sharing one function table with the edited list. Unknown
     * lists scope to themselves.
     */
    public static List<String> functionScopeLists(String list) {
        return switch (list.strip().toLowerCase(Locale.ROOT)) {
            case "player", "hunter", "speedrunner" -> List.of("player", "hunter",
                    "speedrunner");
            default -> List.of(list);
        };
    }
}
