package dev.urinth.boost;

/**
 * The /uboost bench test: measures what Boost really does on this phone, in the spot where the player stands.
 *
 * It runs four short stretches one after the other, off, max, max, off (so a scene that slowly gets heavier or the
 * phone warming up does not favour one side), takes one frame rate reading per second, throws away the first
 * reading after each switch and averages the rest. Plain Java; the game side only feeds it readings.
 *
 * The result says how much the two sides differ AND how much the two stretches of the same side differ from each
 * other. When those two stretches already disagree, the picture changed during the test (people moving, a fight
 * starting) and the number means little, and the result says so instead of showing a confident percentage.
 */
public final class Bench {
    /** The level held in each stretch. */
    static final int[] PLAN = {BoostLevel.OFF, BoostLevel.MAX, BoostLevel.MAX, BoostLevel.OFF};
    /** Readings thrown away at the start of each stretch. */
    static final int WARM = 1;
    /** Readings kept in each stretch. */
    static final int MEASURE = 5;
    /** Two stretches of the same side further apart than this (relative) make the result unreliable. */
    static final double UNSTABLE = 0.12;
    /** A gain below this is no gain: it is inside what the measurement can tell apart. */
    static final double NO_GAIN = 0.03;

    /** What the test found. */
    public static final class Result {
        public final double offAverage;
        public final double maxAverage;
        public final int offLowest;
        public final int offHighest;
        public final int maxLowest;
        public final int maxHighest;
        /** (max - off) / off. */
        public final double gain;
        /** How far apart the two "off" stretches were, relative to their mean. */
        public final double offSpread;
        public final double maxSpread;

        Result(double offAverage, double maxAverage, int offLowest, int offHighest, int maxLowest, int maxHighest,
               double offSpread, double maxSpread) {
            this.offAverage = offAverage;
            this.maxAverage = maxAverage;
            this.offLowest = offLowest;
            this.offHighest = offHighest;
            this.maxLowest = maxLowest;
            this.maxHighest = maxHighest;
            this.gain = offAverage > 0 ? (maxAverage - offAverage) / offAverage : 0;
            this.offSpread = offSpread;
            this.maxSpread = maxSpread;
        }

        /** True when the stretches of one side disagree so much that the comparison is not trustworthy. */
        public boolean unstable() {
            return offSpread > UNSTABLE || maxSpread > UNSTABLE;
        }

        /** True when the difference is too small to be a real gain. */
        public boolean noGain() {
            return gain < NO_GAIN;
        }
    }

    private boolean active;
    private int phase;
    private int readings;
    private final double[] sum = new double[PLAN.length];
    private final int[] low = new int[PLAN.length];
    private final int[] high = new int[PLAN.length];
    private final int[] kept = new int[PLAN.length];
    private Result result;

    public void start() {
        active = true;
        phase = 0;
        readings = 0;
        result = null;
        for (int i = 0; i < PLAN.length; i++) {
            sum[i] = 0;
            low[i] = Integer.MAX_VALUE;
            high[i] = 0;
            kept[i] = 0;
        }
    }

    /** Abandons a test in progress without a result. */
    public void cancel() {
        active = false;
        result = null;
    }

    public boolean active() {
        return active;
    }

    /** The level the game must use right now. Only meaningful while {@link #active()}. */
    public int level() {
        return PLAN[Math.min(phase, PLAN.length - 1)];
    }

    /** The finished result, or null while there is none (not run yet, running, or cancelled). */
    public Result result() {
        return result;
    }

    /** Seconds the whole test takes. */
    public static int totalSeconds() {
        return PLAN.length * (WARM + MEASURE);
    }

    /** Seconds still to go while active. */
    public int secondsLeft() {
        if (!active) return 0;
        int done = phase * (WARM + MEASURE) + readings;
        return Math.max(0, totalSeconds() - done);
    }

    /**
     * One reading, about once a second, while {@link #active()}. After the last one the test is over and
     * {@link #result()} is set.
     */
    public void sample(int fps) {
        if (!active || fps <= 0) return;
        if (readings >= WARM) {
            sum[phase] += fps;
            kept[phase]++;
            low[phase] = Math.min(low[phase], fps);
            high[phase] = Math.max(high[phase], fps);
        }
        readings++;
        if (readings >= WARM + MEASURE) {
            phase++;
            readings = 0;
            if (phase >= PLAN.length) finish();
        }
    }

    private void finish() {
        active = false;
        double off = mean(0, 3);
        double max = mean(1, 2);
        result = new Result(off, max,
                Math.min(low[0], low[3]), Math.max(high[0], high[3]),
                Math.min(low[1], low[2]), Math.max(high[1], high[2]),
                spread(0, 3), spread(1, 2));
    }

    private double mean(int a, int b) {
        int n = kept[a] + kept[b];
        return n == 0 ? 0 : (sum[a] + sum[b]) / n;
    }

    private double spread(int a, int b) {
        double meanA = kept[a] == 0 ? 0 : sum[a] / kept[a];
        double meanB = kept[b] == 0 ? 0 : sum[b] / kept[b];
        double mid = (meanA + meanB) / 2;
        return mid <= 0 ? 0 : Math.abs(meanA - meanB) / mid;
    }
}
