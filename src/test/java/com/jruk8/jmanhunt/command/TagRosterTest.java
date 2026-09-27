package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roster tags over a fake roster: active lists, roles, locations. */
class TagRosterTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final Map<String, String> roles = new HashMap<>();
        final Map<String, List<String>> actives = new HashMap<>();
        final Map<String, Location> locations = new HashMap<>();
        final RosterValues roster = new RosterValues() {
            @Override
            public Optional<String> roleOf(String playerName) {
                return Optional.ofNullable(roles.get(playerName));
            }

            @Override
            public List<String> activePlayers(String role) {
                return actives.getOrDefault(role, List.of());
            }

            @Override
            public Optional<Location> locationOf(String playerName) {
                return Optional.ofNullable(locations.get(playerName));
            }
        };

        TagContext context(long matchId) {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                    "roster", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    matchId, new TagBackends(StatValues.inert(), new FlagStore(),
                            (text, name) -> text, roster));
        }

        String replace(String command, long matchId) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context(matchId));
        }
    }

    @Test
    void activePlayersFormatsEligibleNames() {
        Fixture fixture = new Fixture();
        fixture.actives.put("HUNTER", List.of("Alice", "Bob"));

        assertEquals("say [Alice, Bob]!", fixture.replace("say <active-players:HUNTER>!", 7L));
        assertEquals("say [Alice, Bob]!", fixture.replace("say <active-players:hunter>!", 7L));
        assertEquals("say []!", fixture.replace("say <active-players:SPEEDRUNNER>!", 7L));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void activePlayersRejectsBadRoleAndMatchless() {
        Fixture fixture = new Fixture();

        assertEquals("null", fixture.replace("<active-players:REF>", 7L));
        assertEquals("null", fixture.replace("<active-players:>", 7L));
        assertEquals("null", fixture.replace("<active-players:HUNTER>", TagContext.NO_MATCH));
        assertEquals(3, fixture.warnings.size());
    }

    @Test
    void proleReturnsParticipantRoles() {
        Fixture fixture = new Fixture();
        fixture.roles.put("Steve", "SPEEDRUNNER");
        fixture.roles.put("Alice", "HUNTER");
        fixture.roles.put("Moe", "SPECTATOR");

        assertEquals("SPEEDRUNNER", fixture.replace("<prole:Steve>", 7L));
        assertEquals("HUNTER", fixture.replace("<prole:Alice>", 7L));
        assertEquals("null", fixture.replace("<prole:Moe>", 7L));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void proleWarnsOnUnknownAndMatchless() {
        Fixture fixture = new Fixture();

        assertEquals("null", fixture.replace("<prole:Ghost>", 7L));
        assertEquals("null", fixture.replace("<prole:>", 7L));
        assertEquals("null", fixture.replace("<prole:Steve>", TagContext.NO_MATCH));
        assertEquals(3, fixture.warnings.size());
    }
}
