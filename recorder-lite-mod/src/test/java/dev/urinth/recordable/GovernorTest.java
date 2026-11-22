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
                governor.onFrame(interval, everCaptured ? sinceCapture : 0, hookNs);
                boolean due = clock + frameNs / 2 >= nextCapture;
                int spacing = governor.needsCleanFrame() ? 3 : 2;
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
        assertEquals(30, new Governor(60).fps());
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
    void aSlowGameStillGetsFramesFreeOfCapturesSoTheCostCanBeMeasured() {
        // a 45 fps game and a 30 fps capture would be "every second frame"; the recorder keeps a frame free now and then
        Sim sim = new Sim(new Governor(30), 45);
        sim.extraFirstNs = ms(1);
        sim.hookNs = ms(0.3);
        sim.run(40);
        assertTrue(sim.governor.graphicsMeasured());
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
}
