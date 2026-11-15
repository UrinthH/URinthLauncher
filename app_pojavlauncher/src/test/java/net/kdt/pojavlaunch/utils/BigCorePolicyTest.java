package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class BigCorePolicyTest {
    /** Cores in order: {@code first} cores with one value, then {@code second} cores with another. */
    private static long[] cores(long first, int firstCount, long second, int secondCount) {
        long[] values = new long[firstCount + secondCount];
        Arrays.fill(values, 0, firstCount, first);
        Arrays.fill(values, firstCount, values.length, second);
        return values;
    }

    private static final long[] NO_CAPACITY = new long[0];

    // ---- the core the game library picks -------------------------------------------------------

    @Test
    public void whenSeveralCoresTieTheLastOneIsPicked() {
        // The native code compares with ">=", so a tie goes to the later core.
        assertEquals(7, BigCorePolicy.pickedCore(cores(1_612_000, 6, 1_612_000, 2)));
    }

    @Test
    public void theFastestCoreIsPickedWhenSpeedsDiffer() {
        assertEquals(7, BigCorePolicy.pickedCore(cores(1_800_000, 6, 2_400_000, 2)));
        assertEquals(1, BigCorePolicy.pickedCore(new long[]{1_800_000, 2_400_000, 1_800_000}));
    }

    @Test
    public void withNoReadableSpeedTheLibraryFallsBackToTheFirstCore() {
        assertEquals(0, BigCorePolicy.pickedCore(new long[0]));
    }

    // ---- what the launcher does ------------------------------------------------------------------

    @Test
    public void theUnisocT606PinsCpu7BecauseThatIsAFastCore() {
        // All eight cores report 1612 MHz; cpu6 and cpu7 are the two Cortex-A75.
        long[] capacity = cores(485, 6, 1024, 2);
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(cores(1_612_000, 6, 1_612_000, 2), capacity);
        assertTrue(verdict.note, verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("core speeds: cpu0-7 1612 MHz"));
        assertTrue(verdict.note, verdict.note.contains("capacities: cpu0-5 485, cpu6-7 1024"));
        assertTrue(verdict.note, verdict.note.contains("cpu7 (capacity 1024 of 1024)"));
        assertTrue(verdict.note, verdict.note.endsWith("so the setting is passed on"));
    }

    @Test
    public void aChipWithAFasterClusterPinsThatCluster() {
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(cores(1_800_000, 6, 2_400_000, 2), cores(400, 6, 1024, 2));
        assertTrue(verdict.note, verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("cpu7"));
    }

    @Test
    public void aSettingThatPointsAtAWeakCoreIsSkipped() {
        // Equal speeds with the fast cores listed first: the last core is a slow one.
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(cores(1_600_000, 2, 1_600_000, 6), cores(1024, 2, 485, 6));
        assertFalse(verdict.note, verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("cpu7, but its capacity 485 is below the strongest core's 1024"));
    }

    @Test
    public void withoutCapacityNumbersTheLibrarysPickIsTrusted() {
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(cores(1_612_000, 6, 1_612_000, 2), NO_CAPACITY);
        assertTrue(verdict.note, verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("capacities: not reported by this phone"));
        assertTrue(verdict.note, verdict.note.contains("cpu7, so the setting is passed on"));
        assertTrue(BigCorePolicy.decide(cores(1_612_000, 6, 1_612_000, 2), new long[8]).pin);
    }

    @Test
    public void whenNoSpeedCanBeReadTheSettingIsSkippedBecauseTheLibraryWouldGuessCpu0() {
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(new long[0], cores(485, 6, 1024, 2));
        assertFalse(verdict.note, verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("fall back to cpu0"));
    }

    @Test
    public void aSymmetricChipWithEqualCapacityKeepsTheSetting() {
        assertTrue(BigCorePolicy.decide(cores(1_800_000, 4, 1_800_000, 4), cores(1024, 4, 1024, 4)).pin);
    }

    // ---- the log text ------------------------------------------------------------------------------

    @Test
    public void theLayoutIsWrittenAsRunsOfCores() {
        assertEquals("cpu0-7 1600 MHz", BigCorePolicy.describe(cores(1_600_000, 6, 1_600_000, 2), 1000, "MHz"));
        assertEquals("cpu0 2000 MHz", BigCorePolicy.describe(new long[]{2_000_000}, 1000, "MHz"));
        assertEquals("cpu0-1 1000 MHz, cpu2 ?, cpu3 1000 MHz",
                BigCorePolicy.describe(new long[]{1_000_000, 1_000_000, 0, 1_000_000}, 1000, "MHz"));
        assertEquals("cpu0-5 485, cpu6-7 1024", BigCorePolicy.describe(cores(485, 6, 1024, 2), 1, ""));
        assertEquals("none readable", BigCorePolicy.describe(new long[0], 1000, "MHz"));
    }
}
