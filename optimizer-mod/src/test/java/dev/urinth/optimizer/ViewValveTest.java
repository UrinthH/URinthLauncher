package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The last resort against running out of memory: a shorter world, a little at a time, and given back carefully. */
class ViewValveTest {
    private static final MemoryGuard.Level OK = MemoryGuard.Level.OK;
    private static final MemoryGuard.Level TIGHT = MemoryGuard.Level.TIGHT;
    private static final MemoryGuard.Level CRITICAL = MemoryGuard.Level.CRITICAL;

    @AfterEach
    void clean() {
        ViewCap.set(0);
    }

    @Test
    void nothingHappensUntilTheMemoryIsCritical() {
        ViewValve v = new ViewValve();
        for (long t = 0; t < 2000; t++) {
            assertEquals(0, v.update(t % 2 == 0 ? OK : TIGHT, 10, true, 1000 + t));
        }
        assertEquals(0, v.steps());
    }

    @Test
    void aCriticalLookShortensTheWorldByTwoChunks() {
        ViewValve v = new ViewValve();
        assertEquals(8, v.update(CRITICAL, 10, true, 100));
        assertEquals(9, v.ceiling());
        assertEquals(1, v.steps());
    }

    @Test
    void stepsAreSpacedSoTheWorldHasTimeToUnload() {
        ViewValve v = new ViewValve();
        assertEquals(10, v.update(CRITICAL, 12, true, 100));
        assertEquals(10, v.update(CRITICAL, 12, true, 101));
        assertEquals(10, v.update(CRITICAL, 12, true, 100 + ViewValve.DWELL - 1));
        assertEquals(8, v.update(CRITICAL, 12, true, 100 + ViewValve.DWELL));
        assertEquals(2, v.steps());
    }

    @Test
    void itNeverGoesBelowTheFloor() {
        ViewValve v = new ViewValve();
        long now = 100;
        for (int i = 0; i < 50; i++) {
            v.update(CRITICAL, 16, true, now);
            now += ViewValve.DWELL;
        }
        assertEquals(ViewValve.FLOOR, v.cap());
        int steps = v.steps();
        for (int i = 0; i < 50; i++) {
            v.update(CRITICAL, 16, true, now);
            now += ViewValve.DWELL;
        }
        assertEquals(steps, v.steps());
        // an odd distance lands on the floor too
        ViewValve odd = new ViewValve();
        assertEquals(ViewValve.FLOOR, odd.update(CRITICAL, 7, true, 100));
    }

    @Test
    void aDistanceAtOrBelowTheFloorIsLeftAlone() {
        ViewValve v = new ViewValve();
        assertEquals(0, v.update(CRITICAL, ViewValve.FLOOR, true, 100));
        assertEquals(0, v.update(CRITICAL, ViewValve.FLOOR - 2, true, 200));
        assertEquals(0, v.steps());
    }

    @Test
    void switchedOffMeansNoLimitAndLiftsOne() {
        ViewValve v = new ViewValve();
        assertEquals(0, v.update(CRITICAL, 10, false, 100));
        v.update(CRITICAL, 10, true, 200);
        assertEquals(8, v.cap());
        assertEquals(0, v.update(CRITICAL, 10, false, 300));
        assertEquals(0, v.cap());
    }

    @Test
    void chunksComeBackOneAtATimeButNeverBeyondTheCeiling() {
        ViewValve v = new ViewValve();
        assertEquals(8, v.update(CRITICAL, 10, true, 100)); // ceiling 9
        // quiet, but not for long enough yet
        assertEquals(8, v.update(OK, 10, true, 100 + ViewValve.RESTORE - 1));
        assertEquals(9, v.update(OK, 10, true, 100 + ViewValve.RESTORE));
        // the ceiling is reached: it stays there, however long it is quiet
        for (long t = 0; t < 5000; t += 10) assertEquals(9, v.update(OK, 10, true, 100 + 2 * ViewValve.RESTORE + t));
    }

    @Test
    void aSecondStepLowersTheCeilingToo() {
        ViewValve v = new ViewValve();
        v.update(CRITICAL, 10, true, 100);
        assertEquals(6, v.update(CRITICAL, 10, true, 100 + ViewValve.DWELL));
        assertEquals(7, v.ceiling());
        long calm = 100 + ViewValve.DWELL + ViewValve.RESTORE;
        assertEquals(7, v.update(OK, 10, true, calm));
        for (long t = 0; t < 5000; t += 10) assertEquals(7, v.update(OK, 10, true, calm + ViewValve.RESTORE + t));
    }

    @Test
    void troubleStartsTheWaitForGivingChunksBackAgain() {
        ViewValve v = new ViewValve();
        v.update(CRITICAL, 10, true, 100);
        assertEquals(8, v.update(TIGHT, 10, true, 200));
        // 180 seconds after the last trouble, not after the step
        assertEquals(8, v.update(OK, 10, true, 200 + ViewValve.RESTORE - 1));
        assertEquals(9, v.update(OK, 10, true, 200 + ViewValve.RESTORE));
    }

    @Test
    void aPlayerWhoChoosesAShorterDistanceThanTheLimitEndsIt() {
        ViewValve v = new ViewValve();
        v.update(CRITICAL, 10, true, 100);
        assertEquals(8, v.cap());
        assertEquals(0, v.update(OK, 8, true, 101));
        assertEquals(0, v.update(OK, 7, true, 102));
    }

    @Test
    void resetForgetsEverything() {
        ViewValve v = new ViewValve();
        v.update(CRITICAL, 10, true, 100);
        v.reset();
        assertEquals(0, v.cap());
        assertEquals(0, v.ceiling());
        assertEquals(0, v.steps());
        // and the first step is not held back by the time before the reset
        assertEquals(8, v.update(CRITICAL, 10, true, 101));
    }

    @Test
    void theLimitIsOnlyWhatTheServerIsToldNotTheOption() {
        ViewCap.set(8);
        assertEquals(8, ViewCap.apply(10));
        assertEquals(8, ViewCap.apply(8));
        assertEquals(7, ViewCap.apply(7));
        assertEquals(2, ViewCap.apply(2));
        ViewCap.set(0);
        assertEquals(10, ViewCap.apply(10));
        ViewCap.set(-3);
        assertEquals(10, ViewCap.apply(10));
        assertEquals(0, ViewCap.cap());
    }
}
