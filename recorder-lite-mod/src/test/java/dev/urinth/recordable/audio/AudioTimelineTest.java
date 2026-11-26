package dev.urinth.recordable.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AudioTimelineTest {
    private static final long CHUNK = 20_000;                  // microseconds in one piece

    @Test
    void piecesThatArriveOnTimeAreUsedAsTheyAreEvenWithJitter() {
        AudioTimeline t = new AudioTimeline();
        long label = 5_000_000;
        assertEquals(0, t.plan(label));
        for (int i = 1; i < 300; i++) {
            label += CHUNK + (i % 3 - 1) * 9_000;               // the audio thread wakes up a little early or late
            assertEquals(0, t.plan(label), "piece " + i);
        }
        assertEquals(0, t.silenceFrames());
        assertEquals(0, t.droppedPieces());
        assertEquals(5_000_000, t.anchorUs());
    }

    @Test
    void aStallOfTheAudioThreadBecomesSilence() {
        AudioTimeline t = new AudioTimeline();
        assertEquals(0, t.plan(0));
        assertEquals(0, t.plan(20_000));
        int frames = t.plan(340_000);                           // 300 ms went by without a piece
        assertEquals(14_400, frames);                           // 300 ms at 48 kHz
        assertEquals(14_400, t.silenceFrames());
        assertEquals(360_000, t.positionUs(), "the sound is back in step with the clock");
        assertEquals(0, t.plan(360_000));
    }

    @Test
    void aVeryLongHoleIsClosedInSteps() {
        AudioTimeline t = new AudioTimeline();
        assertEquals(0, t.plan(0));
        assertEquals(96_000, t.plan(5_000_000));                // at most two seconds at once
        assertEquals(96_000, t.plan(5_020_000));
        int last = t.plan(5_040_000);
        assertTrue(last > 40_000 && last < 50_000, "the rest of the hole: " + last);
        assertEquals(0, t.plan(5_060_000), "after that the sound is in step");
        assertEquals(5_080_000, t.positionUs());
    }

    @Test
    void piecesThatComeFasterThanRealTimeAreSkipped() {
        AudioTimeline t = new AudioTimeline();
        assertEquals(0, t.plan(0));
        int used = 0;
        int dropped = 0;
        long label = 0;
        for (int i = 0; i < 12; i++) {                          // a burst: one piece per millisecond
            label += 1_000;
            int r = t.plan(label);
            if (r == AudioTimeline.DROP) {
                dropped++;
            } else {
                used++;
                assertEquals(0, r);
            }
        }
        assertTrue(used >= 3 && used <= 5, "a few are kept: " + used);
        assertTrue(dropped >= 7, "the rest is skipped: " + dropped);
        assertEquals(dropped, t.droppedPieces());
    }

    @Test
    void theClockIsMeasuredAgainstWhatWasReallyGivenToTheEncoder() {
        AudioTimeline t = new AudioTimeline();
        assertEquals(0, t.plan(1_000_000));
        long label = 1_000_000;
        for (int i = 0; i < 100; i++) {
            label += CHUNK;
            t.plan(label);
        }
        assertEquals(label + CHUNK, t.positionUs());
    }
}
