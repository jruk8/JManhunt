package com.jruk8.jmanhunt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TagTeleportTest {

    private record Teleported(String audience, TagLocations.TeleportRequest target) {
    }

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<Teleported> global = new ArrayList<>();
        final List<Teleported> players = new ArrayList<>();
        final List<Teleported> roles = new ArrayList<>();

        TagContext context() {
            ModifierTagScope scope = ModifierTagScope.match("Steve", List.of(),
                    new Random(11), warnings::add);
            PlayerSinks sinks = new PlayerSinks() {
                @Override
                public boolean message(String playerName, String text) {
                    return true;
                }

                @Override
                public boolean sound(String playerName, String soundId, float pitch,
                        float volume) {
                    return true;
                }

                @Override
                public boolean teleport(String playerName,
                        TagLocations.TeleportRequest target) {
                    if (!playerName.equalsIgnoreCase("Steve")) {
                        return false;
                    }
                    players.add(new Teleported(playerName, target));
                    return true;
                }
            };
            return TagContext.run(new TagContext.TagIdentity(scope, "teleport"),
                    new TagContext.TagSinks(warnings::add, warnings::add,
                            (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                            (line, provenance) -> { },
                            target -> global.add(new Teleported("all", target))),
                    new TagContext.TagRole((role, text) -> { }, (role, id, pitch, volume) -> { },
                            (role, target) -> roles.add(new Teleported(role, target))),
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
    void globalTeleportDeliversTarget() {
        Fixture fixture = new Fixture();
        assertEquals("",
                fixture.replace("<gteleport:[100, 64, -30, world, 10, 20]>"));
        assertEquals(1, fixture.global.size());
        TagLocations.TeleportRequest target = fixture.global.get(0).target();
        assertEquals(new TagLocations.TeleportRequest(100, 64, -30, "world", 10.0f, 20.0f),
                target);
        assertTrue(fixture.warnings.isEmpty());
    }

    @Test
    void playerTeleportDeliversAndOfflineWarns() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<pteleport:Steve,[0, 64, 0, nether]>"));
        assertEquals(1, fixture.players.size());
        assertEquals(new TagLocations.TeleportRequest(0, 64, 0, "nether", null, null),
                fixture.players.get(0).target());
        assertTrue(fixture.warnings.isEmpty());

        assertEquals("", fixture.replace("<pteleport:Alex,[0, 64, 0, nether]>"));
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("is offline"));
    }

    @Test
    void roleTeleportDeliversAndBadRoleWarns() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<rteleport:hunter,[0, 64, 0, end, 0, 90]>"));
        assertEquals(1, fixture.roles.size());
        assertEquals("HUNTER", fixture.roles.get(0).audience());
        assertTrue(fixture.warnings.isEmpty());

        assertEquals("", fixture.replace("<rteleport:villager,[0, 64, 0, end]>"));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void malformedLocationsWarnAndSkip() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gteleport:[0, 64, 0]>"));
        assertEquals("", fixture.replace("<gteleport:[0, 64, 0, world, 10]>"));
        assertEquals("", fixture.replace("<gteleport:[nope, 64, 0, world]>"));
        assertEquals("", fixture.replace("<gteleport:hello>"));
        assertEquals("", fixture.replace("<pteleport:Steve>"));
        assertEquals(5, fixture.warnings.size());
        assertTrue(fixture.global.isEmpty());
        assertTrue(fixture.players.isEmpty());
    }

    @Test
    void editTimeValidatesTeleportShapes() {
        assertTrue(CommandSyntax.error("say <gteleport:[0,64,0,world]> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gteleport:[0,64,0,world,10,20]> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <pteleport:Alex,[0,64,0,world]> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <rteleport:hunter,[0,64,0,world]> done")
                .isEmpty());
        assertTrue(CommandSyntax.error("say <gteleport:<plocation:Alex>> done").isEmpty());
        assertTrue(CommandSyntax.error("say <gteleport:[0,64,0]> done").isPresent());
        assertTrue(CommandSyntax.error("say <gteleport:hello> done").isPresent());
        assertTrue(CommandSyntax.error("say <pteleport:Alex> done").isPresent());
        assertTrue(CommandSyntax.error("say <rteleport:villager,[0,64,0,world]> done")
                .isPresent());
    }
}
