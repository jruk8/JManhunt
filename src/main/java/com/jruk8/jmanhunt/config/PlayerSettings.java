package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Player settings. */
@SuppressWarnings("FieldMayBeFinal")
public class PlayerSettings extends OkaeriConfig {

    @Comment("How player roles are assigned and reset around the lifecycle of a match.")
    private Roles roles = new Roles();

    @Comment("Delayed respawn and lives, configured independently per role.")
    private Respawn respawn = new Respawn();

    @CustomKey("friendly-fire")
    @Comment({
            "Friendly fire rules for participants. When disabled for a role, players of",
            "that role cannot damage players of the same role once the game has begun.",
            "Friendly fire is never active during the pre-start window.",
            "Default: true for both"
    })
    private FriendlyFire friendlyFire = new FriendlyFire();

    @Comment("Invulnerability settings.")
    private Invulnerability invulnerability = new Invulnerability();

    @Comment("Spectator toolbar and lock-on follow.")
    private Spectator spectator = new Spectator();

    @CustomKey("announce-roles")
    @Comment({
            "Announces each participant's role when a match starts, in chat and/or as a",
            "title. Both are toggled independently; when both are off, no announcement",
            "(including sounds) plays.",
            "Default: both enabled"
    })
    private AnnounceRoles announceRoles = new AnnounceRoles();

    public Roles getRoles() {
        return roles;
    }

    public void setRoles(Roles roles) {
        this.roles = roles;
    }

    public Respawn getRespawn() {
        return respawn;
    }

    public void setRespawn(Respawn respawn) {
        this.respawn = respawn;
    }

