package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.SoundsConfig;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/*
 * Orchestrates sound playing.
 */
public class SoundService {
    private static final String FALLBACK_SOUND = "minecraft:entity.experience_orb.pickup";
    private static final String NEUTRAL_SOUND_KEY = "ui.neutral-sound";
    private static final String ANGRY_SOUND_KEY = "ui.angry-sound";
    private final JManhuntPlugin plugin;
    private final SoundsConfig sounds;
    private final Set<UUID> neutralSuppressed = new HashSet<>();

    /**
     * @param sounds live sounds store, held by reference across reloads
     *        (null only in unit tests that never play stored sounds)
     */
    public SoundService(JManhuntPlugin plugin, SoundsConfig sounds) {
        this.plugin = plugin;
        this.sounds = sounds;
    }

    public void playGlobalSound(String configKey) {
        Bukkit.getOnlinePlayers().forEach(player -> playSound(player, configKey));
    }

    public void playSound(Player player, String configKey) {
        SoundSettings settings = getSoundSettings(configKey);
        try {
            if (!settings.enabled() || settings.sound() == null) {
                return;
            }
            player.playSound(player.getLocation(), settings.sound(), settings.volume(), settings.pitch());
        } catch (IllegalArgumentException exception) {
            plugin.logger().warning(
                    "Could not play configured sound '" + settings.configKey() + "': " + exception.getMessage());
        }
    }

    public void playNeutralSound() {
        playGlobalSound(NEUTRAL_SOUND_KEY);
    }

    public void playNeutralSound(Player player) {
        if (neutralSuppressed.contains(player.getUniqueId())) {
            return;
        }
        playSound(player, NEUTRAL_SOUND_KEY);
    }

    /** Shared angry blip for validation errors and invalid input. */
    public void playAngrySound(Player player) {
        playSound(player, ANGRY_SOUND_KEY);
    }

    /**
     * Silences playNeutralSound for one player until released. The tutorial
     * wraps its nested commands in this so one click plays one sound.
     * Main thread only, like every other Bukkit sound call.
     */
    public void suppressNeutral(UUID playerId) {
        neutralSuppressed.add(playerId);
    }

    public void releaseNeutral(UUID playerId) {
        neutralSuppressed.remove(playerId);
    }

    /** Plays a sound with explicit parameters, e.g. from lucky-blocks.yml feedback. */
    public void playCustomSound(Player player, String sound, float pitch, float volume) {
        try {
            NamespacedKey soundKey = NamespacedKey.fromString(sound.toLowerCase(Locale.ROOT));
            if (soundKey == null) {
                soundKey = NamespacedKey.minecraft(sound.toLowerCase(Locale.ROOT));
            }
            if (Registry.SOUNDS.get(soundKey) == null) {
                plugin.logger().warning("Sound '" + sound + "' is invalid. Using default sound.");
                soundKey = NamespacedKey.fromString(FALLBACK_SOUND);
            }
            player.playSound(player.getLocation(), soundKey.asString(), volume, pitch);
        } catch (IllegalArgumentException exception) {
            plugin.logger().warning("Could not play sound '" + sound + "': " + exception.getMessage());
        }
    }

    private SoundSettings getSoundSettings(String configKey) {
        Object node = sounds == null ? null : ConfigPathMapper.get(sounds, configKey);
        if (!(node instanceof SoundsConfig.SoundEntry entry)) {
            return new SoundSettings(configKey, false, FALLBACK_SOUND, 1.0f, 1.0f);
        }
        boolean isEnabled = entry.isEnabled();

        String soundInput = entry.getSound();
        NamespacedKey soundKey = NamespacedKey.fromString(soundInput.toLowerCase(Locale.ROOT));
        if (soundKey == null) {
            soundKey = NamespacedKey.minecraft(soundInput.toLowerCase(Locale.ROOT));
        }
        String sound = (Registry.SOUNDS.get(soundKey) == null) ? null : soundKey.asString();
        if (sound == null) {
            plugin.logger().warning(
                    "Sound '" + soundInput + "' for config key '" + configKey + "' is invalid."
                            + " Using default sound.");
            sound = FALLBACK_SOUND;
        }

        float pitch = (float) entry.getPitch();
        float volume = (float) entry.getVolume();
        pitch = Math.min(pitch, 2.0f);
        volume = Math.min(volume, 1.0f);

        return new SoundSettings(configKey, isEnabled, sound, pitch, volume);
    }
}
