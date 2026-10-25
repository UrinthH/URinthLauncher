package net.kdt.pojavlaunch.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoostTimelineTest {
    private static final float EPS = 1e-4f;

    @Test
    public void everythingStaysInRange() {
        for (float t = -100f; t <= BoostTimeline.DURATION_MS + 100f; t += 7f) {
            for (float v : new float[]{BoostTimeline.charge(t), BoostTimeline.meter(t), BoostTimeline.speedLines(t),
                    BoostTimeline.flash(t), BoostTimeline.overall(t), BoostTimeline.titleAlpha(t)}) {
                assertTrue("t=" + t + " v=" + v, v >= -EPS && v <= 1f + EPS);
            }
            assertTrue(BoostTimeline.scrim(t) >= -EPS && BoostTimeline.scrim(t) <= BoostTimeline.SCRIM_MAX + EPS);
            assertTrue(BoostTimeline.zoom(t) >= 1f - EPS && BoostTimeline.zoom(t) <= BoostTimeline.MAX_ZOOM + EPS);
        }
    }

    @Test
    public void chargeAndMeterReachFull() {
        assertEquals(0f, BoostTimeline.charge(0f), EPS);
        assertEquals(1f, BoostTimeline.charge(BoostTimeline.CHARGE_END), EPS);
        assertEquals(1f, BoostTimeline.meter(BoostTimeline.CHARGE_END + 500f), EPS);
        assertTrue(BoostTimeline.charge(500f) < BoostTimeline.meter(500f)); // accelerates
    }

    @Test
    public void zoomRushesUpAndNeverShrinks() {
        assertEquals(1f, BoostTimeline.zoom(BoostTimeline.CHARGE_END), EPS);
        assertEquals(BoostTimeline.MAX_ZOOM, BoostTimeline.zoom(BoostTimeline.ZOOM_END), EPS);
        float last = 0f;
        for (float t = BoostTimeline.CHARGE_END; t <= BoostTimeline.ZOOM_END; t += 5f) {
            assertTrue(BoostTimeline.zoom(t) >= last);
            last = BoostTimeline.zoom(t);
        }
    }

    @Test
    public void flashPeaksAtTheBurstAndDrains() {
        assertEquals(0f, BoostTimeline.flash(0f), EPS);
        assertEquals(1f, BoostTimeline.flash(BoostTimeline.ZOOM_END), EPS);
        assertEquals(0f, BoostTimeline.flash(BoostTimeline.FLASH_END), EPS);
        assertTrue(BoostTimeline.flash(BoostTimeline.ZOOM_END - 50f) > 0f);
    }

    @Test
    public void burstClock() {
        assertFalse(BoostTimeline.burstStarted(BoostTimeline.ZOOM_END - 1f));
        assertTrue(BoostTimeline.burstStarted(BoostTimeline.ZOOM_END));
        assertEquals(0f, BoostTimeline.burstAge(100f), EPS);
        assertEquals(1f, BoostTimeline.burstAge(BoostTimeline.ZOOM_END + 1000f), EPS);
    }

    @Test
    public void titlePopsInOvershootsAndFades() {
        assertEquals(0f, BoostTimeline.titleScale(BoostTimeline.TEXT_IN - 1f), EPS);
        float max = 0f;
        for (float t = BoostTimeline.TEXT_IN; t <= BoostTimeline.TEXT_IN + 380f; t += 5f) {
            max = Math.max(max, BoostTimeline.titleScale(t));
        }
        assertTrue(max > 1f);
        assertEquals(1f, BoostTimeline.titleScale(BoostTimeline.TEXT_IN + 1000f), EPS);
        assertEquals(0f, BoostTimeline.titleAlpha(BoostTimeline.DURATION_MS), EPS);
    }

    @Test
    public void scrimFadesInAndOut() {
        assertEquals(0f, BoostTimeline.scrim(0f), EPS);
        assertEquals(BoostTimeline.SCRIM_MAX, BoostTimeline.scrim(1200f), EPS);
        assertEquals(0f, BoostTimeline.scrim(BoostTimeline.DURATION_MS), EPS);
    }
}
