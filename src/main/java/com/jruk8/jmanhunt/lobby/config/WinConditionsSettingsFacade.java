package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.config.WinConditionsSettings;
import com.jruk8.jmanhunt.match.WinCondition;
import com.jruk8.jmanhunt.player.Role;

/**
 * Typed lobby-aware reads for settings.match.win-conditions. Each method
 * resolves one override path: the lobby value wins, the typed global
 * section is the fallback. Callers take this facade, never raw paths.
 */
public final class WinConditionsSettingsFacade {
    private static final String BASE = "settings.match.win-conditions.";

    private final OverrideService overrides;
    private final WinConditionsSettings global;

    public WinConditionsSettingsFacade(OverrideService overrides,
            WinConditionsSettings global) {
        this.overrides = overrides;
        this.global = global;
    }

    /** True when the side's condition is enabled. */
    public boolean conditionEnabled(Integer lobby, Role role,
            WinCondition condition) {
        return overrides.getBoolean(lobby, base(role, condition) + "enabled",
                conditionEnabledGlobal(role, condition));
    }

    /** Configured clock in seconds for the side. */
    public double surviveTime(Integer lobby, Role role) {
        return overrides.getDouble(lobby,
                base(role, WinCondition.SURVIVE_TIME) + "time",
                surviveTimeGlobal(role));
    }

    /** Configured item for the side's acquire-item condition. */
    public String acquireItem(Integer lobby, Role role) {
        return overrides.getString(lobby,
                base(role, WinCondition.ACQUIRE_ITEM) + "item",
                acquireItemGlobal(role));
    }

    /** Configured mob for the side's kill-mob condition. */
    public String killMob(Integer lobby, Role role) {
        return overrides.getString(lobby,
                base(role, WinCondition.KILL_MOB) + "mob",
                killMobGlobal(role));
    }

    /** Configured advancement for the side's reach-advancement condition. */
    public String reachAdvancement(Integer lobby, Role role) {
        return overrides.getString(lobby,
                base(role, WinCondition.REACH_ADVANCEMENT) + "advancement",
                reachAdvancementGlobal(role));
    }

    /** True when the cancel survived-time condition is enabled. */
    public boolean cancelSurviveEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "cancel.survived-time.enabled",
                global.getCancel().getSurvivedTime().isEnabled());
    }

    /** Cancel survived-time in seconds. */
    public double cancelSurviveTime(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "cancel.survived-time.time",
                global.getCancel().getSurvivedTime().getTime());
    }

    private String base(Role role, WinCondition condition) {
        return BASE + side(role) + "." + leaf(condition) + ".";
    }

    private static String side(Role role) {
        return role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
    }

    private static String leaf(WinCondition condition) {
        // Both clock variants share the survive-time leaf: hunters read
        // settings.match.win-conditions.hunter.survive-time.* like speedrunners.
        return switch (condition) {
            case EXIT_END -> "exit-end";
            case SURVIVE_TIME, TIME_LIMIT -> "survive-time";
            case ACQUIRE_ITEM -> "acquire-item";
            case REACH_ADVANCEMENT -> "reach-advancement";
            case KILL_MOB -> "kill-mob";
        };
    }

    private boolean conditionEnabledGlobal(Role role, WinCondition condition) {
        if (role == Role.SPEEDRUNNER) {
            var side = global.getSpeedrunner();
            return switch (condition) {
                case EXIT_END -> side.getExitEnd().isEnabled();
                case SURVIVE_TIME, TIME_LIMIT -> side.getSurviveTime().isEnabled();
                case ACQUIRE_ITEM -> side.getAcquireItem().isEnabled();
                case REACH_ADVANCEMENT -> side.getReachAdvancement().isEnabled();
                case KILL_MOB -> side.getKillMob().isEnabled();
            };
        }
        var side = global.getHunter();
        return switch (condition) {
            case EXIT_END -> false;
            case SURVIVE_TIME, TIME_LIMIT -> side.getSurviveTime().isEnabled();
            case ACQUIRE_ITEM -> side.getAcquireItem().isEnabled();
            case REACH_ADVANCEMENT -> side.getReachAdvancement().isEnabled();
            case KILL_MOB -> side.getKillMob().isEnabled();
        };
    }

    private double surviveTimeGlobal(Role role) {
        if (role == Role.SPEEDRUNNER) {
            return global.getSpeedrunner().getSurviveTime().getTime();
        }
        if (role == Role.HUNTER) {
            return global.getHunter().getSurviveTime().getTime();
        }
        return 3600.0;
    }

    private String acquireItemGlobal(Role role) {
        if (role == Role.SPEEDRUNNER) {
            return global.getSpeedrunner().getAcquireItem().getItem();
        }
        return global.getHunter().getAcquireItem().getItem();
    }

    private String killMobGlobal(Role role) {
        if (role == Role.SPEEDRUNNER) {
            return global.getSpeedrunner().getKillMob().getMob();
        }
        return global.getHunter().getKillMob().getMob();
    }

    private String reachAdvancementGlobal(Role role) {
        if (role == Role.SPEEDRUNNER) {
            return global.getSpeedrunner().getReachAdvancement().getAdvancement();
        }
        return global.getHunter().getReachAdvancement().getAdvancement();
    }
}
