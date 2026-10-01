package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompassDeltaRendererTest {

    private static final MessagesConfig TEXTS = new MessagesConfig();

    private static final Map<String, String> EXPECTED_HIDDEN = Map.of(
            TEXTS.getCompass().getCompassActionbar(), "Tracking victim",
            TEXTS.getCompass().getCompassLastSeenActionbar(),
            "Tracking victim's Last Seen (Log-Out)",
            TEXTS.getCompass().getCompassLockedActionbar(), "Tracking victim [LOCKED]",
            TEXTS.getCompass().getCompassLastSeenLockedActionbar(),
            "Tracking victim's Last Seen (Log-Out) [LOCKED]",
            TEXTS.getCompass().getTeammateActionbar(), "Tracking teammate victim",
            TEXTS.getCompass().getTeammateLastSeenActionbar(),
            "Tracking teammate victim's Last Seen (Log-Out)",
            TEXTS.getCompass().getTeammateLockedActionbar(),
            "Tracking teammate victim [LOCKED]",
            TEXTS.getCompass().getTeammateLastSeenLockedActionbar(),
            "Tracking teammate victim's Last Seen (Log-Out) [LOCKED]");

    @Test
    void hiddenDistanceRendersAllBarsCleanly() {
        Fixture fixture = fixture(false);

        for (Map.Entry<String, String> bar : EXPECTED_HIDDEN.entrySet()) {
            // Only last-seen templates carry {reason}; the extra is
            // ignored everywhere else.
            fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                    bar.getKey(), "victim", UUID.randomUUID(), 120.0,
                    Map.of("reason", "Log-Out"), exact());

            String text = text(fixture.bars().get(fixture.player().getUniqueId()));
            assertEquals(bar.getValue(), text, bar.getKey());
            assertFalse(text.contains("•"), bar.getKey());
        }
    }

    @Test
    void shownDistanceKeepsMeters() {
        Fixture fixture = fixture(true);

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                TEXTS.getCompass().getCompassActionbar(), "victim", UUID.randomUUID(), 120.0, Map.of(),
                exact());

        assertEquals("Tracking victim • 120m",
                text(fixture.bars().get(fixture.player().getUniqueId())));
    }

    @Test
    void accuracyAppendsSteppedPercent() {
        Fixture fixture = accuracyFixture(true, "#63d42a", "#cc472d");

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                TEXTS.getCompass().getCompassActionbar(), "victim", UUID.randomUUID(), 120.0, Map.of(),
                drift(300.0, 600.0));

        assertEquals("Tracking victim (50%) • 120m",
                text(fixture.bars().get(fixture.player().getUniqueId())));
    }

    @Test
    void accuracyReadsHundredWhenUnapplied() {
        Fixture fixture = accuracyFixture(true, "#63d42a", "#cc472d");

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                TEXTS.getCompass().getCompassActionbar(), "victim", UUID.randomUUID(), 120.0, Map.of(),
                exact());

        assertEquals("Tracking victim (100%) • 120m",
                text(fixture.bars().get(fixture.player().getUniqueId())));
    }

    @Test
    void accuracySurvivesHiddenDistance() {
        Fixture fixture = accuracyHiddenFixture();

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                TEXTS.getCompass().getCompassActionbar(), "victim", UUID.randomUUID(), 120.0, Map.of(),
                exact());

        assertEquals("Tracking victim (100%)",
                text(fixture.bars().get(fixture.player().getUniqueId())));
    }

    @Test
    void accuracyJunkColorsFallBack() {
        Fixture fixture = accuracyFixture(true, "bogus", "#123");

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                TEXTS.getCompass().getCompassActionbar(), "victim", UUID.randomUUID(), 120.0, Map.of(),
                drift(300.0, 600.0));

        assertEquals("Tracking victim (50%) • 120m",
                text(fixture.bars().get(fixture.player().getUniqueId())));
    }

    @Test
    void accuracySegmentClosesColorInMiniMessage() {
        Fixture fixture = accuracyFixture(true, "#63d42a", "#cc472d");

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                TEXTS.getCompass().getCompassActionbar(), "victim", UUID.randomUUID(), 120.0, Map.of(),
                drift(300.0, 600.0));

        String mini = mini(fixture.bars().get(fixture.player().getUniqueId()));
        String hex = CompassAccuracyRenderer.lerpColor("#cc472d", "#63d42a", 0.5);
        assertTrue(mini.contains("(<" + hex + ">50%<gray>)"), mini);
    }

    @Test
    void stripDropsSeparatorTagAndUnit() {
        assertEquals("<#de7766>Tracking <white>{player}",
                CompassDeltaRenderer.stripDistanceSegment(
                        "<#de7766>Tracking <white>{player}<#de7766> • <white>{distance}m"));
        assertEquals("Tracking <white>{player} <gray>[LOCKED]",
                CompassDeltaRenderer.stripDistanceSegment(
                        "Tracking <white>{player} • <red>{distance}m <gray>[LOCKED]"));
    }

    private record Fixture(CompassDeltaRenderer deltas, Player player, Map<UUID, Component> bars) {
    }

    private static Fixture fixture(boolean showDistance) {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.feedback.actionbar.show-distance", showDistance);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(ModifierFiles.inMemory(), log));
        OverrideService overrides =
                new OverrideService(configService, new LobbyConfig(), () -> { });
        CompassSettingsFacade settings =
                new CompassSettingsFacade(overrides, root.getSettings().getCompass());
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        Map<UUID, Component> bars = new HashMap<>();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return new Fixture(new CompassDeltaRenderer(mock(TaskScheduler.class), settings, messages, bars), player, bars);
    }

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String mini(Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    private static CompassInaccuracyService.Result exact() {
        return new CompassInaccuracyService.Result(null, 120.0, 0.0, 0.0, false);
    }

    private static CompassInaccuracyService.Result drift(double error, double theory) {
        return new CompassInaccuracyService.Result(null, 120.0, error, theory, true);
    }

    private static Fixture accuracyFixture(boolean showDistance, String accurate,
            String inaccurate) {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.feedback.actionbar.show-distance",
                showDistance);
        ConfigPathMapper.set(root,
                "settings.compass.feedback.actionbar.show-accuracy.enabled", true);
        ConfigPathMapper.set(root,
                "settings.compass.feedback.actionbar.show-accuracy.accurate-color", accurate);
        ConfigPathMapper.set(root,
                "settings.compass.feedback.actionbar.show-accuracy.inaccurate-color", inaccurate);
        return configuredFixture(root);
    }

    private static Fixture accuracyHiddenFixture() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.feedback.actionbar.show-distance", false);
        ConfigPathMapper.set(root,
                "settings.compass.feedback.actionbar.show-accuracy.enabled", true);
        return configuredFixture(root);
    }

    private static Fixture configuredFixture(JManhuntConfig root) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(ModifierFiles.inMemory(), log));
        OverrideService overrides =
                new OverrideService(configService, new LobbyConfig(), () -> { });
        CompassSettingsFacade settings =
                new CompassSettingsFacade(overrides, root.getSettings().getCompass());
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        Map<UUID, Component> bars = new HashMap<>();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return new Fixture(new CompassDeltaRenderer(mock(TaskScheduler.class), settings, messages, bars), player, bars);
    }
}
