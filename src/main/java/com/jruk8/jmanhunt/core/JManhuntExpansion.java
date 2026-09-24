package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.WinCondition;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.match.lifecycle.TimeLimitService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.placeholders.LobbyPlaceholders;
import com.jruk8.jmanhunt.placeholders.PlaceholderConfig;
import com.jruk8.jmanhunt.placeholders.PlaceholderEntry;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.CareerStats;
import com.jruk8.jmanhunt.stats.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Built-in PlaceholderAPI expansion, intentionally shaped like an eCloud expansion. */
public final class JManhuntExpansion extends PlaceholderExpansion {
    private final JManhuntPlugin plugin;
    private final StatsManager stats;
    private final MessageService messages;
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final WinConditionEngine winConditions;
    private final PlaceholderConfig placeholders;

    public JManhuntExpansion(JManhuntPlugin plugin, StatsManager stats, MessageService messages,
            GameManager game, PlayerStateStore playerStates, WinConditionEngine winConditions,
            PlaceholderConfig placeholders) {
        this.plugin = plugin;
        this.stats = stats;
        this.messages = messages;
        this.game = game;
        this.playerStates = playerStates;
        this.winConditions = winConditions;
        this.placeholders = placeholders;
    }

    @Override public String getIdentifier() { return "jmanhunt"; }
    @Override public String getAuthor() { return "jruk8"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Optional<LobbyPlaceholders.LobbyKey> lobbyKey = LobbyPlaceholders.parseLobbyKey(key);
        if (lobbyKey.isPresent()) {
            return applyFormat(lobbyKey.get().base(), lobbyValue(lobbyKey.get()));
        }
        if (player == null) {
            return "";
        }
        String raw = playerValue(player, key);
        if (raw == null) {
            return null;
        }
        return applyFormat(key, raw);
    }

    private String applyFormat(String base, String raw) {
        PlaceholderEntry entry = placeholders.getPlaceholders().get(base);
        if (entry != null && !entry.isEnabled()) {
            return "";
        }
        String format = entry == null ? "{value}" : entry.getFormat();
        return messages.formatPlaceholder(format.replace("{value}", raw));
    }

    /** Player-scoped values: career totals plus live session numbers. Null when unknown. */
    private String playerValue(OfflinePlayer player, String key) {
        CareerStats value = stats.career(player.getUniqueId());
        return switch (key) {
            case "time_as_speedrunner" -> String.valueOf(value.timeSpeedrunner);
            case "time_as_hunter" -> String.valueOf(value.timeHunter);
            case "formatted_time_as_speedrunner" -> formatTime(value.timeSpeedrunner);
            case "formatted_time_as_hunter" -> formatTime(value.timeHunter);
            case "total_kills" -> String.valueOf(value.kills);
            case "total_kills_as_hunter" -> String.valueOf(value.hunterKills);
            case "total_kills_as_speedrunner" -> String.valueOf(value.speedrunnerKills);
            case "total_final_kills" -> String.valueOf(value.finalKills);
            case "total_damage_dealt" -> String.format(Locale.ROOT, "%.1f", value.damage);
            case "total_wins" -> String.valueOf(value.hunterWins + value.speedrunnerWins);
            case "total_wins_as_hunter" -> String.valueOf(value.hunterWins);
            case "total_wins_as_speedrunner" -> String.valueOf(value.speedrunnerWins);
            case "total_game_sessions" -> String.valueOf(value.sessions);
            case "sessions_as_speedrunner" -> String.valueOf(value.speedrunnerSessions);
            case "sessions_as_hunter" -> String.valueOf(value.hunterSessions);
            case "formatted_total_playtime" -> formatTime(value.timeSpeedrunner + value.timeHunter);
            case "total_kd_as_speedrunner" -> formatKd(value.speedrunnerKills, value.speedrunnerSessions);
            case "total_kd_as_hunter" -> formatKd(value.hunterKills, value.hunterSessions);
            case "current_win_streak" -> String.valueOf(value.currentWinStreak);
            case "best_win_streak" -> String.valueOf(value.bestWinStreak);
            case "game_role" -> messages.formatPlaceholder(messages.roleName(roleOf(player)));
            case "game_kills_this_session" -> String.valueOf(sessionStat(player, true));
            case "game_deaths_this_session" -> String.valueOf(sessionStat(player, false));
            default -> null;
        };
    }

