package dev.urinth.sunflare;

/**
 * Frame times of the last {@value #WINDOW} frames, and a count of the hitches and freezes since the start. No Minecraft classes,
 * no garbage per frame (fixed arrays), unit tested.
 *
 * A <b>hitch</b> is a frame that took at least {@link #HITCH_MS} and more than {@link #HITCH_RATIO} times the recent average: a
 * visible stutter. A <b>freeze</b> is a frame of {@link #FREEZE_MS} or more. The first frames after start are never counted
 * (the game is loading then).
 */
final class FrameStats {
    static final int WINDOW = 600;
    static final double HITCH_MS = 40.0;
    static final double FREEZE_MS = 120.0;
    static final double HITCH_RATIO = 2.5;
    /** Frames to see before hitches are counted. */
    static final int WARMUP = 120;

    static final int NORMAL = 0;
    static final int HITCH = 1;
    static final int FREEZE = 2;

    private final double[] ring = new double[WINDOW];
    private final double[] scratch = new double[WINDOW];
    private int count;
    private int pos;
    private double sum;
    private long total;
    private long hitches;
    private long freezes;
    private double worst;

    /** Records a frame. @return {@link #NORMAL}, {@link #HITCH} or {@link #FREEZE} */
    int frame(double ms) {
        if (!(ms > 0.0)) return NORMAL;
        double avgBefore = count == 0 ? 0.0 : sum / count;
        int kind = NORMAL;
        if (total >= WARMUP) {
            if (ms >= FREEZE_MS) kind = FREEZE;
            else if (ms >= HITCH_MS && ms > HITCH_RATIO * avgBefore) kind = HITCH;
        }
        if (kind == HITCH) hitches++;
        if (kind == FREEZE) freezes++;
        if (kind != NORMAL && ms > worst) worst = ms;
        total++;
        if (count == WINDOW) sum -= ring[pos];
        else count++;
        ring[pos] = ms;
        sum += ms;
        pos = (pos + 1) % WINDOW;
        return kind;
    }

    long frames() {
        return total;
    }

    long hitches() {
        return hitches;
    }

    long freezes() {
        return freezes;
    }

    /** The longest hitch or freeze since the start, in ms (0 if none). */
    double worstMillis() {
        return worst;
    }

    double avgMillis() {
        return count == 0 ? 0.0 : sum / count;
    }

    double avgFps() {
        double a = avgMillis();
        return a <= 0.0 ? 0.0 : 1000.0 / a;
    }

    /** The "1% low": the frame rate of the slowest hundredth of the recent frames (their average frame time). */
    double lowFps() {
        if (count == 0) return 0.0;
        System.arraycopy(ring, 0, scratch, 0, count);
        java.util.Arrays.sort(scratch, 0, count);
        int n = Math.max(1, count / 100);
        double s = 0.0;
        for (int i = 0; i < n; i++) s += scratch[count - 1 - i];
        double ms = s / n;
        return ms <= 0.0 ? 0.0 : 1000.0 / ms;
    }

    void reset() {
        count = 0;
        pos = 0;
        sum = 0.0;
        total = 0;
        hitches = 0;
        freezes = 0;
        worst = 0.0;
    }
}
