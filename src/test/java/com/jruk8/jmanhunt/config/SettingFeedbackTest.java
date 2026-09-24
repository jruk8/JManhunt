package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Shared write feedback: success reports the update with sound, failures
 * report only the validation message, and restart settings add the nudge.
 * Announces stay off because the schema default disables them.
 */
class SettingFeedbackTest {

    private MessageService messages;
    private ConfigService config;
    private SoundService sounds;
    private SettingFeedback feedback;
    private Player sender;
    private List<Component> sent;

    @BeforeEach
    void setup() throws Exception {
        messages = new MessageService();
        messages.reload(new MessagesConfig());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        config = new ConfigService(new JManhuntConfig(),
                new ModifierStore(new ModifiersConfig(), log), () -> {});
        sounds = mock(SoundService.class);
        feedback = new SettingFeedback(messages, config, sounds);
        sent = new ArrayList<>();
        sender = mock(Player.class);
        org.mockito.Mockito.doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(sender).sendMessage(any(Component.class));
    }

    @Test
    void scalarUpdatedReportsOldAndNewWithSound() {
        ConfigService.SetOutcome outcome =
                config.setValue("settings.match.autostart.countdown-seconds", "60");

        assertTrue(outcome.ok());
        feedback.scalarUpdated(sender,
                "settings.match.autostart.countdown-seconds", outcome);

        assertEquals(1, sent.size());
        assertEquals("[JManhunt] Set settings.match.autostart.countdown-seconds to 60. (was 45)",
                plain(sent.get(0)));
        verify(sounds, times(1)).playNeutralSound(sender);
    }

    @Test
    void scalarUpdatedAddsRestartNudge() {
        ConfigService.SetOutcome outcome =
                config.setValue("statistics.pool-size", "8");

        assertTrue(outcome.ok());
        feedback.scalarUpdated(sender, "statistics.pool-size", outcome);

        assertEquals(2, sent.size());
        assertEquals("[JManhunt] Set statistics.pool-size to 8. (was 4)", plain(sent.get(0)));
        assertEquals("[JManhunt] This setting requires a server restart to take effect.",
                plain(sent.get(1)));
        verify(sounds, times(1)).playNeutralSound(sender);
    }

    @Test
    void listAddedAndRemovedReportValues() {
        ConfigService.SetOutcome added =
                config.listAdd("match.end-statistics", "EXTRA");

        assertTrue(added.ok());
        feedback.listAdded(sender, "match.end-statistics", added);

        assertEquals(1, sent.size());
        assertEquals("[JManhunt] Added to match.end-statistics: EXTRA.", plain(sent.get(0)));

        ConfigService.SetOutcome removed = config.listRemove("match.end-statistics", 4);

        assertTrue(removed.ok());
        feedback.listRemoved(sender, "match.end-statistics", removed);

        assertEquals(2, sent.size());
        assertEquals("[JManhunt] Removed from match.end-statistics: EXTRA.", plain(sent.get(1)));
        verify(sounds, times(2)).playNeutralSound(sender);
    }

    @Test
    void failedReportsOnlyTheError() {
        ConfigService.SetOutcome outcome = config.setValue(
                "settings.compass.signal-interference.light-level.min-sky-light", "16");

        feedback.failed(sender, outcome);

        assertEquals(1, sent.size());
        assertEquals("[JManhunt] Value for settings.compass.signal-interference.light-level.min-sky-light"
                + " must be 0 to 15.", plain(sent.get(0)));
        verifyNoInteractions(sounds);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).trim();
    }
}
