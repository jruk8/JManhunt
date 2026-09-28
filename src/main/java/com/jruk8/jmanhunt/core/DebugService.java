package com.jruk8.jmanhunt.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runtime-only debug output state. The console and each player hold
 * independent levels via /manhunt debug; nothing persists across
 * restarts. A null or absent level means output is off.
 */
public final class DebugService {
    private DebugLevel consoleLevel;
    private final Map<UUID, DebugLevel> playerLevels = new HashMap<>();

    public DebugLevel consoleLevel() {
        return consoleLevel;
    }

    public boolean isConsoleEnabled() {
        return consoleLevel != null;
    }

    /** Sets console debug output, returning the level. */
    public DebugLevel setConsoleLevel(DebugLevel level) {
        consoleLevel = level;
        return consoleLevel;
    }

    /** Turns console debug output off. */
    public void disableConsole() {
        consoleLevel = null;
    }

    /**
     * Flips console debug output: off becomes INFO, any level becomes
     * off. Returns the now-active level, or empty when now off.
     */
    public Optional<DebugLevel> toggleConsole() {
        if (consoleLevel == null) {
            return Optional.of(setConsoleLevel(DebugLevel.INFO));
        }
        disableConsole();
        return Optional.empty();
    }

    public DebugLevel playerLevel(UUID playerId) {
        return playerLevels.get(playerId);
    }

    public boolean isPlayerEnabled(UUID playerId) {
        return playerLevels.containsKey(playerId);
    }

    /** Sets a player's debug output, returning the level. */
    public DebugLevel setPlayerLevel(UUID playerId, DebugLevel level) {
        playerLevels.put(playerId, level);
        return level;
    }

    /** Turns a player's debug output off. */
    public void disablePlayer(UUID playerId) {
        playerLevels.remove(playerId);
    }

    /**
     * Flips a player's debug output: off becomes INFO, any level
     * becomes off. Returns the now-active level, or empty when now off.
     */
    public Optional<DebugLevel> togglePlayer(UUID playerId) {
        if (!playerLevels.containsKey(playerId)) {
            return Optional.of(setPlayerLevel(playerId, DebugLevel.INFO));
        }
        disablePlayer(playerId);
        return Optional.empty();
    }

    /** Player debug levels by id; an unmodifiable copy. */
    public Map<UUID, DebugLevel> playerLevels() {
        return Collections.unmodifiableMap(new HashMap<>(playerLevels));
    }

    /** True when at least the console or one player wants debug output. */
    public boolean hasRecipients() {
        return consoleLevel != null || !playerLevels.isEmpty();
    }
}
