package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LaunchZoomMathTest {
    private static final float EPS = 1e-4f;

    // ---- the shrink ------------------------------------------------------------------------

    @Test
    public void theShrinkRunsFromTheFullScreenToTheSmallWindowInItsTime() {
        assertEquals(0f, LaunchZoomMath.shrinkProgress(0), EPS);
        assertEquals(0f, LaunchZoomMath.shrinkProgress(-30), EPS);
        assertEquals(0.5f, LaunchZoomMath.shrinkProgress(LaunchZoomMath.SHRINK_MS / 2), 0.01f);
        assertEquals(1f, LaunchZoomMath.shrinkProgress(LaunchZoomMath.SHRINK_MS), EPS);
        assertEquals(1f, LaunchZoomMath.shrinkProgress(LaunchZoomMath.SHRINK_MS + 5000), EPS);

        assertEquals(1f, LaunchZoomMath.shrinkExpansion(0f), EPS);
        assertEquals(0f, LaunchZoomMath.shrinkExpansion(1f), EPS);
    }

    @Test
    public void theShrinkNeverGoesBackwards() {
        float previous = 2f;
        for (int i = 0; i <= 200; i++) {
            float value = LaunchZoomMath.shrinkExpansion(i / 200f);
            assertTrue("at " + i, value <= previous + 1e-6f);
            previous = value;
        }
    }

    @Test
    public void theLoadingScreenFadesInOverTheShrinkAndIsAllThereBeforeItEnds() {
        assertEquals(0f, LaunchZoomMath.hudAlpha(0f), EPS);
        assertEquals(0f, LaunchZoomMath.hudAlpha(0.25f), EPS);
        assertEquals(1f, LaunchZoomMath.hudAlpha(0.70f), EPS);
        assertEquals(1f, LaunchZoomMath.hudAlpha(1f), EPS);
        float previous = -1f;
        for (int i = 0; i <= 100; i++) {
            float value = LaunchZoomMath.hudAlpha(i / 100f);
            assertTrue(value >= previous);
            previous = value;
        }
    }

    // ---- the zoom --------------------------------------------------------------------------

    @Test
    public void theZoomRunsFromZeroToOneInItsTime() {
        assertEquals(0f, LaunchZoomMath.expandProgress(0), EPS);
        assertEquals(0f, LaunchZoomMath.expandProgress(-5), EPS);
        assertEquals(0.5f, LaunchZoomMath.expandProgress(LaunchZoomMath.EXPAND_MS / 2), 0.01f);
        assertEquals(1f, LaunchZoomMath.expandProgress(LaunchZoomMath.EXPAND_MS), EPS);
        assertEquals(1f, LaunchZoomMath.expandProgress(LaunchZoomMath.EXPAND_MS + 900), EPS);
    }

    @Test
    public void theZoomIsFast() {
        assertTrue("zoom takes " + LaunchZoomMath.EXPAND_MS + " ms", LaunchZoomMath.EXPAND_MS <= 450);
        assertTrue(LaunchZoomMath.EXPAND_MS < LaunchZoomMath.SHRINK_MS);
    }

    @Test
    public void theZoomEaseGoesFromZeroToOneNeverBackwardsAndStartsFast() {
        assertEquals(0f, LaunchZoomMath.expandEase(0f), EPS);
        assertEquals(1f, LaunchZoomMath.expandEase(1f), EPS);
        float previous = -1f;
        for (int i = 0; i <= 200; i++) {
            float value = LaunchZoomMath.expandEase(i / 200f);
            assertTrue("at " + i, value >= previous);
            previous = value;
        }
        // Most of the way is covered early: a quick push-in, then a soft landing.
        assertTrue(LaunchZoomMath.expandEase(0.25f) > 0.40f);
        assertTrue(LaunchZoomMath.expandEase(0.5f) > 0.70f);
    }

    // ---- what the expansion looks like -------------------------------------------------------

    @Test
    public void theWindowIsTheMiniSizeAtFirstAndExactlyTheFullScreenAtTheEnd() {
        assertEquals(LaunchZoomMath.MINI_SCALE, LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, 0f), EPS);
        assertEquals(1f, LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, 1f), EPS);
        assertEquals(1f, LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, 5f), EPS);
        assertEquals(LaunchZoomMath.MINI_SCALE, LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, -3f), EPS);
    }

    @Test
    public void theSmallWindowIsSmallButBigEnoughToRead() {
        assertTrue(LaunchZoomMath.MINI_SCALE >= 0.4f && LaunchZoomMath.MINI_SCALE <= 0.8f);
    }

    @Test
    public void theWindowGrowsByTheSameFactorEveryStep() {
        float a = LaunchZoomMath.windowScale(0.6f, 0.25f);
        float b = LaunchZoomMath.windowScale(0.6f, 0.50f);
        float c = LaunchZoomMath.windowScale(0.6f, 0.75f);
        assertEquals(b / a, c / b, 1e-4f);
    }

    @Test
    public void aMiniSizeThatIsNotASmallWindowMeansTheFullScreen() {
        assertEquals(1f, LaunchZoomMath.windowScale(0f, 0.3f), EPS);
        assertEquals(1f, LaunchZoomMath.windowScale(1f, 0.3f), EPS);
        assertEquals(1f, LaunchZoomMath.windowScale(1.4f, 0.3f), EPS);
    }

    @Test
    public void cornersAreRoundOnTheWindowAndSquareOnTheFullScreen() {
        assertEquals(22f, LaunchZoomMath.cornerRadius(22f, 0f), EPS);
        assertEquals(0f, LaunchZoomMath.cornerRadius(22f, 1f), EPS);
        assertTrue(LaunchZoomMath.cornerRadius(22f, 0.5f) < 22f);
    }

    @Test
    public void theShadowAndTheEdgeBelongToTheSmallWindowOnly() {
        assertEquals(1f, LaunchZoomMath.shadowAlpha(0f), EPS);
        assertEquals(0f, LaunchZoomMath.shadowAlpha(1f), EPS);
        assertEquals(1f, LaunchZoomMath.edgeAlpha(0f), EPS);
        assertEquals(0f, LaunchZoomMath.edgeAlpha(1f), EPS);
        assertEquals(0f, LaunchZoomMath.edgeAlpha(0.8f), EPS);
    }

    @Test
    public void theCaptionIsOnlyThereWhileTheWindowIsSmall() {
        assertEquals(1f, LaunchZoomMath.captionAlpha(0f), EPS);
        assertEquals(0f, LaunchZoomMath.captionAlpha(0.3f), EPS);
        assertEquals(0f, LaunchZoomMath.captionAlpha(1f), EPS);
    }

    @Test
    public void theShrinkEndsOnTheWindowTheZoomStartsFrom() {
        float e = LaunchZoomMath.shrinkExpansion(1f);
        assertEquals(LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, 0f),
                LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, e), EPS);
        assertEquals(LaunchZoomMath.cornerRadius(22f, 0f), LaunchZoomMath.cornerRadius(22f, e), EPS);
        assertEquals(LaunchZoomMath.shadowAlpha(0f), LaunchZoomMath.shadowAlpha(e), EPS);
        assertEquals(LaunchZoomMath.captionAlpha(0f), LaunchZoomMath.captionAlpha(e), EPS);
    }

    @Test
    public void theEasesAreFlatAtBothEnds() {
        assertEquals(0f, LaunchZoomMath.smooth(0f), EPS);
        assertEquals(1f, LaunchZoomMath.smooth(1f), EPS);
        assertEquals(0f, LaunchZoomMath.smoother(-1f), EPS);
        assertEquals(1f, LaunchZoomMath.smoother(2f), EPS);
        assertEquals(0.5f, LaunchZoomMath.smoother(0.5f), EPS);
    }

    // ---- where things go ---------------------------------------------------------------------

    @Test
    public void aViewThatIsTheWholeScreenIsTheScreen() {
        assertTrue(LaunchZoomMath.coversScreen(2400, 1080, 2400, 1080));
    }

    @Test
    public void aViewThatKeepsClearOfACameraCutOutStillCountsAsTheScreen() {
        assertTrue(LaunchZoomMath.coversScreen(2280, 1080, 2400, 1080));
        assertTrue(LaunchZoomMath.coversScreen(2400, 1020, 2400, 1080));
    }

    @Test
    public void aSplitScreenOrAnOversizedViewIsNotTheScreen() {
        assertFalse(LaunchZoomMath.coversScreen(1200, 1080, 2400, 1080));
        assertFalse(LaunchZoomMath.coversScreen(2400, 540, 2400, 1080));
        assertFalse(LaunchZoomMath.coversScreen(2500, 1080, 2400, 1080));
    }

    @Test
    public void unknownSizesAreNotTheScreen() {
        assertFalse(LaunchZoomMath.coversScreen(0, 0, 2400, 1080));
        assertFalse(LaunchZoomMath.coversScreen(2400, 1080, 0, 0));
        assertFalse(LaunchZoomMath.coversScreen(-1, 1080, 2400, 1080));
    }

    @Test
    public void theCaptionGoesJustBelowTheWindow() {
        assertEquals(840f, LaunchZoomMath.captionTop(800f, 40f, 100f, 1080f), EPS);
    }

    @Test
    public void theCaptionDoesNotRunOffTheBottom() {
        assertEquals(940f, LaunchZoomMath.captionTop(1000f, 40f, 100f, 1080f), EPS);
        // A view too short for the text at all: it starts at the top rather than above it.
        assertEquals(0f, LaunchZoomMath.captionTop(800f, 40f, 100f, 50f), EPS);
    }
}
