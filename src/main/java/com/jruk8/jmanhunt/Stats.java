package com.jruk8.jmanhunt;

import java.util.Locale;

public final class Stats {
    public String player = "unknown";
    public double damage;
    public int kills;
    public int finalKills;
    public int progression;

    public int value(String statistic) {
        return switch (statistic.toUpperCase(Locale.ROOT)) {
            case "DAMAGE_DEALT" -> (int) damage;
            case "KILLS" -> kills;
            case "FINAL_KILLS" -> finalKills;
            case "PROGRESSION" -> progression;
            default -> 0;
        };
    }
}