    public FriendlyFire getFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(FriendlyFire friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public Invulnerability getInvulnerability() {
        return invulnerability;
    }

    public void setInvulnerability(Invulnerability invulnerability) {
        this.invulnerability = invulnerability;
    }

    public Spectator getSpectator() {
        return spectator;
    }

    public void setSpectator(Spectator spectator) {
        this.spectator = spectator;
    }

    public AnnounceRoles getAnnounceRoles() {
        return announceRoles;
    }

    public void setAnnounceRoles(AnnounceRoles announceRoles) {
        this.announceRoles = announceRoles;
    }

    /** Role assignment and reset. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Roles extends OkaeriConfig {

        @CustomKey("reset-on-game-end")
        @Comment({
                "When true, all queued hunters/speedrunners are reset to NONE when a match",
                "finishes.",
                "Default: true"
        })
        private Toggle resetOnGameEnd = new Toggle(true);

        @CustomKey("reset-on-leave")
        @Comment({
                "When true, players are reset to NONE when they leave the server.",
                "(only when no game is running, otherwise they are reset to NONE on game end)",
                "Default: true"
        })
        private Toggle resetOnLeave = new Toggle(true);

        @CustomKey("turn-nones-spectator")
        @Comment({
                "Turn NONE-role players into spectators (gamemode plus match",
                "spectating) wherever the plugin would otherwise leave them alone.",
                "AFK players are always left alone.",
                "true: NONEs are put in spectator",
                "false: NONEs keep their gamemode and stay put, like AFK",
                "Default: false"
        })
        private Toggle turnNonesSpectator = new Toggle(false);

        public Toggle getResetOnGameEnd() {
            return resetOnGameEnd;
        }

        public void setResetOnGameEnd(Toggle resetOnGameEnd) {
            this.resetOnGameEnd = resetOnGameEnd;
        }

        public Toggle getResetOnLeave() {
            return resetOnLeave;
        }

        public void setResetOnLeave(Toggle resetOnLeave) {
            this.resetOnLeave = resetOnLeave;
        }

        public Toggle getTurnNonesSpectator() {
            return turnNonesSpectator;
        }

        public void setTurnNonesSpectator(Toggle turnNonesSpectator) {
            this.turnNonesSpectator = turnNonesSpectator;
        }
    }

    /** Per-role respawn delay and lives. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Respawn extends OkaeriConfig {

        @Comment("Delayed hunter respawn and hunter lives.")
        private HunterRespawn hunter = new HunterRespawn();

        @Comment("Delayed speedrunner respawn and speedrunner lives.")
        private SpeedrunnerRespawn speedrunner = new SpeedrunnerRespawn();

        public HunterRespawn getHunter() {
            return hunter;
        }

        public void setHunter(HunterRespawn hunter) {
            this.hunter = hunter;
        }

        public SpeedrunnerRespawn getSpeedrunner() {
            return speedrunner;
        }

        public void setSpeedrunner(SpeedrunnerRespawn speedrunner) {
            this.speedrunner = speedrunner;
        }

        /** Hunter respawn delay and lives. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class HunterRespawn extends OkaeriConfig {

            @Comment({
                    "When true, deceased hunters wait before respawning instead of",
                    "respawning immediately.",
                    "Default: true"
            })
            private boolean enabled = true;

            @CustomKey("delay-seconds")
            @Comment({
                    "Seconds a hunter must wait before respawning after death.",
                    "Set to -1 for no delay (respawn immediately).",
                    "Default: 15"
            })
            private int delaySeconds = 15;

            @Comment({
                    "Hunter lives. A hunter is eliminated when their lives run out.",
                    "Set to -1 for unlimited lives.",
                    "Default: -1"
            })
            private int lives = -1;

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

            public int getLives() {
                return lives;
            }

            public void setLives(int lives) {
                this.lives = lives;
            }
        }

        /** Speedrunner respawn delay and lives. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class SpeedrunnerRespawn extends OkaeriConfig {

            @Comment({
                    "When true, deceased speedrunners wait before respawning instead of",
                    "respawning immediately.",
                    "Default: false"
            })
            private boolean enabled = false;

            @CustomKey("delay-seconds")
            @Comment({
                    "Seconds a speedrunner must wait before respawning after death.",
                    "Set to -1 for no delay (respawn immediately).",
                    "Default: 60"
            })
            private int delaySeconds = 60;

            @Comment({
                    "Speedrunner lives. A speedrunner is eliminated when their lives run",
                    "out.",
                    "Set to -1 for unlimited lives.",
                    "Default: 1"
            })
            private int lives = 1;

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

            public int getLives() {
                return lives;
            }

            public void setLives(int lives) {
                this.lives = lives;
            }
        }
    }

    /** Friendly fire per role. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class FriendlyFire extends OkaeriConfig {
        private boolean speedrunner = true;
        private boolean hunter = true;

        @CustomKey("broadcast-kills")
        @Comment({
                "When true, same-team kills broadcast a mocking line to the",
                "origin lobby, picked at random from three variants.",
                "Default: true"
        })
        private boolean broadcastKills = true;

        public boolean isSpeedrunner() {
            return speedrunner;
        }

        public void setSpeedrunner(boolean speedrunner) {
            this.speedrunner = speedrunner;
        }

        public boolean isHunter() {
            return hunter;
        }

        public void setHunter(boolean hunter) {
            this.hunter = hunter;
        }

        public boolean isBroadcastKills() {
            return broadcastKills;
        }

        public void setBroadcastKills(boolean broadcastKills) {
            this.broadcastKills = broadcastKills;
        }
    }

    /** Invulnerability rules. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Invulnerability extends OkaeriConfig {

        @CustomKey("on-game-end")
        @Comment({
                "Makes all online players invulnerable when the game ends.",
                "Invulnerability is removed when the next match starts.",
                "Default: true"
        })
        private Toggle onGameEnd = new Toggle(true);

        @CustomKey("none-players")
        @Comment({
                "Makes players with role NONE (spectators) invulnerable at all times.",
                "Default: true"
        })
        private Toggle nonePlayers = new Toggle(true);

        public Toggle getOnGameEnd() {
            return onGameEnd;
        }

        public void setOnGameEnd(Toggle onGameEnd) {
            this.onGameEnd = onGameEnd;
        }

        public Toggle getNonePlayers() {
            return nonePlayers;
        }

        public void setNonePlayers(Toggle nonePlayers) {
            this.nonePlayers = nonePlayers;
        }
    }

    /** Spectator toolbar and lock-on follow. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Spectator extends OkaeriConfig {

        @Comment("Hotbar toolbar handed to spectators: layout plus lock-on follow.")
        private Toolbar toolbar = new Toolbar();

        @Comment("Travel limit keeping spectators near the action.")
        private Travel travel = new Travel();

        public Toolbar getToolbar() {
            return toolbar;
        }

        public void setToolbar(Toolbar toolbar) {
            this.toolbar = toolbar;
        }

        public Travel getTravel() {
            return travel;
        }

        public void setTravel(Travel travel) {
            this.travel = travel;
        }

        /** Spectator travel limit. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Travel extends OkaeriConfig {

            @Comment({
                    "Pull spectators back when they roam too far from players",
                    "or last-seen spots. Prevents redundant chunk generation.",
                    "Default: true"
            })
            private boolean enabled = true;

            @CustomKey("max-distance")
            @Comment({
                    "Blocks a spectator may roam from the nearest player or",
                    "last-seen spot before being teleported back to it.",
                    "Default: 125.0"
            })
            private double maxDistance = 125.0;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getMaxDistance() {
                return maxDistance;
            }

            public void setMaxDistance(double maxDistance) {
                this.maxDistance = maxDistance;
            }
        }

        /** Spectator hotbar toolbar. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Toolbar extends OkaeriConfig {

            @Comment({
                    "Hotbar layout, one character per slot 0 to 8. c is the",
                    "match browser, p the player teleporter, b the back-to-lobby",
                    "exit, # an empty slot; anything else is empty too.",
                    "Must be exactly 9 characters.",
                    "Default: \"cp######b\""
            })
            private String layout = "cp######b";

            @CustomKey("lock-on")
            @Comment({
                    "Lock onto teleported-to players: the spectator is pulled",
                    "back within tp-distance every 5 ticks until they move.",
                    "Default: true"
            })
            private boolean lockOn = true;

            @CustomKey("tp-distance")
            @Comment({
                    "Locked-follow teleport distance in blocks. Must be at",
                    "least 1.",
                    "Default: 25"
            })
            private int tpDistance = 25;

            public String getLayout() {
                return layout;
            }

            public void setLayout(String layout) {
                this.layout = layout;
            }

            public boolean isLockOn() {
                return lockOn;
            }

            public void setLockOn(boolean lockOn) {
                this.lockOn = lockOn;
            }

            public int getTpDistance() {
                return tpDistance;
            }

            public void setTpDistance(int tpDistance) {
                this.tpDistance = tpDistance;
            }
        }
    }

    /** Start-of-match role announcements. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class AnnounceRoles extends OkaeriConfig {

        private Toggle chat = new Toggle(true);
        private Title title = new Title();

        public Toggle getChat() {
            return chat;
        }

        public void setChat(Toggle chat) {
            this.chat = chat;
        }

        public Title getTitle() {
            return title;
        }

        public void setTitle(Title title) {
            this.title = title;
        }

        /** Title announcement and timing. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Title extends OkaeriConfig {
            private boolean enabled = true;

            @CustomKey("fade-in-seconds")
            @Comment("Title timing in seconds.")
            private double fadeInSeconds = 0.5;

            @CustomKey("stay-seconds")
            private double staySeconds = 3.0;

            @CustomKey("fade-out-seconds")
            private double fadeOutSeconds = 0.5;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getFadeInSeconds() {
                return fadeInSeconds;
            }

            public void setFadeInSeconds(double fadeInSeconds) {
                this.fadeInSeconds = fadeInSeconds;
            }

            public double getStaySeconds() {
                return staySeconds;
            }

            public void setStaySeconds(double staySeconds) {
                this.staySeconds = staySeconds;
            }

            public double getFadeOutSeconds() {
                return fadeOutSeconds;
            }

            public void setFadeOutSeconds(double fadeOutSeconds) {
                this.fadeOutSeconds = fadeOutSeconds;
            }
        }
    }
}