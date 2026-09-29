package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompassDeltaRendererTest {

    private static final Map<String, String> EXPECTED_HIDDEN = Map.of(
            "compass.compass-actionbar", "Tracking victim",
            "compass.compass-last-seen-actionbar", "Tracking victim's Last Seen (Log-Out)",
            "compass.compass-locked-actionbar", "Tracking victim [LOCKED]",
            "compass.compass-last-seen-locked-actionbar",
            "Tracking victim's Last Seen (Log-Out) [LOCKED]",
            "compass.teammate-actionbar", "Tracking teammate victim",
            "compass.teammate-last-seen-actionbar",
            "Tracking teammate victim's Last Seen (Log-Out)",
            "compass.teammate-locked-actionbar", "Tracking teammate victim [LOCKED]",
            "compass.teammate-last-seen-locked-actionbar",
            "Tracking teammate victim's Last Seen (Log-Out) [LOCKED]");

    @Test
    void hiddenDistanceRendersAllBarsCleanly() {
        Fixture fixture = fixture(false);

        for (Map.Entry<String, String> bar : EXPECTED_HIDDEN.entrySet()) {
            Map<String, String> extra = bar.getKey().contains("last-seen")
                    ? Map.of("reason", "Log-Out") : Map.of();
            fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                    bar.getKey(), "victim", UUID.randomUUID(), 120.0, extra);

            String text = text(fixture.bars().get(fixture.player().getUniqueId()));
            assertEquals(bar.getValue(), text, bar.getKey());
            assertFalse(text.contains("•"), bar.getKey());
        }
    }

    @Test
    void shownDistanceKeepsMeters() {
        Fixture fixture = fixture(true);

        fixture.deltas().putTrackingBar(fixture.player(), Role.HUNTER, null,
                "compass.compass-actionbar", "victim", UUID.randomUUID(), 120.0, Map.of());

        assertEquals("Tracking victim • 120m",
                text(fixture.bars().get(fixture.player().getUniqueId())));
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
                new ModifierStore(new ModifiersConfig(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.overrides()).thenReturn(
                new OverrideService(configService, new LobbyConfig(), () -> { }));
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        Map<UUID, Component> bars = new HashMap<>();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return new Fixture(new CompassDeltaRenderer(plugin, messages, bars), player, bars);
    }

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
