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

/** Player tags over fake backends: states, ground, titles, slots. */
class TagPlayersTest {

    private record Titled(String player, String title, String subtitle, double stay, double in,
            double out) {
    }

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final Map<String, Boolean> states = new HashMap<>();
        final Map<String, String> ground = new HashMap<>();
        final Map<String, RosterValues.SlotContent> slots = new HashMap<>();
        final Map<String, Integer> maxStacks = new HashMap<>(Map.of("DIAMOND_HELMET", 1,
                "IRON_HELMET", 1, "GOLDEN_APPLE", 64, "STONE", 64));
        final List<Titled> titles = new ArrayList<>();
        final RosterValues roster = new RosterValues() {
            @Override
            public Optional<String> roleOf(String playerName) {
                return Optional.empty();
            }

            @Override
            public List<String> activePlayers(String role) {
                return List.of();
            }

            @Override
            public Optional<Location> locationOf(String playerName) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> playerState(String playerName, String state) {
                if (!playerName.equalsIgnoreCase("Steve")) {
                    return Optional.empty();
                }
                return Optional.of(states.getOrDefault("Steve|" + state, false));
            }

            @Override
            public Optional<String> standingOn(String playerName) {
                if (!playerName.equalsIgnoreCase("Steve")) {
                    return Optional.empty();
                }
                return Optional.ofNullable(ground.get("Steve"));
            }

            @Override
            public Optional<RosterValues.SlotContent> slotItem(String playerName,
                    RosterValues.InventorySlot slot) {
                if (!playerName.equalsIgnoreCase("Steve")) {
                    return Optional.empty();
                }
                return Optional.ofNullable(slots.get(slotKey(slot)));
            }
        };
        final PlayerSinks sinks = new PlayerSinks() {
            @Override
            public boolean message(String playerName, String text) {
                return true;
            }

            @Override
            public boolean sound(String playerName, String soundId, float pitch, float volume) {
                return true;
            }

            @Override
            public boolean title(String playerName, String title, String subtitle,
                    double staySeconds, double inSeconds, double outSeconds) {
                if (!playerName.equalsIgnoreCase("Steve")) {
                    return false;
                }
                titles.add(new Titled(playerName, title, subtitle, staySeconds, inSeconds,
                        outSeconds));
                return true;
            }

            @Override
            public boolean setSlot(String playerName, RosterValues.InventorySlot slot,
                    String materialKey, int qty) {
                if (!playerName.equalsIgnoreCase("Steve")) {
                    return false;
                }
                String material = TagItems.normalizeMaterialKey(materialKey);
                Integer max = maxStacks.get(material);
                if (max == null) {
                    return false;
                }
                slots.put(slotKey(slot), new RosterValues.SlotContent(material,
                        Math.min(Math.max(qty, 1), max)));
                return true;
            }
        };

        private static String slotKey(RosterValues.InventorySlot slot) {
            if (slot instanceof RosterValues.InventorySlot.Named named) {
                return named.name();
            }
            return "#" + ((RosterValues.InventorySlot.Index) slot).index();
        }

