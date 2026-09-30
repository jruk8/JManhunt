package com.jruk8.jmanhunt.compass;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.config.SignalInaccuracySettings;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Donut bounds, clamps, gates, target parsing, and the service resolve
 * path over a real config stack. No Bukkit server needed.
 */
class SignalInaccuracyTest {

    private static SignalInaccuracy.Config config(boolean enabled, double deadzone, double drift,
            double min, double max, SignalInaccuracy.InaccurateOn target) {
        return new SignalInaccuracy.Config(enabled, deadzone, drift, min, max, target);
    }

    private static SignalInaccuracy.Config defaults() {
        return config(true, 0.4, 0.6, 100.0, 1000.0, SignalInaccuracy.InaccurateOn.BOTH);
    }

    @Test
    void samplesStayInsideTheDonut() {
        double outer = 300.0;
        double inner = 0.4 * outer;
        for (int angle = 0; angle < 36; angle++) {
            for (int radius = 0; radius <= 10; radius++) {
                double[] point = SignalInaccuracy.sample(1000.0, -500.0, outer, 0.4,
                        angle / 36.0, radius / 10.0);
                double dx = point[0] - 1000.0;
                double dz = point[1] - (-500.0);
                double got = Math.sqrt(dx * dx + dz * dz);
                assertTrue(got >= inner - 1e-9 && got <= outer + 1e-9,
                        "radius out of ring: " + got);
            }
        }
    }

    @Test
    void deadzoneZeroReachesTheTrueSpot() {
        double[] point = SignalInaccuracy.sample(0.0, 0.0, 300.0, 0.0, 0.0, 0.0);
        assertEquals(0.0, point[0], 1e-9);
        assertEquals(0.0, point[1], 1e-9);
    }

    @Test
    void deadzoneOneKeepsARing() {
        assertEquals(SignalInaccuracy.MAX_DEADZONE, SignalInaccuracy.clampDeadzone(1.0), 0.0);
        assertEquals(SignalInaccuracy.MAX_DEADZONE, SignalInaccuracy.clampDeadzone(5.0), 0.0);
        assertEquals(0.0, SignalInaccuracy.clampDeadzone(-2.0), 0.0);
        assertEquals(0.4, SignalInaccuracy.clampDeadzone(Double.NaN), 0.0);
        double[] point = SignalInaccuracy.sample(0.0, 0.0, 300.0, 1.0, 0.0, 0.0);
        double got = Math.hypot(point[0], point[1]);
        assertTrue(got > 0.0 && got < 300.0, "ring collapsed: " + got);
    }

    @Test
    void driftClampsToShare() {
        assertEquals(0.01, SignalInaccuracy.clampDrift(0.0), 0.0);
        assertEquals(0.01, SignalInaccuracy.clampDrift(-3.0), 0.0);
        assertEquals(1.0, SignalInaccuracy.clampDrift(2.0), 0.0);
        assertEquals(0.6, SignalInaccuracy.clampDrift(Double.NaN), 0.0);
        assertEquals(0.6, SignalInaccuracy.clampDrift(0.6), 0.0);
    }

    @Test
    void effectiveMinHandlesDisableAndOrder() {
        assertEquals(-1.0, SignalInaccuracy.effectiveMin(-1.0, 1000.0), 0.0);
        assertEquals(-1.0, SignalInaccuracy.effectiveMin(0.0, 1000.0), 0.0);
        assertEquals(100.0, SignalInaccuracy.effectiveMin(100.0, 1000.0), 0.0);
        assertEquals(100.0, SignalInaccuracy.effectiveMin(100.0, -1.0), 0.0);
        assertEquals(1000.0, SignalInaccuracy.effectiveMin(1500.0, 1000.0), 0.0);
        assertEquals(1000.0, SignalInaccuracy.effectiveMin(1000.0, 1000.0), 0.0);
    }

    @Test
    void appliesGatesOnEnabledAndMin() {
        assertFalse(SignalInaccuracy.applies(
                config(false, 0.4, 0.6, 100.0, 1000.0, SignalInaccuracy.InaccurateOn.BOTH),
                500.0));
        assertFalse(SignalInaccuracy.applies(defaults(), 0.0));
        assertFalse(SignalInaccuracy.applies(defaults(), 100.0));
        assertTrue(SignalInaccuracy.applies(defaults(), 100.5));
        assertTrue(SignalInaccuracy.applies(
                config(true, 0.4, 0.6, -1.0, 1000.0, SignalInaccuracy.InaccurateOn.BOTH), 5.0));
    }

    @Test
    void outerRadiusCapsAtMax() {
        assertEquals(300.0, SignalInaccuracy.outerRadius(500.0, 0.6, 1000.0), 1e-9);
        assertEquals(600.0, SignalInaccuracy.outerRadius(1500.0, 0.6, 1000.0), 1e-9);
        assertEquals(900.0, SignalInaccuracy.outerRadius(1500.0, 0.6, -1.0), 1e-9);
    }

