package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BenchTest {
    private static void feed(Bench bench, int readings, int fps) {
        for (int i = 0; i < readings; i++) bench.sample(fps);
    }

    private static final int STRETCH = Bench.WARM + Bench.MEASURE;

    @Test
    public void runsOffMaxMaxOffAndSaysWhatEachStretchHolds() {
        Bench bench = new Bench();
        bench.start();
        assertTrue(bench.active());
        assertEquals(BoostLevel.OFF, bench.level());
        feed(bench, STRETCH, 40);
        assertEquals(BoostLevel.MAX, bench.level());
        feed(bench, STRETCH, 50);
        assertEquals(BoostLevel.MAX, bench.level());
        feed(bench, STRETCH, 50);
        assertEquals(BoostLevel.OFF, bench.level());
        assertTrue(bench.active());
        feed(bench, STRETCH, 40);
        assertFalse(bench.active());
        assertNotNull(bench.result());
    }

    @Test
    public void measuresTheGainAndThrowsAwayTheFirstReadingOfEachStretch() {
        Bench bench = new Bench();
        bench.start();
        // The first reading after a switch still holds frames from before it and is not counted.
        bench.sample(1);
        feed(bench, Bench.MEASURE, 40);
        bench.sample(1);
        feed(bench, Bench.MEASURE, 50);
        bench.sample(1);
        feed(bench, Bench.MEASURE, 50);
        bench.sample(1);
        feed(bench, Bench.MEASURE, 40);
        Bench.Result result = bench.result();
        assertEquals(40.0, result.offAverage, 0.001);
        assertEquals(50.0, result.maxAverage, 0.001);
        assertEquals(0.25, result.gain, 0.001);
        assertFalse(result.unstable());
        assertFalse(result.noGain());
        assertEquals(40, result.offLowest);
        assertEquals(50, result.maxHighest);
    }

    @Test
    public void saysSoWhenThePictureChangedDuringTheTest() {
        Bench bench = new Bench();
        bench.start();
        feed(bench, STRETCH, 40);
        feed(bench, STRETCH, 50);
        feed(bench, STRETCH, 50);
        feed(bench, STRETCH, 70);
        assertTrue(bench.result().unstable());
    }

    @Test
    public void saysSoWhenThereIsNoDifference() {
        Bench bench = new Bench();
        bench.start();
        feed(bench, STRETCH * 4, 45);
        Bench.Result result = bench.result();
        assertTrue(result.noGain());
        assertFalse(result.unstable());
    }

    @Test
    public void countsDownTheSecondsAndCanBeCancelled() {
        Bench bench = new Bench();
        assertEquals(0, bench.secondsLeft());
        bench.start();
        assertEquals(Bench.totalSeconds(), bench.secondsLeft());
        feed(bench, 5, 40);
        assertEquals(Bench.totalSeconds() - 5, bench.secondsLeft());
        bench.cancel();
        assertFalse(bench.active());
        assertNull(bench.result());
        assertEquals(0, bench.secondsLeft());
    }

    @Test
    public void readingsOfZeroAreNotCountedAndAnIdleBenchIgnoresReadings() {
        Bench bench = new Bench();
        bench.sample(50);
        assertFalse(bench.active());
        bench.start();
        bench.sample(0);
        assertEquals(Bench.totalSeconds(), bench.secondsLeft());
    }

    @Test
    public void startingAgainThrowsTheOldResultAway() {
        Bench bench = new Bench();
        bench.start();
        feed(bench, STRETCH * 4, 45);
        assertNotNull(bench.result());
        bench.start();
        assertNull(bench.result());
        assertTrue(bench.active());
    }
}
