package net.kdt.pojavlaunch.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoostTimelineTest {
    private static final float EPS = 1e-3f;

    @Test
    public void phasesComeInOrderAndFitTheDuration() {
        assertTrue(BoostTimeline.DURATION_MS >= 7000f && BoostTimeline.DURATION_MS <= 10000f);
        assertTrue(BoostTimeline.LOCK_END < BoostTimeline.CRACK_START);
        assertTrue(BoostTimeline.CRACK_START < BoostTimeline.SUCK_START);
        assertTrue(BoostTimeline.ROAM_END <= BoostTimeline.SUCK_START);
        assertTrue(BoostTimeline.SUCK_START < BoostTimeline.BANG_AT);
        assertTrue(BoostTimeline.BANG_AT < BoostTimeline.BUILD_START);
        assertTrue(BoostTimeline.BUILD_START < BoostTimeline.GAUGE_START);
        assertTrue(BoostTimeline.GAUGE_START < BoostTimeline.GAUGE_END);
        assertTrue(BoostTimeline.GAUGE_END < BoostTimeline.FLY_START);
        assertTrue(BoostTimeline.FLY_START < BoostTimeline.FLY_END);
        assertTrue(BoostTimeline.FLY_END < BoostTimeline.FADE_START);
        assertTrue(BoostTimeline.FADE_START < BoostTimeline.DURATION_MS);
    }

    @Test
    public void cameraNeverJumps() {
        float previous = BoostTimeline.cameraZoom(0f);
        for (float t = 1f; t <= BoostTimeline.DURATION_MS; t += 1f) {
            float z = BoostTimeline.cameraZoom(t);
            assertTrue("zoom jumps at " + t, Math.abs(z - previous) < 0.05f);
            previous = z;
        }
        assertEquals(1f, BoostTimeline.cameraZoom(0f), EPS);
        assertEquals(1f, BoostTimeline.cameraZoom(BoostTimeline.DURATION_MS), EPS);
    }

    @Test
    public void cameraZoomsInThenPunchesOutFast() {
        float peak = BoostTimeline.cameraZoom(BoostTimeline.BANG_AT);
        assertEquals(BoostTimeline.ZOOM_PEAK, peak, EPS);
        assertTrue(BoostTimeline.cameraZoom(BoostTimeline.BANG_AT + 260f) < 1f);
        assertTrue(BoostTimeline.cameraZoom(BoostTimeline.BANG_AT + 150f) < peak - 1.5f);
    }

    @Test
    public void cameraPicturesAlwaysCoverTheScreen() {
        // The picture is scaled about the focus point F and moved so F lands between F and the
        // centre C by cameraPull(zoom); the picture's edges must stay outside the screen.
        for (float card = 0f; card <= 1f; card += 0.1f) {
            for (float t = 0f; t <= BoostTimeline.ROAM_END; t += 5f) {
                float z = BoostTimeline.cameraZoom(t);
                float k = BoostTimeline.cameraPull(z);
                float fx = BoostTimeline.cameraFocusX(t, card);
                float fy = BoostTimeline.cameraFocusY(t, card);
                float sx = fx + (BoostTimeline.CENTER_X - fx) * k;
                float sy = fy + (BoostTimeline.CENTER_Y - fy) * k;
                String at = " at t=" + t + " card=" + card;
                assertTrue("left edge" + at, sx - fx * z <= 1e-3f);
                assertTrue("right edge" + at, sx + (1f - fx) * z >= 1f - 1e-3f);
                assertTrue("top edge" + at, sy - fy * z <= 1e-3f);
                assertTrue("bottom edge" + at, sy + (1f - fy) * z >= 1f - 1e-3f);
            }
        }
    }

    @Test
    public void cameraStartsOnTheCardAndRoamsAround() {
        assertEquals(0.4f, BoostTimeline.cameraFocusX(0f, 0.4f), EPS);
        assertEquals(0.6f, BoostTimeline.cameraFocusY(0f, 0.6f), EPS);
        float minX = 1f, maxX = 0f;
        for (float t = 0f; t <= BoostTimeline.ROAM_END; t += 10f) {
            float x = BoostTimeline.cameraFocusX(t, 0.4f);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }
        assertTrue("the camera should sweep across the screen", maxX - minX > 0.4f);
        assertEquals(BoostTimeline.CENTER_X, BoostTimeline.cameraFocusX(BoostTimeline.ROAM_END, 0.2f), EPS);
        assertEquals(BoostTimeline.CENTER_Y, BoostTimeline.cameraFocusY(BoostTimeline.ROAM_END + 500f, 0.2f), EPS);
    }

    @Test
    public void cameraRollSpinsInAndSnapsBack() {
        assertEquals(0f, BoostTimeline.cameraRoll(0f), EPS);
        assertEquals(14f, BoostTimeline.cameraRoll(BoostTimeline.BANG_AT), 0.2f);
        assertEquals(0f, BoostTimeline.cameraRoll(BoostTimeline.BANG_AT + 400f), EPS);
    }

    @Test
    public void suckBuildsUpAndEndsWithTheBang() {
        assertEquals(0f, BoostTimeline.suck(BoostTimeline.SUCK_START), EPS);
        assertEquals(1f, BoostTimeline.suck(BoostTimeline.BANG_AT), EPS);
        assertEquals(0f, BoostTimeline.singularity(BoostTimeline.SUCK_START), EPS);
        assertTrue(BoostTimeline.singularity(BoostTimeline.BANG_AT - 1f) > 0.98f);
        assertEquals(0f, BoostTimeline.singularity(BoostTimeline.BANG_AT + 300f), EPS);
        assertEquals(BoostTimeline.rainScale(BoostTimeline.BANG_AT - 0.01f), BoostTimeline.rainScale(BoostTimeline.BANG_AT), 0.01f);
        assertEquals(1f, BoostTimeline.rainScale(BoostTimeline.BANG_AT + 1000f), EPS);
        assertEquals(0f, BoostTimeline.rainSpin(BoostTimeline.BANG_AT + 1000f), EPS);
    }

    @Test
    public void shakeGrowsUntilTheBang() {
        float previous = 0f;
        for (float t = 0f; t <= BoostTimeline.BANG_AT; t += 10f) {
            float s = BoostTimeline.shake(t);
            assertTrue("shake shrinks at " + t, s >= previous - EPS);
            previous = s;
        }
        assertEquals(1f, BoostTimeline.shake(BoostTimeline.BANG_AT), EPS);
        assertEquals(0f, BoostTimeline.shake(BoostTimeline.BANG_AT + 1500f), EPS);
    }

    @Test
    public void flashPeaksAtTheBang() {
        assertEquals(0f, BoostTimeline.flash(BoostTimeline.BANG_AT - 500f), EPS);
        assertEquals(1f, BoostTimeline.flash(BoostTimeline.BANG_AT), EPS);
        assertEquals(0f, BoostTimeline.flash(BoostTimeline.BANG_AT + 600f), EPS);
    }

    @Test
    public void glitchIsDeterministicAndStopsAtTheBang() {
        int bursts = 0;
        for (float t = 0f; t <= BoostTimeline.DURATION_MS; t += 5f) {
            float g = BoostTimeline.glitch(t);
            assertTrue(g >= 0f && g <= 1f);
            assertEquals(g, BoostTimeline.glitch(t), 0f);
            if (t < 350f || t >= BoostTimeline.BANG_AT) assertEquals(0f, g, 0f);
            else if (g > 0f) bursts++;
        }
        assertTrue("there should be glitch bursts", bursts > 10);
        assertTrue("but not all the time", bursts < (BoostTimeline.BANG_AT - 350f) / 5f / 2f);
    }

    @Test
    public void hashIsStableAndMixesBits() {
        assertEquals(BoostTimeline.hash(12345), BoostTimeline.hash(12345));
        int ones = 0;
        for (int i = 0; i < 1000; i++) ones += BoostTimeline.hash(i) & 1;
        assertTrue(ones > 400 && ones < 600);
    }

    @Test
    public void picturesCrackBeforeTheyAreSucked() {
        assertEquals(0f, BoostTimeline.looseness(BoostTimeline.CRACK_START), EPS);
        assertEquals(1f, BoostTimeline.looseness(BoostTimeline.SUCK_START), EPS);
        assertEquals(0f, BoostTimeline.aberration(BoostTimeline.CRACK_START + 10f), EPS);
        assertEquals(1f, BoostTimeline.shardAlpha(BoostTimeline.BANG_AT), EPS);
        assertEquals(0f, BoostTimeline.shardAlpha(BoostTimeline.BANG_AT + 2400f), EPS);
    }

    @Test
    public void everythingStaysInRange() {
        for (float t = 0f; t <= BoostTimeline.DURATION_MS; t += 5f) {
            assertInUnit("scrim", BoostTimeline.scrim(t), t);
            assertInUnit("vignette", BoostTimeline.vignette(t), t);
            assertInUnit("flash", BoostTimeline.flash(t), t);
            assertInUnit("shake", BoostTimeline.shake(t), t);
            assertInUnit("rainAlpha", BoostTimeline.rainAlpha(t), t);
            assertInUnit("zoomBlur", BoostTimeline.zoomBlur(t), t);
            assertInUnit("aberration", BoostTimeline.aberration(t), t);
            assertInUnit("emblemAlpha", BoostTimeline.emblemAlpha(t), t);
            assertInUnit("fly", BoostTimeline.fly(t), t);
            assertInUnit("overall", BoostTimeline.overall(t), t);
            assertInUnit("frame", BoostTimeline.frame(t), t);
            assertInUnit("gauge", BoostTimeline.gauge(t), t);
        }
    }

    @Test
    public void sceneIsDarkWhilePanelsFallAndRevealsTheCardAtTheEnd() {
        assertTrue(BoostTimeline.scrim(BoostTimeline.SUCK_START) > 0.95f);
        assertTrue(BoostTimeline.scrim(BoostTimeline.GAUGE_START) > 0.95f);
        assertEquals(0.6f, BoostTimeline.scrim(BoostTimeline.FLY_END), 0.02f);
        assertEquals(0f, BoostTimeline.scrim(BoostTimeline.DURATION_MS), EPS);
    }

    @Test
    public void emblemLandsInTheCardOnce() {
        assertEquals(0f, BoostTimeline.emblemAlpha(BoostTimeline.BUILD_START), EPS);
        assertEquals(1f, BoostTimeline.emblemAlpha(BoostTimeline.FLY_START), EPS);
        assertEquals(1f, BoostTimeline.fly(BoostTimeline.FLY_END), EPS);
        assertEquals(0f, BoostTimeline.emblemAlpha(BoostTimeline.FLY_END), EPS);
        assertFalse(BoostTimeline.arrived(BoostTimeline.FLY_END - 1f));
        assertTrue(BoostTimeline.arrived(BoostTimeline.FLY_END));
        assertEquals(0f, BoostTimeline.popAge(BoostTimeline.BANG_AT - 10f), 0f);
        assertEquals(1f, BoostTimeline.popAge(BoostTimeline.BANG_AT + 1000f), EPS);
        assertEquals(0f, BoostTimeline.overall(BoostTimeline.DURATION_MS), EPS);
    }

    private static void assertInUnit(String name, float value, float t) {
        assertTrue(name + " out of range at " + t + ": " + value, value >= -1e-4f && value <= 1f + 1e-4f);
    }
}
