package com.jruk8.jmanhunt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TagSinksArrayTest {

    private record Played(String audience, String id, float pitch, float volume) {
    }

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> globalMessages = new ArrayList<>();
        final List<Played> globalSounds = new ArrayList<>();
        final List<String> playerMessages = new ArrayList<>();
        final List<Played> playerSounds = new ArrayList<>();
        final List<String> roleMessages = new ArrayList<>();
        final List<Played> roleSounds = new ArrayList<>();

        TagContext context() {
            ModifierTagScope scope = ModifierTagScope.match("Steve", List.of(),
                    new Random(7), warnings::add);
            PlayerSinks sinks = new PlayerSinks() {
                @Override
                public boolean message(String playerName, String text) {
                    if (!playerName.equalsIgnoreCase("Steve")) {
                        return false;
                    }
                    playerMessages.add(text);
                    return true;
                }

                @Override
                public boolean sound(String playerName, String soundId, float pitch,
                        float volume) {
                    if (!playerName.equalsIgnoreCase("Steve")) {
                        return false;
                    }
                    playerSounds.add(new Played(playerName, soundId, pitch, volume));
                    return true;
                }
            };
            return TagContext.run(new TagContext.TagIdentity(scope, "arrays"),
                    TagContext.TagSinks.simple(globalMessages::add, globalMessages::add,
                            (id, pitch, volume) ->
                                    globalSounds.add(new Played("all", id, pitch, volume)),
                            (id, pitch, volume) ->
                                    globalSounds.add(new Played("self", id, pitch, volume)),
                            scope),
                    new TagContext.TagRole((role, text) -> roleMessages.add(role + ":" + text),
                            (role, id, pitch, volume) ->
                                    roleSounds.add(new Played(role, id, pitch, volume))),
                    TagContext.TagMatch.simple(7L,
                            new TagBackends(StatValues.inert(), new FlagStore(),
                                    PlaceholderResolver.inert(), RosterValues.inert(), sinks),
                            (player, reason) -> { }, (role, reason) -> { }));
        }

        String replace(String command) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context());
        }
    }

    @Test
    void soundArrayPlaysOnTrueAndOne() {
        Fixture fixture = new Fixture();
        assertEquals("xy", fixture.replace("x<gsound:[true,block.stone.break,0.5,2]>y"));
        assertEquals("xy", fixture.replace("x<gsound:[1,block.stone.break]>y"));
        assertEquals("xy", fixture.replace("x<gsound:[TRUE,block.stone.break]>y"));
        assertEquals(List.of(new Played("all", "block.stone.break", 0.5f, 2.0f),
                new Played("all", "block.stone.break", 1.0f, 1.0f),
                new Played("all", "block.stone.break", 1.0f, 1.0f)),
                fixture.globalSounds);
        assertTrue(fixture.warnings.isEmpty());
    }

    @Test
    void soundArraySkipsSilentlyWhenGated() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gsound:[false,block.stone.break]>"));
        assertEquals("", fixture.replace("<gsound:[0,block.stone.break]>"));
        assertEquals("", fixture.replace("<gsound:[,block.stone.break]>"));
        assertEquals("", fixture.replace("<gsound:[yes,block.stone.break]>"));
        assertTrue(fixture.globalSounds.isEmpty());
        assertTrue(fixture.warnings.isEmpty());
    }

    @Test
    void soundArrayFallsBackOnBadNumbers() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gsound:[true,block.stone.break,nope,2]>"));
        assertEquals(List.of(new Played("all", "block.stone.break", 1.0f, 2.0f)),
                fixture.globalSounds);
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void soundArrayWrongArityWarns() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gsound:[true]>"));
        assertTrue(fixture.globalSounds.isEmpty());
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("array needs enabled plus an id"));
    }

    @Test
    void playerSoundArrayDeliversAndOfflineWarns() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<psound:Steve,[true,block.stone.break,0.5,2]>"));
        assertEquals(List.of(new Played("Steve", "block.stone.break", 0.5f, 2.0f)),
                fixture.playerSounds);
        assertTrue(fixture.warnings.isEmpty());

        assertEquals("", fixture.replace("<psound:Alex,[true,block.stone.break]>"));
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("is offline"));

        assertEquals("", fixture.replace("<psound:Alex,[false,block.stone.break]>"));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void roleSoundArrayDeliversAndBadRoleWarns() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<rsound:hunter,[1,block.stone.break]>"));
        assertEquals(List.of(new Played("HUNTER", "block.stone.break", 1.0f, 1.0f)),
                fixture.roleSounds);
        assertTrue(fixture.warnings.isEmpty());

        assertEquals("", fixture.replace("<rsound:villager,[true,block.stone.break]>"));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void messageArraysSendAndGate() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gmsg:[true,hello]>"));
        assertEquals("", fixture.replace("<gmsg:[false,hello]>"));
        assertEquals(List.of("hello"), fixture.globalMessages);

        assertEquals("", fixture.replace("<pmsg:Steve,[1,hi]>"));
        assertEquals("", fixture.replace("<pmsg:Alex,[true,hi]>"));
        assertEquals("", fixture.replace("<pmsg:Alex,[0,hi]>"));
        assertEquals(List.of("hi"), fixture.playerMessages);

        assertEquals("", fixture.replace("<rmsg:hunter,[true,yo]>"));
        assertEquals("", fixture.replace("<rmsg:hunter,[false,yo]>"));
        assertEquals(List.of("HUNTER:yo"), fixture.roleMessages);

        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("is offline"));
    }

    @Test
    void messageArrayKeepsQuotedCommas() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gmsg:[true,\"gg, well played\"]>"));
        assertEquals(List.of("gg, well played"), fixture.globalMessages);
        assertTrue(fixture.warnings.isEmpty());
    }

    @Test
    void individualFormsUnchanged() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gsound:block.stone.break,0.5,2>"));
        assertEquals("", fixture.replace("<pmsg:Steve,hello>"));
        assertEquals(List.of(new Played("all", "block.stone.break", 0.5f, 2.0f)),
                fixture.globalSounds);
        assertEquals(List.of("hello"), fixture.playerMessages);
        assertTrue(fixture.warnings.isEmpty());
    }

    @Test
    void editTimeAcceptsArrays() {
        assertTrue(CommandSyntax.error("say <gsound:[true,block.stone.break,0.5,2]> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <psound:Alex,[1,block.stone.break]> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <rsound:hunter,[true,block.stone.break]> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <gmsg:[true,hi]> done").isEmpty());
        assertTrue(CommandSyntax.error("say <pmsg:Alex,[1,hi]> done").isEmpty());
        assertTrue(CommandSyntax.error("say <rmsg:hunter,[true,hi]> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gsound:[true]> done").isPresent());
        assertTrue(CommandSyntax.error("say <gmsg:[true]> done").isPresent());
        assertTrue(CommandSyntax.error("say <rsound:villager,[true,block.stone.break]> done")
                .isPresent());
    }
}
