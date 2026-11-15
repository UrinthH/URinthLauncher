package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class BigCorePolicyTest {
    private static long[] cores(long little, int littleCount, long big, int bigCount) {
        long[] speeds = new long[littleCount + bigCount];
        Arrays.fill(speeds, 0, littleCount, little);
        Arrays.fill(speeds, littleCount, speeds.length, big);
        return speeds;
    }

    @Test
    public void aChipWhoseCoresAllReportTheSameSpeedSkipsTheSetting() {
        // Unisoc T606: six slow and two fast cores, all reporting 1.6 GHz.
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(cores(1_600_000, 6, 1_600_000, 2));
        assertFalse(verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("cpu0-7 1600 MHz"));
        assertTrue(verdict.note, verdict.note.contains("skipped"));
    }

    @Test
    public void aChipWithAFasterClusterKeepsTheSetting() {
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(cores(1_800_000, 6, 2_400_000, 2));
        assertTrue(verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("cpu0-5 1800 MHz, cpu6-7 2400 MHz"));
        assertTrue(verdict.note, verdict.note.contains("cpu6 reports the highest"));
    }

    @Test
    public void severalFastCoresStillCountAsAFasterCluster() {
        BigCorePolicy.Verdict verdict = BigCorePolicy.decide(new long[]{1_800_000, 2_400_000, 2_400_000});
        assertTrue(verdict.pin);
        assertTrue(verdict.note, verdict.note.contains("cpu1 reports the highest"));
    }

    @Test
    public void speedsThatCannotBeReadLeaveTheSettingAsItIs() {
        assertTrue(BigCorePolicy.decide(new long[0]).pin);
        assertTrue(BigCorePolicy.decide(new long[]{0, 0, 0, 0}).pin);
        assertTrue(BigCorePolicy.decide(new long[]{2_000_000, 0, 0, 0}).pin);
    }

    @Test
    public void aCoreThatDoesNotSayItsSpeedIsLeftOutOfTheComparison() {
        // Offline cores often have no cpufreq folder. The cores that do report decide.
        assertFalse(BigCorePolicy.decide(new long[]{1_600_000, 0, 1_600_000, 1_600_000}).pin);
        assertTrue(BigCorePolicy.decide(new long[]{1_600_000, 0, 2_000_000, 2_000_000}).pin);
    }

    @Test
    public void theLayoutIsWrittenAsRunsOfCores() {
        assertEquals("cpu0-7 1600 MHz", BigCorePolicy.describe(cores(1_600_000, 6, 1_600_000, 2), 1000, "MHz"));
        assertEquals("cpu0 2000 MHz", BigCorePolicy.describe(new long[]{2_000_000}, 1000, "MHz"));
        assertEquals("cpu0-1 1000 MHz, cpu2 ?, cpu3 1000 MHz",
                BigCorePolicy.describe(new long[]{1_000_000, 1_000_000, 0, 1_000_000}, 1000, "MHz"));
        assertEquals("no cores listed", BigCorePolicy.describe(new long[0], 1000, "MHz"));
    }
}
