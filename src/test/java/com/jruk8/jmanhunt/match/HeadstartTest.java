package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.jruk8.jmanhunt.match.prestart.Headstart;

class HeadstartTest {

    private static ConfigService service(JManhuntConfig root) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new ConfigService(root, new ModifierStore(new ModifiersConfig(), log));
    }

    @Test
    void defaultsAreDisabledThirtySeconds() {
        ConfigService config = service(new JManhuntConfig());

        Headstart hunter = Headstart.parse(config, "hunter");
        Headstart runner = Headstart.parse(config, "speedrunner");

        assertFalse(hunter.enabled());
        assertEquals(30, hunter.delaySeconds());
        assertFalse(runner.enabled());
        assertEquals(30, runner.delaySeconds());
    }

    @Test
    void sidesParseIndependently() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.headstarts.hunter.enabled", true);
        ConfigPathMapper.set(root, "settings.match.headstarts.hunter.delay-seconds", 45);
        ConfigPathMapper.set(root, "settings.match.headstarts.speedrunner.enabled", true);
        ConfigPathMapper.set(root, "settings.match.headstarts.speedrunner.delay-seconds", 10);
        ConfigService config = service(root);

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
