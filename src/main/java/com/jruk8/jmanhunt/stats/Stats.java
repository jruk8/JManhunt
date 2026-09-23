package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.Role;
import java.util.Locale;
import java.util.UUID;

public final class Stats {
    public String player = "unknown";
    public UUID uuid;
    public Role role = Role.NONE;
    public double damage;
    public int kills;
    public int deaths;
    public int finalKills;
    public int progression;
    public String progressionKey;
    public long matchStartedAt = System.currentTimeMillis();

    public double value(String statistic) {
        return switch (statistic.toUpperCase(Locale.ROOT)) {
            case "DAMAGE_DEALT" -> damage / 2.0; case "SPEEDRUNNER_KILLS", "KILLS" -> kills;
            case "HUNTER_FINAL_KILLS", "FINAL_KILLS" -> finalKills; case "PROGRESSION" -> progression; default -> 0;
        };
    }

    public boolean appliesTo(String statistic) {
        return switch (statistic.toUpperCase(Locale.ROOT)) {
            case "HUNTER_FINAL_KILLS", "FINAL_KILLS" -> role == Role.HUNTER;
            case "SPEEDRUNNER_KILLS", "KILLS" -> role == Role.SPEEDRUNNER;
            default -> true;
        };
    }

    public String displayValue(String statistic, MessageService messages) {
        if (statistic.equalsIgnoreCase("DAMAGE_DEALT")) {
            return String.format(Locale.ROOT, "%.1f Hearts", value(statistic));
        }
        if (statistic.equalsIgnoreCase("PROGRESSION")) {
            return messages.string("game.progression-names." + progressionKey, progressionKey);
        }
        return String.valueOf((int) value(statistic));
    }
}