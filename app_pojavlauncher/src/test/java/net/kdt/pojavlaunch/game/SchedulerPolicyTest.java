package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SchedulerPolicyTest {
    @Test
    public void startsWithNothingLowered() {
        assertEquals(SchedulerPolicy.LEVEL_NONE, new SchedulerPolicy().level);
    }

    @Test
    public void risesAtOnceAndSkipsLevels() {
        SchedulerPolicy policy = new SchedulerPolicy();
        policy.step(0.07);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
        policy.step(0.20);
        assertEquals(SchedulerPolicy.LEVEL_STRONG, policy.level);

        SchedulerPolicy jump = new SchedulerPolicy();
        jump.step(0.5);
        assertEquals(SchedulerPolicy.LEVEL_STRONG, jump.level);
    }

    @Test
    public void fallsOneLevelOnlyAfterSeveralCalmReadings() {
        SchedulerPolicy policy = new SchedulerPolicy();
        policy.step(0.3);
        for (int i = 0; i < SchedulerPolicy.CALM_SAMPLES_TO_EASE - 1; i++) policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_STRONG, policy.level);
        policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
        for (int i = 0; i < SchedulerPolicy.CALM_SAMPLES_TO_EASE; i++) policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_NONE, policy.level);
    }

    @Test
    public void aBusyReadingResetsTheCalmCount() {
        SchedulerPolicy policy = new SchedulerPolicy();
        policy.step(0.07);
        for (int i = 0; i < SchedulerPolicy.CALM_SAMPLES_TO_EASE - 1; i++) policy.step(0.01);
        policy.step(0.04); // not calm enough to count, not busy enough to rise
        policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
    }

    @Test
    public void doesNotFlapAroundTheThreshold() {
        SchedulerPolicy policy = new SchedulerPolicy();
        for (int i = 0; i < 20; i++) policy.step(i % 2 == 0 ? 0.065 : 0.05);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
    }

    @Test
    public void waitFractionIsClamped() {
        assertEquals(0.1, SchedulerPolicy.waitFraction(0, 150_000_000L, 1_500_000_000L), 1e-9);
        assertEquals(0.0, SchedulerPolicy.waitFraction(500, 100, 1_000L), 0.0);
        assertEquals(0.0, SchedulerPolicy.waitFraction(0, 100, 0), 0.0);
        assertEquals(1.0, SchedulerPolicy.waitFraction(0, 9_000_000_000L, 1_000_000_000L), 0.0);
    }

    @Test
    public void parsesSchedstat() {
        assertEquals(5678, SchedulerPolicy.parseWaitNanos("1234 5678 90\n"));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos(""));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos("1234"));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos("a b c"));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos(null));
    }

    @Test
    public void picksTheRightThreads() {
        assertTrue(SchedulerPolicy.isRenderThread("Render thread"));
        assertFalse(SchedulerPolicy.isRenderThread("Server thread"));
        assertTrue(SchedulerPolicy.isHelperThread("Worker-Main-3"));
        assertTrue(SchedulerPolicy.isHelperThread("IO-Worker-1"));
        assertFalse(SchedulerPolicy.isHelperThread("Render thread"));
        assertFalse(SchedulerPolicy.isHelperThread("Server thread"));
        assertFalse(SchedulerPolicy.isHelperThread("Netty Client IO"));
        assertFalse(SchedulerPolicy.isHelperThread("C2 CompilerThre"));
        assertFalse(SchedulerPolicy.isHelperThread(null));
    }

    @Test
    public void niceValues() {
        assertEquals(0, SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_NONE));
        assertEquals(4, SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_MILD));
        assertEquals(10, SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_STRONG));
    }

    @Test
    public void jitCompilerThreadsAreTheirOwnGroup() {
        // The kernel cuts the names to 15 characters, so the real ones look like this.
        assertTrue(SchedulerPolicy.isJitThread("C2 CompilerThre"));
        assertTrue(SchedulerPolicy.isJitThread("C1 CompilerThre"));
        assertTrue(SchedulerPolicy.isJitThread("C2 CompilerThread0"));
        // Not the render thread, not the garbage collector, not the helpers, not other VM threads.
        assertFalse(SchedulerPolicy.isJitThread("Render thread"));
        assertFalse(SchedulerPolicy.isJitThread("G1 Main Marker"));
        assertFalse(SchedulerPolicy.isJitThread("GC Thread#0"));
        assertFalse(SchedulerPolicy.isJitThread("Worker-Main-3"));
        assertFalse(SchedulerPolicy.isJitThread("Sodium Chunk Builder"));
        assertFalse(SchedulerPolicy.isJitThread(null));
        // A compiler thread is never also counted as a helper, so it is only ever lowered once.
        assertFalse(SchedulerPolicy.isHelperThread("C1 CompilerThre"));
    }

    @Test
    public void jitNiceValuesStepBackBeforeTheHelpersDoAndNeverMoveForward() {
        assertEquals(0, SchedulerPolicy.niceForJit(SchedulerPolicy.LEVEL_NONE));
        assertEquals(6, SchedulerPolicy.niceForJit(SchedulerPolicy.LEVEL_MILD));
        assertEquals(10, SchedulerPolicy.niceForJit(SchedulerPolicy.LEVEL_STRONG));
        // At the mild level the compiler threads are lowered more than the helpers; at strong, equally.
        assertTrue(SchedulerPolicy.niceForJit(SchedulerPolicy.LEVEL_MILD) > SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_MILD));
        assertEquals(SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_STRONG), SchedulerPolicy.niceForJit(SchedulerPolicy.LEVEL_STRONG));
        // Each level is at least as far back as the one before it.
        for (int level = SchedulerPolicy.LEVEL_MILD; level <= SchedulerPolicy.LEVEL_STRONG; level++) {
            assertTrue(SchedulerPolicy.niceForJit(level) >= SchedulerPolicy.niceForJit(level - 1));
        }
    }
}
