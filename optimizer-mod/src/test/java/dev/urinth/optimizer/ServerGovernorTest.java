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
