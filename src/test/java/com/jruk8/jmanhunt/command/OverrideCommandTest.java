package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Override chat flows over bare configs: settings get, set, and
 * clear with source marks, modifier and preset toggles, lobby
 * clear, and usage plus validation failures. The sender is a
 * console-like fake, proving console execution works.
 */
class OverrideCommandTest {

    private static final String BOOL = "settings.match.autostart.enabled";

    private OverrideCommand command;
    private OverrideService overrides;
    private MessageService messages;
    private MessagesConfig texts;

    @BeforeEach
    void setUp() throws Exception {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierFiles modifiers = ModifierFiles.inMemory();
        ModifierEntry beef = new ModifierEntry();
        beef.setEnabled(true);
        modifiers.getModifiers().put("beef", beef);
        ModifierEntry gapple = new ModifierEntry();
        gapple.setEnabled(false);
        modifiers.getModifiers().put("gapple", gapple);
        ModifierPreset chaos = new ModifierPreset();
        chaos.setModifiers(List.of("beef", "gapple"));
        modifiers.getPresets().put("chaos", chaos);
        ConfigService config =
                new ConfigService(new JManhuntConfig(), new ModifierStore(modifiers, log));
        overrides = new OverrideService(config, new LobbyConfig(), () -> {});
        messages = new MessageService();
        texts = new MessagesConfig();
        messages.reload(texts);
        command = new OverrideCommand(overrides, config, messages,
                texts.getManhunt(), texts.getModifiers(),
                new SettingFeedback(messages, texts.getManhunt(), config, null), null);
    }

    @Test
    void settingsGetMarksGlobalThenOverride() {
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.execute(sender,
                new String[]{"override", "0", "settings", "get", "settings", "match",
                        "autostart", "enabled"}));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideSettingShown(),
                Map.of("setting", BOOL, "value", "true", "source", globalSource()))),
                sender.received());

        assertTrue(command.execute(sender,
                new String[]{"override", "0", "settings", "set", "settings", "match",
                        "autostart", "enabled", "false"}));
        assertTrue(overrides.hasSettingOverride(0, BOOL));

        FakeSender reader = FakeSender.permitted();
        assertTrue(command.execute(reader,
                new String[]{"override", "0", "settings", "get", "settings", "match",
                        "autostart", "enabled"}));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideSettingShown(),
                Map.of("setting", BOOL, "value", "false", "source", overrideSource()))),
                reader.received());
    }

    @Test
    void settingsSetRejectsBadValuesWithoutWriting() {
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.execute(sender,
                new String[]{"override", "0", "settings", "set", "settings", "match",
                        "autostart", "enabled", "maybe"}));
        assertFalse(overrides.hasSettingOverride(0, BOOL));
        assertEquals(1, sender.received().size());

        assertTrue(command.execute(sender,
                new String[]{"override", "0", "settings", "set", "bogus", "path", "1"}));
        assertEquals(2, sender.received().size());
    }

    @Test
    void settingsClearRemovesThenReportsNothing() {
        FakeSender sender = FakeSender.permitted();
        assertTrue(overrides.setSettingOverride(4, BOOL, "false").ok());
        String[] clear = {"override", "4", "settings", "clear", "settings", "match",
                "autostart", "enabled"};

        assertTrue(command.execute(sender, clear));
        assertTrue(overrides.hasSettingOverride(4, BOOL));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideClearConfirm(),
                Map.of("lobby", "4", "what", "overrides under " + BOOL))), sender.received());

        assertTrue(command.execute(sender, clear));
        assertFalse(overrides.hasSettingOverride(4, BOOL));
        assertEquals(messages.componentRaw(texts.getManhunt().getOverrideRemoved(),
                Map.of("lobby", "4", "setting", BOOL)), sender.received().get(1));

        assertTrue(command.execute(sender, clear));
        assertTrue(command.execute(sender, clear));
        assertEquals(messages.componentRaw(texts.getManhunt().getOverrideNothingToClear(),
                Map.of("lobby", "4", "setting", BOOL)), sender.received().get(3));
    }

    @Test
    void modifiersSetGetClearRoundTrip() {
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.execute(sender,
                new String[]{"override", "1", "modifiers", "set", "gapple", "true"}));
        assertTrue(overrides.modifierEnabled(1, "gapple"));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideModifierSet(),
                Map.of("lobby", "1", "modifier", "gapple", "state", "on"))),
                sender.received());

        FakeSender reader = FakeSender.permitted();
        assertTrue(command.execute(reader,
                new String[]{"override", "1", "modifiers", "get", "gapple"}));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideModifierShown(),
                Map.of("modifier", "gapple", "state", "on", "source", overrideSource()))),
                reader.received());

        assertTrue(command.execute(sender,
                new String[]{"override", "1", "modifiers", "clear", "gapple"}));
        assertTrue(overrides.hasModifierOverride(1, "gapple"));

        assertTrue(command.execute(sender,
                new String[]{"override", "1", "modifiers", "clear", "gapple"}));
        assertFalse(overrides.hasModifierOverride(1, "gapple"));
    }

    @Test
    void modifiersSetPresetForcesEveryMember() {
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.execute(sender,
                new String[]{"override", "2", "modifiers", "set", "chaos", "true"}));

        assertTrue(overrides.presetEnabled(2, "chaos"));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverridePresetSet(),
                Map.of("lobby", "2", "preset", "chaos", "state", "on", "count", "2"))),
                sender.received());
    }

    @Test
    void clearLobbyRemovesEverything() {
        FakeSender sender = FakeSender.permitted();
        assertTrue(overrides.setSettingOverride(6, BOOL, "false").ok());
        assertTrue(overrides.setModifierOverride(6, "beef", false));

        assertTrue(command.execute(sender, new String[]{"override", "6", "clear"}));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideClearConfirm(),
                Map.of("lobby", "6", "what", "every override"))), sender.received());

        FakeSender again = FakeSender.permitted();
        assertTrue(command.execute(again, new String[]{"override", "6", "clear"}));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideLobbyCleared(),
                Map.of("lobby", "6", "count", "2"))), again.received());

        assertTrue(command.execute(sender, new String[]{"override", "6", "clear"}));
        assertTrue(command.execute(again, new String[]{"override", "6", "clear"}));
        assertEquals(messages.componentRaw(texts.getManhunt().getOverrideLobbyEmpty(),
                Map.of("lobby", "6")), again.received().get(1));
    }

    @Test
    void invalidLobbyAndUsageForms() {
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.execute(sender, new String[]{"override", "nope"}));
        assertEquals(List.of(messages.componentRaw(texts.getManhunt().getOverrideInvalidLobby(),
                Map.of("lobby", "nope"))), sender.received());

        assertTrue(command.execute(sender, new String[]{"override"}));
        assertTrue(command.execute(sender, new String[]{"override", "0"}));
        assertTrue(command.execute(sender,
                new String[]{"override", "0", "settings", "get", "bogus", "path"}));
        assertEquals(4, sender.received().size());
    }

    private String globalSource() {
        return texts.getManhunt().getOverrideSourceGlobal();
    }

    private String overrideSource() {
        return texts.getManhunt().getOverrideSourceOverride();
    }
}
