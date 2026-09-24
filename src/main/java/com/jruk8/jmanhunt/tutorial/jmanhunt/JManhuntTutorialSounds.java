package com.jruk8.jmanhunt.tutorial.jmanhunt;

import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.tutorial.config.TutorialConfig;
import com.jruk8.jmanhunt.tutorial.ports.TutorialSoundPlayer;
import org.bukkit.entity.Player;

/** Plays tutorial sounds with the ids and levels from tutorial.yml. */
public final class JManhuntTutorialSounds implements TutorialSoundPlayer {

    private final TutorialConfig config;
    private final SoundService sounds;

    public JManhuntTutorialSounds(TutorialConfig config, SoundService sounds) {
        this.config = config;
        this.sounds = sounds;
    }

    @Override
    public void playNeutral(Player player) {
        sounds.playNeutralSound(player);
    }

    @Override
    public void playAngry(Player player) {
        sounds.playAngrySound(player);
    }

    @Override
    public void playCongratulations(Player player) {
        play(player, config.getSounds().getCongratulations());
    }

    private void play(Player player, TutorialConfig.TutorialSound sound) {
        if (sound == null || !sound.isEnabled()) {
            return;
        }
        sounds.playCustomSound(player, sound.getId(), sound.getPitch(), sound.getVolume());
    }
}
