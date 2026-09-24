package com.jruk8.jmanhunt.stats;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves the eight lifetime placeholders for the server history book.
 */
public final class HistoryPlaceholders {

    public static final String SERVER_MATCHES = "server_matches";
    public static final String SERVER_KILLS = "server_kills";
    public static final String SERVER_HUNTER_KILLS = "server_hunter_kills";
    public static final String SERVER_SPEEDRUNNER_KILLS = "server_speedrunner_kills";
    public static final String SERVER_HUNTER_WINS = "server_hunter_wins";
    public static final String SERVER_SPEEDRUNNER_WINS = "server_speedrunner_wins";
    public static final String SERVER_DAMAGE = "server_damage";
    public static final String SERVER_PLAYTIME = "server_playtime";

    private HistoryPlaceholders() {
    }

    /**
     * Lifetime totals in fixed field order: matches, kills, hunter kills,
     * speedrunner kills, hunter wins, speedrunner wins, damage, playtime.
     */
    public record Totals(
            long matches,
            long kills,
            long hunterKills,
            long speedrunnerKills,
            long hunterWins,
            long speedrunnerWins,
            double damage,
            long playtimeMillis) {

        public static Totals empty() {
            return new Totals(0, 0, 0, 0, 0, 0, 0.0, 0);
        }
    }

    public static Map<String, String> resolve(Totals totals) {
        return Map.of(
                SERVER_MATCHES, Long.toString(totals.matches()),
                SERVER_KILLS, Long.toString(totals.kills()),
                SERVER_HUNTER_KILLS, Long.toString(totals.hunterKills()),
                SERVER_SPEEDRUNNER_KILLS, Long.toString(totals.speedrunnerKills()),
                SERVER_HUNTER_WINS, Long.toString(totals.hunterWins()),
                SERVER_SPEEDRUNNER_WINS, Long.toString(totals.speedrunnerWins()),
                SERVER_DAMAGE, String.format(Locale.ROOT, "%.1f", totals.damage()),
                SERVER_PLAYTIME, formatPlaytime(totals.playtimeMillis()));
    }

    static String formatPlaytime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
