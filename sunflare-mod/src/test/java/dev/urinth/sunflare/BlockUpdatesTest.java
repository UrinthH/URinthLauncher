package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** When a block the player changes may be rebuilt at once, and how the average frame time that Auto looks at is kept. */
class BlockUpdatesTest {
    @BeforeEach
    @AfterEach
    void reset() {
        BlockUpdates.reset();
    }

    private static void frames(int count, double ms) {
        for (int i = 0; i < count; i++) BlockUpdates.noteFrame(ms);
    }

    @Test
    void smoothIsNeverInstantAndInstantAlwaysIs() {
        for (double ms : new double[] {0.0, 4.0, 16.7, 20.0, 40.0, 500.0}) {
            assertFalse(BlockUpdates.instantNow(BlockUpdates.SMOOTH, ms), "smooth at " + ms);
            assertTrue(BlockUpdates.instantNow(BlockUpdates.INSTANT, ms), "instant at " + ms);
        }
    }

    @Test
    void autoIsInstantWhileTheFrameRateIsGoodAndSmoothWhenTheGameIsAlreadySlow() {
        assertTrue(BlockUpdates.instantNow(BlockUpdates.AUTO, 11.1), "90 FPS");
        assertTrue(BlockUpdates.instantNow(BlockUpdates.AUTO, 16.7), "60 FPS");
        assertTrue(BlockUpdates.instantNow(BlockUpdates.AUTO, BlockUpdates.AUTO_MAX_MS), "50 FPS");
        assertFalse(BlockUpdates.instantNow(BlockUpdates.AUTO, 20.1), "just under 50 FPS");
        assertFalse(BlockUpdates.instantNow(BlockUpdates.AUTO, 25.0), "40 FPS");
        assertFalse(BlockUpdates.instantNow(BlockUpdates.AUTO, 0.0), "no frame seen yet: Sodium's way");
    }

    @Test
    void anUnknownModeIsSodiumsWay() {
        assertFalse(BlockUpdates.instantNow(-1, 5.0));
        assertFalse(BlockUpdates.instantNow(7, 5.0));
    }

    @Test
    void theAverageFollowsTheFrameTimeAndIgnoresNonsense() {
        BlockUpdates.setMode(BlockUpdates.AUTO);
        BlockUpdates.noteFrame(0.0);
        BlockUpdates.noteFrame(-3.0);
        BlockUpdates.noteFrame(Double.NaN);
        BlockUpdates.noteFrame(Double.POSITIVE_INFINITY);
        BlockUpdates.begin(true);
        assertFalse(BlockUpdates.grant(), "nothing counted yet");

        frames(200, 11.0);
        BlockUpdates.begin(true);
        assertTrue(BlockUpdates.grant(), "a steady 90 FPS");

        frames(200, 40.0);
        BlockUpdates.begin(true);
        assertFalse(BlockUpdates.grant(), "a steady 25 FPS");

        frames(200, 12.0);
        BlockUpdates.begin(true);
        assertTrue(BlockUpdates.grant(), "back to a good frame rate");
    }

    @Test
    void oneBigFreezeMakesAutoCarefulForAMoment() {
        BlockUpdates.setMode(BlockUpdates.AUTO);
        frames(300, 16.7);
        BlockUpdates.begin(true);
        assertTrue(BlockUpdates.grant());

        BlockUpdates.noteFrame(250.0);
        BlockUpdates.begin(true);
        assertFalse(BlockUpdates.grant(), "right after a freeze the next rebuild is not instant");

        frames(120, 16.7);
        BlockUpdates.begin(true);
        assertTrue(BlockUpdates.grant(), "and it is again a few seconds later");
    }

    @Test
    void onlyBlocksThePlayerChangedGetTheFastWay() {
        BlockUpdates.setMode(BlockUpdates.INSTANT);
        BlockUpdates.begin(false);
        assertFalse(BlockUpdates.grant(), "a block the server or the light changed");
        BlockUpdates.begin(true);
        assertTrue(BlockUpdates.grant(), "a block the player changed");
        BlockUpdates.begin(false);
        assertFalse(BlockUpdates.grant(), "the answer is remade for every rebuild");
    }

    @Test
    void smoothNeverGrantsEvenForThePlayer() {
        BlockUpdates.setMode(BlockUpdates.SMOOTH);
        frames(100, 8.0);
        BlockUpdates.begin(true);
        assertFalse(BlockUpdates.grant());
    }

    @Test
    void theModeAcceptsOnlyTheThreeModes() {
        BlockUpdates.setMode(BlockUpdates.INSTANT);
        BlockUpdates.setMode(9);
        assertEquals(BlockUpdates.INSTANT, BlockUpdates.mode());
        BlockUpdates.setMode(-1);
        assertEquals(BlockUpdates.INSTANT, BlockUpdates.mode());
        BlockUpdates.setMode(BlockUpdates.SMOOTH);
        assertEquals(BlockUpdates.SMOOTH, BlockUpdates.mode());
        assertEquals(BlockUpdates.AUTO, BlockUpdates.AUTO, "Auto is the default of a new game");
        BlockUpdates.reset();
        assertEquals(BlockUpdates.AUTO, BlockUpdates.mode());
    }

    @Test
    void theSettingsFileNamesGoBothWays() {
        for (int mode = BlockUpdates.SMOOTH; mode <= BlockUpdates.INSTANT; mode++) {
            assertEquals(mode, BlockUpdates.fromName(BlockUpdates.toName(mode), -1));
        }
        assertEquals("smooth", BlockUpdates.toName(BlockUpdates.SMOOTH));
        assertEquals("instant", BlockUpdates.toName(BlockUpdates.INSTANT));
        assertEquals(BlockUpdates.INSTANT, BlockUpdates.fromName(" INSTANT ", -1));
        assertEquals(BlockUpdates.AUTO, BlockUpdates.fromName("fast", BlockUpdates.AUTO));
        assertEquals(BlockUpdates.AUTO, BlockUpdates.fromName(null, BlockUpdates.AUTO));
        assertEquals(BlockUpdates.AUTO, BlockUpdates.fromName("", BlockUpdates.AUTO));
        assertEquals("smooth", BlockUpdates.toName(-5));
        assertEquals("instant", BlockUpdates.toName(99));
    }

    @Test
    void theLogHearsOfTheFirstFastRebuildOnce() {
        assertTrue(BlockUpdates.firstGrant());
        assertFalse(BlockUpdates.firstGrant());
        assertFalse(BlockUpdates.firstGrant());
    }
}
