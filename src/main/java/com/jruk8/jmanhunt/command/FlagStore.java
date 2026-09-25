package com.jruk8.jmanhunt.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Match-scoped flag storage behind {@code <gflag>} and
 * {@code <pflag>}. Both maps key per match id so concurrent matches
 * never share flags; teardown drops the whole match. Player keys
 * arrive already suffixed ({@code name-Player}); callers build the
 * suffix from the executor. In-memory only: a reload or restart
 * wipes every flag. No Bukkit types.
 */
public final class FlagStore {

    /** What a get returns when nothing was set. */
    public static final String UNSET = "null";

    private final Map<Long, Map<String, String>> globals = new HashMap<>();
    private final Map<Long, Map<String, String>> players = new HashMap<>();

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

    /** Drops every flag of one match: globals and players alike. */
    public void clearMatch(long matchId) {
        globals.remove(matchId);
        players.remove(matchId);
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

    /** Validates one flag name: trimmed, non-blank, quotes parsed. */
    public static Optional<String> parseName(String raw) {
        Optional<String> item = CommandPlaceholders.parsePickItem(raw);
        if (item.isEmpty() || item.get().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(item.get().strip());
    }
}
