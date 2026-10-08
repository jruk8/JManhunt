package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.config.MatchSettings;
import com.jruk8.jmanhunt.match.prestart.OnExpire;

/**
 * Typed lobby-aware reads for the settings.match subtree, except the
 * win-conditions section which has its own facade. Each method resolves
 * one override path: the lobby value wins, the typed global section is
 * the fallback. Callers take this facade, never raw paths.
 */
public final class MatchSettingsFacade {
    private static final String BASE = "settings.match.";

    private final OverrideService overrides;
    private final MatchSettings global;

    public MatchSettingsFacade(OverrideService overrides, MatchSettings global) {
        this.overrides = overrides;
        this.global = global;
    }

    /** True when lobbies autostart matches. */
    public boolean autostartEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "autostart.enabled",
                global.getAutostart().isEnabled());
    }

    /** Autostart countdown style, raw name. */
    public String autostartCountdownStyle(Integer lobby) {
        return overrides.getString(lobby, BASE + "autostart.countdown-style",
                global.getAutostart().getCountdownStyle());
    }

    /** Hunters required for autostart. */
    public int autostartMinimumHunters(Integer lobby) {
        return overrides.getInt(lobby, BASE + "autostart.minimums.hunter",
                global.getAutostart().getMinimums().getHunter());
    }

    /** Speedrunners required for autostart. */
    public int autostartMinimumSpeedrunners(Integer lobby) {
        return overrides.getInt(lobby, BASE + "autostart.minimums.speedrunner",
                global.getAutostart().getMinimums().getSpeedrunner());
    }

    /** Hunter cap for autostart, -1 means unlimited. */
    public int autostartMaximumHunters(Integer lobby) {
        return overrides.getInt(lobby, BASE + "autostart.maximums.hunter",
                global.getAutostart().getMaximums().getHunter());
    }

    /** Speedrunner cap for autostart, -1 means unlimited. */
    public int autostartMaximumSpeedrunners(Integer lobby) {
        return overrides.getInt(lobby, BASE + "autostart.maximums.speedrunner",
                global.getAutostart().getMaximums().getSpeedrunner());
    }

    /** True when autostart broadcasts its requirements. */
    public boolean autostartBroadcastEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "autostart.broadcast-requirements.enabled",
                global.getAutostart().getBroadcastRequirements().isEnabled());
    }

    /** Seconds between autostart requirement broadcasts. */
    public int autostartBroadcastIntervalSeconds(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "autostart.broadcast-requirements.interval-seconds",
                global.getAutostart().getBroadcastRequirements()
                        .getIntervalSeconds());
    }

    /** Autostart countdown length in seconds. */
    public int autostartCountdownSeconds(Integer lobby) {
        return overrides.getInt(lobby, BASE + "autostart.countdown-seconds",
                global.getAutostart().getCountdownSeconds());
    }

    /** Where a match leaver goes, raw name. */
    public String gameLeaveDestination(Integer lobby) {
        return overrides.getString(lobby, BASE + "game-leave.destination",
                global.getGameLeave().getDestination().name());
    }

    /** True when play waits for the first speedrunner hit. */
    public boolean startOnDamageEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "start-on-speedrunner-damage.enabled",
                global.getStartOnSpeedrunnerDamage().isEnabled());
    }

    /** Seconds to wait for the first hit before timing out. */
    public int startOnDamageDelaySeconds(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "start-on-speedrunner-damage.delay-seconds",
                global.getStartOnSpeedrunnerDamage().getDelaySeconds());
    }

    /** What happens when the pre-start wait expires. */
    public OnExpire startOnDamageOnExpire(Integer lobby) {
        return overrides.getEnum(lobby,
                BASE + "start-on-speedrunner-damage.on-expire",
                OnExpire.class,
                global.getStartOnSpeedrunnerDamage().getOnExpire());
    }

    /** True when participants wait in adventure mode. */
    /** Global headstarts section, mirroring the prestart path (no lobby override). */
    public MatchSettings.Headstarts getHeadstarts() {
        return global.getHeadstarts();
    }

    public boolean startInAdventureMode(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "start-on-speedrunner-damage.start-in-adventure-mode",
                global.getStartOnSpeedrunnerDamage().isStartInAdventureMode());
    }
}
