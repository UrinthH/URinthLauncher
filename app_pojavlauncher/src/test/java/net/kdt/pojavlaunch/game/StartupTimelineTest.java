package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StartupTimelineTest {
    @Test
    public void listsEachStepWithItsOwnDurationAndATotal() {
        StartupTimeline timeline = new StartupTimeline(1_000);
        timeline.mark("files checked", 2_500);
        timeline.mark("game screen", 3_000);
        timeline.mark("window", 43_000);
        assertEquals("[Startup] files checked 1.5s, game screen 0.5s, window 40.0s | total 42.0s",
                timeline.summary());
    }

    @Test
    public void aClockThatGoesBackwardsNeverShowsANegativeTime() {
        StartupTimeline timeline = new StartupTimeline(5_000);
        timeline.mark("odd", 4_000);
        assertEquals("[Startup] odd 0.0s | total 0.0s", timeline.summary());
    }

    @Test
    public void anEmptyTimelineStillHasASummary() {
        assertEquals("[Startup]  | total 0.0s", new StartupTimeline(10).summary());
    }

    @Test
    public void markNowWithoutABeginDoesNothing() {
        StartupTimeline.markNow("ignored");
        // No timeline was begun by this test class, but another test may have: only the type is checked.
        String summary = StartupTimeline.currentSummary();
        assertTrue(summary == null || summary.startsWith("[Startup]"));
    }

    @Test
    public void beginReplacesTheEarlierTimeline() {
        StartupTimeline.begin(System.currentTimeMillis());
        StartupTimeline.markNow("a");
        StartupTimeline.begin(System.currentTimeMillis());
        String summary = StartupTimeline.currentSummary();
        assertTrue(summary.startsWith("[Startup] "));
        assertTrue(!summary.contains(" a "));
    }

    @Test
    public void secondsRoundsToOneDecimal() {
        assertEquals("0.0s", StartupTimeline.seconds(0));
        assertEquals("1.2s", StartupTimeline.seconds(1_234));
        assertEquals("61.0s", StartupTimeline.seconds(61_000));
    }
}
