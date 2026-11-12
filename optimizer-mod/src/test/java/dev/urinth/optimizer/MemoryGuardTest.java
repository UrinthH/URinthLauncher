package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How short of memory the game is: it is slow to say "critical" and slow to take it back. */
class MemoryGuardTest {
    private static final long MB = 1L << 20;

    /** A look at a game with room: half the heap used, the system has plenty, nothing is being taken away. */
    private static MemoryGuard.Level roomy(MemoryGuard g) {
        return g.update(0.40, 0.02, 0, 800 * MB, 5);
    }

    /** Two looks at a game that is clearly in trouble (the old generation almost full). */
    private static void makeCritical(MemoryGuard g) {
        g.update(0.95, 0.30, 0, 300 * MB, 10);
        assertEquals(MemoryGuard.Level.CRITICAL, g.update(0.95, 0.30, 0, 300 * MB, 10));
    }

    @Test
    void aGameWithRoomIsOk() {
        MemoryGuard g = new MemoryGuard();
        for (int i = 0; i < 200; i++) assertEquals(MemoryGuard.Level.OK, roomy(g));
    }

    @Test
    void whatCannotBeReadCountsAsNothingWrong() {
        MemoryGuard g = new MemoryGuard();
        for (int i = 0; i < 50; i++) assertEquals(MemoryGuard.Level.OK, g.update(0, 0, 0, -1, -1));
    }

    @Test
    void littleFreeMemoryAloneIsOnlyTightHoweverLongItLasts() {
        // a phone is full, as phones are: that is not the game being in trouble
        MemoryGuard g = new MemoryGuard();
        for (int i = 0; i < 100; i++) assertEquals(MemoryGuard.Level.TIGHT, g.update(0.5, 0.02, 0, 120 * MB, 5));
        for (int i = 0; i < 100; i++) assertEquals(MemoryGuard.Level.TIGHT, g.update(0.5, 0.02, 0, 40 * MB, 20));
    }

    @Test
    void aBusyCollectorAloneIsOnlyTight() {
        MemoryGuard g = new MemoryGuard();
        for (int i = 0; i < 100; i++) assertEquals(MemoryGuard.Level.TIGHT, g.update(0.5, 0.6, 0, 800 * MB, 5));
    }

    @Test
    void anOldGenerationOverEightyPercentIsTight() {
        MemoryGuard g = new MemoryGuard();
        assertEquals(MemoryGuard.Level.TIGHT, g.update(MemoryGuard.TIGHT_OLD, 0, 0, 800 * MB, 5));
    }

    @Test
    void anAlmostFullOldGenerationIsCriticalOnTheSecondLookNotTheFirst() {
        MemoryGuard g = new MemoryGuard();
        assertEquals(MemoryGuard.Level.TIGHT, g.update(0.95, 0, 0, 800 * MB, 5));
        assertEquals(MemoryGuard.Level.CRITICAL, g.update(0.95, 0, 0, 800 * MB, 5));
    }

    @Test
    void oneBadLookInBetweenStartsTheCountAgain() {
        MemoryGuard g = new MemoryGuard();
        g.update(0.95, 0, 0, 800 * MB, 5);
        assertEquals(MemoryGuard.Level.TIGHT, roomy(g));
        assertEquals(MemoryGuard.Level.TIGHT, g.update(0.95, 0, 0, 800 * MB, 5));
    }

    @Test
    void fullCollectionsOneIsTightTwoAreCritical() {
        MemoryGuard one = new MemoryGuard();
        for (int i = 0; i < 20; i++) assertEquals(MemoryGuard.Level.TIGHT, one.update(0.5, 0.05, 1, 800 * MB, 5));
        MemoryGuard two = new MemoryGuard();
        two.update(0.5, 0.05, MemoryGuard.FULL_GCS_CRITICAL, 800 * MB, 5);
        assertEquals(MemoryGuard.Level.CRITICAL, two.update(0.5, 0.05, MemoryGuard.FULL_GCS_CRITICAL, 800 * MB, 5));
    }

