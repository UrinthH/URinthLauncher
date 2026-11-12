package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class GovernorTest {
    /** A pretend game: the frame rate is a base rate (what the scene costs) times what each level gives back. */
    private static final class Sim {
        final Governor governor;
        final double[] speedup;
        long now = 100_000;
        int changes;
        int last;

        Sim(int target, double... speedup) {
            this.governor = new Governor(target);
            this.speedup = speedup;
        }

        int step(double base) {
            int fps = (int) Math.round(base * (1.0 + speedup[governor.level()]));
            governor.sample(now, fps);
            now += 1000;
            int level = governor.level();
            if (level != last) {
                changes++;
                last = level;
            }
            return level;
        }

        void run(int seconds, double base) {
            for (int i = 0; i < seconds; i++) step(base);
        }
    }

    @Test
    public void nothingIsTakenAwayAtOrAboveTheTarget() {
        Sim sim = new Sim(50, 0, 0.25, 0.40, 0.55);
        sim.run(120, 60);
        assertEquals(0, sim.governor.level());
        assertEquals(0, sim.changes);
        sim.run(60, 50);
        assertEquals(0, sim.governor.level());
    }

    @Test
    public void raisesWhileTheFrameRateIsLowAndKeepsWhatHelps() {
        Sim sim = new Sim(50, 0, 0.25, 0.40, 0.55);
        sim.run(60, 30);
        assertEquals(3, sim.governor.level());
    }

    @Test
    public void oneVeryLowReadingIsEnoughToStart() {
        Governor governor = new Governor(50);
        governor.sample(100_000, 20);
        assertEquals(1, governor.level());
    }

    @Test
    public void aSingleSlightlyLowReadingIsIgnored() {
        Governor governor = new Governor(50);
        governor.sample(100_000, 45);
        assertEquals(0, governor.level());
        governor.sample(101_000, 55);
        governor.sample(102_000, 45);
        assertEquals(0, governor.level());
        governor.sample(103_000, 44);
        assertEquals(1, governor.level());
    }

    @Test
    public void zeroOrNegativeReadingsAreIgnored() {
        Governor governor = new Governor(50);
        governor.sample(100_000, 0);
        governor.sample(101_000, -5);
        assertEquals(0, governor.level());
        assertEquals("", governor.note());
    }

    @Test
    public void sheddingThatDoesNotHelpIsPutBackAndNotRepeatedAtOnce() {
        Sim sim = new Sim(50, 0, 0, 0, 0);
        int highest = 0;
        for (int i = 0; i < 40; i++) highest = Math.max(highest, sim.step(30));
        assertEquals(1, highest);
        assertEquals(0, sim.governor.level());
        assertTrue(sim.governor.raiseLocked(sim.now));
        assertTrue(sim.governor.note().contains("does not help"));
        // Inside the lock nothing is tried again.
        int changesBefore = sim.changes;
        for (int guard = 0; guard < 400 && sim.governor.raiseLocked(sim.now); guard++) {
            sim.step(30);
            if (sim.governor.raiseLocked(sim.now)) assertEquals(changesBefore, sim.changes);
        }
        assertFalse(sim.governor.raiseLocked(sim.now));
        // Once the lock is over it tries again.
        sim.run(10, 30);
        assertTrue(sim.changes > changesBefore, "it tries again once the lock is over");
    }

    @Test
    public void everyFutileTryMakesTheNextWaitLonger() {
        Sim sim = new Sim(50, 0, 0, 0, 0);
        long[] triedAt = new long[3];
        int tries = 0;
        int previous = 0;
        for (int i = 0; i < 600 && tries < 3; i++) {
            int level = sim.step(30);
            if (level == 1 && previous == 0) triedAt[tries++] = sim.now;
            previous = level;
        }
        assertEquals(3, tries);
        long first = triedAt[1] - triedAt[0];
        long second = triedAt[2] - triedAt[1];
        assertTrue(second > first, "the wait grows: " + first + " then " + second);
    }

    @Test
    public void aDifferentSceneEndsTheLock() {
        Sim sim = new Sim(50, 0, 0, 0, 0);
        sim.run(12, 30);
        assertTrue(sim.governor.raiseLocked(sim.now));
        // Much lower frame rate: the scene is not the one the lock was made in.
        sim.run(10, 20);
        assertTrue(sim.changes >= 3, "tried again after the scene changed");
    }

    @Test
    public void comesBackToFullQualityWhenTheLoadGoesAway() {
        Sim sim = new Sim(50, 0, 0.25, 0.40, 0.55);
        sim.run(40, 30);
        assertEquals(3, sim.governor.level());
        sim.run(120, 100);
        assertEquals(0, sim.governor.level());
    }

    @Test
    public void staysAtALevelThatJustReachesTheTargetWithoutFlicker() {
        Sim sim = new Sim(50, 0, 0.20, 0.30, 0.40);
        sim.run(100, 45);
        assertTrue(sim.governor.level() >= 1, "raised to reach the target");
        int before = sim.changes;
        sim.run(300, 45);
        assertTrue(sim.changes - before <= 8, "changes in five minutes: " + (sim.changes - before));
    }

    @Test
    public void changingTheTargetForgetsTheCurrentJudgement() {
        Governor governor = new Governor(50);
        governor.sample(100_000, 20);
        assertTrue(governor.judging());
        governor.setTarget(30);
        assertFalse(governor.judging());
        assertEquals(30, governor.target());
    }

    @Test
    public void resetGoesBackToLevelZeroAndForgetsEverything() {
        Sim sim = new Sim(50, 0, 0, 0, 0);
        sim.run(12, 30);
        assertTrue(sim.governor.raiseLocked(sim.now));
        sim.governor.reset();
        assertEquals(0, sim.governor.level());
        assertFalse(sim.governor.raiseLocked(sim.now));
        assertEquals("", sim.governor.note());
    }

    @Test
    public void neverGoesAboveMaxOrBelowOff() {
        Sim sim = new Sim(200, 0, 0.5, 1.0, 1.5);
        for (int i = 0; i < 200; i++) {
            int level = sim.step(20);
            assertTrue(level >= 0 && level <= 3);
        }
        assertEquals(3, sim.governor.level());
    }
}
