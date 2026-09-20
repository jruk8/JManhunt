package com.jruk8.jmanhunt;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime-only debug output state. The console and each player toggle
 * independently via /manhunt debug; nothing persists across restarts.
 */
public final class DebugService {
    private boolean consoleEnabled;
    private final Set<UUID> players = new HashSet<>();

    public boolean isConsoleEnabled() {
        return consoleEnabled;
    }

    /** Sets console debug output, returning the new state. */
    public boolean setConsoleEnabled(boolean enabled) {
        consoleEnabled = enabled;
        return consoleEnabled;
    }

    /** Flips console debug output, returning the new state. */
    public boolean toggleConsole() {
        return setConsoleEnabled(!consoleEnabled);
    }

    public boolean isPlayerEnabled(UUID playerId) {
        return players.contains(playerId);
    }

    /** Sets a player's debug output, returning the new state. */
    public boolean setPlayerEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            players.add(playerId);
        } else {
            players.remove(playerId);
        }
        return enabled;
    }

    /** Flips a player's debug output, returning the new state. */
    public boolean togglePlayer(UUID playerId) {
        return setPlayerEnabled(playerId, !isPlayerEnabled(playerId));
    }

    /** Ids of players with debug output enabled. */
    public Set<UUID> debugPlayerIds() {
        return Collections.unmodifiableSet(new HashSet<>(players));
    }

    /** True when at least the console or one player wants debug output. */
    public boolean hasRecipients() {
        return consoleEnabled || !players.isEmpty();
    }

    /** Applies the configured startup default, clearing all runtime toggles. */
    public void resetToDefaults(boolean consoleDefault) {
        consoleEnabled = consoleDefault;
        players.clear();
    }
}
