package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.jruk8.jmanhunt.match.prestart.Headstart;

class HeadstartTest {

    @Test
    void defaultsAreDisabledThirtySeconds() {
        YamlConfiguration config = new YamlConfiguration();

        Headstart hunter = Headstart.parse(config, "hunter");
        Headstart runner = Headstart.parse(config, "speedrunner");

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

        assertEquals(new Headstart(true, 45),
                Headstart.parse(config, "hunter"));
        assertEquals(new Headstart(true, 10),
                Headstart.parse(config, "speedrunner"));
    }

    @Test
    void oppositeSwapsParticipantSides() {
        assertEquals(Role.SPEEDRUNNER, GameManager.opposite(Role.HUNTER));
        assertEquals(Role.HUNTER, GameManager.opposite(Role.SPEEDRUNNER));
        assertEquals(Role.NONE, GameManager.opposite(Role.NONE));
    }
}
