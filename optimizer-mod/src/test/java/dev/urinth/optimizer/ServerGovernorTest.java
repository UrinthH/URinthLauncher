package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerGovernorTest {
    @BeforeEach
    void clean() {
        ServerCap.reset();
    }

    @Test
    void aServerWithRoomKeepsTheConfiguredLimit() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        for (int i = 0; i < 100; i++) assertEquals(8, g.sample(12f, 8, 4));
        assertEquals(0, g.changes());
    }

    @Test
    void aSlowServerLowersItOneChunkAtATimeDownToTheMinimum() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        assertEquals(8, g.sample(47f, 8, 4));
        assertEquals(7, g.sample(47f, 8, 4));
        assertEquals(7, g.sample(47f, 8, 4));
        assertEquals(6, g.sample(47f, 8, 4));
        for (int i = 0; i < 20; i++) g.sample(47f, 8, 4);
        assertEquals(4, g.cap());
    }

    @Test
    void oneSlowSampleIsIgnored() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        for (int i = 0; i < 20; i++) {
            assertEquals(8, g.sample(i % 3 == 0 ? 60f : 15f, 8, 4));
        }
    }

    @Test
    void itComesBackWhenTheServerHasRoomAgain() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        for (int i = 0; i < 20; i++) g.sample(50f, 8, 4);
        assertEquals(4, g.cap());
        int samples = 0;
        while (g.cap() < 8 && samples < 200) {
            g.sample(10f, 8, 4);
            samples++;
        }
        assertEquals(8, g.cap());
        assertTrue(samples <= 6 * 4 + 4, "took " + samples + " samples");
    }

    private static void slow(ServerGovernor g, int samples) {
        for (int i = 0; i < samples; i++) g.sample(50f, 8, 4);
    }

    private static void room(ServerGovernor g, int samples) {
        for (int i = 0; i < samples; i++) g.sample(10f, 8, 4);
    }

    @Test
    void aRaiseThatDoesNotHoldMakesTheNextOneWaitTwiceAsLong() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        slow(g, ServerGovernor.SLOW_SAMPLES);
        assertEquals(7, g.cap());
        assertEquals(ServerGovernor.ROOM_SAMPLES, g.hold());
        room(g, ServerGovernor.ROOM_SAMPLES);
        assertEquals(8, g.cap());
        // the chunks that came back woke up and the server is slow again: down at once, and the wait doubles
        slow(g, ServerGovernor.SLOW_SAMPLES);
        assertEquals(7, g.cap());
        assertEquals(2 * ServerGovernor.ROOM_SAMPLES, g.hold());
        room(g, 2 * ServerGovernor.ROOM_SAMPLES - 1);
        assertEquals(7, g.cap());
        room(g, 1);
        assertEquals(8, g.cap());
    }

    @Test
    void aRaiseThatHoldsMakesTheNextWaitShorterAgain() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        slow(g, 2);
        room(g, ServerGovernor.ROOM_SAMPLES);
        slow(g, 2); // one failed raise: the wait is 12 now
        assertEquals(2 * ServerGovernor.ROOM_SAMPLES, g.hold());
        room(g, 2 * ServerGovernor.ROOM_SAMPLES);
        assertEquals(8, g.cap());
        // it holds for longer than the trial: the wait goes back down
        room(g, ServerGovernor.TRIAL_SAMPLES + 2);
        assertEquals(8, g.cap());
        assertEquals(ServerGovernor.ROOM_SAMPLES, g.hold());
    }

    @Test
    void theWaitIsNeverLongerThanTenMinutes() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        slow(g, 2);
        for (int round = 0; round < 12; round++) {
            room(g, g.hold());
            assertEquals(8, g.cap(), "round " + round);
            slow(g, 2);
            assertEquals(7, g.cap(), "round " + round);
            assertTrue(g.hold() <= ServerGovernor.ROOM_SAMPLES_MAX, "wait " + g.hold());
        }
        assertEquals(ServerGovernor.ROOM_SAMPLES_MAX, g.hold());
        // a sample is taken every five seconds: 120 of them are ten minutes
        assertEquals(10 * 60, ServerGovernor.ROOM_SAMPLES_MAX * SAMPLE_SECONDS);
    }

    @Test
    void aFreshStartForgetsTheLongWait() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        slow(g, 2);
        room(g, ServerGovernor.ROOM_SAMPLES);
        slow(g, 2);
        assertTrue(g.hold() > ServerGovernor.ROOM_SAMPLES);
        g.reset(8);
        assertEquals(ServerGovernor.ROOM_SAMPLES, g.hold());
    }

    /** One server governor sample is taken every 100 server ticks, which is five seconds (see OptimizerMod.SAMPLE_TICKS). */
    private static final int SAMPLE_SECONDS = 5;

    @Test
    void theBandInBetweenChangesNothing() {
        ServerGovernor g = new ServerGovernor();
        g.reset(8);
        for (int i = 0; i < 30; i++) g.sample(50f, 8, 4);
        int cap = g.cap();
        for (int i = 0; i < 100; i++) assertEquals(cap, g.sample(30f, 8, 4));
    }

    @Test
    void noLimitConfiguredMeansNoLimit() {
        ServerGovernor g = new ServerGovernor();
        g.reset(0);
        for (int i = 0; i < 20; i++) assertEquals(0, g.sample(80f, 0, 4));
    }

    @Test
    void loweringTheConfiguredLimitIsFollowedAtOnce() {
        ServerGovernor g = new ServerGovernor();
        g.reset(10);
        assertEquals(6, g.sample(10f, 6, 4));
    }

    @Test
    void theMinimumCanNotBeAboveTheLimit() {
        ServerGovernor g = new ServerGovernor();
        g.reset(3);
        for (int i = 0; i < 20; i++) assertTrue(g.sample(80f, 3, 4) <= 3);
    }

    @Test
    void theCapAppliesToWhatTheGameAsksFor() {
        ServerCap.setCap(8);
        assertEquals(8, ServerCap.apply(12));
        assertEquals(12, ServerCap.requested());
        assertEquals(6, ServerCap.apply(6));
        ServerCap.setCap(0);
        assertEquals(12, ServerCap.apply(12));
    }

    @Test
    void theGamesRequestIsRememberedWhenTheLimitMoves() {
        ServerCap.setCap(8);
        ServerCap.apply(12);
        ServerCap.setCap(5);
        assertEquals(12, ServerCap.requested());
        assertEquals(5, ServerCap.apply(ServerCap.requested()));
    }
}
