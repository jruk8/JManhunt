package com.jruk8.jmanhunt.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSyntaxTest {

    @Test
    void blankCommandIsFatal() {
        assertTrue(CommandSyntax.error("").isPresent());
        assertTrue(CommandSyntax.error("   ").isPresent());
    }

    @Test
    void plainCommandPasses() {
        assertTrue(CommandSyntax.error("give Steve bread 8").isEmpty());
        assertTrue(CommandSyntax.warnings("give Steve bread 8").isEmpty());
    }

    @Test
    void unbalancedBracketsAreFatal() {
        assertEquals("Unclosed '<' tag in command.", CommandSyntax.error("give <p bread").orElseThrow());
        assertEquals("Unmatched '>' in command.", CommandSyntax.error("give p> bread").orElseThrow());
    }

    @Test
    void knownTagsPass() {
        assertTrue(CommandSyntax.error("give <p> <random-item>").isEmpty());
        assertTrue(CommandSyntax.error("summon <random-mob> ~ ~1 ~").isEmpty());
        assertTrue(CommandSyntax.error("tp <all-players:HUNTER> <p>").isEmpty());
        assertTrue(CommandSyntax.error("effect give <p> speed <duration> 1").isEmpty());
    }

    @Test
    void unknownTagWarnsButPasses() {
        String command = "say <hello> <p>";
        assertTrue(CommandSyntax.error(command).isEmpty());
        assertEquals(1, CommandSyntax.warnings(command).size());
        assertTrue(CommandSyntax.warnings(command).get(0).contains("hello"));
    }

    @Test
    void randomNumberRules() {
        assertTrue(CommandSyntax.error("give <p> apple <random-num:1,6>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> apple <random-num:6,1>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> apple <random-num:1>").isPresent());
        assertTrue(CommandSyntax.error("give <p> apple <random-num:a,b>").isPresent());
        assertTrue(CommandSyntax.error("give <p> apple <random-num>").isPresent());
        assertTrue(CommandSyntax.error("give <p> apple <random-num:0,99999999999>").isPresent());
        assertTrue(CommandSyntax.error("say <random-num:-9223372036854775808,9223372036854775807>").isPresent());
    }

    @Test
    void randomPickRules() {
        assertTrue(CommandSyntax.error("give <p> <random-pick:apple, bread>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> <random-pick:\"golden apple\", bread>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> <random-pick:>").isPresent());
        assertTrue(CommandSyntax.error("give <p> <random-pick>").isPresent());
        assertTrue(CommandSyntax.error("give <p> <random-pick:\"oops, 'oops>").isPresent());
    }

    @Test
    void invalidPickItemAmongValidWarnsOnly() {
        String command = "give <p> <random-pick:apple, \"oops>";
        assertTrue(CommandSyntax.error(command).isEmpty());
        assertEquals(1, CommandSyntax.warnings(command).size());
    }

    @Test
    void allPlayersFilterWarnsWhenNotLetters() {
        assertTrue(CommandSyntax.warnings("tp <all-players:HUNTER> <p>").isEmpty());
        assertEquals(1, CommandSyntax.warnings("tp <all-players:HUNTER 1> <p>").size());
    }

    @Test
    void nestedTagsValidateInsideOut() {
        assertTrue(CommandSyntax.error("give <p> <random-pick:coal <random-num:4,12>, diamond>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> <random-pick:coal <random-num:4>, diamond>").isPresent());
    }

    @Test
    void fatalErrorSuppressesWarnings() {
        assertTrue(CommandSyntax.warnings("give <bogus").isEmpty());
    }
}
