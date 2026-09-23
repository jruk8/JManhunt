package com.jruk8.jmanhunt.message;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        verify(player, never()).playSound(
                any(Location.class), anyString(), anyFloat(), anyFloat());
    }
}
