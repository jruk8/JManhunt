package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidMatchPolicyTest {

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(MidMatchPolicy.HOLD, MidMatchPolicy.parse("hold"));
        assertEquals(MidMatchPolicy.JOIN_ANY, MidMatchPolicy.parse("Join_Any"));
        assertEquals(MidMatchPolicy.JOIN_SPECTATORS, MidMatchPolicy.parse("JOIN_SPECTATORS"));
        assertEquals(MidMatchPolicy.SUBLOBBY, MidMatchPolicy.parse("sublobby"));
        assertEquals(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS,
                MidMatchPolicy.parse("sublobby_with_spectators"));
    }

    @Test
    void parseFallsBackToSublobbyWithSpectators() {
        assertEquals(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS, MidMatchPolicy.parse("bogus"));
        assertEquals(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS, MidMatchPolicy.parse(null));
        assertEquals(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS, MidMatchPolicy.parse(""));
    }

    @Test
    void holdNeverJoins() {
        for (Role role : Role.values()) {
            assertFalse(MidMatchPolicy.HOLD.joinsMidMatch(role), role.name());
        }
    }

    @Test
    void joinAnyAlwaysJoins() {
        for (Role role : Role.values()) {
            assertTrue(MidMatchPolicy.JOIN_ANY.joinsMidMatch(role), role.name());
        }
    }

    @Test
    void joinSpectatorsOnlyJoinsSpectators() {
        assertTrue(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.SPECTATOR));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.HUNTER));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.SPEEDRUNNER));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.AFK));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.NONE));
    }

    @Test
    void sublobbyHoldsForNextSublobby() {
        for (Role role : Role.values()) {
            assertFalse(MidMatchPolicy.SUBLOBBY.joinsMidMatch(role), role.name());
        }
    }

    @Test
    void sublobbyWithSpectatorsOnlyJoinsSpectators() {
        assertTrue(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.joinsMidMatch(Role.SPECTATOR));
        assertFalse(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.joinsMidMatch(Role.HUNTER));
        assertFalse(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.joinsMidMatch(Role.SPEEDRUNNER));
        assertFalse(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.joinsMidMatch(Role.AFK));
        assertFalse(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.joinsMidMatch(Role.NONE));
    }

    @Test
    void sublobbyPoliciesUseSubLobbies() {
        assertTrue(MidMatchPolicy.SUBLOBBY.usesSubLobbies());
        assertTrue(MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.usesSubLobbies());
        assertFalse(MidMatchPolicy.HOLD.usesSubLobbies());
        assertFalse(MidMatchPolicy.JOIN_ANY.usesSubLobbies());
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.usesSubLobbies());
    }

    @Test
    void onlySublobbyPoliciesWithEngineOnAllowConcurrentStart() {
        for (MidMatchPolicy policy : MidMatchPolicy.values()) {
            assertEquals(policy.usesSubLobbies(),
                    policy.allowsConcurrentStart(true), policy.name());
            assertFalse(policy.allowsConcurrentStart(false), policy.name());
        }
    }

    @Test
    void queueMessageTemplateNamesSublobbyQueueUnderSublobbyPolicies() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "manhunt.setplayer-queued-sublobby", "queued tpl");
        ConfigPathMapper.set(config, "manhunt.setplayer-held", "held tpl");
        ManhuntMessages texts = config.getManhunt();
        assertEquals("queued tpl",
                MidMatchPolicy.SUBLOBBY.queueMessageTemplate(texts));
        assertEquals("queued tpl",
                MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS.queueMessageTemplate(texts));
        assertEquals("held tpl", MidMatchPolicy.HOLD.queueMessageTemplate(texts));
        assertEquals("held tpl", MidMatchPolicy.JOIN_ANY.queueMessageTemplate(texts));
        assertEquals("held tpl", MidMatchPolicy.JOIN_SPECTATORS.queueMessageTemplate(texts));
    }
}
