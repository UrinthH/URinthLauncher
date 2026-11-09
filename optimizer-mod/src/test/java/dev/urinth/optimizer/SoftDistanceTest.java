package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoftDistanceTest {
    /** A fake clock: every frame is 16 ms. */
    private long now = 1_000_000_000L;

    private float frame(float vanilla) {
        now += 16_000_000L;
        return SoftDistance.fogAt(vanilla, now);
    }

    @BeforeEach
    void clean() {
        SoftDistance.reset();
        now = 1_000_000_000L;
    }

    @Test
    void doesNothingUntilTold() {
        assertFalse(SoftDistance.active());
        assertEquals(Integer.MAX_VALUE, SoftDistance.cullLimit());
        assertEquals(160f, frame(160f), 0f);
    }

    @Test
    void aShorterDistanceMovesTheFogInSmoothlyNotInOneJump() {
        frame(160f); // the first frame
        SoftDistance.apply(7, 10, true);
        assertTrue(SoftDistance.active());
        float previous = frame(160f);
        assertTrue(previous < 160f && previous > 150f, "the first frame after the change: " + previous);
        int frames = 0;
        float biggestStep = 0f;
        float f = previous;
        while (f > 112f && frames < 1000) {
            f = frame(160f);
            biggestStep = Math.max(biggestStep, previous - f);
            previous = f;
            frames++;
        }
        assertEquals(112f, f, 0.001f);
        assertTrue(biggestStep <= 32f * 0.016f + 0.01f, "no frame moves it by more than the rate allows: " + biggestStep);
        // 48 blocks at 32 blocks a second: 1.5 seconds, about 94 frames
        assertTrue(frames >= 90 && frames <= 100, "frames: " + frames);
    }

    @Test
    void approachMovesAtTheRateAndStopsOnTheGoal() {
        assertEquals(132f, SoftDistance.approach(100f, 200f, 1f, 32f), 0.001f);
        assertEquals(200f, SoftDistance.approach(190f, 200f, 1f, 32f), 0.001f);
        assertEquals(68f, SoftDistance.approach(100f, 40f, 1f, 32f), 0.001f);
        assertEquals(40f, SoftDistance.approach(50f, 40f, 1f, 32f), 0.001f);
        assertEquals(100f, SoftDistance.approach(100f, 40f, 0f, 32f), 0.001f);
        assertEquals(100f, SoftDistance.approach(100f, 40f, -5f, 32f), 0.001f);
    }

    @Test
    void aLongPauseDoesNotTeleportTheFog() {
        frame(160f);
        SoftDistance.apply(5, 10, true);
        now += 60_000_000_000L; // the game was paused for a minute
        float f = SoftDistance.fogAt(160f, now);
        assertTrue(f > 150f, "one step of at most a quarter second: " + f);
    }

    @Test
    void theFogIsNeverFartherThanWhatTheGameWanted() {
        frame(160f);
        SoftDistance.apply(9, 10, true);
        for (int i = 0; i < 20; i++) assertTrue(frame(80f) <= 80f);
        // the player lowered the render distance below the soft one
        assertEquals(48f, frame(48f), 0f);
    }

    @Test
    void cullingFollowsTheFogInAndOpensAtOnceOnTheWayOut() {
        frame(160f);
        // shrinking: the fog is still at 160 so the culling does not close yet
        SoftDistance.apply(6, 10, true);
        assertEquals(10, SoftDistance.cullLimit());
        // once the fog has come in over the chunks that are dropped, the culling closes, one chunk at a time
        int last = 10;
        for (int i = 0; i < 400; i++) {
            frame(160f);
            int limit = SoftDistance.cullLimit();
            assertTrue(limit <= last && limit >= last - 1, "it closes a chunk at a time: " + last + " -> " + limit);
            last = limit;
        }
        assertEquals(6, SoftDistance.cullLimit());
        // growing: the culling opens at once, to the new distance, before the fog gets there
        SoftDistance.apply(9, 10, true);
        assertEquals(9, SoftDistance.cullLimit());
        assertTrue(frame(160f) < 9 * 16f, "the fog is still on its way");
    }

    @Test
    void cullingCanBeSwitchedOff() {
        frame(160f);
        SoftDistance.apply(6, 10, false);
        assertEquals(Integer.MAX_VALUE, SoftDistance.cullLimit());
        assertTrue(SoftDistance.active(), "the fog is still moved");
    }

    @Test
    void cullingNeverClosesBelowTheMinimum() {
        frame(160f);
        SoftDistance.apply(1, 10, true);
        for (int i = 0; i < 500; i++) {
            frame(160f);
            assertTrue(SoftDistance.cullLimit() >= SoftDistance.MIN_CULL);
        }
    }

    @Test
    void backToTheFullDistanceTheModStopsInterfering() {
        frame(160f);
        SoftDistance.apply(6, 10, true);
        for (int i = 0; i < 300; i++) frame(160f);
        SoftDistance.apply(10, 10, true);
        float f = 0f;
        for (int i = 0; i < 1000 && SoftDistance.active(); i++) f = frame(160f);
        assertFalse(SoftDistance.active());
        assertEquals(160f, f, 0.001f);
        assertEquals(Integer.MAX_VALUE, SoftDistance.cullLimit());
    }

    @Test
    void resetGoesBackToTheGamesOwnBehaviour() {
        frame(160f);
        SoftDistance.apply(5, 10, true);
        SoftDistance.reset();
        assertFalse(SoftDistance.active());
        assertEquals(160f, frame(160f), 0f);
        assertEquals(Integer.MAX_VALUE, SoftDistance.cullLimit());
    }

    @Test
    void theFullDistanceFromTheStartDoesNotStartAnything() {
        SoftDistance.apply(10, 10, true);
        assertFalse(SoftDistance.active());
    }

    @Test
    void theGamesOwnCallUsesTheRealClock() {
        SoftDistance.apply(5, 10, true);
        float f = SoftDistance.fogFor(160f);
        assertTrue(f <= 160f && f > 0f);
    }
}
