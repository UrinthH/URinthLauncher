package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LagWatchTest {
    private static final int TARGET = 85;

    /** One pretend second after another, with counters that only go up. */
    private static final class Clock {
        final LagWatch watch = new LagWatch();
        final LagWatch.Reading reading = new LagWatch.Reading();
        long now = 100_000;
        long gcCount;
        long gcMillis;
        long entityCalls;
        long playerCalls;
        long heapUsed = 400;
        long heapMax = 1024;
        boolean knowsMemory = true;
        boolean knowsGc = true;

        String second(int fps, long gcMsThisSecond, long entitiesPerFrame) {
            if (gcMsThisSecond > 0) {
                gcCount++;
                gcMillis += gcMsThisSecond;
            }
            entityCalls += entitiesPerFrame * fps;
            playerCalls += 5L * fps;
            reading.gcCount = knowsGc ? gcCount : -1;
            reading.gcMillis = knowsGc ? gcMillis : -1;
            reading.heapUsedMb = knowsMemory ? heapUsed : -1;
            reading.heapMaxMb = knowsMemory ? heapMax : -1;
            reading.entityCalls = entityCalls;
            reading.playerCalls = playerCalls;
            now += 1000;
            return watch.sample(now, fps, TARGET, "Max", reading);
        }

        String second(int fps) {
            return second(fps, 0, 20);
        }
    }

    @Test
    public void nothingIsSaidWhileTheFrameRateIsFine() {
        Clock clock = new Clock();
        for (int i = 0; i < 100; i++) assertNull(clock.second(88));
        for (int i = 0; i < 100; i++) assertNull(clock.second(60), "low but not below half of the aim");
        assertEquals(0, clock.watch.episodes());
        assertEquals(0, clock.watch.worstFps());
        assertEquals("", clock.watch.last());
    }

    @Test
    public void anEpisodeIsReportedOnceWhenTheFrameRateIsBack() {
        Clock clock = new Clock();
        clock.second(88);
        assertNull(clock.second(8));
        assertNull(clock.second(9));
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.startsWith(LagWatch.TAG), line);
        assertTrue(line.contains("lasted 2 s"), line);
        assertTrue(line.contains("lowest 8 fps"), line);
        assertTrue(line.contains("back to 88 fps"), line);
        assertTrue(line.contains("aim 85"), line);
        assertNull(clock.second(88));
        assertEquals(1, clock.watch.episodes());
        assertEquals(8, clock.watch.worstFps());
        assertEquals(line, clock.watch.last());
    }

    @Test
    public void theEpisodeStaysOpenUntilTheFrameRateIsNearTheAim() {
        Clock clock = new Clock();
        clock.second(88);
        assertNull(clock.second(8));
        assertNull(clock.second(60));
        assertNull(clock.second(70));
        assertNull(clock.second(75), "75 is still under 90 percent of 85");
        String line = clock.second(80);
        assertNotNull(line);
        assertTrue(line.contains("lasted 4 s"), line);
        assertTrue(line.contains("back to 80 fps"), line);
        assertEquals(1, clock.watch.episodes());
    }

    @Test
    public void separateEpisodesAreCountedSeparately() {
        Clock clock = new Clock();
        for (int round = 0; round < 3; round++) {
            clock.second(88);
            clock.second(20 - round * 5);
            assertNotNull(clock.second(88));
        }
        assertEquals(3, clock.watch.episodes());
        assertEquals(10, clock.watch.worstFps());
    }

    @Test
    public void longCollectionsAreNamedAsTheLikelyCause() {
        Clock clock = new Clock();
        clock.second(88);
        clock.second(90, 600, 20);
        clock.second(10, 0, 20);
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.contains("memory collections: 1 (600 ms)"), line);
        assertTrue(line.contains("likely: memory collections"), line);
    }

    @Test
    public void theSecondBeforeTheDropCountsBecauseTheFrameRateLagsBehind() {
        Clock clock = new Clock();
        clock.second(88);
        clock.second(88, 500, 20);
        clock.second(9, 0, 20);
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.contains("(500 ms)"), line);
    }

    @Test
    public void aFullMemoryIsNamedWhenNothingElseIs() {
        Clock clock = new Clock();
        clock.second(88);
        clock.heapUsed = 990;
        clock.second(9);
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.contains("memory up to 96% of 1024 MB"), line);
        assertTrue(line.contains("likely: memory nearly full"), line);
    }

    @Test
    public void aCrowdIsNamedWhenMemoryLooksFine() {
        Clock clock = new Clock();
        clock.second(88);
        clock.second(12, 0, 220);
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.contains("up to 220 entities"), line);
        assertTrue(line.contains("220 entities, 5 players"), line);
        assertTrue(line.contains("likely: a crowd of entities"), line);
    }

    @Test
    public void whenNoneOfThoseFitItSaysSoInsteadOfGuessing() {
        Clock clock = new Clock();
        clock.second(88);
        clock.second(12, 40, 30);
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.contains("likely: not memory and not a crowd"), line);
    }

    @Test
    public void numbersTheGameCannotGiveAreLeftOutAndNothingIsClaimedAboutThem() {
        Clock clock = new Clock();
        clock.knowsMemory = false;
        clock.knowsGc = false;
        clock.second(88);
        clock.second(12, 0, 30);
        String line = clock.second(88);
        assertNotNull(line);
        assertFalse(line.contains("memory collections:"), line);
        assertFalse(line.contains("memory up to"), line);
        assertTrue(line.contains("likely: cannot tell"), line);
    }

    @Test
    public void aLongEpisodeSaysHowItIsGoingAndThenOnlyOnceAMinute() {
        Clock clock = new Clock();
        clock.second(88);
        int lines = 0;
        String first = null;
        for (int i = 0; i < 140; i++) {
            String line = clock.second(9);
            if (line != null) {
                lines++;
                if (first == null) first = line;
            }
        }
        assertEquals(3, lines, "after 10 s, 70 s and 130 s");
        assertTrue(first.contains("still going after 10 s"), first);
        assertTrue(first.contains("now 9 fps"), first);
        String end = clock.second(88);
        assertNotNull(end);
        assertTrue(end.contains("lasted 140 s"), end);
        assertEquals(1, clock.watch.episodes());
    }

    @Test
    public void aGameStartWritesNoMoreThanThirtyLines() {
        Clock clock = new Clock();
        int lines = 0;
        String lastLine = null;
        for (int round = 0; round < 100; round++) {
            clock.second(88);
            clock.second(9);
            String line = clock.second(88);
            if (line != null) {
                lines++;
                lastLine = line;
            }
        }
        assertEquals(LagWatch.MAX_LINES, lines);
        assertTrue(lastLine.contains("the last lag line"), lastLine);
        assertEquals(100, clock.watch.episodes(), "they are still counted");
    }

    @Test
    public void leavingTheWorldDropsAnEpisodeWithoutALine() {
        Clock clock = new Clock();
        clock.second(88);
        clock.second(9);
        clock.watch.leaveWorld();
        assertNull(clock.second(88));
        assertNull(clock.second(88));
        assertEquals(1, clock.watch.episodes());
    }

    @Test
    public void aReadingWithoutAFrameRateIsIgnored() {
        Clock clock = new Clock();
        clock.second(88);
        assertNull(clock.second(0));
        assertNull(clock.second(-3));
        assertEquals(0, clock.watch.episodes());
        assertNull(clock.watch.sample(1, 9, 0, "Max", clock.reading), "no aim, nothing to compare with");
    }

    @Test
    public void theFirstReadingIsNotMistakenForAllTheMemoryCollectionsSoFar() {
        Clock clock = new Clock();
        clock.gcCount = 40;
        clock.gcMillis = 5_000;
        clock.second(9);
        String line = clock.second(88);
        assertNotNull(line);
        assertTrue(line.contains("memory collections: 0 (0 ms)"), line);
    }
}
