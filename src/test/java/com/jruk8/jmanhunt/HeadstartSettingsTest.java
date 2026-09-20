package com.jruk8.jmanhunt;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadstartSettingsTest {

    @Test
    void defaultsAreDisabledThirtySeconds() {
        YamlConfiguration config = new YamlConfiguration();

        HeadstartSettings.Headstart hunter = HeadstartSettings.parse(config, "hunter");
        HeadstartSettings.Headstart runner = HeadstartSettings.parse(config, "speedrunner");

        assertFalse(hunter.enabled());
        assertEquals(30, hunter.delaySeconds());
        assertFalse(runner.enabled());
        assertEquals(30, runner.delaySeconds());
    }

    @Test
    void sidesParseIndependently() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.headstarts.hunter.enabled", true);
        config.set("settings.headstarts.hunter.delay-seconds", 45);
        config.set("settings.headstarts.speedrunner.enabled", true);
        config.set("settings.headstarts.speedrunner.delay-seconds", 10);

        assertEquals(new HeadstartSettings.Headstart(true, 45),
                HeadstartSettings.parse(config, "hunter"));
        assertEquals(new HeadstartSettings.Headstart(true, 10),
                HeadstartSettings.parse(config, "speedrunner"));
    }
}
