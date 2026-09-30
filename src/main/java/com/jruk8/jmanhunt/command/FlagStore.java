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
 * player keys from the executor and role keys from the named role.
 * In-memory only: a reload or restart wipes every flag. No Bukkit
 * types.
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
     * One role flag by prefixed key, honoring ALL consensus: an ALL
     * key reads the value both teams share, or {@link #UNSET} when
     * they disagree.
     */
    public String roleOrAll(long matchId, String key) {
        if (!key.startsWith("ALL:")) {
            return role(matchId, key);
        }
        String name = key.substring(4);
        String hunter = role(matchId, roleKey("HUNTER", name));
        String runner = role(matchId, roleKey("SPEEDRUNNER", name));
        return hunter.equals(runner) ? hunter : UNSET;
    }

    /**
     * Stores one role flag by prefixed key, fanning ALL keys out to
     * both teams so later single-role reads see the value.
     */
    public void setRoleOrAll(long matchId, String key, String value) {
        if (!key.startsWith("ALL:")) {
            setRole(matchId, key, value);
            return;
        }
        String name = key.substring(4);
        setRole(matchId, roleKey("HUNTER", name), value);
        setRole(matchId, roleKey("SPEEDRUNNER", name), value);
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
     * Canonical role for role-scoped tags: HUNTER, SPEEDRUNNER, or
     * ALL (both teams), case-insensitive, quotes parsed. Anything
     * else misses silently for reference detection.
     */
    public static Optional<String> canonicalRole(String raw) {
        Optional<String> item = CommandPlaceholders.parsePickItem(raw);
        String role = item.map(value -> value.strip().toUpperCase(Locale.ROOT)).orElse("");
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER") && !role.equals("ALL")) {
            return Optional.empty();
        }
        return Optional.of(role);
    }

    /**
     * Canonical role for one role tag arg, warning on anything but
     * HUNTER, SPEEDRUNNER, or ALL.
     */
    public static Optional<String> parseRole(String tag, String root, String raw,
            ModifierTagScope scope) {
        Optional<String> role = canonicalRole(raw);
        if (role.isEmpty()) {
            scope.warn("Tag <" + root + "> needs HUNTER, SPEEDRUNNER, or ALL: " + tag);
        }
        return role;
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
