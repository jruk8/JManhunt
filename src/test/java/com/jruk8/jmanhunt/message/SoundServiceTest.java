package com.jruk8.jmanhunt.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.SoundsConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SoundServiceTest {

    @Mock
    private Player player;

    @Test
    void suppressedNeutralPlaysNothing() {
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        SoundService sounds = new SoundService(null, null);

        sounds.suppressNeutral(playerId);
        sounds.playNeutralSound(player);

        verify(player, never()).playSound(any(Sound.class), any(Sound.Emitter.class));
    }

    @Test
    void customSoundFollowsPlayerEntity() {
        SoundService sounds = new SoundService(mock(JManhuntLogger.class), null);

        sounds.playCustomSound(player, "minecraft:entity.experience_orb.pickup", 1.2f, 0.8f);

        ArgumentCaptor<Sound> sound = ArgumentCaptor.forClass(Sound.class);
        verify(player).playSound(sound.capture(), eq(Sound.Emitter.self()));
        assertEquals(Key.key("minecraft:entity.experience_orb.pickup"), sound.getValue().name());
        assertEquals(Sound.Source.MASTER, sound.getValue().source());
        assertEquals(0.8f, sound.getValue().volume());
        assertEquals(1.2f, sound.getValue().pitch());
    }

    @Test
    void configuredSoundFollowsPlayerEntity() {
        SoundService sounds = new SoundService(mock(JManhuntLogger.class), new SoundsConfig());

        sounds.playSound(player, "chat.team-chat");

        ArgumentCaptor<Sound> sound = ArgumentCaptor.forClass(Sound.class);
        verify(player).playSound(sound.capture(), eq(Sound.Emitter.self()));
        assertEquals(Sound.Source.MASTER, sound.getValue().source());
        assertEquals(0.8f, sound.getValue().volume());
        assertEquals(1.3f, sound.getValue().pitch());
    }
}
