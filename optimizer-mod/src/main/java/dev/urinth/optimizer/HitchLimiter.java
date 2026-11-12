package dev.urinth.optimizer;

/**
 * Keeps the freeze lines in the log few: a burst of {@link #BURST}, then one more every {@link #REFILL_NANOS}. Lines that were
 * held back are counted and reported in the one-minute summary instead. Plain Java, unit tested.
 */
final class HitchLimiter {
    static final int BURST = 4;
    static final long REFILL_NANOS = 5_000_000_000L;

    private double tokens = BURST;
    private long last;
    private int suppressed;

    /** True if a line may be written at {@code nowNanos}. */
    boolean allow(long nowNanos) {
        if (last != 0 && nowNanos > last) {
            tokens = Math.min(BURST, tokens + (nowNanos - last) / (double) REFILL_NANOS);
        }
        last = nowNanos;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        suppressed++;
        return false;
    }

    /** How many lines were held back since the last call. */
    int takeSuppressed() {
        int n = suppressed;
        suppressed = 0;
        return n;
    }
}
