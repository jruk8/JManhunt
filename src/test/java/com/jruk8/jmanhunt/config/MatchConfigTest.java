package com.jruk8.jmanhunt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchConfigTest {

    @Test
    void knownRulesCoverFiveKeysWithFiveDefaults() {
        assertEquals(5, MatchConfig.GameRules.KNOWN.size());
        assertEquals(5, MatchConfig.GameRules.DEFAULT_RULES.size());
        assertTrue(MatchConfig.GameRules.DEFAULT_RULES
                .containsAll(MatchConfig.GameRules.KNOWN));
        assertEquals(MatchConfig.GameRules.DEFAULT_RULES,
                new MatchConfig.GameRules().getRules());
    }

    @Test
    void isRuleEnabledMatchesCaseInsensitively() {
        List<String> rules = List.of("DISABLE_PHANTOMS", "SET_DAYTIME");
        assertTrue(MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_PHANTOMS"));
        assertTrue(MatchConfig.GameRules.isRuleEnabled(rules, "set_daytime"));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_LOCATOR_BAR"));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(rules, "BOGUS_RULE"));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(rules, "disable-phantoms"));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(List.of(), "SET_DAYTIME"));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(null, "SET_DAYTIME"));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(rules, null));
        assertFalse(MatchConfig.GameRules.isRuleEnabled(Arrays.asList(null, null), "SET_DAYTIME"));
    }
}
