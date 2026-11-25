package dev.urinth.recordable;

/**
 * Keeps the recorder from costing frames per second: it picks how many pictures per second are captured and lowers that number when the
 * game gets slower, then raises it again when there is room.
 *
 * <p>Two ways of watching the game, depending on how often pictures are taken:
 * <ul>
 *   <li><b>Normal mode</b> (a picture every second frame or less often). The frames right after a capture are compared with frames
 *       that are not near a capture. That tells how much the recorder costs, including the work of the graphics chip, which no timer
 *       inside the mod could see. The mod's own processor time is added to that, and so is whether the encoder keeps up.</li>
 *   <li><b>Fast mode</b> (a picture on most frames, as with 90 fps). Now there are no frames free of captures to compare with, so the
 *       frame time measured before the first capture is the yardstick: if the game becomes clearly slower than that for a few seconds,
 *       the rate steps down, which brings it back to normal mode where the comparison works again.</li>
 * </ul>
 * When the cost stays above the budget for a few seconds the capture rate steps down one level; when it stays far below the budget for
 * a long time it steps back up (never above the rate that was asked for). If even the lowest level costs far too much, the governor
 * asks for the recording to stop, because protecting the game is the point.
 *
 * <p>No dependence on Minecraft or on the real clock: time is the sum of the frame times it is given, which makes it easy to test.
 */
public final class Governor {
    /** Capture rates, fastest first. */
    public static final int[] TIERS = {90, 60, 45, 30, 24, 20, 15, 12, 10};

    /** Share of game time the recorder may use before the rate is lowered (normal mode). */
    public static final double BUDGET = 0.035;
    /** Share below which the rate may go up again (normal mode). */
    public static final double COMFORT = 0.012;
    /** Share that is not acceptable even at the lowest rate. */
    public static final double LIMIT = 0.12;
    /** The same budget for the mod's own processor time in fast mode, where every picture costs a little. */
    public static final double FAST_BUDGET = 0.06;
    public static final double FAST_COMFORT = 0.02;
    /** How much slower than before the first capture the game may get in fast mode before the rate steps down. */
    public static final double FAST_LIMIT = 0.08;

    private static final double ALPHA = 0.05;
    private static final double FAST_ALPHA = 0.02;
    private static final long OVER_NS = 3_000_000_000L;
    private static final long UNDER_NS = 20_000_000_000L;
    private static final long LIMIT_NS = 8_000_000_000L;
    private static final long SETTLE_NS = 4_000_000_000L;
    private static final long FAST_OVER_NS = 4_000_000_000L;
    /** After the fast mode guard stepped down, going back into fast mode is not tried again for this long. */
    private static final long FAST_BLOCK_NS = 120_000_000_000L;
    private static final long MAX_INTERVAL_NS = 120_000_000L;
    private static final long CLEAN_FRESH_NS = 3_000_000_000L;
    private static final int CLEAN_MIN_SAMPLES = 8;
    private static final int FAST_MIN_FRAMES = 60;
    /** The first this many frames without a capture are averaged by their median, so one long frame at the start cannot skew the yardstick. */
    private static final int WARMUP = 24;
    /** A frame that follows no capture closely is wanted at least this often, or the graphics cost cannot be measured. */
    private static final long CLEAN_WANTED_NS = 350_000_000L;

    private final int fastest;
    private int index;
    private double nearMs = -1;        // frame time right after a capture (first frame)
    private double nearMs2 = -1;       // second frame after a capture
    private double cleanMs = -1;       // frames that follow no capture closely
    private int cleanSamples;
    private final double[] warm = new double[WARMUP];
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
    private boolean fast;
    private double fastMs = -1;
    private int fastFrames;
    private long fastOverSince = -1;
    private long upBlockedUntil;

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

    /** True if the last frame was handled in fast mode (a picture on most frames). */
    public boolean fastMode() {
        return fast;
    }

    /** The frame time of the game without captures in milliseconds, or -1 if it is not known yet. */
    public double baselineMs() {
        return cleanMs;
    }

    /** True once enough frames without captures were seen to judge the game's speed. */
    public boolean hasBaseline() {
        return cleanSamples >= CLEAN_MIN_SAMPLES;
    }

    /**
     * True when no frame free of capture influence was seen for a little while. The recorder then waits one more frame before the next
     * capture. Without this, a capture that makes frames longer pushes the game into "every second frame is a capture", where nothing
     * can be compared any more and the governor would be blind exactly when it is needed. Never true in fast mode.
     */
    public boolean needsCleanFrame() {
        return !fast && clock - cleanAt > CLEAN_WANTED_NS;
    }

