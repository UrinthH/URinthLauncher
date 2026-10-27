package net.kdt.pojavlaunch.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the time between tapping Play and the game window goes.
 *
 * <p>Each step is recorded with the wall-clock time it finished at, so steps that happen in the
 * launcher process (tap) and in the game process (everything after) share one clock. The summary
 * lists every step with how long it took and is written to the game log once the window shows,
 * so a slow start can be traced to a step instead of guessed at.
 *
 * <p>Plain Java on purpose: no Android classes, so it is covered by JVM unit tests.
 */
public final class StartupTimeline {
    private static volatile StartupTimeline sCurrent;

    private final long mStartMs;
    private final List<String> mLabels = new ArrayList<>();
    private final List<Long> mTimes = new ArrayList<>();

    /** @param startMs when the player tapped Play (epoch milliseconds) */
    public StartupTimeline(long startMs) {
        mStartMs = startMs;
    }

    /** Begin a new timeline for this launch. Replaces any earlier one. */
    public static void begin(long startMs) {
        sCurrent = new StartupTimeline(startMs);
    }

    /** Record a finished step on the current timeline, if there is one. */
    public static void markNow(String label) {
        StartupTimeline timeline = sCurrent;
        if (timeline != null) timeline.mark(label, System.currentTimeMillis());
    }

    /** The summary line of the current timeline, or null when none was started. */
    public static String currentSummary() {
        StartupTimeline timeline = sCurrent;
        return timeline == null ? null : timeline.summary();
    }

    public synchronized void mark(String label, long timeMs) {
        mLabels.add(label);
        mTimes.add(timeMs);
    }

    /** One line: each step with its duration, then the total from the tap. */
    public synchronized String summary() {
        StringBuilder out = new StringBuilder("[Startup] ");
        long previous = mStartMs;
        for (int i = 0; i < mLabels.size(); i++) {
            long now = mTimes.get(i);
            if (i > 0) out.append(", ");
            out.append(mLabels.get(i)).append(' ').append(seconds(Math.max(0, now - previous)));
            previous = now;
        }
        out.append(" | total ").append(seconds(Math.max(0, previous - mStartMs)));
        return out.toString();
    }

    /** 1234 becomes "1.2s". */
    static String seconds(long millis) {
        return String.format(Locale.US, "%.1fs", millis / 1000.0);
    }
}
