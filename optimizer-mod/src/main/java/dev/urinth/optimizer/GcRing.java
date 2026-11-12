package dev.urinth.optimizer;

/**
 * The last few seconds of the garbage collector's running total, one entry every poll of the monitor. It answers "how much of this
 * period was the game standing still in collections" for any period inside it, which is how a freeze is told apart from other kinds.
 * Plain Java, one thread only (the monitor's), unit tested.
 */
final class GcRing {
    private final long[] times;
    private final long[] totals;
    private int head;
    private int size;

    GcRing(int capacity) {
        times = new long[Math.max(2, capacity)];
        totals = new long[times.length];
    }

    /** Remembers the total (milliseconds in collections since the start) seen at {@code nanos}. Times only increase. */
    void add(long nanos, long totalMillis) {
        times[head] = nanos;
        totals[head] = totalMillis;
        head = (head + 1) % times.length;
        if (size < times.length) size++;
    }

    boolean isEmpty() {
        return size == 0;
    }

    /** The total at the newest entry that is not after {@code nanos}; the oldest entry if all are later; -1 if there are none. */
    long totalAt(long nanos) {
        if (size == 0) return -1;
        int newest = (head - 1 + times.length) % times.length;
        for (int i = 0; i < size; i++) {
            int at = (newest - i + times.length) % times.length;
            if (times[at] <= nanos) return totals[at];
        }
        int oldest = (head - size + times.length) % times.length;
        return totals[oldest];
    }

    /** The newest total, or -1. */
    long latest() {
        return size == 0 ? -1 : totals[(head - 1 + times.length) % times.length];
    }

    long latestTime() {
        return size == 0 ? 0 : times[(head - 1 + times.length) % times.length];
    }

    /**
     * The share (0 to 1) of the last {@code windowNanos} that went into collections. A ring that does not reach back that far
     * uses what it has; one with less than a second of data answers 0.
     */
    double share(long windowNanos) {
        if (size < 2) return 0;
        long now = latestTime();
        long from = now - windowNanos;
        int oldest = (head - size + times.length) % times.length;
        long start = Math.max(from, times[oldest]);
        long span = now - start;
        if (span < 1_000_000_000L) return 0;
        long gc = latest() - totalAt(start);
        if (gc <= 0) return 0;
        double share = gc * 1_000_000.0 / span;
        return share > 1 ? 1 : share;
    }
}
