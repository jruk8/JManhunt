package com.jruk8.jmanhunt.command;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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

    @Test
    void unknownRootAcceptsKnownSlashAliasAndNamespace() {
        Set<String> roots = Set.of("give", "effect", "mhelp");
        assertTrue(CommandSyntax.unknownRoot("give <p> apple", roots).isEmpty());
        assertTrue(CommandSyntax.unknownRoot("/give <p> apple", roots).isEmpty());
        assertTrue(CommandSyntax.unknownRoot("//give <p> apple", roots).isEmpty());
        assertTrue(CommandSyntax.unknownRoot("mhelp", roots).isEmpty());
        assertTrue(CommandSyntax.unknownRoot("minecraft:give <p> apple", roots).isEmpty());
        assertTrue(CommandSyntax.unknownRoot("Give <p> apple", roots).isEmpty());
    }

    @Test
    void unknownRootRejectsUnknownNamingToken() {
        assertEquals(Optional.of("Unknown command 'asd'."),
                CommandSyntax.unknownRoot("asd asd asd asd", Set.of("give")));
        assertEquals(Optional.of("Unknown command 'asd'."),
                CommandSyntax.unknownRoot("/asd", Set.of("give")));
    }

    @Test
    void unknownRootSkipsPlaceholderBuiltRoots() {
        assertTrue(CommandSyntax
                .unknownRoot("<random-pick:give,effect> <p> apple", Set.of("give")).isEmpty());
    }

    @Test
    void giveItemCheckAcceptsKnownPlaceholderAndNonGive() {
        Predicate<String> known = token -> token.equalsIgnoreCase("golden_apple")
                || token.equalsIgnoreCase("minecraft:golden_apple");
        Set<String> names = Set.of("golden_apple", "diamond_sword");
        assertTrue(CommandSyntax.giveItemCheck("give <p> golden_apple", known, names).isEmpty());
        assertTrue(CommandSyntax
                .giveItemCheck("minecraft:give <p> golden_apple", known, names).isEmpty());
        assertTrue(CommandSyntax.giveItemCheck("/give <p> golden_apple", known, names).isEmpty());
        assertTrue(CommandSyntax.giveItemCheck("give <p> <random-item>", known, names).isEmpty());
        assertTrue(CommandSyntax.giveItemCheck("effect give <p> slowness", known, names).isEmpty());
    }

    @Test
    void giveItemTypoFailsWithHint() {
        Predicate<String> known = token -> token.equalsIgnoreCase("golden_apple");
        Set<String> names = Set.of("golden_apple", "diamond_sword");
        assertEquals(Optional.of("Unknown item 'gulden_apple'. Did you mean 'golden_apple'?"),
                CommandSyntax.giveItemCheck("give <p> gulden_apple", known, names));
    }

    @Test
    void giveItemFarMissFailsWithoutHint() {
        Predicate<String> known = token -> false;
        Set<String> names = Set.of("golden_apple", "diamond_sword");
        assertEquals(Optional.of("Unknown item 'zzzqqq'."),
                CommandSyntax.giveItemCheck("give <p> zzzqqq", known, names));
    }
}
