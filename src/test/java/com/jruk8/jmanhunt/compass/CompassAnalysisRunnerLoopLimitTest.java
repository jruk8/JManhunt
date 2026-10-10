package com.jruk8.jmanhunt.compass;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.command.TagLoops;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.message.CompassMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Debuff loop-limit sink logs the configured step limit. */
class CompassAnalysisRunnerLoopLimitTest {

    @Test
    void loopLimitExceededLogsConfiguredLimit() {
        JManhuntLogger log = mock(JManhuntLogger.class);
        CompassAnalysisRunner runner = new CompassAnalysisRunner(
                mock(CompassSettingsFacade.class),
                new CompassAnalysisRunner.RunnerFeedback(mock(MessageService.class),
                        mock(CompassMessages.class), mock(ModifiersMessages.class),
                        mock(SoundService.class), log),
                null, null, null);

        runner.loopLimitExceeded("detail", TagContext.NO_MATCH);

        ArgumentCaptor<String> logged = ArgumentCaptor.forClass(String.class);
        verify(log).severe(logged.capture());
        assertTrue(logged.getValue().contains(String.valueOf(TagLoops.LOOP_LIMIT)));
        assertFalse(logged.getValue().contains("1000 steps"));
    }
}
