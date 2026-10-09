package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.core.TaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

/** Shared ticker cadence, ladder points, and lifecycle. */
class CountdownServiceTest {

    private record Fixture(CountdownService countdowns, AtomicReference<Runnable> tick,
            BukkitTask task) {
    }

    private static Fixture fixture() {
        TaskScheduler tasks = mock(TaskScheduler.class);
        AtomicReference<Runnable> tick = new AtomicReference<>();
        BukkitTask task = mock(BukkitTask.class);
        when(tasks.runTimer(any(Runnable.class), anyLong(), anyLong())).thenAnswer(invocation -> {
            tick.set(invocation.getArgument(0));
            return task;
        });
        return new Fixture(new CountdownService(tasks), tick, task);
    }

    @Test
    void ladderHoldsWholeSequence() {
        assertEquals(17, CountdownService.LADDER.size());
        for (int mark : new int[] {38400, 19200, 9600, 4800, 2400, 1200, 600, 300, 150, 90, 60,
                30, 15, 10, 3, 2, 1}) {
            assertTrue(CountdownService.onLadder(mark), "ladder misses " + mark);
        }
        assertFalse(CountdownService.onLadder(0));
        assertFalse(CountdownService.onLadder(-5));
        assertFalse(CountdownService.onLadder(4));
        assertFalse(CountdownService.onLadder(5));
        assertFalse(CountdownService.onLadder(38401));
    }

    @Test
    void ticksCountDownThenDoneOnce() {
        Fixture fixture = fixture();
        List<Integer> seen = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean();

        fixture.countdowns().start("key", 3, seen::add, () -> done.set(true));
        assertTrue(fixture.countdowns().running("key"));
        assertEquals(List.of(3), seen);

        fixture.tick().get().run();
        fixture.tick().get().run();
        assertEquals(List.of(3, 2, 1), seen);
        assertFalse(done.get());

        fixture.tick().get().run();
        assertTrue(done.get());
        assertFalse(fixture.countdowns().running("key"));
        verify(fixture.task()).cancel();
    }

    @Test
    void initialTickFiresAtStartWithoutTimer() {
        Fixture fixture = fixture();
        List<Integer> seen = new ArrayList<>();

        fixture.countdowns().start("key", 35, seen::add, () -> {
        });

        assertEquals(List.of(35), seen);
        assertTrue(fixture.countdowns().running("key"));
    }

    @Test
    void selfCancellingInitialTickSkipsTimer() {
        Fixture fixture = fixture();
        List<Integer> seen = new ArrayList<>();

        fixture.countdowns().start("key", 35, remaining -> {
            seen.add(remaining);
            fixture.countdowns().cancel("key");
        }, () -> {
        });

        assertEquals(List.of(35), seen);
        assertFalse(fixture.countdowns().running("key"));
    }

    @Test
    void pollMarkFiresInitialSkipsShadowThenLadder() {
        Fixture fixture = fixture();

        assertTrue(fixture.countdowns().pollMark("key", 35));
        assertFalse(fixture.countdowns().pollMark("key", 34));
        assertTrue(fixture.countdowns().pollMark("key", 30));
        assertFalse(fixture.countdowns().pollMark("key", 29));
        assertTrue(fixture.countdowns().pollMark("key", 15));
        assertFalse(fixture.countdowns().pollMark("key", 0));
        assertFalse(fixture.countdowns().pollMark("key", -1));
    }

    @Test
    void pollMarkSkipsLadderInsideTwoSecondShadow() {
        Fixture fixture = fixture();

        assertTrue(fixture.countdowns().pollMark("key", 31));
        assertFalse(fixture.countdowns().pollMark("key", 30));
        assertTrue(fixture.countdowns().pollMark("key", 15));
        assertTrue(fixture.countdowns().pollMark("key", 10));
        assertTrue(fixture.countdowns().pollMark("key", 3));
        assertTrue(fixture.countdowns().pollMark("key", 2));
        assertTrue(fixture.countdowns().pollMark("key", 1));
    }

    @Test
    void pollMarkStateClearsWithCancel() {
        Fixture fixture = fixture();

        assertTrue(fixture.countdowns().pollMark("key", 35));
        assertTrue(fixture.countdowns().pollMark("other", 35));
        fixture.countdowns().cancel("key");

        assertTrue(fixture.countdowns().pollMark("key", 14));
        assertFalse(fixture.countdowns().pollMark("other", 14));
    }

    @Test
    void nonPositiveTotalRunsDoneAtOnce() {
        Fixture fixture = fixture();
        AtomicBoolean done = new AtomicBoolean();

        fixture.countdowns().start("key", 0, remaining -> {
            throw new AssertionError("tick must not fire");
        }, () -> done.set(true));

        assertTrue(done.get());
        assertFalse(fixture.countdowns().running("key"));
    }

    @Test
    void restartReplacesPreviousCountdown() {
        Fixture fixture = fixture();
        List<Integer> seen = new ArrayList<>();

        fixture.countdowns().start("key", 100, seen::add, () -> {
        });
        fixture.countdowns().start("key", 2, seen::add, () -> {
        });

        fixture.tick().get().run();
        assertEquals(List.of(100, 2, 1), seen);
        verify(fixture.task()).cancel();
    }

    @Test
    void cancelStopsTicks() {
        Fixture fixture = fixture();
        List<Integer> seen = new ArrayList<>();
        fixture.countdowns().start("key", 5, seen::add, () -> {
        });

        assertTrue(fixture.countdowns().cancel("key"));
        assertFalse(fixture.countdowns().cancel("key"));

        fixture.tick().get().run();
        assertEquals(List.of(5), seen);
        verify(fixture.task()).cancel();
    }
}
