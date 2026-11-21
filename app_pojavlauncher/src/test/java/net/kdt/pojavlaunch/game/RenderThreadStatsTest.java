package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RenderThreadStatsTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long FAST = 0xC0L; // cpu6-7, as on the Unisoc T606

    /** A /proc/self/task/TID/stat line: 52 fields as on a current kernel, with the three that matter set. */
    private static String statLine(String name, long utime, long stime, int cpu) {
        StringBuilder line = new StringBuilder("4321 (").append(name).append(") S");
        for (int field = 4; field <= 13; field++) line.append(" 0");
        line.append(' ').append(utime).append(' ').append(stime);
        for (int field = 16; field <= 38; field++) line.append(" 0");
        line.append(' ').append(cpu);
        for (int field = 40; field <= 52; field++) line.append(" 0");
        return line.toString();
    }

    private static boolean near(double expected, double actual) {
        return Math.abs(expected - actual) < 1e-9;
    }

    @Test
    public void readsTheCpuTimesAndTheCoreOutOfAStatLine() {
        RenderThreadStats.Reading reading = RenderThreadStats.parseStat(statLine("Render thread", 400, 150, 7));
        assertNotNull(reading);
        assertEquals(400, reading.userTicks);
        assertEquals(150, reading.systemTicks);
        assertEquals(7, reading.cpu);
    }

    @Test
    public void aThreadNameWithSpacesAndBracketsDoesNotShiftTheFields() {
        RenderThreadStats.Reading reading = RenderThreadStats.parseStat(statLine("Render (main) thread)", 12, 3, 2));
        assertNotNull(reading);
        assertEquals(12, reading.userTicks);
        assertEquals(3, reading.systemTicks);
        assertEquals(2, reading.cpu);
    }

    @Test
    public void linesThatAreNotStatLinesGiveNothing() {
        assertNull(RenderThreadStats.parseStat(null));
        assertNull(RenderThreadStats.parseStat(""));
        assertNull(RenderThreadStats.parseStat("garbage"));
        assertNull(RenderThreadStats.parseStat("1 (x) S 1 2 3"));
        assertNull(RenderThreadStats.parseStat(statLine("x", -5, 3, 1)));
        assertNull(RenderThreadStats.parseStat(statLine("x", 1, -3, 1)));
        assertNull(RenderThreadStats.parseStat(statLine("x", 1, 3, 1).replace(" 1 3 ", " one three ")));
    }

    @Test
    public void nothingToReportBeforeAReading() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 100);
        assertFalse(stats.hasData());
        assertTrue(near(-1, stats.fastShare()));
        stats.add(null, SECOND);
        assertFalse(stats.hasData());
    }

    @Test
    public void aBusyThreadOnTheFastCoresIsSummedUp() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 100);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 0, 0, 7)), 0);
        // One second later: 80 ticks of game code and 15 of system time out of 100 ticks of wall time.
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 80, 15, 7)), SECOND);
        assertTrue(stats.hasData());
        assertTrue(near(1.0, stats.fastShare()));
        String text = stats.summarise("");
        assertTrue(text, text.contains("busy 95% of the time"));
        assertTrue(text, text.contains("game code 80%"));
        assertTrue(text, text.contains("system and graphics driver 15%"));
        assertTrue(text, text.contains("never above 95%"));
        assertTrue(text, text.contains("fast cores (cpu6-7) 100% of the time"));
        assertTrue(text, text.contains("seen on cpu7 100%"));
    }

    @Test
    public void aThreadOnTheSlowCoresIsToldApart() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 100);
        for (int second = 0; second < 4; second++) {
            int cpu = second < 3 ? 2 : 6; // three readings on a slow core, one on a fast one
            stats.add(RenderThreadStats.parseStat(statLine("Render thread", second * 90, second * 5, cpu)), second * SECOND);
        }
        assertTrue(near(0.25, stats.fastShare()));
        String text = stats.summarise("");
        assertTrue(text, text.contains("fast cores (cpu6-7) 25% of the time"));
        assertTrue(text, text.contains("seen on cpu2 75%, cpu6 25%"));
    }

    @Test
    public void aPhoneThatDoesNotSayWhichCoresAreFastIsReportedAsSuch() {
        RenderThreadStats stats = new RenderThreadStats(0, 100);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 0, 0, 3)), 0);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 50, 0, 3)), SECOND);
        assertTrue(near(-1, stats.fastShare()));
        String text = stats.summarise("");
        assertTrue(text, text.contains("busy 50% of the time"));
        assertTrue(text, text.contains("this phone does not report which of its cores are fast"));
    }

    @Test
    public void moreCpuTimeThanWallTimeIsCappedAtAlwaysBusy() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 100);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 0, 0, 7)), 0);
        // A reading that arrived late or a tick that rounded up: 120 ticks in one second.
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 100, 20, 7)), SECOND);
        String text = stats.summarise("");
        assertTrue(text, text.contains("busy 100% of the time"));
        assertTrue(text, text.contains("never above 100%"));
    }

    @Test
    public void aCounterThatGoesBackwardsIsNotCountedAsBusyTime() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 100);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 500, 100, 7)), 0);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 10, 1, 7)), SECOND);
        assertTrue(stats.hasData()); // the cores were still seen
        String text = stats.summarise("");
        assertTrue(text, text.contains("could not be read"));
    }

    @Test
    public void theTickRateIsUsedToTurnTicksIntoTime() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 250);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 0, 0, 7)), 0);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 125, 0, 7)), SECOND);
        assertTrue(stats.summarise("").contains("busy 50% of the time"));
        // An unusable tick rate falls back to 100.
        RenderThreadStats fallback = new RenderThreadStats(FAST, 0);
        fallback.add(RenderThreadStats.parseStat(statLine("Render thread", 0, 0, 7)), 0);
        fallback.add(RenderThreadStats.parseStat(statLine("Render thread", 25, 0, 7)), SECOND);
        assertTrue(fallback.summarise("").contains("busy 25% of the time"));
    }

    @Test
    public void theSummaryCarriesWhatTheLauncherDidAndThenStartsOver() {
        RenderThreadStats stats = new RenderThreadStats(FAST, 100);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 0, 0, 7)), 0);
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 60, 0, 7)), SECOND);
        String text = stats.summarise("the big-core setting is on");
        assertTrue(text, text.startsWith("Last minute: "));
        assertTrue(text, text.endsWith("; the big-core setting is on"));
        assertFalse(stats.hasData());
        assertTrue(near(-1, stats.fastShare()));
        // The next minute starts clean, but the last reading stays as the baseline, so nothing is lost between two minutes.
        stats.add(RenderThreadStats.parseStat(statLine("Render thread", 110, 0, 7)), 2 * SECOND);
        String next = stats.summarise("");
        assertTrue(next, next.contains("busy 50% of the time"));
        assertTrue(next, next.contains("never above 50%"));
    }

    @Test
    public void onlyTheFourMostSeenCoresAreListed() {
        RenderThreadStats stats = new RenderThreadStats(0, 100);
        int[] cores = {0, 0, 0, 0, 1, 1, 1, 2, 2, 3, 4, 5};
        for (int i = 0; i < cores.length; i++) {
            stats.add(RenderThreadStats.parseStat(statLine("Render thread", i, 0, cores[i])), i * SECOND);
        }
        String text = stats.summarise("");
        assertTrue(text, text.contains("seen on cpu0 33%, cpu1 25%, cpu2 17%, cpu3 8%"));
        assertFalse(text, text.contains("cpu4"));
    }
}
