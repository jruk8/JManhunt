package com.jruk8.jmanhunt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mute-around-dispatch ordering for {@link QuietConsoleDispatch}.
 * The rule rides as a parameter (null here) so the tests never
 * initialize the server-backed GameRules constants.
 */
class QuietConsoleDispatchTest {

    @Test
    void dispatchMutesRestoresAndDelivers() {
        World world = mock(World.class);
        doReturn(Boolean.TRUE).when(world).getGameRuleValue((GameRule<Boolean>) null);
        List<String> delivered = new ArrayList<>();
        QuietConsoleDispatch.dispatch(world, null, "say hi", delivered::add);
        assertEquals(List.of("say hi"), delivered);
        InOrder order = inOrder(world);
        order.verify(world).setGameRule(isNull(), eq(false));
        order.verify(world).setGameRule(isNull(), eq(true));
    }

    @Test
    void dispatchRestoresPreviousValue() {
        World world = mock(World.class);
        doReturn(Boolean.FALSE).when(world).getGameRuleValue((GameRule<Boolean>) null);
        QuietConsoleDispatch.dispatch(world, null, "say hi", line -> { });
        ArgumentCaptor<Boolean> values = ArgumentCaptor.forClass(Boolean.class);
        verify(world, times(2)).setGameRule(isNull(), values.capture());
        assertEquals(List.of(false, false), values.getAllValues());
    }

    @Test
    void dispatchRestoresWhenDispatchThrows() {
        World world = mock(World.class);
        doReturn(Boolean.TRUE).when(world).getGameRuleValue((GameRule<Boolean>) null);
        assertThrows(IllegalStateException.class, () -> QuietConsoleDispatch.dispatch(world, null,
                "say hi", line -> {
                    throw new IllegalStateException("boom");
                }));
        InOrder order = inOrder(world);
        order.verify(world).setGameRule(isNull(), eq(false));
        order.verify(world).setGameRule(isNull(), eq(true));
    }

    @Test
    void dispatchAtWrapsLineAndMutesExecutorWorld() {
        Player executor = mock(Player.class);
        UUID executorId = UUID.randomUUID();
        when(executor.getUniqueId()).thenReturn(executorId);
        World world = mock(World.class);
        when(executor.getWorld()).thenReturn(world);
        doReturn(Boolean.TRUE).when(world).getGameRuleValue((GameRule<Boolean>) null);
        List<String> delivered = new ArrayList<>();
        QuietConsoleDispatch.dispatchAt(executor, null, "summon pig 1 2 3", delivered::add);
        assertEquals(List.of("execute as " + executorId + " at @s run summon pig 1 2 3"), delivered);
        InOrder order = inOrder(world);
        order.verify(world).setGameRule(isNull(), eq(false));
        order.verify(world).setGameRule(isNull(), eq(true));
    }

    @Test
    void dispatchAtNullWorldDeliversWithoutMute() {
        Player executor = mock(Player.class);
        UUID executorId = UUID.randomUUID();
        when(executor.getUniqueId()).thenReturn(executorId);
        when(executor.getWorld()).thenReturn(null);
        List<String> delivered = new ArrayList<>();
        QuietConsoleDispatch.dispatchAt(executor, null, "say hi", delivered::add);
        assertEquals(List.of("execute as " + executorId + " at @s run say hi"), delivered);
    }
}
