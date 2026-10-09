package com.jruk8.jmanhunt.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Shared max-health ledger: totals, heals, dirty detection, zero death. */
class MaxHealthServiceTest {

    private record Fixture(MaxHealthService service, GameManager game, Player player, UUID id,
            AttributeInstance attribute, List<String> warnings) {
    }

    private static Fixture fixture(double currentMax, double currentHealth) {
        GameManager game = mock(GameManager.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alex");
        when(player.getHealth()).thenReturn(currentHealth);
        AttributeInstance attribute = mock(AttributeInstance.class);
        double[] applied = {currentMax};
        when(attribute.getValue()).thenAnswer(call -> applied[0]);
        doAnswer(call -> {
            applied[0] = call.getArgument(0);
            return null;
        }).when(attribute).setBaseValue(anyDouble());
        when(game.instanceOf(id)).thenReturn(Optional.empty());
        List<String> warnings = new ArrayList<>();
        return new Fixture(new MaxHealthService(game, warnings::add, ignored -> attribute),
                game, player, id, attribute, warnings);
    }

    @Test
    void totalIsBasePlusEveryId() {
        Fixture fixture = fixture(20.0, 20.0);

        assertEquals(20.0, fixture.service().totalFor(fixture.id()), 0.0);
        assertTrue(fixture.service().setContribution(fixture.player(), "a", 4.0));
        assertTrue(fixture.service().modifyContribution(fixture.player(), "b", -2.0));

        assertEquals(22.0, fixture.service().totalFor(fixture.id()), 0.0);
        assertEquals(4.0, fixture.service().getContribution(fixture.id(), "a"), 0.0);
        assertEquals(-2.0, fixture.service().getContribution(fixture.id(), "b"), 0.0);
        assertEquals(0.0, fixture.service().getContribution(fixture.id(), "missing"), 0.0);
        verify(fixture.attribute()).setBaseValue(24.0);
        verify(fixture.attribute()).setBaseValue(22.0);
        assertTrue(fixture.warnings().isEmpty(), fixture.warnings().toString());
    }

    @Test
    void setOverwritesAndClearDropsOneOrAll() {
        Fixture fixture = fixture(20.0, 20.0);

        fixture.service().setContribution(fixture.player(), "a", 4.0);
        fixture.service().setContribution(fixture.player(), "a", 10.0);
        assertEquals(30.0, fixture.service().totalFor(fixture.id()), 0.0);

        fixture.service().setContribution(fixture.player(), "b", 2.0);
        fixture.service().clearContribution(fixture.player(), "a");
        assertEquals(22.0, fixture.service().totalFor(fixture.id()), 0.0);

        fixture.service().clearContribution(fixture.player(), null);
        assertEquals(20.0, fixture.service().totalFor(fixture.id()), 0.0);
        verify(fixture.attribute()).setBaseValue(20.0);
    }

    @Test
    void increaseHealsByDeltaClampedToTotal() {
        Fixture fixture = fixture(20.0, 15.0);

        fixture.service().setContribution(fixture.player(), "boost", 4.0);

        verify(fixture.player()).setHealth(19.0);
    }

    @Test
    void increaseClampsOverflowHealth() {
        Fixture fixture = fixture(20.0, 23.0);

        fixture.service().setContribution(fixture.player(), "boost", 4.0);

        verify(fixture.player()).setHealth(24.0);
    }

    @Test
    void decreaseLeavesCurrentHealthAlone() {
        Fixture fixture = fixture(20.0, 20.0);
        fixture.service().setContribution(fixture.player(), "boost", 4.0);
        clearInvocations(fixture.player());

        fixture.service().setContribution(fixture.player(), "boost", -4.0);

        verify(fixture.attribute()).setBaseValue(16.0);
        verify(fixture.player(), never()).setHealth(anyDouble());
    }

    @Test
    void foreignAttributeEditWarnsOnce() {
        Fixture fixture = fixture(40.0, 20.0);

        fixture.service().setContribution(fixture.player(), "boost", 4.0);
        fixture.service().modifyContribution(fixture.player(), "boost", 1.0);

        assertEquals(1, fixture.warnings().size());
        assertTrue(fixture.warnings().get(0).contains("Dirty Max HP Hack"),
                fixture.warnings().toString());
        assertTrue(fixture.warnings().get(0).contains("configuration/modifiers/dirty-max-hp"),
                fixture.warnings().toString());
        verify(fixture.attribute()).setBaseValue(24.0);
    }

    @Test
    void zeroTotalEliminatesAndClears() {
        Fixture fixture = fixture(20.0, 20.0);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(fixture.game().instanceOf(fixture.id())).thenReturn(Optional.of(instance));

        assertTrue(fixture.service().setContribution(fixture.player(), "curse", -20.0));

        verify(fixture.game()).eliminateAnyRole(7L, "Alex", "their max health reached 0");
        verify(fixture.attribute(), never()).setBaseValue(anyDouble());
        assertEquals(0.0, fixture.service().getContribution(fixture.id(), "curse"), 0.0);
        assertEquals(20.0, fixture.service().totalFor(fixture.id()), 0.0);
    }

    @Test
    void negativeTotalEliminatesWithoutMatch() {
        Fixture fixture = fixture(20.0, 20.0);

        assertTrue(fixture.service().setContribution(fixture.player(), "curse", -25.0));

        verify(fixture.game(), never()).eliminateAnyRole(any(Long.class), any(), any());
        assertEquals(20.0, fixture.service().totalFor(fixture.id()), 0.0);
    }

    @Test
    void inertMissesEveryOp() {
        MaxHealthService inert = MaxHealthService.inert();
        Player player = mock(Player.class);

        assertFalse(inert.setContribution(player, "a", 1.0));
        assertFalse(inert.modifyContribution(player, "a", 1.0));
        assertFalse(inert.clearContribution(player, null));
        assertEquals(0.0, inert.getContribution(UUID.randomUUID(), "a"), 0.0);
    }
}
