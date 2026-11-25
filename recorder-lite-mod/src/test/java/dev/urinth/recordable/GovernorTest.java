package dev.urinth.recordable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Plays a game at a fixed frame rate for the governor and mimics what {@link RecorderSession} does with it: a capture is started when
 * it is due, the frame right after a capture can be made longer, and the capture code can take processor time.
 */
final class GovernorTest {
    private static final class Sim {
        final Governor governor;
        final long frameNs;
        long clock;
        long nextCapture;
        int sinceCapture;
        boolean everCaptured;
        long extraFirstNs;
        long extraSecondNs;
        long hookNs;
        /** The recorder takes no picture for this long after the start, so the game's own speed can be measured. */
        long preRollNs = 300_000_000L;

        Sim(Governor governor, double gameFps) {
            this.governor = governor;
            this.frameNs = (long) (1e9 / gameFps);
        }

        void run(double seconds) {
            long end = clock + (long) (seconds * 1e9);
            while (clock < end) {
                if (everCaptured) {
                    sinceCapture++;
                }
                long interval = frameNs;
                if (everCaptured && sinceCapture == 1) {
                    interval += extraFirstNs;
                } else if (everCaptured && sinceCapture == 2) {
                    interval += extraSecondNs;
                }
                clock += interval;
                // the same rule as the session: pictures on most frames mean fast mode
                boolean fast = 1e9 / governor.fps() < 1.8 * frameNs;
                governor.onFrame(interval, everCaptured ? sinceCapture : 0, hookNs, fast);
                boolean due = clock >= preRollNs && clock + frameNs / 2 >= nextCapture;
                int spacing = fast ? 1 : governor.needsCleanFrame() ? 3 : 2;
                if (due && (!everCaptured || sinceCapture >= spacing)) {
                    everCaptured = true;
                    sinceCapture = 0;
                    long step = 1_000_000_000L / governor.fps();
                    nextCapture += step;
                    if (nextCapture < clock + step / 4) {
                        nextCapture = clock + step;
                    }
                }
            }
        }
    }

    private static long ms(double v) {
        return (long) (v * 1e6);
    }

    @Test
    void startsAtTheHighestRateNotAboveTheRequest() {
        assertEquals(90, new Governor(90).fps());
        assertEquals(90, new Governor(120).fps());
        assertEquals(60, new Governor(60).fps());
        assertEquals(45, new Governor(50).fps());
        assertEquals(30, new Governor(40).fps());
        assertEquals(30, new Governor(30).fps());
        assertEquals(24, new Governor(25).fps());
        assertEquals(24, new Governor(24).fps());
        assertEquals(10, new Governor(5).fps());
    }