    /** Why the rate changed last time; empty if it never did. */
    public String reason() {
        return lastReason;
    }

    /** Same as the four argument version, for normal mode. */
    public void onFrame(long intervalNs, int sinceCapture, long hookNs) {
        onFrame(intervalNs, sinceCapture, hookNs, false);
    }

    /**
     * Reports one rendered frame.
     *
     * @param intervalNs time since the previous frame started
     * @param sinceCapture how many frames ago a capture was started: 1 means it was the previous frame; 0 or less means none yet
     * @param hookNs processor time the mod's capture code took in the previous frame (only looked at when sinceCapture is 1)
     * @param fastMode true if pictures are taken on most frames, so there are no frames to compare with
     */
    public void onFrame(long intervalNs, int sinceCapture, long hookNs, boolean fastMode) {
        if (intervalNs <= 0) {
            return;
        }
        long iv = Math.min(intervalNs, MAX_INTERVAL_NS);        // a loading stall says nothing about the recorder
        clock += iv;
        double ms = iv / 1e6;
        if (fastMode != fast) {
            fast = fastMode;
            fastMs = -1;
            fastFrames = 0;
            fastOverSince = -1;
            overSince = -1;
            underSince = -1;
        }
        if (fast && sinceCapture > 0) {
            fastMs = fastMs < 0 ? ms : fastMs + FAST_ALPHA * (ms - fastMs);
            fastFrames++;
            if (sinceCapture == 1) {
                noteHook(hookNs);
            }
        } else if (sinceCapture == 1) {
            nearMs = nearMs < 0 ? ms : nearMs + ALPHA * (ms - nearMs);
            noteHook(hookNs);
        } else if (sinceCapture == 2) {
            nearMs2 = nearMs2 < 0 ? ms : nearMs2 + ALPHA * (ms - nearMs2);
        } else {
            if (cleanSamples < WARMUP) {
                warm[cleanSamples] = ms;
                cleanMs = median(warm, cleanSamples + 1);
            } else {
                cleanMs += ALPHA * (ms - cleanMs);
            }
            cleanSamples++;
            cleanAt = clock;
        }
        if (!hookSeen) {
            return;
        }
        double rate = fps();
        double processor = hookMs * rate / 1000.0;
        double graphics = 0;
        graphicsKnown = !fast && cleanSamples >= CLEAN_MIN_SAMPLES && clock - cleanAt <= CLEAN_FRESH_NS && nearMs >= 0;
        if (graphicsKnown) {
            double excess = Math.max(0, nearMs - cleanMs);
            if (nearMs2 >= 0) {
                excess += Math.max(0, nearMs2 - cleanMs);
            }
            graphics = excess * rate / 1000.0;
        }
        share = Math.max(graphics, processor);
        evaluate();
        if (fast) {
            evaluateFast();
        }
    }

    private static double median(double[] values, int n) {
        double[] copy = java.util.Arrays.copyOf(values, n);
        java.util.Arrays.sort(copy);
        return n % 2 == 1 ? copy[n / 2] : 0.5 * (copy[n / 2 - 1] + copy[n / 2]);
    }

    private void noteHook(long hookNs) {
        double h = hookNs / 1e6;
        hookMs = hookSeen ? hookMs + ALPHA * (h - hookMs) : h;
        hookSeen = true;
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
        double budget = fast ? FAST_BUDGET : BUDGET;
        double comfort = fast ? FAST_COMFORT : COMFORT;
        if (share > budget) {
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
            if (share < comfort && index > fastest && clock >= upBlockedUntil) {
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

    /** Fast mode: is the game clearly slower than before the recording started taking pictures? */
    private void evaluateFast() {
        if (cleanSamples < CLEAN_MIN_SAMPLES || cleanMs <= 0 || fastFrames < FAST_MIN_FRAMES || fastMs < 0) {
            return;
        }
        if (clock - lastChange < SETTLE_NS) {
            return;
        }
        double drop = (fastMs - cleanMs) / cleanMs;
        if (drop > FAST_LIMIT) {
            if (fastOverSince < 0) {
                fastOverSince = clock;
            }
            if (clock - fastOverSince >= FAST_OVER_NS) {
                fastOverSince = -1;
                if (stepDown(String.format("the game was %.0f%% slower while recording", drop * 100))) {
                    upBlockedUntil = clock + FAST_BLOCK_NS;
                }
            }
        } else {
            fastOverSince = -1;
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
