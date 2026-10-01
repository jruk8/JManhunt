package com.jruk8.jmanhunt.compass;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.CompassAnalysisSettings;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** INITIATE/SUCCESS costs: poverty blocks, failure stamps, and used sounds. */
class CompassAnalysisSessionsTest {

    private static final String COST = "settings.compass.actions.manual.analysis.cost.";

    @Test
    void poorHolderBlocksStampsFailureAndSoundsTooHigh() {
        Fixture fixture = fixture();
        Player holder = holder(fixture, 3.0, 0.0f, 0, 0);
        set(fixture.root(), COST + "payment.health.enabled", true);

        assertFalse(fixture.sessions().tryInitiateCost(holder));

        assertTrue(fixture.sessions().lastFailureStamps().containsKey(holder.getUniqueId()));
        verify(fixture.sounds(), times(1)).playSound(holder, "compass.cost-too-high");
        verify(holder, never()).setHealth(any(Double.class));
        verify(holder, never()).setLevel(any(Integer.class));
    }

    @Test
    void disabledFailureCooldownSkipsStamp() {
        Fixture fixture = fixture();
        Player holder = holder(fixture, 3.0, 0.0f, 0, 0);
        set(fixture.root(), COST + "payment.health.enabled", true);
        set(fixture.root(), COST + "payment.failure-cooldown", 0.0);

        assertFalse(fixture.sessions().tryInitiateCost(holder));

        assertTrue(fixture.sessions().lastFailureStamps().isEmpty());
        verify(fixture.sounds(), times(1)).playSound(holder, "compass.cost-too-high");
    }

    @Test
    void richHolderPaysAndSoundsSingleUsedType() {
        Fixture fixture = fixture();
        Player holder = holder(fixture, 20.0, 5.0f, 20, 5);

        assertTrue(fixture.sessions().tryInitiateCost(holder));

        verify(holder, times(1)).setLevel(4);
        verify(holder, times(1)).setHealth(20.0);
        verify(fixture.sounds(), times(1)).playSound(holder, "compass.cost-used-exp");
        assertTrue(fixture.sessions().lastFailureStamps().isEmpty());
    }

    @Test
    void richHolderSoundsExactlyOneOfManyTypes() {
        Fixture fixture = fixture();
        Player holder = holder(fixture, 20.0, 5.0f, 20, 5);
        set(fixture.root(), COST + "payment.health.enabled", true);

        assertTrue(fixture.sessions().tryInitiateCost(holder));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fixture.sounds(), times(1)).playSound(eq(holder), key.capture());
        assertTrue(Set.of("compass.cost-used-health", "compass.cost-used-exp")
                .contains(key.getValue()), key.getValue());
    }

    @Test
    void successCostSkippedWhenNotListed() {
        Fixture fixture = fixture();
        Player holder = holder(fixture, 20.0, 5.0f, 20, 5);

        assertTrue(fixture.sessions().trySuccessCost(holder));

        verify(holder, never()).setLevel(any(Integer.class));
        verify(fixture.sounds(), never()).playSound(any(Player.class), any(String.class));
    }

    @Test
    void disabledCostSkipsEverything() {
        Fixture fixture = fixture();
        set(fixture.root(), COST + "enabled", false);
        Player holder = holder(fixture, 3.0, 0.0f, 0, 0);

        assertTrue(fixture.sessions().tryInitiateCost(holder));

        assertTrue(fixture.sessions().lastFailureStamps().isEmpty());
        verify(fixture.sounds(), never()).playSound(any(Player.class), any(String.class));
    }

    @Test
    void paymentDefaultsToExpOnly() {
        CompassAnalysisSettings.Cost cost = new CompassAnalysisSettings.Cost();

        assertFalse(cost.isEnabled());
        assertEquals("INITIATE", cost.getCostOn());
        assertFalse(cost.getPayment().getHealth().isEnabled());
        assertFalse(cost.getPayment().getSaturation().isEnabled());
        assertTrue(cost.getPayment().getExpLevel().isEnabled());
        assertEquals(1.0, cost.getPayment().getFailureCooldown(), 1e-9);
    }

    @Test
    void pickUsedTypeIsDeterministicForSingleTypes() {
        assertEquals("exp", CompassAnalysisSessions.pickUsedType(List.of("exp"), new Random(0)));
    }

    @Test
    void pickUsedTypeStaysWithinCandidates() {
        List<String> types = List.of("health", "saturation", "exp");
        Random random = new Random(7);

        for (int i = 0; i < 25; i++) {
            assertTrue(types.contains(CompassAnalysisSessions.pickUsedType(types, random)));
        }
    }

    private static void set(JManhuntConfig root, String path, Object value) {
        ConfigPathMapper.set(root, path, value);
    }

    private static Player holder(Fixture fixture, double health, float saturation,
            int foodLevel, int level) {
        Player holder = mock(Player.class);
        when(holder.getUniqueId()).thenReturn(fixture.holderId());
        when(holder.isOnline()).thenReturn(true);
        when(holder.getHealth()).thenReturn(health);
        when(holder.getSaturation()).thenReturn(saturation);
        when(holder.getFoodLevel()).thenReturn(foodLevel);
        when(holder.getLevel()).thenReturn(level);
        return holder;
    }

    private record Fixture(JManhuntConfig root, CompassAnalysisSessions sessions, UUID holderId,
            SoundService sounds) {
    }

    private static Fixture fixture() {
        JManhuntConfig root = new JManhuntConfig();
        set(root, COST + "enabled", true);
        set(root, COST + "cost-on", "INITIATE");
        set(root, COST + "payment.saturation.enabled", false);
        set(root, COST + "payment.health.enabled", false);
        set(root, COST + "payment.exp-level.enabled", true);
        set(root, COST + "payment.exp-level.value", 1);
        set(root, COST + "payment.failure-cooldown", 1.0);
        set(root, COST + "poverty-behavior.cancel-when-poor", true);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(ModifierFiles.inMemory(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        OverrideService overrides =
                new OverrideService(configService, new LobbyConfig(), () -> { });
        when(plugin.overrides()).thenReturn(overrides);
        CompassSettingsFacade settings =
                new CompassSettingsFacade(overrides, root.getSettings().getCompass());
        MessageService messages = new MessageService();
        MessagesConfig texts = new MessagesConfig();
        messages.reload(texts);
        SoundService sounds = mock(SoundService.class);
        CompassAnalysisSessions sessions = new CompassAnalysisSessions(plugin, settings, messages,
                texts.getCompass(), new PlayerStateStore(), mock(CompassTargetService.class),
                mock(CompassSignalService.class), mock(CompassItemService.class),
                new HashMap<UUID, Component>(), sounds);
        return new Fixture(root, sessions, UUID.randomUUID(), sounds);
    }
}
