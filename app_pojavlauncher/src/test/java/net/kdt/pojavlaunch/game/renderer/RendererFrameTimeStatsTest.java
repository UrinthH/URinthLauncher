package net.kdt.pojavlaunch.game.renderer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RendererFrameTimeStatsTest {
    private static final double EPSILON = 0.01;

    @Test
    public void emptyWindowHasNoSamplesOrRates() {
        RendererFrameTimeStats.Snapshot snapshot = new RendererFrameTimeStats(4).snapshot();

        assertEquals(0, snapshot.sampleCount);
        assertEquals(0.0, snapshot.averageFps, EPSILON);
        assertEquals(0.0, snapshot.averageFrameMs, EPSILON);
        assertEquals(0L, snapshot.frameSpikesOver50Ms);
    }

    @Test
    public void ignoresNonPositiveSamples() {
        RendererFrameTimeStats stats = new RendererFrameTimeStats(4);
        stats.recordFrameTimeNanos(0);
        stats.recordFrameTimeNanos(-10);

        assertEquals(0, stats.snapshot().sampleCount);
    }

    @Test
    public void computesFrameTimePercentilesAndSpikes() {
        RendererFrameTimeStats stats = new RendererFrameTimeStats(8);
        stats.recordFrameTimeNanos(10_000_000L);
        stats.recordFrameTimeNanos(20_000_000L);
        stats.recordFrameTimeNanos(60_000_000L);

        RendererFrameTimeStats.Snapshot snapshot = stats.snapshot();
        assertEquals(3, snapshot.sampleCount);
        assertEquals(30.0, snapshot.averageFrameMs, EPSILON);
        assertEquals(1000.0 / 30.0, snapshot.averageFps, EPSILON);
        assertEquals(20.0, snapshot.p50FrameMs, EPSILON);
        assertEquals(60.0, snapshot.p95FrameMs, EPSILON);
        assertEquals(60.0, snapshot.p99FrameMs, EPSILON);
        assertEquals(1000.0 / 60.0, snapshot.onePercentLowFps, EPSILON);
        assertEquals(1L, snapshot.frameSpikesOver50Ms);
    }

    @Test
    public void onePercentLowAveragesSlowestOnePercentRatherThanUsingP99Proxy() {
        RendererFrameTimeStats stats = new RendererFrameTimeStats(200);
        for (int i = 0; i < 198; i++) {
            stats.recordFrameTimeNanos(10_000_000L); // 100 FPS
        }
        stats.recordFrameTimeNanos(50_000_000L); // 20 FPS
        stats.recordFrameTimeNanos(100_000_000L); // 10 FPS

        // Slowest 1% of 200 samples is two frames: average(20, 10) = 15 FPS.
        assertEquals(15.0, stats.snapshot().onePercentLowFps, EPSILON);
    }

    @Test
    public void evictsOldestSampleWhenWindowIsFull() {
        RendererFrameTimeStats stats = new RendererFrameTimeStats(2);
        stats.recordFrameTimeNanos(10_000_000L);
        stats.recordFrameTimeNanos(20_000_000L);
        stats.recordFrameTimeNanos(30_000_000L);

        RendererFrameTimeStats.Snapshot snapshot = stats.snapshot();
        assertEquals(2, snapshot.sampleCount);
        assertEquals(25.0, snapshot.averageFrameMs, EPSILON);
        assertEquals(20.0, snapshot.p50FrameMs, EPSILON);
        assertEquals(30.0, snapshot.p99FrameMs, EPSILON);
    }

    @Test
    public void resetClearsWindow() {
        RendererFrameTimeStats stats = new RendererFrameTimeStats(2);
        stats.recordFrameTimeNanos(16_000_000L);
        stats.reset();

        assertEquals(0, stats.snapshot().sampleCount);
    }

    @Test
    public void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RendererFrameTimeStats(0));
    }
}
