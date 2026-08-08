package com.jruk8.jmanhunt;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/** Built-in PlaceholderAPI expansion, intentionally shaped like an eCloud expansion. */
public final class JManhuntExpansion extends PlaceholderExpansion {
    private final JManhuntPlugin plugin;
    private final StatsManager stats;
    private final MessageService messages;

    public JManhuntExpansion(JManhuntPlugin plugin, StatsManager stats, MessageService messages) {
        this.plugin = plugin; this.stats = stats; this.messages = messages;
    }

    @Override public String getIdentifier() { return "jmanhunt"; }
    @Override public String getAuthor() { return "jruk8"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        String key = params.toLowerCase(Locale.ROOT);
        String path = "placeholders." + key;
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) return "";
        StatsManager.CareerStats value = stats.career(player.getUniqueId());
        String raw = switch (key) {
            case "time_as_speedrunner" -> String.valueOf(value.timeSpeedrunner);
            case "time_as_hunter" -> String.valueOf(value.timeHunter);
            case "formatted_time_as_speedrunner" -> formatTime(value.timeSpeedrunner);
            case "formatted_time_as_hunter" -> formatTime(value.timeHunter);
            case "total_kills" -> String.valueOf(value.kills);
            case "total_kills_as_hunter" -> String.valueOf(value.hunterKills);
            case "total_kills_as_speedrunner" -> String.valueOf(value.speedrunnerKills);
            case "total_final_kills" -> String.valueOf(value.finalKills);
            case "total_damage_dealt" -> String.format(Locale.ROOT, "%.1f", value.damage);
            case "total_wins" -> String.valueOf(value.wins);
            case "total_wins_as_hunter" -> String.valueOf(value.hunterWins);
            case "total_wins_as_speedrunner" -> String.valueOf(value.speedrunnerWins);
            case "total_game_sessions" -> String.valueOf(value.sessions);
            case "sessions_as_speedrunner" -> String.valueOf(value.speedrunnerSessions);
            case "sessions_as_hunter" -> String.valueOf(value.hunterSessions);
            case "formatted_total_playtime" -> formatTime(value.timeSpeedrunner + value.timeHunter);
            case "total_kd_as_speedrunner" -> formatKd(value.speedrunnerKills, value.speedrunnerSessions);
            case "total_kd_as_hunter" -> formatKd(value.hunterKills, value.hunterSessions);
            default -> null;
        };
        if (raw == null) return null;
        String format = plugin.getConfig().getString(path + ".format", "{value}");
        return messages.formatPlaceholder(format.replace("{value}", raw));
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
    }

    private String formatKd(int kills, int sessions) {
        if (sessions <= 0) return "0.00";
        return String.format(Locale.ROOT, "%.2f", (double) kills / sessions);
    }
}
