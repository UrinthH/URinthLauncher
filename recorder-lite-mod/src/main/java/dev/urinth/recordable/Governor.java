package dev.urinth.recordable;

/**
 * Keeps the recorder from costing frames per second: it picks how many pictures per second are captured and lowers that number when the
 * game gets slower, then raises it again when there is room.
 *
 * <p>Three things are watched:
 * <ul>
 *   <li>how much longer the frames right after a capture take than the frames that are not near a capture (this also covers the work of
 *       the graphics chip, which no timer inside the mod could see),</li>
 *   <li>how much processor time the mod's own capture code took, and</li>
 *   <li>whether the encoder keeps up.</li>
 * </ul>
 * The first two are turned into a "share of the game's time". When that share stays above the budget for a few seconds the capture rate
 * steps down one level; when it stays far below the budget for a long time it steps back up (never above the rate that was asked for).
 * If even the lowest level costs far too much, the governor asks for the recording to stop, because protecting the game is the point.
 *
 * <p>The graphics share can only be told apart from normal frame-to-frame changes when there are frames that follow no capture. That is
 * the case while the game runs at least about three times faster than the capture rate (90 fps game, 30 fps video). Below that the
 * governor works from the processor time and the encoder only.
 *
 * <p>No dependence on Minecraft or on the real clock: time is the sum of the frame times it is given, which makes it easy to test.
 */
public final class Governor {
    /** Capture rates, fastest first. */
    public static final int[] TIERS = {30, 24, 20, 15, 12, 10};

    /** Share of game time the recorder may use before the rate is lowered. */
    public static final double BUDGET = 0.035;
    /** Share below which the rate may go up again. */
    public static final double COMFORT = 0.012;
    /** Share that is not acceptable even at the lowest rate. */
    public static final double LIMIT = 0.12;

    private static final double ALPHA = 0.05;
    private static final long OVER_NS = 3_000_000_000L;
    private static final long UNDER_NS = 20_000_000_000L;
    private static final long LIMIT_NS = 8_000_000_000L;
    private static final long SETTLE_NS = 4_000_000_000L;
    private static final long MAX_INTERVAL_NS = 120_000_000L;
    private static final long CLEAN_FRESH_NS = 3_000_000_000L;
    private static final int CLEAN_MIN_SAMPLES = 8;

    private final int fastest;
    private int index;
    private double nearMs = -1;        // frame time right after a capture (first frame)
    private double nearMs2 = -1;       // second frame after a capture
    private double cleanMs = -1;       // frames that follow no capture closely
    private int cleanSamples;
    private long cleanAt = Long.MIN_VALUE / 2;
    private double hookMs;
    private boolean hookSeen;
    private double share;
    private long clock;
    private long overSince = -1;
    private long underSince = -1;
    private long limitSince = -1;
    private long lastChange;
    private int backlog;
    private long backlogWindowStart;
    private boolean stopRequested;
    private String lastReason = "";
    private boolean graphicsKnown;

    /** @param maxFps the highest capture rate wanted; the governor never goes above the highest tier at or below it */
    public Governor(int maxFps) {
        int f = TIERS.length - 1;
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i] <= maxFps) {
                f = i;
                break;
            }
        }
        this.fastest = f;
        this.index = f;
    }

    /** Current capture rate in pictures per second. */
    public int fps() {
        return TIERS[index];
    }

    public boolean stopRequested() {
        return stopRequested;
    }

    /** Estimated share of the game's time used by the recorder (0.02 = 2 percent). */
    public double share() {
        return share;
    }

    /** True if the graphics part of the cost could be measured (there were frames without a capture close by). */
    public boolean graphicsMeasured() {
        return graphicsKnown;
    }

    /** Why the rate changed last time; empty if it never did. */
    public String reason() {
        return lastReason;
    }

    /**
     * Reports one rendered frame.
     *
     * @param intervalNs time since the previous frame started
     * @param sinceCapture how many frames ago a capture was started: 1 means it was the previous frame; 0 or less means none yet
     * @param hookNs processor time the mod's capture code took in the previous frame (only looked at when sinceCapture is 1)
     */
    public void onFrame(long intervalNs, int sinceCapture, long hookNs) {
        if (intervalNs <= 0) {
            return;
        }
        long iv = Math.min(intervalNs, MAX_INTERVAL_NS);        // a loading stall says nothing about the recorder
        clock += iv;
        double ms = iv / 1e6;
        if (sinceCapture == 1) {
            nearMs = nearMs < 0 ? ms : nearMs + ALPHA * (ms - nearMs);
            double h = hookNs / 1e6;
            hookMs = hookSeen ? hookMs + ALPHA * (h - hookMs) : h;
            hookSeen = true;
        } else if (sinceCapture == 2) {
            nearMs2 = nearMs2 < 0 ? ms : nearMs2 + ALPHA * (ms - nearMs2);
        } else {
            cleanMs = cleanMs < 0 ? ms : cleanMs + ALPHA * (ms - cleanMs);
            cleanSamples++;
            cleanAt = clock;
        }
        if (!hookSeen) {
            return;
        }
        double rate = fps();
        double processor = hookMs * rate / 1000.0;
        double graphics = 0;
        graphicsKnown = cleanSamples >= CLEAN_MIN_SAMPLES && clock - cleanAt <= CLEAN_FRESH_NS && nearMs >= 0;
        if (graphicsKnown) {
            double excess = Math.max(0, nearMs - cleanMs);
            if (nearMs2 >= 0) {
                excess += Math.max(0, nearMs2 - cleanMs);
            }
            graphics = excess * rate / 1000.0;
        }
        share = Math.max(graphics, processor);
        evaluate();
    }

    /** The encoder could not take a picture: it is slower than the capture rate. */
    public void onBacklog() {
        if (clock - backlogWindowStart > 2_000_000_000L) {
            backlogWindowStart = clock;
            backlog = 0;
        }
        if (++backlog >= 3) {
            backlog = 0;
            backlogWindowStart = clock;
            stepDown("the encoder cannot keep up");
        }
    }

    private void evaluate() {
        if (clock - lastChange < SETTLE_NS) {
            return;                                            // let the averages catch up after a change
        }
        if (share > BUDGET) {
            underSince = -1;
            if (overSince < 0) {
                overSince = clock;
            }
            if (clock - overSince >= OVER_NS) {
                overSince = -1;
                stepDown(String.format("the recorder used %.1f%% of the game's time", share * 100));
            }
        } else {
            overSince = -1;
            if (share < COMFORT && index > fastest) {
                if (underSince < 0) {
                    underSince = clock;
                }
                if (clock - underSince >= UNDER_NS) {
                    underSince = -1;
                    index--;
                    lastChange = clock;
                    lastReason = "room to spare, back up to " + fps() + " fps";
                }
            } else {
                underSince = -1;
            }
        }
        if (index == TIERS.length - 1 && share > LIMIT) {
            if (limitSince < 0) {
                limitSince = clock;
            }
            if (clock - limitSince >= LIMIT_NS) {
                stopRequested = true;
                lastReason = String.format("even at %d fps the recorder used %.0f%% of the game's time", fps(), share * 100);
            }
        } else {
            limitSince = -1;
        }
    }

    /** @return true if the rate went down, false if it was already the lowest */
    private boolean stepDown(String why) {
        if (index >= TIERS.length - 1) {
            return false;
        }
        index++;
        lastChange = clock;
        lastReason = why + ", down to " + fps() + " fps";
        return true;
    }
}
