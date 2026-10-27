package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LaunchZoomMathTest {
    private static final float EPS = 1e-4f;

    @Test
    public void thePopStartsAtZeroAndLandsExactlyOnOne() {
        assertEquals(0f, LaunchZoomMath.pop(0), EPS);
        assertEquals(0f, LaunchZoomMath.pop(-50), EPS);
        assertEquals(1f, LaunchZoomMath.pop(LaunchZoomMath.POP_MS), EPS);
        assertEquals(1f, LaunchZoomMath.pop(5000), EPS);
    }

    @Test
    public void thePopOvershootsABitOnTheWayIn() {
        float max = 0f;
        for (long t = 0; t <= LaunchZoomMath.POP_MS; t += 5) max = Math.max(max, LaunchZoomMath.pop(t));
        assertTrue("overshoot " + max, max > 1.02f && max < 1.2f);
    }

    @Test
    public void theZoomOnlyStartsAfterTheHold() {
        assertEquals(0f, LaunchZoomMath.zoomProgress(0), EPS);
        assertEquals(0f, LaunchZoomMath.zoomProgress(LaunchZoomMath.HOLD_END_MS), EPS);
        assertEquals(0.5f, LaunchZoomMath.zoomProgress(LaunchZoomMath.HOLD_END_MS + LaunchZoomMath.ZOOM_MS / 2), EPS);
        assertEquals(1f, LaunchZoomMath.zoomProgress(LaunchZoomMath.TOTAL_MS), EPS);
        assertEquals(1f, LaunchZoomMath.zoomProgress(LaunchZoomMath.TOTAL_MS + 900), EPS);
    }

    @Test
    public void theEaseGoesFromZeroToOneAndNeverBackwards() {
        assertEquals(0f, LaunchZoomMath.zoomEase(0f), EPS);
        assertEquals(1f, LaunchZoomMath.zoomEase(1f), EPS);
        assertEquals(0.5f, LaunchZoomMath.zoomEase(0.5f), EPS);
        float previous = -1f;
        for (int i = 0; i <= 100; i++) {
            float value = LaunchZoomMath.zoomEase(i / 100f);
            assertTrue(value >= previous);
            previous = value;
        }
    }

    @Test
    public void theSquareStartsAtItsBaseSizeAndEndsExactlyFillingTheScreen() {
        float base = 128f;
        assertEquals(base, LaunchZoomMath.rectSize(base, 1612f, 0f), EPS);
        assertEquals(1612f, LaunchZoomMath.rectSize(base, 1612f, 1f), 0.01f);
        assertEquals(720f, LaunchZoomMath.rectSize(base, 720f, 1f), 0.01f);
    }

    @Test
    public void theSquareGrowsByTheSameFactorEveryStep() {
        float base = 100f;
        float full = 1600f;
        float a = LaunchZoomMath.rectSize(base, full, 0.25f);
        float b = LaunchZoomMath.rectSize(base, full, 0.50f);
        float c = LaunchZoomMath.rectSize(base, full, 0.75f);
        assertEquals(b / a, c / b, 1e-3f);
    }

    @Test
    public void aSquareThatIsAlreadyBigEnoughStaysPut() {
        assertEquals(500f, LaunchZoomMath.rectSize(500f, 400f, 0.7f), EPS);
    }

    @Test
    public void aZeroBaseDoesNotDivideByZero() {
        assertEquals(0f, LaunchZoomMath.rectSize(0f, 1000f, 0f), EPS);
        assertEquals(1000f, LaunchZoomMath.rectSize(0f, 1000f, 1f), EPS);
    }

    @Test
    public void theLauncherBehindIsGoneBeforeTheSquareLandsAndWhollyThereAtTheStart() {
        assertEquals(1f, LaunchZoomMath.backdropAlpha(0f), EPS);
        assertEquals(0f, LaunchZoomMath.backdropAlpha(1f), EPS);
        assertEquals(0f, LaunchZoomMath.backdropAlpha(0.8f), EPS);
        assertEquals(1f, LaunchZoomMath.backdropScale(0f), EPS);
        assertEquals(LaunchZoomMath.BACKDROP_MAX_SCALE, LaunchZoomMath.backdropScale(1f), 1e-3f);
    }

    @Test
    public void cornersAreRoundAtFirstAndSquareAtTheEnd() {
        assertEquals(16f, LaunchZoomMath.cornerRadius(16f, 0f), EPS);
        assertEquals(0f, LaunchZoomMath.cornerRadius(16f, 1f), EPS);
    }

    @Test
    public void streaksAreGoneAtBothEndsAndStrongestInTheMiddle() {
        assertEquals(0f, LaunchZoomMath.streakAmount(0f), EPS);
        assertEquals(0f, LaunchZoomMath.streakAmount(1f), 1e-3f);
        assertEquals(1f, LaunchZoomMath.streakAmount(0.5f), EPS);
    }
}
