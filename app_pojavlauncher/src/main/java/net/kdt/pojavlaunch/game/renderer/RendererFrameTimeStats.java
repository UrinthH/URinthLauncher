package net.kdt.pojavlaunch.game.renderer;

import java.util.Arrays;

/**
 * Bounded frame-time statistics accumulator for URinthRender diagnostics.
 *
 * This class does not hook into Minecraft's render loop by itself. A trusted
 * in-game/frame-loop integration must call recordFrameTimeNanos() once per
 * completed frame before these metrics represent game performance.
 */
public final class RendererFrameTimeStats {
    private final long[] samplesNanos;
    private int nextIndex;
    private int sampleCount;
    private long totalNanos;
    private long spikeCount;

    /**
     * @param capacity number of recent frame samples to retain; must be positive
     */
    public RendererFrameTimeStats(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        samplesNanos = new long[capacity];
    }

    /** Record one completed frame's duration. Non-positive samples are ignored. */
    public synchronized void recordFrameTimeNanos(long frameTimeNanos) {
        if (frameTimeNanos <= 0) return;

        if (sampleCount == samplesNanos.length) {
            totalNanos -= samplesNanosNanosAt(nextIndex);
        } else {
            sampleCount++;
        }

        samplesNanos[nextIndex] = frameTimeNanos;
        totalNanos += frameTimeNanos;
        if (frameTimeNanos >= 50_000_000L) spikeCount++;
        nextIndex = (nextIndex + 1) % samplesNanos.length;
    }

    private long samplesNanosNanosAt(int index) {
        return samplesNanos[index];
    }

    /** Return a snapshot of the most recent bounded sample window. */
    public synchronized Snapshot snapshot() {
        if (sampleCount == 0) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0);
        }

        long[] sorted = new long[sampleCount];
        System.arraycopy(samplesNanos, 0, sorted, 0, sampleCount);
        Arrays.sort(sorted);

        double averageFrameMs = (totalNanos / (double) sampleCount) / 1_000_000.0;
        double averageFps = averageFrameMs > 0 ? 1000.0 / averageFrameMs : 0;
        double p50Ms = percentileMs(sorted, 0.50);
        double p95Ms = percentileMs(sorted, 0.95);
        double p99Ms = percentileMs(sorted, 0.99);
        // Approximate 1% low FPS from the 99th-percentile frame time.
        double onePercentLowFps = p99Ms > 0 ? 1000.0 / p99Ms : 0;

        long recentSpikes = 0;
        for (long sample : sorted) {
            if (sample >= 50_000_000L) recentSpikes++;
        }
        return new Snapshot(sampleCount, averageFps, averageFrameMs,
                p50Ms, p95Ms, p99Ms, onePercentLowFps, recentSpikes);
    }

    private static double percentileMs(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index] / 1_000_000.0;
    }

    /** Clear the rolling window before starting a separate benchmark run. */
    public synchronized void reset() {
        Arrays.fill(samplesNanos, 0L);
        nextIndex = 0;
        sampleCount = 0;
        totalNanos = 0;
        spikeCount = 0;
    }

    public static final class Snapshot {
        public final int sampleCount;
        public final double averageFps;
        public final double averageFrameMs;
        public final double p50FrameMs;
        public final double p95FrameMs;
        public final double p99FrameMs;
        public final double onePercentLowFps;
        public final long frameSpikesOver50Ms;

        private Snapshot(int sampleCount, double averageFps, double averageFrameMs,
                         double p50FrameMs, double p95FrameMs, double p99FrameMs,
                         double onePercentLowFps) {
            this(sampleCount, averageFps, averageFrameMs, p50FrameMs, p95FrameMs,
                    p99FrameMs, onePercentLowFps, 0);
        }

        private Snapshot(int sampleCount, double averageFps, double averageFrameMs,
                         double p50FrameMs, double p95FrameMs, double p99FrameMs,
                         double onePercentLowFps, long frameSpikesOver50Ms) {
            this.sampleCount = sampleCount;
            this.averageFps = averageFps;
            this.averageFrameMs = averageFrameMs;
            this.p50FrameMs = p50FrameMs;
            this.p95FrameMs = p95FrameMs;
            this.p99FrameMs = p99FrameMs;
            this.onePercentLowFps = onePercentLowFps;
            this.frameSpikesOver50Ms = frameSpikesOver50Ms;
        }
    }
}
