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
            float[] unit = {BoostTimeline.lock(t), BoostTimeline.zoomBlur(t), BoostTimeline.aberration(t),
                    BoostTimeline.snapshotAlpha(t), BoostTimeline.vignette(t), BoostTimeline.flash(t),
                    BoostTimeline.shake(t), BoostTimeline.frame(t), BoostTimeline.gauge(t), BoostTimeline.sweep(t),
                    BoostTimeline.emblemAlpha(t), BoostTimeline.fly(t), BoostTimeline.arrival(t), BoostTimeline.overall(t)};
            for (float v : unit) assertTrue("t=" + t + " v=" + v, v >= -EPS && v <= 1f + EPS);
            assertTrue(BoostTimeline.scrim(t) >= -EPS && BoostTimeline.scrim(t) <= BoostTimeline.SCRIM_MAX + EPS);
            assertTrue(BoostTimeline.zoomScale(t) >= 1f - EPS && BoostTimeline.zoomScale(t) <= BoostTimeline.ZOOM_MAX + EPS);
            assertTrue(BoostTimeline.emblemPop(t) >= 0f && BoostTimeline.emblemPop(t) <= 1.2f);
            assertTrue(BoostTimeline.core(t) >= 0f && BoostTimeline.core(t) <= 1.3f);
        }
    }

    @Test
    public void zoomIsSmoothAndOnlyGoesIn() {
        assertEquals(1f, BoostTimeline.zoomScale(0f), EPS);
        assertEquals(BoostTimeline.ZOOM_MAX, BoostTimeline.zoomScale(BoostTimeline.ZOOM_END), EPS);
        float last = 0f;
        for (float t = 0f; t <= BoostTimeline.ZOOM_END; t += 5f) {
            float z = BoostTimeline.zoomScale(t);
            assertTrue("t=" + t, z >= last - EPS);
            last = z;
        }
    }

    @Test
    public void blurGrowsDuringTheZoomAndIsGoneAfterTheShutter() {
        assertEquals(0f, BoostTimeline.zoomBlur(BoostTimeline.LOCK_END), EPS);
        assertTrue(BoostTimeline.zoomBlur(BoostTimeline.ZOOM_END - 1f) > 0.9f);
        assertEquals(0f, BoostTimeline.zoomBlur(BoostTimeline.FLASH_AT + 1f), EPS);
        assertTrue(BoostTimeline.aberration(BoostTimeline.FLASH_AT + 10f) > 0.8f);
        assertEquals(0f, BoostTimeline.aberration(BoostTimeline.FLASH_AT + 400f), EPS);
    }

    @Test
    public void frozenPictureIsOpaqueThenDissolves() {
        assertEquals(1f, BoostTimeline.snapshotAlpha(BoostTimeline.FLASH_AT), EPS);
        assertEquals(0f, BoostTimeline.snapshotAlpha(BoostTimeline.SNAPSHOT_GONE), EPS);
        assertEquals(0f, BoostTimeline.snapshotAlpha(BoostTimeline.DURATION_MS), EPS);
    }

    @Test
    public void flashPeaksAtTheShutter() {
        assertEquals(0f, BoostTimeline.flash(0f), EPS);
        assertEquals(1f, BoostTimeline.flash(BoostTimeline.FLASH_AT), EPS);
        assertEquals(0f, BoostTimeline.flash(BoostTimeline.FLASH_AT + 500f), EPS);
        assertTrue(BoostTimeline.glitching(BoostTimeline.FLASH_AT));
        assertFalse(BoostTimeline.glitching(0f));
        assertFalse(BoostTimeline.glitching(BoostTimeline.FLASH_AT + 400f));
    }

    @Test
    public void emblemBuildsInOrder() {
        assertEquals(0f, BoostTimeline.frame(BoostTimeline.BUILD_START - 1f), EPS);
        assertEquals(1f, BoostTimeline.frame(BoostTimeline.BUILD_START + 600f), EPS);
        assertEquals(0f, BoostTimeline.gauge(BoostTimeline.GAUGE_START - 1f), EPS);
        assertEquals(1f, BoostTimeline.gauge(BoostTimeline.GAUGE_END), EPS);
        assertEquals(0f, BoostTimeline.core(BoostTimeline.GAUGE_START), EPS);
        assertEquals(1f, BoostTimeline.core(BoostTimeline.GAUGE_START + 600f), EPS);
        assertEquals(0f, BoostTimeline.emblemPop(0f), EPS);
        assertEquals(1f, BoostTimeline.emblemPop(BoostTimeline.BUILD_START + 700f), EPS);
        assertTrue(BoostTimeline.sweep(BoostTimeline.SWEEP_START + 200f) > 0f);
    }

    @Test
    public void emblemFliesIntoTheCardIcon() {
        assertEquals(0f, BoostTimeline.fly(BoostTimeline.FLY_START), EPS);
        assertEquals(1f, BoostTimeline.fly(BoostTimeline.FLY_END), EPS);
        assertEquals(0f, BoostTimeline.emblemAlpha(BoostTimeline.FLY_END), EPS);
        assertEquals(1f, BoostTimeline.emblemAlpha(BoostTimeline.FLY_START), EPS);
        assertFalse(BoostTimeline.arrived(BoostTimeline.FLY_END - 1f));
        assertTrue(BoostTimeline.arrived(BoostTimeline.FLY_END));
        assertEquals(0f, BoostTimeline.arrival(BoostTimeline.FLY_END), EPS);
    }

    @Test
    public void endsClean() {
        assertEquals(0f, BoostTimeline.overall(BoostTimeline.DURATION_MS), EPS);
        assertEquals(0f, BoostTimeline.scrim(BoostTimeline.DURATION_MS), EPS);
        assertEquals(0f, BoostTimeline.popAge(100f), EPS);
        assertTrue(BoostTimeline.popAge(BoostTimeline.FLASH_AT + 500f) > 0.4f);
    }
}
