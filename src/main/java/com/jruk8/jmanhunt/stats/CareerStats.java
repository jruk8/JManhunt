package com.jruk8.jmanhunt.stats;

public final class CareerStats {
    public String player = "unknown";
    public long timeSpeedrunner;
    public long timeHunter;
    public int kills;
    public int hunterKills;
    public int speedrunnerKills;
    public int finalKills;
    public double damage;
    public int wins;
    public int hunterWins;
    public int speedrunnerWins;
    public int sessions;
    public int speedrunnerSessions;
    public int hunterSessions;
    public int deaths;

    public boolean isEmpty() {
        return timeSpeedrunner == 0 && timeHunter == 0 && kills == 0 && hunterKills == 0
                && speedrunnerKills == 0 && finalKills == 0 && damage == 0 && wins == 0 && hunterWins == 0
                && speedrunnerWins == 0 && sessions == 0 && speedrunnerSessions == 0
                && hunterSessions == 0 && deaths == 0;
    }

    public void copyFrom(CareerStats source) {
        player = source.player; timeSpeedrunner = source.timeSpeedrunner; timeHunter = source.timeHunter;
        kills = source.kills; hunterKills = source.hunterKills; speedrunnerKills = source.speedrunnerKills;
        finalKills = source.finalKills; damage = source.damage; wins = source.wins;
        hunterWins = source.hunterWins; speedrunnerWins = source.speedrunnerWins; sessions = source.sessions;
        speedrunnerSessions = source.speedrunnerSessions; hunterSessions = source.hunterSessions;
        deaths = source.deaths;
    }

    public void add(CareerStats source) {
        timeSpeedrunner += source.timeSpeedrunner; timeHunter += source.timeHunter; kills += source.kills;
        hunterKills += source.hunterKills; speedrunnerKills += source.speedrunnerKills; finalKills += source.finalKills;
        damage += source.damage; wins += source.wins; hunterWins += source.hunterWins;
        speedrunnerWins += source.speedrunnerWins; sessions += source.sessions;
        speedrunnerSessions += source.speedrunnerSessions; hunterSessions += source.hunterSessions;
        deaths += source.deaths;
    }

    public CareerStats copy() {
        CareerStats copy = new CareerStats(); copy.player = player; copy.timeSpeedrunner = timeSpeedrunner;
        copy.timeHunter = timeHunter; copy.kills = kills; copy.hunterKills = hunterKills;
        copy.speedrunnerKills = speedrunnerKills; copy.finalKills = finalKills; copy.damage = damage;
        copy.wins = wins; copy.hunterWins = hunterWins; copy.speedrunnerWins = speedrunnerWins;
        copy.sessions = sessions; copy.speedrunnerSessions = speedrunnerSessions;
        copy.hunterSessions = hunterSessions; copy.deaths = deaths;
        return copy;
    }
}