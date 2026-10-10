package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.TagLoops;
import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Role sink filtering: ALL covers both teams, one side otherwise. */
class ModifierTagSinksTest {

    @Test
    void roleMatchesCoversBothTeamsForAll() {
        assertTrue(ModifierTagSinks.roleMatches("ALL", Role.HUNTER));
        assertTrue(ModifierTagSinks.roleMatches("ALL", Role.SPEEDRUNNER));
        assertFalse(ModifierTagSinks.roleMatches("ALL", Role.SPECTATOR));
        assertFalse(ModifierTagSinks.roleMatches("ALL", Role.NONE));
    }

    @Test
    void roleMatchesSingleSideOtherwise() {
        assertTrue(ModifierTagSinks.roleMatches("HUNTER", Role.HUNTER));
        assertFalse(ModifierTagSinks.roleMatches("HUNTER", Role.SPEEDRUNNER));
        assertTrue(ModifierTagSinks.roleMatches("SPEEDRUNNER", Role.SPEEDRUNNER));
        assertFalse(ModifierTagSinks.roleMatches("SPEEDRUNNER", Role.HUNTER));
    }

    @Test
    void loopLimitExceededLogsConfiguredLimit() {
        JManhuntLogger log = mock(JManhuntLogger.class);
        GameManager game = mock(GameManager.class);
        when(game.instance(7L)).thenReturn(Optional.empty());
        ModifierTagSinks sinks = new ModifierTagSinks(log,
                new ModifierTagSinks.SinkBus(mock(MessageService.class),
                        mock(ModifiersMessages.class), mock(SoundService.class)),
                game, new PlayerStateStore(), new MiscConfig.Interop());

        sinks.loopLimitExceeded("detail", 7L);

        ArgumentCaptor<String> logged = ArgumentCaptor.forClass(String.class);
        verify(log).severe(logged.capture());
        assertTrue(logged.getValue().contains(String.valueOf(TagLoops.LOOP_LIMIT)));
        assertFalse(logged.getValue().contains("1000 steps"));
    }
}
