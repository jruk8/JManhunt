package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PseudoborderConfigTest {
    private static final String BASE = "world-engine.world-border.particles.";

    private static ConfigService service(JManhuntConfig root) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new ConfigService(root, new ModifierStore(new ModifiersConfig(), log));
    }

    @Test
    void defaultsMatchSpec() {
        PseudoborderConfig config = PseudoborderConfig.fromConfig(service(new JManhuntConfig()));

        assertEquals(PseudoBorderParticle.DUST, config.type());
        assertEquals(1, config.particleSpacing());
        assertEquals("#de7766", config.colorHex());
        assertEquals(10.0, config.renderRadius(), 0.0);
        assertEquals(PulseMode.SINE_WAVE, config.pulseMode());
        assertEquals(0.5, config.intervalSeconds(), 0.0);
        assertEquals(0.0, config.waveDirectionAngle(), 0.0);
        assertEquals(8.0, config.waveLength(), 0.0);
        assertEquals(0.5, config.waveSpeedHz(), 0.0);
        assertEquals(1000, config.maxParticlesPerPlayer());
    }

    @Test
    void readsConfig() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, BASE + "type", "HEART");
        ConfigPathMapper.set(root, BASE + "particle-spacing", 4);
        ConfigPathMapper.set(root, BASE + "color", "#ffffff");
        ConfigPathMapper.set(root, BASE + "render-radius", 20.0);
        ConfigPathMapper.set(root, BASE + "pulse-mode", "INTERVAL");
        ConfigPathMapper.set(root, BASE + "interval", 1.5);
        ConfigPathMapper.set(root, BASE + "wave-direction-angle", 90.0);
        ConfigPathMapper.set(root, BASE + "wave-length", 16.0);
        ConfigPathMapper.set(root, BASE + "wave-speed", 1.0);
        ConfigPathMapper.set(root, BASE + "max-particles-per-player", 50);
        PseudoborderConfig config = PseudoborderConfig.fromConfig(service(root));

        assertEquals(PseudoBorderParticle.HEART, config.type());
        assertEquals(4, config.particleSpacing());
        assertEquals("#ffffff", config.colorHex());
        assertEquals(20.0, config.renderRadius(), 0.0);
        assertEquals(PulseMode.INTERVAL, config.pulseMode());
        assertEquals(1.5, config.intervalSeconds(), 0.0);
        assertEquals(90.0, config.waveDirectionAngle(), 0.0);
        assertEquals(16.0, config.waveLength(), 0.0);
        assertEquals(1.0, config.waveSpeedHz(), 0.0);
        assertEquals(50, config.maxParticlesPerPlayer());
    }

    @Test
    void clampsEveryKnob() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, BASE + "particle-spacing", 0);
        ConfigPathMapper.set(root, BASE + "render-radius", -1.0);
        ConfigPathMapper.set(root, BASE + "pulse-mode", "bogus");
        ConfigPathMapper.set(root, BASE + "interval", 9.0);
        ConfigPathMapper.set(root, BASE + "wave-direction-angle", 400.0);
        ConfigPathMapper.set(root, BASE + "wave-length", 1.0);
        ConfigPathMapper.set(root, BASE + "wave-speed", 0.0);
        ConfigPathMapper.set(root, BASE + "max-particles-per-player", 0);
        PseudoborderConfig low = PseudoborderConfig.fromConfig(service(root));

        assertEquals(1, low.particleSpacing());
        assertEquals(0.0, low.renderRadius(), 0.0);
        assertEquals(PulseMode.SINE_WAVE, low.pulseMode());
        assertEquals(3.0, low.intervalSeconds(), 0.0);
        assertEquals(360.0, low.waveDirectionAngle(), 0.0);
        assertEquals(2.0, low.waveLength(), 0.0);
        assertEquals(0.05, low.waveSpeedHz(), 0.0);
        assertEquals(1, low.maxParticlesPerPlayer());

        JManhuntConfig high = new JManhuntConfig();
        ConfigPathMapper.set(high, BASE + "particle-spacing", 9);
        ConfigPathMapper.set(high, BASE + "interval", -1.0);
        ConfigPathMapper.set(high, BASE + "wave-direction-angle", -5.0);
        ConfigPathMapper.set(high, BASE + "wave-length", 100.0);
        ConfigPathMapper.set(high, BASE + "wave-speed", 9.0);
        PseudoborderConfig clamped = PseudoborderConfig.fromConfig(service(high));

        assertEquals(8, clamped.particleSpacing());
        assertEquals(0.0, clamped.intervalSeconds(), 0.0);
        assertEquals(0.0, clamped.waveDirectionAngle(), 0.0);
        assertEquals(64.0, clamped.waveLength(), 0.0);
        assertEquals(3.0, clamped.waveSpeedHz(), 0.0);
    }
}
