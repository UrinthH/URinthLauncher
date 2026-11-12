package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The freeze lines in the log stay few, whatever the game does. */
class HitchLimiterTest {
    private static final long BASE = 50_000_000_000L;

    @Test
    void aBurstIsAllowedThenItIsHeldBack() {
        HitchLimiter l = new HitchLimiter();
        for (int i = 0; i < HitchLimiter.BURST; i++) assertTrue(l.allow(BASE), "line " + i);
        assertFalse(l.allow(BASE));
        assertFalse(l.allow(BASE + 1));
        assertEquals(2, l.takeSuppressed());
    }

    @Test
    void oneMoreLineComesEveryRefillPeriod() {
        HitchLimiter l = new HitchLimiter();
        for (int i = 0; i < HitchLimiter.BURST; i++) l.allow(BASE);
        assertFalse(l.allow(BASE + HitchLimiter.REFILL_NANOS / 2));
        assertTrue(l.allow(BASE + HitchLimiter.REFILL_NANOS));
        assertFalse(l.allow(BASE + HitchLimiter.REFILL_NANOS + 1));
    }

    @Test
    void aLongQuietTimeNeverSavesUpMoreThanTheBurst() {
        HitchLimiter l = new HitchLimiter();
        l.allow(BASE);
        long later = BASE + 1000 * HitchLimiter.REFILL_NANOS;
        for (int i = 0; i < HitchLimiter.BURST; i++) assertTrue(l.allow(later), "line " + i);
        assertFalse(l.allow(later));
    }

    @Test
    void theHeldBackCountIsHandedOverOnceAndStartsAgain() {
        HitchLimiter l = new HitchLimiter();
        for (int i = 0; i < HitchLimiter.BURST + 3; i++) l.allow(BASE);
        assertEquals(3, l.takeSuppressed());
        assertEquals(0, l.takeSuppressed());
        l.allow(BASE);
        assertEquals(1, l.takeSuppressed());
    }
}
