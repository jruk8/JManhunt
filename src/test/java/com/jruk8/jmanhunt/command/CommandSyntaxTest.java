package com.jruk8.jmanhunt.command;

import java.util.List;
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
    }

    @Test
    void durationTagIsCompassOnly() {
        String command = "effect give <p> speed <duration> 1";
        assertTrue(CommandSyntax.error(command).isEmpty());
        assertEquals(List.of("Unknown tag '<duration>', left untouched at runtime."),
                CommandSyntax.warnings(command));
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

    @Test
    void extendedTagArityPasses() {
        assertTrue(CommandSyntax.error("say <id>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> apple <min:8,10>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> apple <max:8,10>").isEmpty());
        assertTrue(CommandSyntax.error("give <p> apple <clamp:8,1,10>").isEmpty());
        assertTrue(CommandSyntax.error("say <gmessage:\"hi\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <pmessage:yo> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gsound:block.stone.break> done").isEmpty());
        assertTrue(CommandSyntax.error("say <psound:block.stone.break,0.5,2> done").isEmpty());
        assertTrue(CommandSyntax.error("say <if:\"1 == 1\",\"y\",\"n\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <if:\"7 <= 5\",\"y\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <if:\"<random-num:1,6> == 5\",\"y\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <if:\"<flag:a> == <flag:a>\",\"y\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <if:\"<if:1==1,y,n> == y\",\"Y\"> done").isEmpty());
    }

    @Test
    void statAndFlagTagsPass() {
        assertTrue(CommandSyntax.error("say <pstat:\"<p>\",\"health\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <pstat:Steve,HUNGER> done").isEmpty());
        assertTrue(CommandSyntax.error("say <pstat:Steve,mobs-killed> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gstat:duration> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gstat:\"daytime\"> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gflag:phase> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gflag:phase,one> done").isEmpty());
        assertTrue(CommandSyntax.error("say <pflag:\"cooldown\",732> done").isEmpty());
        assertTrue(CommandSyntax.error("say <lflag:x> done").isEmpty());
        assertTrue(CommandSyntax.error("say <placeholder:jmanhunt_game_kills_this_session> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <placeholder:\"some_key\"> done").isEmpty());
    }

    @Test
    void statAndFlagTagsFail() {
        assertTrue(CommandSyntax.error("say <pstat:Steve> done").isPresent());
        assertTrue(CommandSyntax.error("say <pstat:Steve,heath> done").isPresent());
        assertTrue(CommandSyntax.error("say <gstat:> done").isPresent());
        assertTrue(CommandSyntax.error("say <gstat:uptime> done").isPresent());
        assertTrue(CommandSyntax.error("say <gstat:duration,daytime> done").isPresent());
        assertTrue(CommandSyntax.error("say <gstat:<random-pick:duration,daytime>> done").isPresent());
        assertTrue(CommandSyntax.error("say <gflag:> done").isPresent());
        assertTrue(CommandSyntax.error("say <gflag:a,b,c> done").isPresent());
        assertTrue(CommandSyntax.error("say <pflag:\"  \",1> done").isPresent());
        assertTrue(CommandSyntax.error("say <placeholder:a,b> done").isPresent());
        assertTrue(CommandSyntax.error("say <placeholder:> done").isPresent());
    }

    @Test
    void extendedTagArityFails() {
        assertTrue(CommandSyntax.error("say <id:x>").isPresent());
        assertTrue(CommandSyntax.error("give <p> apple <min:8>").isPresent());
        assertTrue(CommandSyntax.error("give <p> apple <clamp:8,1>").isPresent());
        assertTrue(CommandSyntax.error("say <gmessage> done").isPresent());
        assertTrue(CommandSyntax.error("say <gsound> done").isPresent());
        assertTrue(CommandSyntax.error("say <psound:a,b,c,d> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"1 == 1\"> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"abc\",\"y\"> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"7<=7\",\"y\"> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"7 <=7\",\"y\"> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"<flag:a>\",\"y\"> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"<papi:x==y>\",\"y\"> done").isPresent());
        assertTrue(CommandSyntax.error("say <if:\"7 <= 5\" done").isPresent());
    }

    @Test
    void loneExitPassesButMisuseFails() {
        assertTrue(CommandSyntax.error("exit").isEmpty());
        assertTrue(CommandSyntax.error("  exit  ").isEmpty());
        assertTrue(CommandSyntax.error("exit give <p> apple").isPresent());
        assertTrue(CommandSyntax.unknownRoot("exit", Set.of("give")).isEmpty());
    }
}
