package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The freeze monitor on its own thread: a long frame is reported with the numbers around it, and only while the game is watched. */
class MonitorTest {
    @Test
    void aLongFrameIsReportedWithWhatTheGameWasDoing() throws InterruptedException {
        String before = Monitor.lastHitch();
        Monitor.start();
        try {
            assertTrue(Monitor.running());
            Monitor.setWatching(true);
            Monitor.frame(); // the first frame only starts the clock
            Thread.sleep(20);
            Monitor.frame(); // a normal frame
            Thread.sleep(220); // a freeze
            Monitor.frame();
            String line = waitForNewLine(before);
            assertFalse(line.equals(before), "no freeze was reported");
            assertTrue(line.startsWith("hitch "), line);
            assertTrue(line.contains(" ms: "), line);
            assertTrue(line.contains("heap "), line);
            assertTrue(line.contains("free RAM "), line);
            assertTrue(line.contains("server tick "), line);
            assertTrue(line.contains("fps "), line);
            long ms = Long.parseLong(line.substring("hitch ".length(), line.indexOf(" ms")));
            assertTrue(ms >= 200 && ms < 5000, "reported " + ms + " ms");
        } finally {
            Monitor.setWatching(false);
            Monitor.stop();
        }
        assertFalse(Monitor.running());
    }

    @Test
    void nothingIsReportedWhileTheGameIsNotWatched() throws InterruptedException {
        Monitor.start();
        try {
            Monitor.setWatching(false);
            String before = Monitor.lastHitch();
            Monitor.frame();
            Thread.sleep(160);
            Monitor.frame();
            Thread.sleep(300);
            assertEquals(before, Monitor.lastHitch());
        } finally {
            Monitor.stop();
        }
    }

    @Test
    void startingTwiceKeepsOneThread() {
        Monitor.start();
        try {
            Monitor.start();
            assertTrue(Monitor.running());
        } finally {
            Monitor.stop();
        }
    }

    @Test
    void theMemoryLineSaysWhatIsKnown() {
        String text = Monitor.describe();
        assertTrue(text.startsWith("Memory "), text);
        assertTrue(text.contains("free"), text);
        assertTrue(text.contains("heap"), text);
        assertEquals("1 MB", Monitor.mb(1L << 20));
        assertEquals("?", Monitor.mb(-1));
    }

    @Test
    void withoutAShortageTheSimulationDistanceIsNotLimited() {
        assertEquals(MemoryGuard.Level.OK, Monitor.level());
        assertEquals(0, Monitor.simulationLimit());
    }

    private static String waitForNewLine(String before) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (Monitor.lastHitch().equals(before) && System.nanoTime() < deadline) Thread.sleep(20);
        return Monitor.lastHitch();
    }
}
