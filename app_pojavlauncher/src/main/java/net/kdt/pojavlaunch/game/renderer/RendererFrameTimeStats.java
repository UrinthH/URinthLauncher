package net.kdt.pojavlaunch.game.renderer;

import java.util.Arrays;
import java.util.Locale;

/**
 * Bounded frame-time statistics accumulator for URinthRender diagnostics.
 *
 * This class does not hook into Minecraft's render loop by itself. A trusted
 * in-game/frame-loop integration must call recordFrameTimeNanos() once per
 * completed frame before these metrics represent game performance.
 */
public final class RendererFrameTimeStats {
    private static final long FRAME_SPIKE_THRESHOLD_NANOS = 50_000_000L;
    private final long[] samplesNanos;
    private int nextIndex;
    private int sampleCount;
    private long totalNanos;

    /** @param capacity number of recent frame samples to retain; must be positive */
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
            totalNanos -= samplesNanos[nextIndex];
        } else {
            sampleCount++;
        }

        samplesNanos[nextIndex] = frameTimeNanos;
        totalNanos += frameTimeNanos;
        nextIndex = (nextIndex + 1) % samplesNanos.length;
    }

    /** Return a snapshot of the most recent bounded sample window. */
    public synchronized Snapshot snapshot() {
        if (sampleCount == 0) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Every slot is valid once full; before full, valid samples occupy [0, sampleCount).
        long[] sorted = Arrays.copyOf(samplesNanos, sampleCount);
        Arrays.sort(sorted);

        double averageFrameMs = (totalNanos / (double) sampleCount) / 1_000_000.0;
        double averageFps = averageFrameMs > 0 ? 1000.0 / averageFrameMs : 0;
        double p50Ms = percentileMs(sorted, 0.50);
        double p95Ms = percentileMs(sorted, 0.95);
        double p99Ms = percentileMs(sorted, 0.99);
        // Approximate 1% low FPS from the 99th-percentile frame time.
        double onePercentLowFps = p99Ms > 0 ? 1000.0 / p99Ms : 0;

        long spikes = 0;
        for (long sample : sorted) {
            if (sample >= FRAME_SPIKE_THRESHOLD_NANOS) spikes++;
        }
        return new Snapshot(sampleCount, averageFps, averageFrameMs,
                p50Ms, p95Ms, p99Ms, onePercentLowFps, spikes);
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

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "samples=%d avgFps=%.2f avgFrameMs=%.3f p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f onePercentLowFps=%.2f spikesOver50Ms=%d",
                    sampleCount, averageFps, averageFrameMs, p50FrameMs, p95FrameMs,
                    p99FrameMs, onePercentLowFps, frameSpikesOver50Ms);
        }
    }
}
