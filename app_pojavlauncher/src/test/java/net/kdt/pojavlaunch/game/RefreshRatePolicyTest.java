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
}
