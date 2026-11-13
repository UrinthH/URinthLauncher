package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The collector's numbers: the names that mean a full collection, and that the running JVM can be read without trouble. */
class GcWatchTest {
    @Test
    void theSlowCompleteCollectionsAreRecognisedByName() {
        assertTrue(GcWatch.isFull("g1 old generation"));
        assertTrue(GcWatch.isFull("ps marksweep"));
        assertTrue(GcWatch.isFull("marksweepcompact"));
        assertTrue(GcWatch.isFull("shenandoah full gc"));
        assertFalse(GcWatch.isFull("g1 young generation"));
        assertFalse(GcWatch.isFull("copy"));
        assertFalse(GcWatch.isFull("ps scavenge"));
        assertFalse(GcWatch.isFull("parnew"));
    }

    @Test
    void onlyTheTimeTheGameStoodStillCountsAsPause() {
        // G1: the young and old generation beans are pauses, the concurrent one is not.
        assertTrue(GcWatch.countsAsPause("g1 young generation"));
        assertTrue(GcWatch.countsAsPause("g1 old generation"));
        assertFalse(GcWatch.countsAsPause("g1 concurrent gc"));
        // The low-pause collectors: "Pauses" stood still, "Cycles" ran beside the game.
        assertTrue(GcWatch.countsAsPause("shenandoah pauses"));
        assertFalse(GcWatch.countsAsPause("shenandoah cycles"));
        assertTrue(GcWatch.countsAsPause("zgc pauses"));
        assertFalse(GcWatch.countsAsPause("zgc cycles"));
        assertFalse(GcWatch.countsAsPause("zgc minor cycles"));
        assertFalse(GcWatch.countsAsPause("zgc major cycles"));
        assertTrue(GcWatch.countsAsPause("zgc major pauses"));
        // Anything unknown counts, as before.
        assertTrue(GcWatch.countsAsPause("ps scavenge"));
    }

    @Test
    void theRunningJvmCanBeRead() {
        GcWatch w = new GcWatch();
        assertFalse(w.collectors().isEmpty());
        long before = w.pauseMillis();
        assertTrue(before >= -1L, "pause total " + before);
        long fulls = w.fullCollections();
        assertTrue(fulls >= -1L, "full collections " + fulls);
        // a collection is made on purpose: the totals never go down, and the old generation can be asked about
        byte[][] garbage = new byte[64][];
        for (int i = 0; i < garbage.length; i++) garbage[i] = new byte[64 * 1024];
        garbage = null;
        System.gc();
        long after = w.pauseMillis();
        assertTrue(after >= before, "pause total went from " + before + " to " + after);
        assertTrue(w.fullCollections() >= fulls);
        assertTrue(w.oldAfterCollection() >= -1L);
    }
}