    @Test
    void aFloodOfPageFaultsIsCriticalEvenWithMemoryLeft() {
        MemoryGuard g = new MemoryGuard();
        g.update(0.5, 0.05, 0, 500 * MB, MemoryGuard.SEVERE_FAULTS);
        assertEquals(MemoryGuard.Level.CRITICAL, g.update(0.5, 0.05, 0, 500 * MB, MemoryGuard.SEVERE_FAULTS));
    }

    @Test
    void aFewHundredPageFaultsAreOnlyTight() {
        MemoryGuard g = new MemoryGuard();
        for (int i = 0; i < 50; i++) assertEquals(MemoryGuard.Level.TIGHT, g.update(0.5, 0.05, 0, 500 * MB, 500));
    }

    @Test
    void almostNothingFreeTogetherWithPageFaultsIsCritical() {
        MemoryGuard g = new MemoryGuard();
        g.update(0.5, 0.05, 0, 80 * MB, MemoryGuard.HEAVY_FAULTS);
        assertEquals(MemoryGuard.Level.CRITICAL, g.update(0.5, 0.05, 0, 80 * MB, MemoryGuard.HEAVY_FAULTS));
        // the same page faults with memory to spare, or almost nothing free without them, are not
        MemoryGuard spare = new MemoryGuard();
        for (int i = 0; i < 20; i++) assertEquals(MemoryGuard.Level.TIGHT, spare.update(0.5, 0.05, 0, 400 * MB, MemoryGuard.HEAVY_FAULTS));
        MemoryGuard still = new MemoryGuard();
        for (int i = 0; i < 20; i++) assertEquals(MemoryGuard.Level.TIGHT, still.update(0.5, 0.05, 0, 80 * MB, 20));
    }

    @Test
    void theLevelFallsOneStepAtATimeAfterALongCalm() {
        MemoryGuard g = new MemoryGuard();
        makeCritical(g);
        for (int i = 1; i < MemoryGuard.CALM_FROM_CRITICAL; i++) assertEquals(MemoryGuard.Level.CRITICAL, roomy(g), "look " + i);
        assertEquals(MemoryGuard.Level.TIGHT, roomy(g));
        for (int i = 1; i < MemoryGuard.CALM_FROM_TIGHT; i++) assertEquals(MemoryGuard.Level.TIGHT, roomy(g), "look " + i);
        assertEquals(MemoryGuard.Level.OK, roomy(g));
    }

    @Test
    void aBadLookRestartsTheCalm() {
        MemoryGuard g = new MemoryGuard();
        g.update(0.85, 0, 0, 800 * MB, 5);
        for (int i = 0; i < MemoryGuard.CALM_FROM_TIGHT - 1; i++) assertEquals(MemoryGuard.Level.TIGHT, roomy(g));
        // trouble again one look before the end of the calm
        assertEquals(MemoryGuard.Level.TIGHT, g.update(0.85, 0, 0, 800 * MB, 5));
        for (int i = 0; i < MemoryGuard.CALM_FROM_TIGHT - 1; i++) assertEquals(MemoryGuard.Level.TIGHT, roomy(g));
        assertEquals(MemoryGuard.Level.OK, roomy(g));
    }

    @Test
    void aTightLookWhileCriticalOnlyEverBringsItDownToTight() {
        MemoryGuard g = new MemoryGuard();
        makeCritical(g);
        for (int i = 0; i < 200; i++) g.update(0.85, 0, 0, 800 * MB, 5);
        assertEquals(MemoryGuard.Level.TIGHT, g.level());
    }

    @Test
    void resetStartsOver() {
        MemoryGuard g = new MemoryGuard();
        makeCritical(g);
        g.reset();
        assertEquals(MemoryGuard.Level.OK, g.level());
        assertEquals(MemoryGuard.Level.TIGHT, g.update(0.95, 0, 0, 800 * MB, 5));
    }
}
