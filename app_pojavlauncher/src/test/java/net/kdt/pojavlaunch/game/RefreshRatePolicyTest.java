package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshRatePolicyTest {
    @Test
    public void belowTargetOnlyWhenClearlyLower() {
        assertTrue(RefreshRatePolicy.isBelowTarget(90f, 60f));
        assertFalse(RefreshRatePolicy.isBelowTarget(90f, 90f));
        assertFalse(RefreshRatePolicy.isBelowTarget(90f, 120f));
        // Rates are reported as 89.9 or 90.0 depending on the phone: that is the same rate.
        assertFalse(RefreshRatePolicy.isBelowTarget(90f, 89.9f));
        assertFalse(RefreshRatePolicy.isBelowTarget(90f, 89.6f));
        assertTrue(RefreshRatePolicy.isBelowTarget(90f, 89.3f));
        // Nothing was requested, nothing to keep.
        assertFalse(RefreshRatePolicy.isBelowTarget(0f, 60f));
        assertFalse(RefreshRatePolicy.isBelowTarget(-1f, 60f));
    }

    @Test
    public void theFirstDropIsAnsweredAtOnce() {
        assertTrue(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 5_000, -1, 0, false));
    }

    @Test
    public void waitsOutTheCooldownBetweenRequests() {
        assertFalse(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 10_500, 10_000, 0, false));
        assertTrue(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 11_000, 10_000, 0, false));
    }

    @Test
    public void everyRequestThatDidNotHelpDoublesTheWait() {
        assertEquals(1_000L, RefreshRatePolicy.cooldownMs(0));
        assertEquals(2_000L, RefreshRatePolicy.cooldownMs(1));
        assertEquals(4_000L, RefreshRatePolicy.cooldownMs(2));
        assertEquals(32_000L, RefreshRatePolicy.cooldownMs(5));
        // It stops growing, so a phone is never left alone for good.
        assertEquals(32_000L, RefreshRatePolicy.cooldownMs(6));
        assertEquals(32_000L, RefreshRatePolicy.cooldownMs(1_000));
        assertEquals(1_000L, RefreshRatePolicy.cooldownMs(-3));
        // Three failures in a row: 8 s must pass.
        assertFalse(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 17_999, 10_000, 3, false));
        assertTrue(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 18_000, 10_000, 3, false));
    }

    @Test
    public void neverAsksWhileThePhoneIsHot() {
        assertFalse(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 5_000, -1, 0, true));
        assertFalse(RefreshRatePolicy.shouldRequestAgain(90f, 60f, 999_999, 0, 0, true));
    }

    @Test
    public void doesNothingWhenTheRateIsFine() {
        assertFalse(RefreshRatePolicy.shouldRequestAgain(90f, 90f, 5_000, -1, 0, false));
        assertFalse(RefreshRatePolicy.shouldRequestAgain(90f, 120f, 5_000, -1, 0, false));
        assertFalse(RefreshRatePolicy.shouldRequestAgain(0f, 60f, 5_000, -1, 0, false));
    }

    // ---- which mode to ask for

    private static float[] modes(float... hz) {
        return hz;
    }

    /** The loop {@link RefreshRateBoost#apply} had before the cap existed, kept here as the reference for "auto". */
    private static int oldAutoChoice(float current, float[] supported) {
        float best = current;
        int index = -1;
        for (int i = 0; i < supported.length; i++) {
            if (supported[i] <= 120f + 0.5f && supported[i] > best + 0.5f) {
                best = supported[i];
                index = i;
            }
        }
        return index;
    }

    @Test
    public void autoAsksForTheHighestRateUpTo120AndOnlyIfItIsAboveTheCurrentOne() {
        assertEquals(2, RefreshRatePolicy.pickMode(60f, modes(60f, 90f, 120f), 0));
        assertEquals(1, RefreshRatePolicy.pickMode(60f, modes(60f, 90f), 0));
        // The player's phone: a 90 Hz panel already running at 90 has nothing to ask for.
        assertEquals(-1, RefreshRatePolicy.pickMode(90f, modes(60f, 90f), 0));
        assertEquals(-1, RefreshRatePolicy.pickMode(60f, modes(60f), 0));
        // Never above 120, even when the panel offers more.
        assertEquals(1, RefreshRatePolicy.pickMode(60f, modes(60f, 120f, 144f), 0));
        assertEquals(-1, RefreshRatePolicy.pickMode(60f, modes(60f, 144f), 0));
        assertEquals(-1, RefreshRatePolicy.pickMode(60f, modes(), 0));
        // A negative cap is "no cap" too.
        assertEquals(1, RefreshRatePolicy.pickMode(60f, modes(60f, 90f), -1));
    }

    @Test
    public void autoChoosesExactlyWhatTheOldLoopChose() {
        float[][] panels = {
                {60f}, {60f, 90f}, {60f, 90f, 120f}, {90f, 60f, 120f}, {48f, 60f, 72f, 90f, 120f, 144f},
                {59.94f, 60f, 89.9f, 90f}, {30f, 60f}, {120f, 90f, 60f, 144f},
        };
        float[] currents = {30f, 48f, 59.94f, 60f, 72f, 89.9f, 90f, 120f, 144f};
        for (float[] panel : panels) {
            for (float current : currents) {
                assertEquals("panel " + java.util.Arrays.toString(panel) + " at " + current,
                        oldAutoChoice(current, panel), RefreshRatePolicy.pickMode(current, panel, 0));
            }
        }
    }

    @Test
    public void aCapPicksTheHighestRateThatDoesNotGoOverIt() {
        float[] panel = modes(60f, 90f, 120f);
        assertEquals(0, RefreshRatePolicy.pickMode(90f, panel, 60));
        assertEquals(1, RefreshRatePolicy.pickMode(120f, panel, 90));
        assertEquals(2, RefreshRatePolicy.pickMode(60f, panel, 120));
        // A cap above what the panel has is the panel's best.
        assertEquals(1, RefreshRatePolicy.pickMode(60f, modes(60f, 90f), 120));
    }

    @Test
    public void aCapMayAskForALowerRateThanTheCurrentOne() {
        // The point of a cap: the screen is at 90 and the player asked for at most 60.
        float[] panel = modes(60f, 90f);
        int index = RefreshRatePolicy.pickMode(90f, panel, 60);
        assertEquals(0, index);
        assertTrue(panel[index] < 90f);
    }

    @Test
    public void aCapAtTheCurrentRateStillNamesThatMode() {
        // So the request is explicit and the system is told which mode to keep.
        assertEquals(1, RefreshRatePolicy.pickMode(90f, modes(60f, 90f), 90));
    }

    @Test
    public void aCapNoModeFitsUnderGetsNothing() {
        assertEquals(-1, RefreshRatePolicy.pickMode(90f, modes(90f, 120f), 60));
        assertEquals(-1, RefreshRatePolicy.pickMode(90f, modes(), 60));
    }

    @Test
    public void ratesThatDifferByLessThanTheToleranceAreTheSameRate() {
        // 90.4 is "90" for a 90 Hz cap; 91 is not.
        assertEquals(1, RefreshRatePolicy.pickMode(60f, modes(60f, 90.4f), 90));
        assertEquals(0, RefreshRatePolicy.pickMode(60f, modes(60f, 91f), 90));
    }

    @Test
    public void theFirstOfTwoModesAtTheSameRateWins() {
        assertEquals(0, RefreshRatePolicy.pickMode(60f, modes(90f, 90f), 0));
        assertEquals(0, RefreshRatePolicy.pickMode(60f, modes(90f, 89.9f), 90));
    }
}
