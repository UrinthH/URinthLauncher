package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RefreshRatePolicyTest {
    private static final int W = 1080;
    private static final int H = 2400;

    private static RefreshRatePolicy.Mode mode(int id, float hz) {
        return new RefreshRatePolicy.Mode(id, W, H, hz);
    }

    private static final List<RefreshRatePolicy.Mode> PHONE = Arrays.asList(mode(1, 60f), mode(2, 90f), mode(3, 120f));

    // ---------------------------------------------------------------- choosing the mode

    @Test
    public void autoAsksForTheHighestRateUpTo120() {
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(1, 60f), PHONE, 0);
        assertEquals(120f, choice.hz, 0f);
        assertEquals(3, choice.modeId);
    }

    @Test
    public void autoOnA144HzScreenStopsAt120() {
        List<RefreshRatePolicy.Mode> modes = Arrays.asList(mode(1, 60f), mode(2, 120f), mode(3, 144f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(1, 60f), modes, 0);
        assertEquals(120f, choice.hz, 0f);
        assertEquals(2, choice.modeId);
    }

    @Test
    public void autoNeverLowersAScreenThatAlreadyRunsAbove120() {
        List<RefreshRatePolicy.Mode> modes = Arrays.asList(mode(1, 60f), mode(2, 120f), mode(3, 144f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(3, 144f), modes, 0);
        assertEquals(144f, choice.hz, 0f);
        assertEquals(0, choice.modeId);
    }

    @Test
    public void aRateTheScreenAlreadyRunsAtNeedsNoModeChange() {
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(3, 120f), PHONE, 0);
        assertEquals(120f, choice.hz, 0f);
        assertEquals(0, choice.modeId);
    }

    @Test
    public void aChosenCapLowersTheRateToo() {
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(3, 120f), PHONE, 90);
        assertEquals(90f, choice.hz, 0f);
        assertEquals(2, choice.modeId);
    }

    @Test
    public void aCapBetweenTwoModesTakesTheLowerOne() {
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(1, 60f), PHONE, 100);
        assertEquals(90f, choice.hz, 0f);
        assertEquals(2, choice.modeId);
    }

    @Test
    public void whenEveryModeIsAboveTheCapTheLowestOneIsUsed() {
        List<RefreshRatePolicy.Mode> modes = Arrays.asList(mode(1, 90f), mode(2, 120f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(2, 120f), modes, 60);
        assertEquals(90f, choice.hz, 0f);
        assertEquals(1, choice.modeId);
    }

    @Test
    public void aSixtyHertzScreenStaysAtSixty() {
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(1, 60f), Collections.singletonList(mode(1, 60f)), 0);
        assertEquals(60f, choice.hz, 0f);
        assertEquals(0, choice.modeId);
    }

    @Test
    public void modesWithAnotherPhysicalSizeAreNeverChosen() {
        // A 120 Hz mode at a different size would change the picture size, which is never wanted.
        List<RefreshRatePolicy.Mode> modes = Arrays.asList(
                mode(1, 60f), new RefreshRatePolicy.Mode(2, 720, 1600, 120f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(1, 60f), modes, 0);
        assertEquals(60f, choice.hz, 0f);
        assertEquals(0, choice.modeId);
    }

    @Test
    public void fractionalRatesCountAsTheSameRate() {
        List<RefreshRatePolicy.Mode> modes = Arrays.asList(mode(1, 59.99f), mode(2, 90.0001f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(2, 90f), modes, 90);
        assertEquals(0, choice.modeId);
        assertEquals(90f, choice.hz, 0.01f);
    }

    @Test
    public void twoModesWithTheSameRateKeepTheCurrentRateAsIs() {
        List<RefreshRatePolicy.Mode> modes = Arrays.asList(mode(1, 60f), mode(2, 120f), mode(3, 120f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(3, 120f), modes, 0);
        assertEquals(0, choice.modeId);
    }

    @Test
    public void noKnownModesKeepsTheCurrentRate() {
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(mode(1, 60f), Collections.<RefreshRatePolicy.Mode>emptyList(), 0);
        assertEquals(60f, choice.hz, 0f);
        assertEquals(0, choice.modeId);
    }

    @Test
    public void oldAndroidWithOnlyRatesWorks() {
        // Before Android 6 there are no modes: width and height are unknown (0) for all of them.
        List<RefreshRatePolicy.Mode> rates = Arrays.asList(
                new RefreshRatePolicy.Mode(0, 0, 0, 60f), new RefreshRatePolicy.Mode(0, 0, 0, 90f));
        RefreshRatePolicy.Choice choice = RefreshRatePolicy.choose(new RefreshRatePolicy.Mode(0, 0, 0, 60f), rates, 0);
        assertEquals(90f, choice.hz, 0f);
    }

    // ---------------------------------------------------------------- the stored limit

    @Test
    public void theStoredLimitIsReadAsHertz() {
        assertEquals(0, RefreshRatePolicy.parseCap("0"));
        assertEquals(60, RefreshRatePolicy.parseCap("60"));
        assertEquals(90, RefreshRatePolicy.parseCap(" 90 "));
        assertEquals(120, RefreshRatePolicy.parseCap("120"));
    }

    @Test
    public void aBadStoredLimitMeansAuto() {
        assertEquals(0, RefreshRatePolicy.parseCap(null));
        assertEquals(0, RefreshRatePolicy.parseCap(""));
        assertEquals(0, RefreshRatePolicy.parseCap("fast"));
        assertEquals(0, RefreshRatePolicy.parseCap("-90"));
        assertEquals(0, RefreshRatePolicy.parseCap("5"));
        assertEquals(0, RefreshRatePolicy.parseCap("1000"));
    }

    // ---------------------------------------------------------------- asking again

    @Test
    public void doesNothingWhileTheScreenIsAtTheWantedRate() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        assertFalse(guard.shouldAsk(0, 90f, 90f));
        assertFalse(guard.shouldAsk(5_000, 89.9f, 90f));
        assertFalse(guard.shouldAsk(10_000, 120f, 90f));
        assertEquals(0, guard.tries());
    }

    @Test
    public void asksAtOnceWhenTheScreenFallsBelow() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        assertTrue(guard.shouldAsk(1_000_000, 60f, 90f));
        assertEquals(1, guard.tries());
    }

    @Test
    public void asksAtMostOncePerSecond() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        assertTrue(guard.shouldAsk(10_000, 60f, 90f));
        assertFalse(guard.shouldAsk(10_400, 60f, 90f));
        assertFalse(guard.shouldAsk(10_999, 60f, 90f));
        assertTrue(guard.shouldAsk(11_000, 60f, 90f));
    }

    @Test
    public void afterThreeFruitlessTriesItBacksOffToHalfAMinute() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        long now = 100_000;
        for (int i = 0; i < RefreshRatePolicy.Guard.QUICK_TRIES; i++) {
            assertTrue(guard.shouldAsk(now, 60f, 90f));
            now += RefreshRatePolicy.Guard.COOLDOWN_MS;
        }
        // The system keeps the rate down: no more asking every second.
        assertFalse(guard.shouldAsk(now, 60f, 90f));
        assertFalse(guard.shouldAsk(now + 20_000, 60f, 90f));
        assertTrue(guard.shouldAsk(now - RefreshRatePolicy.Guard.COOLDOWN_MS + RefreshRatePolicy.Guard.PATIENT_COOLDOWN_MS, 60f, 90f));
    }

    @Test
    public void reachingTheWantedRateStartsTheCountAgain() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        long now = 0;
        for (int i = 0; i < RefreshRatePolicy.Guard.QUICK_TRIES; i++) {
            assertTrue(guard.shouldAsk(now, 60f, 90f));
            now += RefreshRatePolicy.Guard.COOLDOWN_MS;
        }
        assertFalse(guard.shouldAsk(now, 60f, 90f));
        assertFalse(guard.shouldAsk(now + 1, 90f, 90f)); // it got there
        assertEquals(0, guard.tries());
        // A later drop is answered quickly again, one second after the last ask.
        assertTrue(guard.shouldAsk(now + RefreshRatePolicy.Guard.COOLDOWN_MS, 60f, 90f));
    }

    @Test
    public void comingBackToTheFrontForgetsTheWaits() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        assertTrue(guard.shouldAsk(5_000, 60f, 90f));
        assertFalse(guard.shouldAsk(5_100, 60f, 90f));
        guard.reset();
        assertTrue(guard.shouldAsk(5_200, 60f, 90f));
        assertEquals(1, guard.tries());
    }

    @Test
    public void neverAsksWhenNoRateIsWanted() {
        RefreshRatePolicy.Guard guard = new RefreshRatePolicy.Guard();
        assertFalse(guard.shouldAsk(0, 30f, 0f));
        assertFalse(guard.shouldAsk(0, 30f, -1f));
    }
}
