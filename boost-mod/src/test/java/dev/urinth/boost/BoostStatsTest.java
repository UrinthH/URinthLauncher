package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BoostStatsTest {
    @Test
    public void countsCallsAndActsPerHook() {
        BoostStats stats = new BoostStats();
        stats.call(BoostStats.PARTICLE);
        stats.call(BoostStats.PARTICLE);
        stats.act(BoostStats.PARTICLE);
        stats.call(BoostStats.LAYERS);
        assertEquals(2L, stats.calls(BoostStats.PARTICLE));
        assertEquals(1L, stats.acted(BoostStats.PARTICLE));
        assertEquals(1L, stats.calls(BoostStats.LAYERS));
        assertEquals(0L, stats.acted(BoostStats.LAYERS));
        assertEquals(0L, stats.calls(BoostStats.ENTITY));
    }

    @Test
    public void saysWhetherAnythingWasTakenAway() {
        BoostStats stats = new BoostStats();
        stats.call(BoostStats.ENTITY);
        assertFalse(stats.anyActed());
        stats.act(BoostStats.NAME_TAG);
        assertTrue(stats.anyActed());
    }

    @Test
    public void aCopyDoesNotFollowAndSinceGivesTheDifference() {
        BoostStats stats = new BoostStats();
        stats.call(BoostStats.ENTITY);
        BoostStats before = stats.copy();
        stats.call(BoostStats.ENTITY);
        stats.call(BoostStats.ENTITY);
        stats.act(BoostStats.ENTITY);
        assertEquals(1L, before.calls(BoostStats.ENTITY));
        BoostStats diff = stats.since(before);
        assertEquals(2L, diff.calls(BoostStats.ENTITY));
        assertEquals(1L, diff.acted(BoostStats.ENTITY));
    }
}
