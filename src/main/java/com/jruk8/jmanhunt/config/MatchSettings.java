package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.match.LeaveDestination;
import com.jruk8.jmanhunt.match.prestart.OnExpire;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Match flow settings. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class MatchSettings extends OkaeriConfig {

    @Comment({
            "Automatically start a match once each role reaches its configured",
            "minimum queued players.",
            "Default: false"
    })
    private Autostart autostart = new Autostart();

    @CustomKey("start-on-speedrunner-damage")
    @Comment({
            "Start the game only after a speedrunner hits a hunter.",
            "Default: true"
    })
    private StartOnSpeedrunnerDamage startOnSpeedrunnerDamage = new StartOnSpeedrunnerDamage();

    @Comment({
            "Head starts that hold one role in limbo while the other role",
            "plays. Each side is configured independently.",
            "If start-on-speedrunner-damage is also enabled, the",
            "headstarts only begin when a speedrunner first damages a hunter."
    })
    private Headstarts headstarts = new Headstarts();

    @CustomKey("game-leave")
    @Comment({
            "Where players go when they leave a running match, voluntarily or",
            "automatically: SPECTATOR keeps them at the match as a watcher, LOBBY",
            "sends them back to their lobby as NONE.",
            "Default: SPECTATOR"
    })
    private GameLeave gameLeave = new GameLeave();

    @CustomKey("limbo")
    @Comment({
            "Feedback for players waiting to spawn: headstart holds, join holds,",
            "and respawn waits."
    })
    private Limbo limbo = new Limbo();

    @CustomKey("win-conditions")
    @Comment({
            "Alternate win conditions. Multiple conditions can be enabled at the same",
            "time; any satisfied condition leads to a win.",
            "Default: exit-end enabled, all others disabled"
    })
    private WinConditionsSettings winConditions = new WinConditionsSettings();

    @CustomKey("game-boosts")
    @Comment({
            "Game boosts that skew world generation and loot odds in the speedrunners'",
            "favor, without touching any actual gameplay rules."
    })
    private GameBoosts gameBoosts = new GameBoosts();

    /** Automatic match start. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Autostart extends OkaeriConfig {
        private boolean enabled = true;

        @CustomKey("countdown-seconds")
        @Comment({
                "Seconds to wait before automatically running /manhunt start.",
                "Set to 0 to start immediately as soon as the queue is eligible."
        })
        private int countdownSeconds = 30;

        @CustomKey("countdown-style")
        @Comment({
                "Countdown announcement style: SIMPLE uses the plain",
                "autostart messages, VERSUS shows the runner-vs-hunter",
                "lineup instead.",
                "Default: VERSUS"
        })
        private String countdownStyle = "VERSUS";

        @Comment({
                "Minimum queued players per role before the autostart countdown can",
                "begin. Only applies to autostart; manual /manhunt start keeps its",
                "own one-hunter-one-speedrunner check. Hard minimum: 1 per role.",
                "Default: 1 hunter, 1 speedrunner"
        })
        private Minimums minimums = new Minimums();

        @Comment({
                "Maximum queued players per role before autostart refuses",
                "to begin. Only applies to autostart; manual start keeps",
                "its own checks. -1 disables per role.",
                "Default: -1 hunter, -1 speedrunner"
        })
        private Maximums maximums = new Maximums();

        @CustomKey("broadcast-requirements")
        @Comment({
                "Shortfall broadcasts telling queued players how many more of",
                "each role autostart needs.",
                "Default: false"
        })
        private BroadcastRequirements broadcastRequirements = new BroadcastRequirements();

        /** Minimum queued players per role. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class Minimums extends OkaeriConfig {
            private int hunter = 1;
            private int speedrunner = 1;

        }

        /** Maximum queued players per role; -1 disables per role. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class Maximums extends OkaeriConfig {
            private int hunter = -1;
            private int speedrunner = -1;

        }

        /** Shortfall broadcasts. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class BroadcastRequirements extends OkaeriConfig {
            private boolean enabled = false;

            @CustomKey("interval-seconds")
            @Comment({
                    "Seconds between broadcasts.",
                    "Default: 60"
            })
            private int intervalSeconds = 60;

        }
    }

    /** Pre-start wait for the first speedrunner hit. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class StartOnSpeedrunnerDamage extends OkaeriConfig {
        private boolean enabled = true;

        @CustomKey("delay-seconds")
        @Comment({
                "Seconds to wait for a speedrunner to hit a hunter before timing out.",
                "Values below 5 are clamped to 5 seconds. Set to -1 to wait indefinitely.",
                "Default: 45 seconds"
        })
        private int delaySeconds = 45;

        @CustomKey("on-expire")
        @Comment({
                "What happens if delay-seconds expires before a hit occurs:",
                "CANCEL - Abort the match without recording stats.",
                "FORCE_START - Force the match to start anyway.",
                "Default: FORCE_START"
        })
        private OnExpire onExpire = OnExpire.FORCE_START;

        @CustomKey("start-in-adventure-mode")
        @Comment({
                "When true, all participating players are set to adventure mode during",
                "the pre-start window (before a speedrunner hits a hunter). This prevents",
                "breaking blocks while waiting. Players are restored to survival when the",
                "game begins.",
                "Default: true"
        })
        private boolean startInAdventureMode = true;

        @CustomKey("reminder-interval")
        @Comment({
                "Seconds between reminders while waiting for the first speedrunner hit.",
                "Only used as the repeat interval when delay-seconds is -1 (wait",
                "indefinitely). Set to -1 to disable the reminders entirely while",
                "still waiting for damage."
        })
        private double reminderInterval = 30.0;

    }

    /** Per-side head starts. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Headstarts extends OkaeriConfig {

        @Comment({
                "Gives speedrunners a head start at match start by holding hunters in",
                "spectator mode.",
                "Default: true"
        })
        private Headstart speedrunner = headstart(true, 20);

        @Comment({
                "Same, but for hunters.",
                "Default: false"
        })
        private Headstart hunter = headstart(false, 20);

        private static Headstart headstart(boolean enabled, int delaySeconds) {
            Headstart headstart = new Headstart();
            headstart.setEnabled(enabled);
            headstart.setDelaySeconds(delaySeconds);
            return headstart;
        }

        /** One side's head start. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class Headstart extends OkaeriConfig {
            private boolean enabled = false;

            @CustomKey("delay-seconds")
            @Comment({
                    "Delay in seconds. Values <= 0 mean no delay.",
                    "Default: 20"
            })
            private int delaySeconds = 20;

        }
    }

    /** Match leave destination. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class GameLeave extends OkaeriConfig {
        private LeaveDestination destination = LeaveDestination.SPECTATOR;

    }

    /** Limbo feedback for players waiting to spawn. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Limbo extends OkaeriConfig {

        @CustomKey("multi-broadcast-interval")
        @Comment({
                "Seconds between 'players will spawn soon' broadcasts while two or",
                "more players wait to spawn. Minimum 1.",
                "Default: 20"
        })
        private int multiBroadcastInterval = 20;

    }

    /** World generation and loot boosts. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class GameBoosts extends OkaeriConfig {

        @CustomKey("nether-structures")
        @Comment({
                "When true, a datapack that significantly boosts nether structure (fortress and",
                "bastion remnant) spawn frequency is applied to the world.",
                "Requires a server restart to take effect.",
                "Default: false"
        })
        private Toggle netherStructures = new Toggle(false);

        @CustomKey("overworld-structures")
        @Comment({
                "When true, a datapack that moderately boosts overworld structure (village,",
                "shipwreck, buried treasure, dungeon, ruined portal) spawn frequency is",
                "applied to the world.",
                "Requires a server restart to take effect.",
                "Default: false"
        })
        private Toggle overworldStructures = new Toggle(false);

        @CustomKey("disable-brutes")
        @Comment({
                "When true, piglin brutes never spawn naturally, imitating",
                "1.16.1 where they did not spawn. Spawner eggs, spawners,",
                "and commands still work. Checked live on each spawn, so",
                "no restart is needed.",
                "Default: true"
        })
        private Toggle disableBrutes = new Toggle(true);

        @CustomKey("custom-piglin-barter")
        @Comment({
                "Custom loot tables for things like piglin bartering.",
                "Tables may be found in settings/loot-tables.",
                "Loot tables are JSON files with the same format as vanilla Minecraft loot tables",
                "but parsed with some limitations.",
                "",
                "Toggles between custom piglin barter odds.",
                "By default, the custom odds have boosted up ender pearl and obsidian rates.",
                "This may help in balancing the game.",
                "Default: true"
        })
        private boolean customPiglinBarter = true;

    }
}
