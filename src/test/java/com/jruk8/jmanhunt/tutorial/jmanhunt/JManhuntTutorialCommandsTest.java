package com.jruk8.jmanhunt.tutorial.jmanhunt;

import com.jruk8.jmanhunt.message.SoundService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JManhuntTutorialCommandsTest {

    @Mock
    private SoundService sounds;

    @Mock
    private Player player;

    @Test
    void runAsPlayerSuppressesNestedNeutral() {
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        new JManhuntTutorialCommands(sounds).runAsPlayer(player, "say hi");

        InOrder order = inOrder(sounds, player);
        order.verify(sounds).suppressNeutral(playerId);
        order.verify(player).performCommand("say hi");
        order.verify(sounds).releaseNeutral(playerId);
    }

    @Test
    void blankCommandDispatchesNothing() {
        new JManhuntTutorialCommands(sounds).runAsPlayer(player, "  ");

        verifyNoInteractions(sounds, player);
    }

    @Test
    void leadingSlashIsStripped() {
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        new JManhuntTutorialCommands(sounds).runAsPlayer(player, "/say hi");

        InOrder order = inOrder(sounds, player);
        order.verify(sounds).suppressNeutral(playerId);
        order.verify(player).performCommand("say hi");
        order.verify(sounds).releaseNeutral(playerId);
    }
}
