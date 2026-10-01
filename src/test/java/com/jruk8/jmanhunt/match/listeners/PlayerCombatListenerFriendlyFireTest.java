package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;

class PlayerCombatListenerFriendlyFireTest {

    @Test
    void sameParticipantRoleCountsAsFriendlyFire() {
        assertTrue(PlayerCombatListener.isFriendlyFireKill(Role.HUNTER, Role.HUNTER, false));
        assertTrue(PlayerCombatListener.isFriendlyFireKill(
                Role.SPEEDRUNNER, Role.SPEEDRUNNER, false));
    }

    @Test
    void crossTeamSuicideAndNonParticipantKillsAreExcluded() {
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.HUNTER, Role.SPEEDRUNNER, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.SPEEDRUNNER, Role.HUNTER, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.HUNTER, Role.HUNTER, true));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.SPECTATOR, Role.SPECTATOR, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.NONE, Role.NONE, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.AFK, Role.AFK, false));
    }

    @Test
    void friendlyFireTemplatesCycleThreeLines() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "game.friendly-fire-1", "line one");
        ConfigPathMapper.set(config, "game.friendly-fire-2", "line two");
        ConfigPathMapper.set(config, "game.friendly-fire-3", "line three");
        GameMessages texts = config.getGame();
        assertEquals("line one", PlayerCombatListener.friendlyFireTemplate(texts, 0));
        assertEquals("line two", PlayerCombatListener.friendlyFireTemplate(texts, 1));
        assertEquals("line three", PlayerCombatListener.friendlyFireTemplate(texts, 2));
        assertEquals("line one", PlayerCombatListener.friendlyFireTemplate(texts, 3));
    }
}
