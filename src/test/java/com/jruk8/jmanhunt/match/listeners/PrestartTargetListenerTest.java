package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetEvent;
import org.junit.jupiter.api.Test;

/** Pre-start targeting gate decisions without a Bukkit server. */
class PrestartTargetListenerTest {

    private record Fixture(GameManager game, PrestartTargetListener listener, Player player) {
    }

    private static Fixture fixture() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        GameManager game = mock(GameManager.class);
        return new Fixture(game, new PrestartTargetListener(game), player);
    }

    private static EntityTargetEvent targeted(Entity mob, Entity target) {
        return new EntityTargetEvent(mob, target, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
    }

    @Test
    void shieldsActivePlayersBeforeBegin() {
        Fixture fixture = fixture();
        GameInstance instance = mock(GameInstance.class);
        when(instance.begun()).thenReturn(false);
        when(fixture.game().instanceOf(fixture.player().getUniqueId()))
                .thenReturn(Optional.of(instance));
        EntityTargetEvent event = targeted(mock(Entity.class), fixture.player());

        fixture.listener().onTarget(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void passesThroughAfterBegin() {
        Fixture fixture = fixture();
        GameInstance instance = mock(GameInstance.class);
        when(instance.begun()).thenReturn(true);
        when(fixture.game().instanceOf(fixture.player().getUniqueId()))
                .thenReturn(Optional.of(instance));
        EntityTargetEvent event = targeted(mock(Entity.class), fixture.player());

        fixture.listener().onTarget(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void passesThroughOutsideMatches() {
        Fixture fixture = fixture();
        when(fixture.game().instanceOf(fixture.player().getUniqueId()))
                .thenReturn(Optional.empty());
        EntityTargetEvent event = targeted(mock(Entity.class), fixture.player());

        fixture.listener().onTarget(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void ignoresNonPlayerTargets() {
        Fixture fixture = fixture();
        EntityTargetEvent event = targeted(mock(Entity.class), mock(Entity.class));

        fixture.listener().onTarget(event);

        assertFalse(event.isCancelled());
    }
}
