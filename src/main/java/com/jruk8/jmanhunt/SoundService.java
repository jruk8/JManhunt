package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.Locale;

/*
 * Orchestrates sound playing.
 */
public class SoundService {
    private static final String SOUND_PATH_PREFIX = "sounds.";
    private static final String DEFAULT_SOUND = "minecraft:entity.experience_orb.pickup";
    private static final String NEUTRAL_SOUND_KEY = "neutral-sound";
    private final JManhuntPlugin plugin;
    private final ConfigService config;
    public record SoundSettings(String configKey, boolean enabled, String sound, float pitch, float volume) {}

    public SoundService(JManhuntPlugin plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void playGlobalSound(String configKey) {
        Bukkit.getOnlinePlayers().forEach(player -> playSound(player, configKey));
    }

    public void playSound(Player player, String configKey) {
        SoundSettings settings = getSoundSettings(configKey);
        try {
            if (!settings.enabled() || settings.sound() == null) return;
            player.playSound(player.getLocation(), settings.sound, settings.volume, settings.pitch);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not play configured sound '" + settings.configKey + "': " + exception.getMessage());
        }
    }

    public void playNeutralSound() {
        playGlobalSound(NEUTRAL_SOUND_KEY);
    }

    public void playNeutralSound(Player player) {
        playSound(player, NEUTRAL_SOUND_KEY);
    }

    private SoundSettings getSoundSettings(String configKey) {
        String soundPath = getSoundPath(configKey);
        boolean isEnabled = config.getBoolSetting(soundPath + ".enabled", false);

        String soundInput = config.getStringSetting(soundPath + ".sound", DEFAULT_SOUND);
        NamespacedKey soundKey = NamespacedKey.fromString(soundInput.toLowerCase(Locale.ROOT));
        if (soundKey == null) soundKey = NamespacedKey.minecraft(soundInput.toLowerCase(Locale.ROOT));
        String sound = (Registry.SOUNDS.get(soundKey) == null) ? null : soundKey.asString();
        if (sound == null) {
            plugin.getLogger().warning("Sound '" + soundInput + "' for config key '" + configKey + "' is invalid. Using default sound.");
            sound = DEFAULT_SOUND;
        }

        float pitch = config.getFloatSetting(soundPath + ".pitch", 1.0f);
        float volume = config.getFloatSetting(soundPath + ".volume", 1.0f);
        pitch = Math.min(pitch, 2.0f);
        volume = Math.min(volume, 1.0f);

        return new SoundSettings(configKey, isEnabled, sound, pitch, volume);
    }

    private String getSoundPath(String soundName) {
        return SOUND_PATH_PREFIX + soundName;
    }
}