    @Test
    void cheapCaptureKeepsTheRate() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.extraFirstNs = ms(0.2);
        sim.hookNs = ms(0.3);
        sim.run(90);
        assertEquals(30, sim.governor.fps());
        assertFalse(sim.governor.stopRequested());
        assertTrue(sim.governor.graphicsMeasured(), "with a 90 fps game there are frames without a capture close by");
        assertTrue(sim.governor.share() < Governor.BUDGET, "share " + sim.governor.share());
    }

    @Test
    void expensiveGraphicsWorkLowersTheRateToTheBottomButDoesNotStop() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.extraFirstNs = ms(6);
        sim.extraSecondNs = ms(3);
        sim.hookNs = ms(0.3);
        sim.run(150);
        assertEquals(10, sim.governor.fps());
        assertFalse(sim.governor.stopRequested(), "about 9% at the lowest rate is below the stop limit");
        assertTrue(sim.governor.reason().contains("down to"), sim.governor.reason());
    }

    @Test
    void expensiveProcessorWorkAloneLowersTheRate() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.hookNs = ms(5);
        sim.run(150);
        assertEquals(10, sim.governor.fps());
        assertFalse(sim.governor.stopRequested());
    }

    @Test
    void stopsWhenEvenTheLowestRateIsFarTooExpensive() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.extraFirstNs = ms(30);
        sim.run(150);
        assertTrue(sim.governor.stopRequested());
        assertTrue(sim.governor.reason().contains("even at 10 fps"), sim.governor.reason());
    }

    @Test
    void goesBackUpWhenTheCostDisappears() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.extraFirstNs = ms(6);
        sim.extraSecondNs = ms(3);
        sim.run(100);
        assertEquals(10, sim.governor.fps());
        sim.extraFirstNs = 0;
        sim.extraSecondNs = 0;
        sim.hookNs = ms(0.2);
        sim.run(260);
        assertEquals(30, sim.governor.fps(), "reason: " + sim.governor.reason());
    }

    @Test
    void neverGoesAboveTheRequestedRate() {
        Sim sim = new Sim(new Governor(20), 90);
        sim.hookNs = ms(0.1);
        sim.run(120);
        assertEquals(20, sim.governor.fps());
    }

    @Test
    void anEncoderThatCannotKeepUpLowersTheRate() {
        Governor g = new Governor(30);
        g.onBacklog();
        g.onBacklog();
        assertEquals(30, g.fps());
        g.onBacklog();
        assertEquals(24, g.fps());
        assertTrue(g.reason().contains("encoder"), g.reason());
    }

    @Test
    void aGameAtSixtyWithAThirtyFpsCaptureStillGetsFramesFreeOfCapturesSoTheCostCanBeMeasured() {
        // every second frame is a capture at best; the recorder keeps a frame free now and then so the graphics cost can be seen
        Sim sim = new Sim(new Governor(30), 60);
        sim.extraFirstNs = ms(1);
        sim.hookNs = ms(0.3);
        sim.run(40);
        assertFalse(sim.governor.fastMode());
        assertTrue(sim.governor.graphicsMeasured());
        assertEquals(30, sim.governor.fps());
    }

    @Test
    void aGameAtFortyFiveWithAThirtyFpsCaptureTakesPicturesOnMostFramesSoItUsesFastMode() {
        Sim sim = new Sim(new Governor(30), 45);
        sim.extraFirstNs = ms(0.5);
        sim.hookNs = ms(0.3);
        sim.run(40);
        assertTrue(sim.governor.fastMode(), "a picture on two of every three frames leaves nothing to compare with");
        assertTrue(sim.governor.hasBaseline(), "the speed before the first picture was measured");
        assertEquals(30, sim.governor.fps());
    }

    @Test
    void anExpensiveCaptureIsSeenEvenWhenItWouldPutTheGameInAnEverySecondFrameRhythm() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.extraFirstNs = ms(30);
        sim.run(60);
        assertTrue(sim.governor.fps() < 30, "the rate went down, it is " + sim.governor.fps());
        assertTrue(sim.governor.graphicsMeasured());
    }

    @Test
    void occasionalLongStallsAreNotBlamedOnTheRecorder() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.hookNs = ms(0.2);
        sim.run(20);
        for (int i = 0; i < 6; i++) {
            sim.governor.onFrame(800_000_000L, 1 + (i % 3), ms(0.2));   // the game froze for 0.8 s, like a world loading
            sim.run(3);
        }
        sim.run(20);
        assertEquals(30, sim.governor.fps());
        assertFalse(sim.governor.stopRequested());
    }

    // ---- fast mode (90 fps) ----------------------------------------------------------------------------------------------------

    @Test
    void ninetyFramesPerSecondIsKeptWhenTheCaptureIsCheap() {
        Sim sim = new Sim(new Governor(90), 90);
        sim.extraFirstNs = ms(0.2);
        sim.hookNs = ms(0.3);
        sim.run(90);
        assertEquals(90, sim.governor.fps(), sim.governor.reason());
        assertTrue(sim.governor.fastMode());
        assertTrue(sim.governor.hasBaseline(), "the first moments, before any picture, give the baseline");
        assertFalse(sim.governor.stopRequested());
    }

    @Test
    void ninetyFramesPerSecondStepsDownWhenTheGameBecomesClearlySlower() {
        Sim sim = new Sim(new Governor(90), 90);
        sim.extraFirstNs = ms(3.0);
        sim.hookNs = ms(0.3);
        sim.run(40);
        assertTrue(sim.governor.fps() < 90, "still " + sim.governor.fps());
        assertTrue(sim.governor.reason().contains("slower") || sim.governor.reason().contains("used"), sim.governor.reason());
        assertFalse(sim.governor.stopRequested());
    }

    @Test
    void theFastModeStepDownLeadsToNormalModeWhereTheCostIsMeasuredProperly() {
        Sim sim = new Sim(new Governor(90), 90);
        sim.extraFirstNs = ms(6);
        sim.extraSecondNs = ms(3);
        sim.hookNs = ms(0.3);
        sim.run(200);
        assertTrue(sim.governor.fps() <= 30, "ended at " + sim.governor.fps() + ": " + sim.governor.reason());
        assertFalse(sim.governor.fastMode());
        assertFalse(sim.governor.stopRequested());
    }

    @Test
    void afterAFastModeStepDownTheRateDoesNotBounceBackUpAtOnce() {
        Sim sim = new Sim(new Governor(90), 90);
        sim.extraFirstNs = ms(3.0);
        sim.hookNs = ms(0.3);
        sim.run(40);
        int low = sim.governor.fps();
        assertTrue(low < 90);
        sim.extraFirstNs = 0;
        sim.run(60);
        assertTrue(sim.governor.fps() <= low, "went up after only 60 s: " + sim.governor.fps());
    }

    @Test
    void aSlowGameNeverUsesFastModeAtLowCaptureRates() {
        Sim sim = new Sim(new Governor(30), 90);
        sim.hookNs = ms(0.2);
        sim.run(30);
        assertFalse(sim.governor.fastMode(), "a picture every third frame is normal mode");
    }
}
