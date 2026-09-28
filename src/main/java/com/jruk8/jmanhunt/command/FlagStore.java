package com.jruk8.jmanhunt.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Match-scoped flag storage behind {@code <gflag>},
 * {@code <pflag>}, and {@code <rflag>}. Every map keys per match id
 * so concurrent matches never share flags; teardown drops the whole
 * match. Player keys arrive already suffixed ({@code name-Player})
 * and role keys already prefixed ({@code ROLE:name}); callers build
 * both from the executor. In-memory only: a reload or restart wipes
 * every flag. No Bukkit types.
 */
public final class FlagStore {

    /** What a get returns when nothing was set. */
    public static final String UNSET = "null";

    private final Map<Long, Map<String, String>> globals = new HashMap<>();
    private final Map<Long, Map<String, String>> players = new HashMap<>();
    private final Map<Long, Map<String, String>> roles = new HashMap<>();

    /** One global flag, or {@link #UNSET} when nothing was set. */
    public String global(long matchId, String key) {
        return get(globals, matchId, key);
    }

    /** Stores one global flag for the match. */
    public void setGlobal(long matchId, String key, String value) {
        set(globals, matchId, key, value);
    }

    /** One player flag by suffixed key, or {@link #UNSET}. */
    public String player(long matchId, String key) {
        return get(players, matchId, key);
    }

    /** Stores one player flag by suffixed key. */
    public void setPlayer(long matchId, String key, String value) {
        set(players, matchId, key, value);
    }

    /** One role flag by prefixed key, or {@link #UNSET}. */
    public String role(long matchId, String key) {
        return get(roles, matchId, key);
    }

    /** Stores one role flag by prefixed key. */
    public void setRole(long matchId, String key, String value) {
        set(roles, matchId, key, value);
    }

    /**
     * Drops every player flag whose suffix names this player. Names
     * match exactly, like the keys themselves.
     */
    public void removePlayer(long matchId, String playerName) {
        Map<String, String> map = players.get(matchId);
        if (map == null) {
            return;
        }
        String suffix = "-" + playerName;
        Iterator<String> keys = map.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().endsWith(suffix)) {
                keys.remove();
            }
        }
    }

    /** Drops every flag of one match: globals, players, and roles. */
    public void clearMatch(long matchId) {
        globals.remove(matchId);
        players.remove(matchId);
        roles.remove(matchId);
    }

    private static String get(Map<Long, Map<String, String>> outer, long matchId, String key) {
        Map<String, String> map = outer.get(matchId);
        return map == null ? UNSET : map.getOrDefault(key, UNSET);
    }

    private static void set(Map<Long, Map<String, String>> outer, long matchId, String key,
            String value) {
        outer.computeIfAbsent(matchId, ignored -> new HashMap<>()).put(key, value);
    }

    /** Suffix joining a flag name to its owning player. */
    public static String playerKey(String name, String suffix) {
        return name + "-" + suffix;
    }

    /** Player suffix for console dispatch, which names no player. */
    public static String consoleSuffix() {
        return "CONSOLE";
    }

    /** Player suffix from the scope executor, else the console one. */
    public static String suffixFor(ModifierTagScope scope) {
        String executor = scope.executorName();
        return executor == null ? consoleSuffix() : executor;
    }

    /** Key joining a role flag name to its owning upper-case role. */
    public static String roleKey(String role, String name) {
        return role + ":" + name;
    }

    /**
     * Executor role for role-scoped tags: the executor's team when it
     * is HUNTER or SPEEDRUNNER, else empty (console, unknown, or
     * other roles like spectator).
     */
    public static Optional<String> executorRole(ModifierTagScope scope) {
        String executor = scope.executorName();
        if (executor == null) {
            return Optional.empty();
        }
        return scope.participants().stream()
                .filter(candidate -> candidate.name().equals(executor))
                .map(ModifierTagScope.Participant::team)
                .filter(team -> team.equalsIgnoreCase("HUNTER")
                        || team.equalsIgnoreCase("SPEEDRUNNER"))
                .map(team -> team.toUpperCase(Locale.ROOT))
                .findFirst();
    }

    /** Validates one flag name: trimmed, non-blank, quotes parsed. */
    public static Optional<String> parseName(String raw) {
        Optional<String> item = CommandPlaceholders.parsePickItem(raw);
        if (item.isEmpty() || item.get().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(item.get().strip());
    }
}