    @Test
    void parseTargetFallsBackToBoth() {
        assertEquals(SignalInaccuracy.InaccurateOn.NEEDLE,
                SignalInaccuracy.parseTarget("needle"));
        assertEquals(SignalInaccuracy.InaccurateOn.DISTANCE_FEEDBACK,
                SignalInaccuracy.parseTarget(" distance_feedback "));
        assertEquals(SignalInaccuracy.InaccurateOn.BOTH, SignalInaccuracy.parseTarget(null));
        assertEquals(SignalInaccuracy.InaccurateOn.BOTH, SignalInaccuracy.parseTarget("bogus"));
    }

    private record ServiceFixture(CompassInaccuracyService service, JManhuntConfig root,
            World world) {
    }

    private static ServiceFixture serviceFixture() {
        JManhuntConfig root = new JManhuntConfig();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(new ModifiersConfig(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.overrides()).thenReturn(
                new OverrideService(configService, new LobbyConfig(), () -> { }));
        return new ServiceFixture(new CompassInaccuracyService(plugin), root, mock(World.class));
    }

    private static void setTarget(ServiceFixture fixture, SignalInaccuracy.InaccurateOn target) {
        Object schema = ConfigPathMapper.get(fixture.root(),
                "settings.compass.signal.inaccuracy");
        ((SignalInaccuracySettings) schema).setInaccurateOn(target);
    }

    @Test
    void disabledServiceStaysExact() {
        ServiceFixture fixture = serviceFixture();
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(fixture.world(), 500.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertFalse(result.applied());
        assertSame(truth, result.needleSpot());
        assertEquals(500.0, result.feedbackDistance(), 1e-9);
        assertEquals(0.0, result.errorDistance(), 0.0);
        assertEquals(600.0, result.theoreticalMax(), 1e-9);
    }

    @Test
    void enabledServiceDriftsPastMin() {
        ServiceFixture fixture = serviceFixture();
        ConfigPathMapper.set(fixture.root(), "settings.compass.signal.inaccuracy.enabled", true);
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(fixture.world(), 500.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertTrue(result.applied());
        assertTrue(result.errorDistance() >= 120.0 - 1e-9
                && result.errorDistance() <= 300.0 + 1e-9,
                "error out of ring: " + result.errorDistance());
        assertEquals(64.0, result.needleSpot().getY(), 0.0);
        assertSame(fixture.world(), result.needleSpot().getWorld());
    }

    @Test
    void belowMinStaysExact() {
        ServiceFixture fixture = serviceFixture();
        ConfigPathMapper.set(fixture.root(), "settings.compass.signal.inaccuracy.enabled", true);
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(fixture.world(), 50.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertFalse(result.applied());
        assertSame(truth, result.needleSpot());
        assertEquals(50.0, result.feedbackDistance(), 1e-9);
    }

    @Test
    void nonCompassItemKeepsNeedleExact() {
        ServiceFixture fixture = serviceFixture();
        ConfigPathMapper.set(fixture.root(), "settings.compass.signal.inaccuracy.enabled", true);
        ConfigPathMapper.set(fixture.root(), "settings.compass.obtaining.item", "clock");
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(fixture.world(), 500.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertTrue(result.applied());
        assertSame(truth, result.needleSpot());
        assertNotEquals(500.0, result.feedbackDistance(), 1e-9);
    }

    @Test
    void hiddenDistanceDisablesDistanceOnlyMode() {
        ServiceFixture fixture = serviceFixture();
        ConfigPathMapper.set(fixture.root(), "settings.compass.signal.inaccuracy.enabled", true);
        ConfigPathMapper.set(fixture.root(),
                "settings.compass.feedback.actionbar.show-distance", false);
        ConfigPathMapper.set(fixture.root(),
                "settings.compass.feedback.actionbar.show-distance-delta.enabled", false);
        setTarget(fixture, SignalInaccuracy.InaccurateOn.DISTANCE_FEEDBACK);
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(fixture.world(), 500.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertFalse(result.applied());
        assertSame(truth, result.needleSpot());
    }

    @Test
    void needleModeKeepsDistanceExact() {
        ServiceFixture fixture = serviceFixture();
        ConfigPathMapper.set(fixture.root(), "settings.compass.signal.inaccuracy.enabled", true);
        setTarget(fixture, SignalInaccuracy.InaccurateOn.NEEDLE);
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(fixture.world(), 500.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertTrue(result.applied());
        assertNotEquals(truth, result.needleSpot());
        assertEquals(500.0, result.feedbackDistance(), 1e-9);
    }

    @Test
    void mismatchedWorldsStayExact() {
        ServiceFixture fixture = serviceFixture();
        ConfigPathMapper.set(fixture.root(), "settings.compass.signal.inaccuracy.enabled", true);
        Location tracker = new Location(fixture.world(), 0.0, 64.0, 0.0);
        Location truth = new Location(mock(World.class), 500.0, 64.0, 0.0);
        CompassInaccuracyService.Result result =
                fixture.service().resolve(null, tracker, truth, 0.25, 0.5);
        assertFalse(result.applied());
        assertSame(truth, result.needleSpot());
        assertEquals(0.0, result.feedbackDistance(), 0.0);
    }
}
