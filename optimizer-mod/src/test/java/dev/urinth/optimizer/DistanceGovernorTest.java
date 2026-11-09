package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistanceGovernorTest {
    /** A device: how long one frame takes at a given distance (chunks), and the frame limit. */
    private record Device(double baseMs, double perChunkSquaredMs, int cap) {
        double fps(int chunks) {
            return Math.min(cap, 1000.0 / (baseMs + perChunkSquaredMs * chunks * chunks));
        }
    }

    /** A phone that cannot hold 60 FPS at 10 chunks: 38 at 10, 44 at 9, 51 at 8, 59.5 at 7, 60 (the limit) at 6 and under. */
    private static final Device HEAVY = new Device(8.0, 0.18, 60);
    /** A device with room to spare at any distance. */
    private static final Device LIGHT = new Device(4.0, 0.05, 60);

    private static int run(DistanceGovernor g, Device d, int seconds, int real, int min, int target) {
        int soft = real;
        for (int s = 0; s < seconds; s++) {
            soft = g.second((int) Math.round(d.fps(Math.max(1, g.soft() == 0 ? real : g.soft()))), target, real, min, true);
        }
        return soft;
    }

    @Test
    void aPhoneThatCannotHoldTheTargetSettlesAtTheLongestDistanceThatCan() {
        DistanceGovernor g = new DistanceGovernor();
        int soft = run(g, HEAVY, 240, 10, 5, 60);
        assertTrue(soft == 7 || soft == 6, "settled at " + soft);
        assertTrue(HEAVY.fps(g.soft()) >= 58, "frame rate there: " + HEAVY.fps(g.soft()));
    }

    @Test
    void itGetsThereInAFewSecondsNotMinutes() {
        DistanceGovernor g = new DistanceGovernor();
        run(g, HEAVY, 20, 10, 5, 60);
        // 38 FPS is far below the target: the first step is big, so within 20 seconds it is close
        assertTrue(g.soft() <= 8, "after 20 s: " + g.soft());
    }

    @Test
    void aDeviceWithRoomNeverLosesAChunk() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 600; s++) {
            int soft = g.second((int) Math.round(LIGHT.fps(g.soft() == 0 ? 10 : g.soft())), 60, 10, 5, true);
            assertEquals(10, soft);
        }
        assertEquals(0, g.changes());
    }

    @Test
    void itDoesNotKeepChangingOnceSettled() {
        DistanceGovernor g = new DistanceGovernor();
        run(g, HEAVY, 120, 10, 5, 60);
        int before = g.changes();
        run(g, HEAVY, 600, 10, 5, 60);
        // it may try one chunk more now and then (and step back), but the waits double, so there are few changes in ten minutes
        assertTrue(g.changes() - before <= 10, "changes in 10 minutes: " + (g.changes() - before));
    }

    @Test
    void theFrameRateAfterSettlingStaysNearTheTarget() {
        DistanceGovernor g = new DistanceGovernor();
        run(g, HEAVY, 120, 10, 5, 60);
        int low = 0;
        int seconds = 600;
        for (int s = 0; s < seconds; s++) {
            int fps = (int) Math.round(HEAVY.fps(g.soft()));
            if (fps < 54) low++;
            g.second(fps, 60, 10, 5, true);
        }
        // the occasional attempt for one more chunk costs a couple of seconds below 90% each time
        assertTrue(low <= seconds * 0.04, "seconds below 54 FPS: " + low + " of " + seconds);
    }

    @Test
    void oneBadSecondIsAHitchNotAReason() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 100; s++) {
            int fps = s % 10 == 5 ? 12 : 60;
            assertEquals(10, g.second(fps, 60, 10, 5, true));
        }
        assertEquals(0, g.changes());
    }

    @Test
    void neverBelowTheMinimum() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 200; s++) {
            int soft = g.second(10, 60, 10, 8, true);
            assertTrue(soft >= 8, "went to " + soft);
        }
        assertEquals(8, g.soft());
    }

    @Test
    void neverAboveWhatThePlayerChose() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 300; s++) {
            int soft = g.second(500, 60, 6, 3, true);
            assertTrue(soft <= 6);
        }
        assertEquals(6, g.soft());
    }

    @Test
    void theFurtherBelowTheBiggerTheFirstStep() {
        DistanceGovernor mild = new DistanceGovernor();
        mild.second(52, 60, 12, 4, true);
        assertEquals(11, mild.second(52, 60, 12, 4, true));
        DistanceGovernor bad = new DistanceGovernor();
        bad.second(40, 60, 12, 4, true);
        assertEquals(10, bad.second(40, 60, 12, 4, true));
        DistanceGovernor awful = new DistanceGovernor();
        awful.second(20, 60, 12, 4, true);
        assertEquals(9, awful.second(20, 60, 12, 4, true));
    }

    @Test
    void whenShorterDoesNotHelpItGivesTheChunksBackAndStopsTrying() {
        DistanceGovernor g = new DistanceGovernor();
        int soft = 10;
        int minSeen = 10;
        for (int s = 0; s < 40; s++) {
            soft = g.second(35, 60, 10, 5, true); // 35 FPS whatever the distance
            minSeen = Math.min(minSeen, soft);
        }
        assertEquals(10, soft, "it should have given the chunks back");
        assertTrue(minSeen < 10, "it did try first");
        assertTrue(g.giveUp());
        // and it stays put for a while
        for (int s = 0; s < 60; s++) assertEquals(10, g.second(35, 60, 10, 5, true));
    }

    @Test
    void aFailedAttemptForOneMoreChunkWaitsLongerNextTime() {
        DistanceGovernor g = new DistanceGovernor();
        Device d = HEAVY;
        run(g, d, 90, 10, 5, 60);
        int settled = g.soft();
        // count the seconds between two attempts to go up
        int[] gaps = new int[3];
        int found = 0;
        int sinceUp = 0;
        for (int s = 0; s < 1500 && found < gaps.length; s++) {
            int before = g.soft();
            g.second((int) Math.round(d.fps(g.soft())), 60, 10, 5, true);
            sinceUp++;
            if (g.soft() > before) {
                if (found > 0 || sinceUp > 0) gaps[found++] = sinceUp;
                sinceUp = 0;
            }
        }
        assertTrue(found >= 2, "attempts found: " + found);
        assertTrue(gaps[1] >= gaps[0], "the second wait is not shorter: " + gaps[0] + ", " + gaps[1]);
        assertTrue(settled >= 5);
    }

    @Test
    void roomToSpareBringsTheChunksBackQuickly() {
        DistanceGovernor g = new DistanceGovernor();
        // the player is in a heavy place for a while (the frame rate follows the distance), then somewhere easy (far above the target)
        for (int s = 0; s < 40; s++) g.second((int) Math.round(HEAVY.fps(g.soft() == 0 ? 10 : g.soft())), 60, 10, 5, true);
        int low = g.soft();
        assertTrue(low < 10, "after the heavy place: " + low);
        int seconds = 0;
        while (g.soft() < 10 && seconds < 200) {
            g.second(100, 60, 10, 5, true);
            seconds++;
        }
        assertEquals(10, g.soft());
        assertTrue(seconds <= 100, "took " + seconds + " s");
    }

    @Test
    void nothingChangesWhileTheGameIsNotReallyRunning() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 100; s++) assertEquals(10, g.second(5, 60, 10, 5, false));
        assertEquals(0, g.changes());
        assertFalse(g.struggling());
    }

    @Test
    void aMenuInTheMiddleDoesNotCountTowardsAChange() {
        DistanceGovernor g = new DistanceGovernor();
        g.second(20, 60, 10, 5, true);
        for (int s = 0; s < 30; s++) g.second(300, 60, 10, 5, false); // the pause menu: very high FPS, ignored
        assertEquals(10, g.soft());
        // one low second before and one after do not make a streak of two
        assertEquals(10, g.second(20, 60, 10, 5, true));
    }

    @Test
    void raisingTheRenderDistanceDrawsItAtOnceThenAdapts() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 20; s++) g.second(60, 60, 6, 4, true);
        assertEquals(6, g.soft());
        assertEquals(10, g.second(60, 60, 10, 4, true));
        // and if that is too much for the device, it comes back
        for (int s = 0; s < 40; s++) g.second((int) Math.round(HEAVY.fps(g.soft())), 60, 10, 4, true);
        assertTrue(g.soft() < 10);
    }

    @Test
    void loweringTheRenderDistanceBelowWhatIsDrawnFollowsAtOnce() {
        DistanceGovernor g = new DistanceGovernor();
        g.second(60, 60, 12, 4, true);
        assertEquals(8, g.second(60, 60, 8, 4, true));
    }

    @Test
    void theStatusSaysWhyItChanged() {
        DistanceGovernor g = new DistanceGovernor();
        g.second(30, 60, 10, 5, true);
        g.second(30, 60, 10, 5, true);
        assertTrue(g.reason().contains("10 -> "), g.reason());
        assertTrue(g.struggling());
        assertEquals(30, g.lastFps());
        assertEquals(60, g.lastTarget());
    }

    @Test
    void resetStartsOver() {
        DistanceGovernor g = new DistanceGovernor();
        for (int s = 0; s < 40; s++) g.second((int) Math.round(HEAVY.fps(g.soft() == 0 ? 10 : g.soft())), 60, 10, 5, true);
        assertTrue(g.soft() < 10, "before the reset: " + g.soft());
        g.reset();
        assertEquals(0, g.soft());
        assertEquals(10, g.second(60, 60, 10, 5, true));
    }

    @Test
    void nonsenseInputsDoNotBreakIt() {
        DistanceGovernor g = new DistanceGovernor();
        assertEquals(1, g.second(-5, 0, 0, 0, true));
        assertTrue(g.second(Integer.MAX_VALUE, Integer.MAX_VALUE, 3, 9, true) >= 1);
    }
}
