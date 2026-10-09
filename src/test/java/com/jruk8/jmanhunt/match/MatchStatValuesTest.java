package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Player stats read live, except -1 for assigned but inactive players. */
class MatchStatValuesTest {

    private record Fixture(MatchStatValues stats, Player player, UUID id,
            GameInstance instance, StatsManager statsManager, MatchStore store) {
    }

    private static Fixture fixture(boolean active) {
        StatsManager statsManager = mock(StatsManager.class);
        MatchStore store = mock(MatchStore.class);
        GameInstance instance = mock(GameInstance.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getHealth()).thenReturn(15.5);
        when(player.getFoodLevel()).thenReturn(12);
        when(player.getLevel()).thenReturn(3);
        when(store.instance(7L)).thenReturn(Optional.of(instance));
        when(instance.matchId()).thenReturn(7L);
        when(instance.assignedPlayerIds()).thenReturn(Set.of(id));
        when(instance.isActive(id)).thenReturn(active);
        Stats slice = new Stats();
        slice.mobsKilled = 5;
        slice.achievementsGained = 2;
        when(store.instances()).thenReturn(Map.of(7L, instance));
        when(statsManager.matchStats(7L, id)).thenReturn(Optional.of(slice));
        AttributeInstance max = mock(AttributeInstance.class);
        when(max.getValue()).thenReturn(30.0);
        return new Fixture(new MatchStatValues(statsManager, store, 7L, ignored -> max),
                player, id, instance, statsManager, store);
    }

    @Test
    void eliminatedReadsMinusOneForEveryPlayerKey() {
        Fixture fixture = fixture(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(fixture.player());

            for (String key : List.of("health", "hunger", "exp-level", "max-health",
                    "mobs-killed", "achievements-gained")) {
                assertEquals(Optional.of("-1"), fixture.stats().player("Alex", key), key);
            }
        }
    }

    @Test
    void activeReadsLiveValues() {
        Fixture fixture = fixture(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(fixture.player());

            assertEquals(Optional.of("15.5"), fixture.stats().player("Alex", "health"));
            assertEquals(Optional.of("12"), fixture.stats().player("Alex", "hunger"));
            assertEquals(Optional.of("3"), fixture.stats().player("Alex", "exp-level"));
            assertEquals(Optional.of("30"), fixture.stats().player("Alex", "max-health"));
            assertEquals(Optional.of("5"), fixture.stats().player("Alex", "mobs-killed"));
            assertEquals(Optional.of("2"),
                    fixture.stats().player("Alex", "achievements-gained"));
        }
    }

    @Test
    void unassignedReadsLiveValues() {
        Fixture fixture = fixture(false);
        when(fixture.instance().assignedPlayerIds()).thenReturn(Set.of());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(fixture.player());

            assertEquals(Optional.of("15.5"), fixture.stats().player("Alex", "health"));
        }
    }

    @Test
    void offlineReadsEmpty() {
        Fixture fixture = fixture(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(null);

            assertEquals(Optional.empty(), fixture.stats().player("Alex", "health"));
        }
    }

    @Test
    void globalsIgnoreElimination() {
        Fixture fixture = fixture(false);
        when(fixture.instance().elapsedSeconds(anyLong())).thenReturn(42L);
        World world = mock(World.class);
        when(world.getTime()).thenReturn(6000L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));

            assertEquals(Optional.of("42"), fixture.stats().global("duration"));
            assertEquals(Optional.of("6000"), fixture.stats().global("daytime"));
        }
    }
}
