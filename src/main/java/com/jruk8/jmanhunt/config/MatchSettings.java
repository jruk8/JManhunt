package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.match.LeaveDestination;
import com.jruk8.jmanhunt.match.prestart.OnExpire;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Match flow settings. */
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
            "Head starts that hold one role in spectator mode while the other role",
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

    public Autostart getAutostart() {
        return autostart;
    }

    public void setAutostart(Autostart autostart) {
        this.autostart = autostart;
    }

    public StartOnSpeedrunnerDamage getStartOnSpeedrunnerDamage() {
        return startOnSpeedrunnerDamage;
    }

    public void setStartOnSpeedrunnerDamage(StartOnSpeedrunnerDamage startOnSpeedrunnerDamage) {
        this.startOnSpeedrunnerDamage = startOnSpeedrunnerDamage;
    }

    public Headstarts getHeadstarts() {
        return headstarts;
    }

    public void setHeadstarts(Headstarts headstarts) {
        this.headstarts = headstarts;
    }

    public GameLeave getGameLeave() {
        return gameLeave;
    }

    public void setGameLeave(GameLeave gameLeave) {
        this.gameLeave = gameLeave;
    }

    public WinConditionsSettings getWinConditions() {
        return winConditions;
    }

    public void setWinConditions(WinConditionsSettings winConditions) {
        this.winConditions = winConditions;
    }

    public GameBoosts getGameBoosts() {
        return gameBoosts;
    }

    public void setGameBoosts(GameBoosts gameBoosts) {
        this.gameBoosts = gameBoosts;
    }

    /** Automatic match start. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Autostart extends OkaeriConfig {
        private boolean enabled = true;

        @CustomKey("countdown-seconds")
        @Comment({
                "Seconds to wait before automatically running /manhunt start.",
                "Set to 0 to start immediately as soon as the queue is eligible."
        })
        private int countdownSeconds = 45;

        @Comment({
                "Minimum queued players per role before the autostart countdown can",
                "begin. Only applies to autostart; manual /manhunt start keeps its",
                "own one-hunter-one-speedrunner check. Hard minimum: 1 per role.",
                "Default: 1 hunter, 1 speedrunner"
        })
        private Minimums minimums = new Minimums();

        @CustomKey("broadcast-requirements")
        @Comment({
                "Shortfall broadcasts telling queued players how many more of",
                "each role autostart needs.",
                "Default: false"
        })
        private BroadcastRequirements broadcastRequirements = new BroadcastRequirements();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCountdownSeconds() {
            return countdownSeconds;
        }

        public void setCountdownSeconds(int countdownSeconds) {
            this.countdownSeconds = countdownSeconds;
        }

        public Minimums getMinimums() {
            return minimums;
        }

        public void setMinimums(Minimums minimums) {
            this.minimums = minimums;
        }

        public BroadcastRequirements getBroadcastRequirements() {
            return broadcastRequirements;
        }

        public void setBroadcastRequirements(BroadcastRequirements broadcastRequirements) {
            this.broadcastRequirements = broadcastRequirements;
        }

        /** Minimum queued players per role. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Minimums extends OkaeriConfig {
            private int hunter = 1;
            private int speedrunner = 1;

            public int getHunter() {
                return hunter;
            }

            public void setHunter(int hunter) {
                this.hunter = hunter;
            }

            public int getSpeedrunner() {
                return speedrunner;
            }

            public void setSpeedrunner(int speedrunner) {
                this.speedrunner = speedrunner;
            }
        }

        /** Shortfall broadcasts. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class BroadcastRequirements extends OkaeriConfig {
            private boolean enabled = false;

            @CustomKey("interval-seconds")
            @Comment({
                    "Seconds between broadcasts.",
                    "Default: 60"
            })
            private int intervalSeconds = 60;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getIntervalSeconds() {
                return intervalSeconds;
            }

            public void setIntervalSeconds(int intervalSeconds) {
                this.intervalSeconds = intervalSeconds;
            }
        }
    }

    /** Pre-start wait for the first speedrunner hit. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDelaySeconds() {
            return delaySeconds;
        }

        public void setDelaySeconds(int delaySeconds) {
            this.delaySeconds = delaySeconds;
        }

        public OnExpire getOnExpire() {
            return onExpire;
        }

        public void setOnExpire(OnExpire onExpire) {
            this.onExpire = onExpire;
        }

        public boolean isStartInAdventureMode() {
            return startInAdventureMode;
        }

        public void setStartInAdventureMode(boolean startInAdventureMode) {
            this.startInAdventureMode = startInAdventureMode;
        }
    }

    /** Per-side head starts. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Headstarts extends OkaeriConfig {

        @Comment({
                "Gives speedrunners a head start at match start by holding hunters in",
                "spectator mode.",
                "Default: false"
        })
        private Headstart speedrunner = new Headstart();

        @Comment({
                "Same, but for hunters.",
                "Default: false"
        })
        private Headstart hunter = new Headstart();

        public Headstart getSpeedrunner() {
            return speedrunner;
        }

        public void setSpeedrunner(Headstart speedrunner) {
            this.speedrunner = speedrunner;
        }

        public Headstart getHunter() {
            return hunter;
        }

        public void setHunter(Headstart hunter) {
            this.hunter = hunter;
        }

        /** One side's head start. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Headstart extends OkaeriConfig {
            private boolean enabled = false;

            @CustomKey("delay-seconds")
            @Comment({
                    "Delay in seconds. Values <= 0 mean no delay.",
                    "Default: 30"
            })
            private int delaySeconds = 30;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getDelaySeconds() {
                return delaySeconds;
            }

            public void setDelaySeconds(int delaySeconds) {
                this.delaySeconds = delaySeconds;
            }
        }
    }

    /** Match leave destination. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class GameLeave extends OkaeriConfig {
        private LeaveDestination destination = LeaveDestination.SPECTATOR;

        public LeaveDestination getDestination() {
            return destination;
        }

        public void setDestination(LeaveDestination destination) {
            this.destination = destination;
        }
    }

    /** World generation and loot boosts. */
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

        public Toggle getNetherStructures() {
            return netherStructures;
        }

        public void setNetherStructures(Toggle netherStructures) {
            this.netherStructures = netherStructures;
        }

        public Toggle getOverworldStructures() {
            return overworldStructures;
        }

        public void setOverworldStructures(Toggle overworldStructures) {
            this.overworldStructures = overworldStructures;
        }

        public boolean isCustomPiglinBarter() {
            return customPiglinBarter;
        }

        public void setCustomPiglinBarter(boolean customPiglinBarter) {
            this.customPiglinBarter = customPiglinBarter;
        }
    }
}