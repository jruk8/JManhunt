package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.SectionPinner;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Consumers hold message section references across reloads. Okaeri
 * load() replaces section instances, so the registrar pins them: the
 * held instance stays identical while its values refresh.
 */
class MessageSectionStabilityTest {

    @Test
    void pinnedSectionStaysIdenticalWithFreshValues() {
        MessagesConfig config = ConfigManager.create(MessagesConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer());
        });
        SectionPinner pinner = new SectionPinner();
        config.load(Map.of("spectator", Map.of("no-matches", "first")));
        pinner.pin(config);
        SpectatorMessages held = config.getSpectator();

        config.load(Map.of("spectator", Map.of("no-matches", "second")));
        pinner.pin(config);

        assertSame(held, config.getSpectator());
        assertEquals("second", held.getNoMatches());
        assertEquals("second", ConfigPathMapper.get(config, "spectator.no-matches"));
    }
}
