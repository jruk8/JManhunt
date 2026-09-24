package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import lombok.Getter;

/**
 * Typed root of sounds.yml. Mirrors the former {@code sounds} block of
 * config.yml one-to-one. Sounds are file-only: they are not editable
 * through the in-game config command.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "JManhunt sounds.",
        "",
        "Sound names are namespaced Minecraft keys.",
        "eg. block.note_block.pling",
        "It's recommended to use a sound explorer like",
        "https://mudkipdev.github.io/minecraft-sound-explorer/"
})
public class SoundsConfig extends OkaeriConfig {

    @CustomKey("sounds-version")
    @Comment("Used for sounds updates, don't change unless you know what you're doing.")
    private int soundsVersion = 2;

    private Game game = new Game();
    private Announce announce = new Announce();
    private Compass compass = new Compass();
    private Ui ui = new Ui();

    public int getSoundsVersion() {
        return soundsVersion;
    }

    public void setSoundsVersion(int soundsVersion) {
        this.soundsVersion = soundsVersion;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Announce getAnnounce() {
        return announce;
    }

    public void setAnnounce(Announce announce) {
        this.announce = announce;
    }

    public Compass getCompass() {
        return compass;
    }

    public void setCompass(Compass compass) {
        this.compass = compass;
    }

    public Ui getUi() {
        return ui;
    }

    public void setUi(Ui ui) {
        this.ui = ui;
    }

    /** One sound: toggle, namespaced key, pitch, and volume. */
    @Getter
    @SuppressWarnings("FieldMayBeFinal")
    public static class SoundEntry extends OkaeriConfig {
        private boolean enabled = true;
        private String sound = "minecraft:entity.experience_orb.pickup";
        private double pitch = 1.0;
        private double volume = 1.0;

        public static SoundEntry of(String sound, double pitch, double volume) {
            SoundEntry entry = new SoundEntry();
            entry.setSound(sound);
            entry.setPitch(pitch);
            entry.setVolume(volume);
            return entry;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setSound(String sound) {
            this.sound = sound;
        }

        public void setPitch(double pitch) {
            this.pitch = pitch;
        }

        public void setVolume(double volume) {
            this.volume = volume;
        }
    }

    /** Match sounds. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Game extends OkaeriConfig {

        @CustomKey("autostart-countdown")
        @Comment("Runs on autostart countdown and hunter spawn countdown")
        private SoundEntry autostartCountdown =
                SoundEntry.of("block.stone_button.click_on", 2.0, 1.0);

        @CustomKey("speedrunner-death")
        @Comment("Runs on speedrunner killed (by any means)")
        private SoundEntry speedrunnerDeath =
                SoundEntry.of("block.trial_spawner.detect_player", 1.2, 1.0);

        @CustomKey("hunter-death")
        @Comment("Runs on hunter killed (by any means)")
        private SoundEntry hunterDeath =
                SoundEntry.of("entity.player.hurt_sweet_berry_bush", 0.9, 1.0);

        @CustomKey("win-sound")
        @Comment("Runs when speedrunners win")
        private SoundEntry winSound = SoundEntry.of("entity.ender_dragon.death", 1.0, 1.0);

        @CustomKey("fail-sound")
        @Comment("Runs when hunters win")
        private SoundEntry failSound = SoundEntry.of("entity.wither.spawn", 1.0, 1.0);

        @CustomKey("cancelled-sound")
        @Comment("Runs when a match is canceled")
        private SoundEntry cancelledSound =
                SoundEntry.of("block.bubble_column.whirlpool_ambient", 0.9, 1.0);

        public SoundEntry getAutostartCountdown() {
            return autostartCountdown;
        }

        public void setAutostartCountdown(SoundEntry autostartCountdown) {
            this.autostartCountdown = autostartCountdown;
        }

        public SoundEntry getSpeedrunnerDeath() {
            return speedrunnerDeath;
        }

        public void setSpeedrunnerDeath(SoundEntry speedrunnerDeath) {
            this.speedrunnerDeath = speedrunnerDeath;
        }

        public SoundEntry getHunterDeath() {
            return hunterDeath;
        }

        public void setHunterDeath(SoundEntry hunterDeath) {
            this.hunterDeath = hunterDeath;
        }

        public SoundEntry getWinSound() {
            return winSound;
        }

        public void setWinSound(SoundEntry winSound) {
            this.winSound = winSound;
        }

        public SoundEntry getFailSound() {
            return failSound;
        }

        public void setFailSound(SoundEntry failSound) {
            this.failSound = failSound;
        }

        public SoundEntry getCancelledSound() {
            return cancelledSound;
        }

        public void setCancelledSound(SoundEntry cancelledSound) {
            this.cancelledSound = cancelledSound;
        }
    }

    /**
     * Per-role sounds for the start-of-match role announcement. Each participant
     * hears their own role's sound.
     */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Announce extends OkaeriConfig {

        @Comment("Heard by each hunter when roles are announced.")
        private SoundEntry hunter = SoundEntry.of("entity.wither.break_block", 1.3, 0.8);

        @Comment("Heard by each speedrunner when roles are announced.")
        private SoundEntry speedrunner = SoundEntry.of("block.copper_chest.close", 1.0, 1.0);

        @Comment("Heard by each spectator when roles are announced.")
        private SoundEntry spectator = SoundEntry.of("block.note_block.chime", 1.0, 0.8);

        public SoundEntry getHunter() {
            return hunter;
        }

        public void setHunter(SoundEntry hunter) {
            this.hunter = hunter;
        }

        public SoundEntry getSpeedrunner() {
            return speedrunner;
        }

        public void setSpeedrunner(SoundEntry speedrunner) {
            this.speedrunner = speedrunner;
        }

        public SoundEntry getSpectator() {
            return spectator;
        }

        public void setSpectator(SoundEntry spectator) {
            this.spectator = spectator;
        }
    }

    /** Compass interaction sounds. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Compass extends OkaeriConfig {

        @CustomKey("left-click")
        @Comment({
                "Heard by the holder when left-click cycling the compass target",
                "lock. Only plays when the scroll changes the lock."
        })
        private SoundEntry leftClick = SoundEntry.of("ui.button.click", 1.0, 1.0);

        @CustomKey("right-click")
        @Comment({
                "Heard by the holder on an accepted right-click compass refresh,",
                "and again when a click-initiated analysis resolves."
        })
        private SoundEntry rightClick = SoundEntry.of("entity.experience_orb.pickup", 1.0, 1.0);

        @CustomKey("analysis")
        @Comment("Ticked while a compass analysis runs, on the analyze sound interval.")
        private SoundEntry analysis = SoundEntry.of("block.note_block.hat", 1.0, 1.0);

        public SoundEntry getLeftClick() {
            return leftClick;
        }

        public void setLeftClick(SoundEntry leftClick) {
            this.leftClick = leftClick;
        }

        public SoundEntry getRightClick() {
            return rightClick;
        }

        public void setRightClick(SoundEntry rightClick) {
            this.rightClick = rightClick;
        }

        public SoundEntry getAnalysis() {
            return analysis;
        }

        public void setAnalysis(SoundEntry analysis) {
            this.analysis = analysis;
        }
    }

    /** Shared interface feedback for commands, GUIs, and the tutorial. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Ui extends OkaeriConfig {

        @CustomKey("neutral-sound")
        @Comment("Heard on command and GUI confirmations.")
        private SoundEntry neutralSound =
                SoundEntry.of("block.note_block.pling", 1.0, 1.0);

        @CustomKey("angry-sound")
        @Comment("Heard on validation errors and invalid input.")
        private SoundEntry angrySound =
                SoundEntry.of("block.bamboo_wood.place", 1.0, 1.0);

        public SoundEntry getNeutralSound() {
            return neutralSound;
        }

        public void setNeutralSound(SoundEntry neutralSound) {
            this.neutralSound = neutralSound;
        }

        public SoundEntry getAngrySound() {
            return angrySound;
        }

        public void setAngrySound(SoundEntry angrySound) {
            this.angrySound = angrySound;
        }
    }
}
