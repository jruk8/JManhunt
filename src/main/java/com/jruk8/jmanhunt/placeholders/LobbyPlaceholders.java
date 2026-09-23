package com.jruk8.jmanhunt.placeholders;

import com.jruk8.jmanhunt.match.GameInstance;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure lobby-placeholder logic: key parsing plus phase mapping. No
 * PlaceholderAPI types, so unit tests cover it directly.
 */
public final class LobbyPlaceholders {
    /** Lobby-scoped bases: game_duration_0 etc. */
    private static final Pattern LOBBY_KEY = Pattern.compile(
            "^(game_duration|game_speedrunners_remaining|game_hunters_remaining|game_players_remaining|"
                    + "game_spectators|lobby_lifetime_sessions|lobby_current_sessions|game_phase|"
                    + "game_time_remaining(?:_formatted)?)_(\\d+)$");

    private LobbyPlaceholders() {
    }

    /** Parsed lobby placeholder: base name plus lobby id. */
    public record LobbyKey(String base, int lobbyId) {
    }

    /** Splits lobby placeholders like game_phase_0; empty for anything else. */
    public static Optional<LobbyKey> parseLobbyKey(String key) {
        Matcher matcher = LOBBY_KEY.matcher(key);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LobbyKey(matcher.group(1), Integer.parseInt(matcher.group(2))));
        } catch (NumberFormatException overlarge) {
            return Optional.empty();
        }
    }

    /**
     * Lobby match phase: LOBBY with no game, PRESTART inside a configured
     * pre-start window, ENDED once the end delay starts, else IN_PROGRESS
     * (names come from the placeholders config).
     */
    public static String phaseFor(GameInstance match, boolean prestartEnabled, PhaseNames phases) {
        if (match == null) {
            return phases.getLobby();
        }
        if (match.ending()) {
            return phases.getEnded();
        }
        if (!match.begun() && prestartEnabled) {
            return phases.getPrestart();
        }
        return phases.getInprogress();
    }
}
