package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The garbage collector's running total over the last seconds, which tells a freeze from the collector apart from other freezes. */
class GcRingTest {
    private static final long S = 1_000_000_000L;

    @Test
    void anEmptyRingKnowsNothing() {
        GcRing ring = new GcRing(8);
        assertTrue(ring.isEmpty());
        assertEquals(-1L, ring.latest());
        assertEquals(-1L, ring.totalAt(5 * S));
        assertEquals(0.0, ring.share(10 * S));
    }

    @Test
    void theTotalAtATimeIsTheNewestEntryNotAfterIt() {
        GcRing ring = new GcRing(8);
        ring.add(100, 10);
        ring.add(200, 25);
        ring.add(300, 25);
        assertEquals(25L, ring.totalAt(250));
        assertEquals(10L, ring.totalAt(100));
        assertEquals(25L, ring.totalAt(200));
        assertEquals(25L, ring.totalAt(1000));
        assertEquals(25L, ring.latest());
    }

    @Test
    void aTimeBeforeEverythingGivesTheOldestEntry() {
        GcRing ring = new GcRing(8);
        ring.add(100, 10);
        ring.add(200, 25);
        assertEquals(10L, ring.totalAt(5));
    }

    @Test
    void itKeepsTheNewestEntriesWhenFull() {
        GcRing ring = new GcRing(4);
        for (int i = 0; i < 10; i++) ring.add(i * S, i * 100L);
        assertEquals(900L, ring.latest());
        assertEquals(9 * S, ring.latestTime());
        assertEquals(700L, ring.totalAt(7 * S + 7));
        // it only remembers the last four: an older time gives the oldest it still has
        assertEquals(600L, ring.totalAt(2 * S));
    }

    @Test
    void theShareIsTheTimeInCollectionsOverTheWindow() {
        GcRing ring = new GcRing(256);
        // every second 100 ms are spent in collections
        for (int i = 0; i <= 12; i++) ring.add(i * S, i * 100L);
        assertEquals(0.1, ring.share(10 * S), 1e-9);
        assertEquals(0.1, ring.share(3 * S), 1e-9);
    }

    @Test
    void aWindowLongerThanTheRingUsesWhatItHas() {
        GcRing ring = new GcRing(256);
        for (int i = 0; i <= 4; i++) ring.add(i * S, i * 200L);
        assertEquals(0.2, ring.share(60 * S), 1e-9);
    }

    @Test
    void lessThanASecondOfDataAnswersZero() {
        GcRing ring = new GcRing(8);
        ring.add(0, 0);
        assertEquals(0.0, ring.share(10 * S));
        ring.add(S / 2, 400);
        assertEquals(0.0, ring.share(10 * S));
    }

    @Test
    void theShareIsNeverMoreThanAll() {
        GcRing ring = new GcRing(8);
        ring.add(0, 0);
        ring.add(S, 5000);
        assertEquals(1.0, ring.share(10 * S));
    }

    @Test
    void aTotalThatDoesNotGrowIsNoShare() {
        GcRing ring = new GcRing(8);
        ring.add(0, 700);
        ring.add(2 * S, 700);
        assertEquals(0.0, ring.share(10 * S));
    }

    @Test
    void theCollectorTimeDuringAFreezeIsTheDifferenceOfTheTotals() {
        GcRing ring = new GcRing(256);
        // polled every 50 ms; a 400 ms pause shows up in one poll
        long t = 0;
        long total = 0;
        for (int i = 0; i < 40; i++) {
            t += 50_000_000L;
            if (i == 20) total += 400;
            ring.add(t, total);
        }
        long freezeStart = 15 * 50_000_000L;
        long freezeEnd = 30 * 50_000_000L;
        long during = ring.totalAt(freezeEnd) - ring.totalAt(freezeStart);
        assertEquals(400L, during);
        long before = ring.totalAt(10 * 50_000_000L) - ring.totalAt(2 * 50_000_000L);
        assertEquals(0L, before);
    }
}
