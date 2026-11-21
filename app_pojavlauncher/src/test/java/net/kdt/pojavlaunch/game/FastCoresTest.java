package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FastCoresTest {
    private static long[] repeat(long value, int times) {
        long[] values = new long[times];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static long[] join(long[] first, long[] second) {
        long[] all = new long[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    @Test
    public void theUnisocT606HasTwoFastCoresEvenThoughEveryCoreReportsTheSameClock() {
        long[] capacity = join(repeat(544, 6), repeat(1024, 2));
        long[] clock = repeat(1_612_000, 8);
        long mask = FastCores.maskFor(capacity, clock);
        assertEquals(0xC0L, mask);
        assertEquals("cpu6-7", FastCores.describe(mask));
        assertEquals(2, FastCores.count(mask));
        assertTrue(FastCores.contains(mask, 6));
        assertTrue(FastCores.contains(mask, 7));
        assertFalse(FastCores.contains(mask, 5));
    }

    @Test
    public void capacityDecidesBeforeTheClockSpeed() {
        long[] capacity = {544, 544, 1024, 1024};
        long[] clock = repeat(2_000_000, 4);
        assertEquals(0b1100L, FastCores.maskFor(capacity, clock));
    }

    @Test
    public void theClockSpeedIsUsedWhenThereAreNoCapacities() {
        long[] capacity = repeat(-1, 8);
        long[] clock = join(repeat(1_800_000, 4), repeat(2_400_000, 4));
        assertEquals(0xF0L, FastCores.maskFor(capacity, clock));
        assertEquals("cpu4-7", FastCores.describe(FastCores.maskFor(capacity, clock)));
    }

    @Test
    public void coresThatAreAllAlikeGiveNoMask() {
        assertEquals(0L, FastCores.maskFor(repeat(1024, 8), repeat(1_612_000, 8)));
        // 5% apart is the same class of core, not a fast one.
        assertEquals(0L, FastCores.maskFor(join(repeat(1000, 4), repeat(1050, 4)), repeat(-1, 8)));
    }

    @Test
    public void aMissingNumberMeansNoGuess() {
        long[] capacity = {544, 544, -1, 1024};
        assertEquals(0L, FastCores.maskFor(capacity, repeat(-1, 4)));
        assertEquals(0L, FastCores.maskFor(null, null));
        assertEquals(0L, FastCores.maskFor(new long[] {1024}, new long[] {1_000_000}));
        assertEquals(0L, FastCores.maskFor(new long[0], new long[0]));
    }

    @Test
    public void aSingleTopCoreGetsTheNextTierWithIt() {
        // A prime core on a three-tier chip: the render thread should have two cores to move between.
        long[] capacity = {300, 300, 300, 300, 700, 700, 700, 1024};
        long mask = FastCores.maskFor(capacity, repeat(-1, 8));
        assertEquals(0xF0L, mask);
        assertEquals("cpu4-7", FastCores.describe(mask));
    }

    @Test
    public void aCoreJustBelowTheTopStillCounts() {
        // 900 is 88% of 1024, over the 85% line: the cores of one tier are rarely exactly equal.
        long[] capacity = {400, 400, 900, 1024};
        assertEquals(0b1100L, FastCores.maskFor(capacity, repeat(-1, 4)));
    }

    @Test
    public void describeListsRunsOfCores() {
        assertEquals("none", FastCores.describe(0));
        assertEquals("cpu0", FastCores.describe(1));
        assertEquals("cpu0, cpu2-3, cpu5", FastCores.describe(0b101101));
        assertEquals("cpu0-7", FastCores.describe(0xFFL));
    }

    @Test
    public void containsAndCountIgnoreNumbersOutsideTheMask() {
        assertFalse(FastCores.contains(0xFF, -1));
        assertFalse(FastCores.contains(0xFF, 64));
        assertFalse(FastCores.contains(0xFF, 63));
        assertEquals(0, FastCores.count(0));
        assertEquals(8, FastCores.count(0xFF));
    }
}
