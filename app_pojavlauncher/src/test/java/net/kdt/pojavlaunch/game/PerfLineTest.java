package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PerfLineTest {
    @Test
    public void statIsCountedFromTheLastBracket() {
        // The name holds a space and a bracket on purpose.
        String line = "1234 (Java (main) x) S 1 1234 1234 0 -1 4194560 1000 0 25 0 700 300 0 0 20 0 80 0 1 2 3";
        assertArrayEquals(new long[]{25, 700, 300}, PerfLine.parseStat(line));
    }

    @Test
    public void brokenStatGivesNull() {
        assertNull(PerfLine.parseStat(null));
        assertNull(PerfLine.parseStat("no brackets here"));
        assertNull(PerfLine.parseStat("1 (x) S 1 2"));
        assertNull(PerfLine.parseStat("1 (x) S 1 1 1 0 -1 0 0 0 abc 0 1 2"));
    }

    @Test
    public void residentMemoryIsReadInMegabytes() {
        String status = "Name:\tjava\nVmPeak:\t 3000000 kB\nVmRSS:\t 1572864 kB\nThreads:\t80\n";
        assertEquals(1536, PerfLine.residentMegabytes(status));
        assertEquals(-1, PerfLine.residentMegabytes("Name:\tjava\n"));
        assertEquals(-1, PerfLine.residentMegabytes(null));
    }

    @Test
    public void loadAverageKeepsThreeNumbers() {
        assertEquals("1.20 / 0.90 / 0.70", PerfLine.loadAverage("1.20 0.90 0.70 2/900 12345\n"));
        assertEquals("", PerfLine.loadAverage("1.0"));
        assertEquals("", PerfLine.loadAverage(null));
    }

    @Test
    public void lineShowsWhatChangedBetweenReports() {
        long[] before = {10, 1000, 500};
        long[] now = {16, 1600, 800}; // 6 major faults, 9 s of CPU in 30 s
        String line = PerfLine.describe(now, before, 30_000, 1500, 220, true, 2, "3.0 2.0 1.0 1/2 3");
        assertTrue(line, line.contains("game memory 1500 MB"));
        assertTrue(line, line.contains("system free 220 MB (LOW)"));
        assertTrue(line, line.contains("major page faults 6"));
        assertTrue(line, line.contains("game CPU 30% of one core"));
        assertTrue(line, line.contains("heat level 2"));
        assertTrue(line, line.contains("phone load 3.0 / 2.0 / 1.0"));
    }

    @Test
    public void firstReportHasNoDeltas() {
        String line = PerfLine.describe(new long[]{1, 2, 3}, null, 30_000, -1, -1, false, -1, null);
        assertTrue(line, line.contains("game memory ?"));
        assertTrue(!line.contains("page faults"));
        assertTrue(!line.contains("heat"));
    }
}
