package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
public class SoundsConfig extends OkaeriConfig {

    @CustomKey("sounds-version")
    @Comment("Used for sounds updates, don't change unless you know what you're doing.")
    private int soundsVersion = 4;

    private Game game = new Game();
    @Comment({
            "Per-role sounds for the start-of-match role announcement. Each participant",
            "hears their own role's sound."
    })
    private Announce announce = new Announce();
    @Comment("Compass interaction sounds.")
    private Compass compass = new Compass();
    private Ui ui = new Ui();
    @Comment("Chat sounds.")
    private Chat chat = new Chat();

    /**
     * Sound entry for one dotted key (for example
     * {@code game.speedrunner-death}), or null when the key resolves to
     * nothing. Sounds stay key addressed: entries are file-only and the
     * call sites pass dynamic keys.
     */
    public SoundEntry entry(String key) {
        Object node = ConfigPathMapper.get(this, key);
        return node instanceof SoundEntry entry ? entry : null;
    }

    /** One sound: toggle, namespaced key, pitch, and volume. */
    @Getter
    @Setter
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

    }

    /** Chat sounds. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Chat extends OkaeriConfig {

        @CustomKey("team-chat")
        @Comment("Heard by team chat recipients on each message.")
        private SoundEntry teamChat = SoundEntry.of("block.calcite.place", 1.3, 0.8);

    }

    /** Match sounds. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Game extends OkaeriConfig {

        @CustomKey("autostart-countdown")
        @Comment("Runs on autostart countdown and hunter spawn countdown")
        private SoundEntry autostartCountdown =
                SoundEntry.of("block.stone_button.click_on", 2.0, 1.0);

        @CustomKey("autostart-cancelled")
        @Comment("Runs when an autostart countdown is cancelled")
        private SoundEntry autostartCancelled =
                SoundEntry.of("block.lever.click", 1.0, 1.0);

        @CustomKey("speedrunner-death")
        @Comment("Runs on speedrunner killed (by any means)")
        private SoundEntry speedrunnerDeath =
                SoundEntry.of("block.dried_ghast.ambient", 1.0, 1.0);

        @CustomKey("hunter-death")
        @Comment("Runs on hunter killed (by any means)")
        private SoundEntry hunterDeath =
                SoundEntry.of("item.spyglass.use", 1.0, 1.0);

        @CustomKey("win-sound")
        @Comment("Runs when speedrunners win")
        private SoundEntry winSound = SoundEntry.of("block.beacon.activate", 1.1, 1.0);

        @CustomKey("fail-sound")
        @Comment("Runs when hunters win")
        private SoundEntry failSound = SoundEntry.of("block.sculk_shrieker.break", 0.7, 1.0);

        @CustomKey("cancelled-sound")
        @Comment("Runs when a match is canceled")
        private SoundEntry cancelledSound =
                SoundEntry.of("block.bubble_column.whirlpool_ambient", 0.9, 1.0);

        @CustomKey("match-started")
        @Comment("Runs when a match begins, after any prestart window")
        private SoundEntry matchStarted =
                SoundEntry.of("minecraft:entity.illusioner.ambient", 0.9, 1.0);

    }

    /**
     * Per-role sounds for the start-of-match role announcement. Each participant
     * hears their own role's sound.
     */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Announce extends OkaeriConfig {

        @Comment("Heard by each hunter when roles are announced.")
        private SoundEntry hunter = SoundEntry.of("entity.wither.break_block", 1.3, 0.8);

        @Comment("Heard by each speedrunner when roles are announced.")
        private SoundEntry speedrunner = SoundEntry.of("block.copper_chest.close", 1.0, 1.0);

        @Comment("Heard by each spectator when roles are announced.")
        private SoundEntry spectator = SoundEntry.of("block.note_block.chime", 1.0, 0.8);

    }

    /** Compass interaction sounds. */
    @Getter
    @Setter
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

        @CustomKey("failure")
        @Comment("Heard by the holder when a compass click fails to track a target.")
        private SoundEntry failure =
                SoundEntry.of("block.cherry_wood_hanging_sign.place", 0.8, 1.0);

        @CustomKey("cost-too-high")
        @Comment("Heard by the holder when an analysis cost is too high to pay.")
        private SoundEntry costTooHigh =
                SoundEntry.of("block.cherry_wood_hanging_sign.place", 0.8, 1.0);

        @CustomKey("cost-used-exp")
        @Comment("Heard by the holder when an analysis charges experience levels.")
        private SoundEntry costUsedExp =
                SoundEntry.of("entity.experience_bottle.throw", 1.0, 1.0);

        @CustomKey("cost-used-health")
        @Comment("Heard by the holder when an analysis charges health.")
        private SoundEntry costUsedHealth =
                SoundEntry.of("entity.camel.eat", 1.2, 0.8);

        @CustomKey("cost-used-saturation")
        @Comment("Heard by the holder when an analysis charges the hunger pool.")
        private SoundEntry costUsedSaturation =
                SoundEntry.of("entity.splash_potion.break", 1.2, 1.0);

    }

    /** Shared interface feedback for commands, GUIs, and the tutorial. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Ui extends OkaeriConfig {

        @CustomKey("neutral-sound")
        @Comment("Heard on command and GUI confirmations.")
        private SoundEntry neutralSound =
                SoundEntry.of("block.spawner.fall", 1.0, 1.0);

        @CustomKey("angry-sound")
        @Comment("Heard on validation errors and invalid input.")
        private SoundEntry angrySound =
                SoundEntry.of("block.bamboo_wood.place", 1.0, 1.0);

        @CustomKey("destructive-sound")
        @Comment("Heard when a delete is confirmed, like removing a modifier or preset.")
        private SoundEntry destructiveSound =
                SoundEntry.of("block.cherry_wood_hanging_sign.step", 0.8, 1.0);

    }
}