    /** Lobby-scoped values; every base resolves (fallbacks included), never null. */
    private String lobbyValue(LobbyPlaceholders.LobbyKey lobbyKey) {
        Optional<GameInstance> match = resolveLobbyInstance(lobbyKey.lobbyId());
        return switch (lobbyKey.base()) {
            case "game_duration" -> match
                    .map(instance -> String.valueOf(System.currentTimeMillis() - instance.startedAtMillis()))
                    .orElse("-1");
            case "game_speedrunners_remaining" -> String.valueOf(
                    match.map(game::activeRunnerCount).orElse(0));
            case "game_hunters_remaining" -> String.valueOf(
                    match.map(game::activeHunterCount).orElse(0));
            case "game_players_remaining" -> String.valueOf(match
                    .map(instance -> game.activeRunnerCount(instance) + game.activeHunterCount(instance))
                    .orElse(0));
            case "game_spectators" -> String.valueOf(match.map(this::spectatorCount).orElse(0));
            case "lobby_lifetime_sessions" -> String.valueOf(stats.lifetimeSessions(lobbyKey.lobbyId()));
            case "lobby_current_sessions" -> String.valueOf(game.instancesForLobby(lobbyKey.lobbyId()).size());
            case "game_phase" -> LobbyPlaceholders.phaseFor(match.orElse(null), prestartEnabled(),
                    placeholders.getPhases());
            case "game_time_remaining" -> String.valueOf(match
                    .map(this::timeRemainingMillis).orElse(-1L));
            case "game_time_remaining_formatted" -> match
                    .map(instance -> formatRemaining(timeRemainingMillis(instance))).orElse("-1");
            default -> "-1";
        };
    }

    /**
     * The lobby's match: with sub-lobbies the first sub-lobby running a
     * game wins, otherwise the lobby's own match.
     */
    private Optional<GameInstance> resolveLobbyInstance(int lobbyId) {
        List<GameInstance> matches = game.instancesForLobby(lobbyId);
        return matches.stream()
                .sorted(Comparator.comparing((GameInstance instance) -> instance.subLobby() == null)
                        .thenComparing(instance -> instance.subLobby() == null
                                ? 0 : instance.subLobby().subId()))
                .findFirst();
    }

    private int spectatorCount(GameInstance instance) {
        int count = 0;
        for (var player : game.onlineAssignedPlayers(instance)) {
            if (playerStates.role(player).isWatching()) {
                count++;
            }
        }
        return count;
    }

    private boolean prestartEnabled() {
        return plugin.configService().getBoolean("settings.match.start-on-speedrunner-damage.enabled", false);
    }

    /** Survive clock left in milliseconds, or -1 when none runs. */
    private long timeRemainingMillis(GameInstance instance) {
        Double runnerSecs = winConditions.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME)
                ? winConditions.time(Role.SPEEDRUNNER) : null;
        Double hunterSecs = winConditions.enabled(Role.HUNTER, WinCondition.TIME_LIMIT)
                ? winConditions.time(Role.HUNTER) : null;
        Double cancelSecs = winConditions.cancelSurviveEnabled()
                ? winConditions.cancelSurviveTime() : null;
        TimeLimitService.SurviveOutcome limit = TimeLimitService.resolveSurvive(
                runnerSecs, hunterSecs, cancelSecs);
        if (limit == null) {
            return -1L;
        }
        long elapsed = System.currentTimeMillis() - instance.startedAtMillis();
        return Math.max(0L, Math.round(limit.limitSecs() * 1000.0) - elapsed);
    }

    private static String formatRemaining(long remainingMillis) {
        if (remainingMillis < 0L) {
            return "-1";
        }
        return DurationFormat.format(remainingMillis / 1000L);
    }

    private Role roleOf(OfflinePlayer player) {
        return playerStates.role(player.getUniqueId());
    }

    /** Session kills or deaths: -1 outside a match, 0 with no score yet. */
    private int sessionStat(OfflinePlayer player, boolean kills) {
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty()) {
            return -1;
        }
        return stats.matchStats(match.get().matchId(), player.getUniqueId())
                .map(slice -> kills ? slice.kills : slice.deaths)
                .orElse(0);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
    }

    private String formatKd(int kills, int sessions) {
        if (sessions <= 0) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%.2f", (double) kills / sessions);
    }
}