        TagContext context() {
            return TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve", List.of(),
                    new Random(31), warnings::add), "players"),
                    TagContext.TagSinks.simple(warnings::add, warnings::add,
                            (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                            ModifierTagScope.match("Steve", List.of(), new Random(31), warnings::add)),
                    TagContext.TagRole.silent(),
                    TagContext.TagMatch.simple(7L,
                            new TagBackends(StatValues.inert(), new FlagStore(), (text, name) -> text, roster, sinks),
                            (player, reason) -> { }, (role, reason) -> { }));
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void stateMatrix() {
        Fixture fixture = new Fixture();
        fixture.states.put("Steve|SNEAK", true);
        fixture.states.put("Steve|GROUND", true);
        TagContext context = fixture.context();

        assertEquals("true", fixture.replace("<pstate:Steve,SNEAK>", context));
        assertEquals("true", fixture.replace("<pstate:Steve,sneak>", context));
        assertEquals("false", fixture.replace("<pstate:Steve,SPRINT>", context));
        assertEquals("false", fixture.replace("<pstate:Steve,GLIDE>", context));
        assertEquals("false", fixture.replace("<pstate:Steve,SWIM>", context));
        assertEquals("true", fixture.replace("<pstate:Steve,GROUND>", context));
        assertEquals("null", fixture.replace("<pstate:Ghost,SNEAK>", context));
        assertEquals("null", fixture.replace("<pstate:Steve,FLY>", context));
        assertEquals("null", fixture.replace("<pstate:,SNEAK>", context));
        assertEquals("null", fixture.replace("<pstate:Steve>", context));
        assertEquals(3, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("SNEAK, SPRINT, GLIDE, SWIM, GROUND"),
                fixture.warnings.toString());
    }

    @Test
    void standingMaterials() {
        Fixture fixture = new Fixture();
        fixture.ground.put("Steve", "STONE");
        TagContext context = fixture.context();

        assertEquals("STONE", fixture.replace("<pstandingon:Steve>", context));
        assertEquals("null", fixture.replace("<pstandingon:Ghost>", context));
        assertEquals("null", fixture.replace("<pstandingon:>", context));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void titleTimingsDefaultTrailing() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<ptitle:Steve,Hi,Sub>", context));
        assertEquals("", fixture.replace("<ptitle:Steve,Hi,Sub,3>", context));
        assertEquals("", fixture.replace("<ptitle:Steve,Hi,Sub,3,0.5>", context));
        assertEquals("", fixture.replace("<ptitle:Steve,Hi,Sub,3,0.5,0.6>", context));
        assertEquals(List.of(
                new Titled("Steve", "Hi", "Sub", 2.0, 0.4, 0.4),
                new Titled("Steve", "Hi", "Sub", 3.0, 0.4, 0.4),
                new Titled("Steve", "Hi", "Sub", 3.0, 0.5, 0.4),
                new Titled("Steve", "Hi", "Sub", 3.0, 0.5, 0.6)), fixture.titles);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void titleRejectsBadTimingAndOffline() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<ptitle:Steve,Hi,Sub,soon>", context));
        assertEquals("null", fixture.replace("<ptitle:Steve,Hi,Sub,3,-1>", context));
        assertEquals("null", fixture.replace("<ptitle:Steve,Hi,Sub,1,2,3,4>", context));
        assertEquals("null", fixture.replace("<ptitle:Steve,Hi>", context));
        assertEquals("null", fixture.replace("<ptitle:Ghost,Hi,Sub>", context));
        assertEquals("null", fixture.replace("<ptitle:,Hi,Sub>", context));
        assertEquals(6, fixture.warnings.size());
        assertTrue(fixture.titles.isEmpty(), fixture.titles.toString());
    }

    @Test
    void slotGetReadsNamesAndIndices() {
        Fixture fixture = new Fixture();
        fixture.slots.put("HELMET",
                new RosterValues.SlotContent("DIAMOND_HELMET", 1));
        fixture.slots.put("#0", new RosterValues.SlotContent("STONE", 64));
        TagContext context = fixture.context();

        assertEquals("[DIAMOND_HELMET, 1]",
                fixture.replace("<pslot:Steve,helmet>", context));
        assertEquals("[STONE, 64]", fixture.replace("<pslot:Steve,0>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,boots>", context));
        assertEquals("null", fixture.replace("<pslot:Ghost,helmet>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,hat>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,41>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,-1>", context));
        assertEquals(3, fixture.warnings.size());
    }

    @Test
    void slotSetRoundTripsAndClamps() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<pslot:Steve,helmet>", context));
        assertEquals("", fixture.replace("<pslot:Steve,helmet,[IRON_HELMET,1]>", context));
        assertEquals("[IRON_HELMET, 1]", fixture.replace("<pslot:Steve,helmet>", context));
        assertEquals("", fixture.replace("<pslot:Steve,helmet,GOLDEN_APPLE>", context));
        assertEquals("[GOLDEN_APPLE, 1]", fixture.replace("<pslot:Steve,helmet>", context));
        assertEquals("", fixture.replace("<pslot:Steve,helmet,[GOLDEN_APPLE,99]>", context));
        assertEquals("[GOLDEN_APPLE, 64]", fixture.replace("<pslot:Steve,helmet>", context));
        assertEquals("", fixture.replace("<pslot:Steve,helmet,[STONE,0]>", context));
        assertEquals("[STONE, 1]", fixture.replace("<pslot:Steve,helmet>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void slotSetRejectsWithoutChange() {
        Fixture fixture = new Fixture();
        fixture.slots.put("HELMET",
                new RosterValues.SlotContent("DIAMOND_HELMET", 1));
        TagContext context = fixture.context();

        assertEquals("null",
                fixture.replace("<pslot:Steve,helmet,UNOBTAINIUM>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,helmet,[STONE,many]>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,helmet,[STONE]>", context));
        assertEquals("null", fixture.replace("<pslot:Steve,helmet,[STONE,1,2]>", context));
        assertEquals("null", fixture.replace("<pslot:Ghost,helmet,[STONE,1]>", context));
        assertEquals("[DIAMOND_HELMET, 1]",
                fixture.replace("<pslot:Steve,helmet>", context));
        assertEquals(5, fixture.warnings.size());
    }
}
