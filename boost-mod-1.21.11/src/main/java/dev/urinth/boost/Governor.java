package dev.urinth.boost;

import java.util.Locale;

/**
 * The automatic part: picks the {@link BoostLevel} from the frame rate, once a second. Plain Java, no game code,
 * so the whole decision is unit tested with simulated frame rates.
 *
 * The rules, in order of importance:
 * <ul>
 * <li><b>Only shed when it is needed.</b> At or above the target frame rate nothing is taken away.</li>
 * <li><b>Only keep shedding that helps.</b> After raising the level it waits a moment, measures the frame rate
 * again and compares. If the gain is too small (the slowdown comes from the terrain, the screen, the phone's heat,
 * not from particles or far entities) the level is put back and raising is switched off for a while, longer each
 * time it happens again. A limit that does not buy frames only makes the picture worse, so it is not kept.</li>
 * <li><b>Come back to full quality.</b> When the frame rate would still be at the target without the current
 * level (using the gain measured when the level was raised), the level is lowered again after a few seconds.
 * When that evidence is missing, lowering is tried after a longer calm stretch, and put back if the frame rate
 * falls again, with a longer wait each time that happens.</li>
 * <li><b>No flicker.</b> At least a few seconds between changes, a measured judgement after every change, and
 * back-off after every wrong guess.</li>
 * </ul>
 */
public final class Governor {
    /** A frame rate below this share of the target is a severe drop: one such sample is enough to raise. */
    static final double SEVERE_FACTOR = 0.5;
    /** Samples in a row below the target before the level is raised. */
    static final int FALL_SAMPLES = 2;
    /** At least this long between two changes of level. */
    static final long CHANGE_COOLDOWN_MS = 3_000;
    /** Samples thrown away after a change, because they still hold frames from before it. */
    static final int SETTLE_SAMPLES = 1;
    /** Samples averaged to judge a change. */
    static final int JUDGE_SAMPLES = 3;
    /** The smallest gain that makes a raise INTO a level worth keeping. More shedding must prove more. */
    static final double[] MIN_GAIN = {0.0, 0.04, 0.07, 0.10};
    /** How long raising is off after a raise that did not help; doubles each time it happens again. */
    static final long FUTILE_LOCK_MS = 45_000;
    static final long FUTILE_LOCK_MAX_MS = 300_000;
    /** The scene counts as a different one when the frame rate moved this far from where the lock started. */
    static final double SCENE_CHANGE = 0.25;
    /** Samples in a row with room to spare before the level is lowered. */
    static final int HEADROOM_SAMPLES = 6;
    /** How close to the target the frame rate of a lower level must be expected to stay. */
    static final double SLACK = 0.97;
    /** Calm for this long at a raised level (at or above the target) and a lower level is tried anyway. */
    static final long PROBE_AFTER_MS = 20_000;
    static final long PROBE_MAX_MS = 300_000;

    private enum Judging { NONE, RAISE, LOWER }

    private int target;
    private int level;
    private long lastChangeAt = Long.MIN_VALUE / 4;

    private int belowCount;
    private int headroomCount;
    private long calmSince = -1;
    /** The gain measured when each level was raised into (until then, the least that would have been kept). */
    private final double[] gain = MIN_GAIN.clone();

    private final int[] recent = new int[3];
    private int recentCount;

    private Judging judging = Judging.NONE;
    private int skipLeft;
    private int judgeCount;
    private double judgeSum;
    private double baseline;
    private int judgedFrom;

    private long raiseLockedUntil;
    private long raiseLockMs = FUTILE_LOCK_MS;
    private double lockFps;
    private long lowerBlockedUntil;
    private long probeBackoffMs = PROBE_AFTER_MS;

    private String note = "";

    public Governor(int targetFps) {
        this.target = Math.max(1, targetFps);
    }

    /** The level the game should use now. */
    public int level() {
        return level;
    }

    public int target() {
        return target;
    }

    /** A short sentence about the last decision, for /uboost. Empty until there was one. */
    public String note() {
        return note;
    }

    /** True while a change is being measured. */
    public boolean judging() {
        return judging != Judging.NONE;
    }

    /** True while raising is switched off because the last raise did not help. */
    public boolean raiseLocked(long now) {
        return now < raiseLockedUntil;
    }

    public void setTarget(int fps) {
        target = Math.max(1, fps);
        belowCount = 0;
        headroomCount = 0;
        calmSince = -1;
        judging = Judging.NONE;
    }

    /** Back to level 0 with everything forgotten (leaving the world, switching mode). */
    public void reset() {
        level = BoostLevel.OFF;
        lastChangeAt = Long.MIN_VALUE / 4;
        belowCount = 0;
        headroomCount = 0;
        calmSince = -1;
        recentCount = 0;
        judging = Judging.NONE;
        raiseLockedUntil = 0;
        raiseLockMs = FUTILE_LOCK_MS;
        lowerBlockedUntil = 0;
        probeBackoffMs = PROBE_AFTER_MS;
        System.arraycopy(MIN_GAIN, 0, gain, 0, gain.length);
        note = "";
    }

    /**
     * One observation of the frame rate, about once a second.
     *
     * @param nowMs a clock in milliseconds that only goes forward
     * @param fps the frames per second the game reports; zero or less (nothing measured yet) is ignored
     */
    public void sample(long nowMs, int fps) {
        if (fps <= 0) return;
        remember(fps);

        // A lock made in one scene should not hold back help in a very different one.
        if (nowMs < raiseLockedUntil && lockFps > 0 && Math.abs(fps - lockFps) >= lockFps * SCENE_CHANGE) {
            raiseLockedUntil = 0;
            raiseLockMs = FUTILE_LOCK_MS;
            note = "the scene changed, so shedding can be tried again";
        }

        if (judging != Judging.NONE) {
            judge(nowMs, fps);
            return;
        }

        belowCount = fps < target ? belowCount + 1 : 0;
        if (level > BoostLevel.OFF) {
            double withoutThisLevel = fps / (1.0 + gain[level]);
            headroomCount = withoutThisLevel >= target * SLACK ? headroomCount + 1 : 0;
            if (fps >= target) {
                if (calmSince < 0) calmSince = nowMs;
            } else {
                calmSince = -1;
            }
        } else {
            headroomCount = 0;
            calmSince = -1;
        }

        boolean cooled = nowMs - lastChangeAt >= CHANGE_COOLDOWN_MS;
        boolean severe = fps < target * SEVERE_FACTOR;
        if (cooled && level < BoostLevel.MAX && nowMs >= raiseLockedUntil && (belowCount >= FALL_SAMPLES || severe)) {
            raise(nowMs, fps);
            return;
        }
        if (cooled && level > BoostLevel.OFF && nowMs >= lowerBlockedUntil) {
            boolean evidence = headroomCount >= HEADROOM_SAMPLES;
            boolean longCalm = calmSince >= 0 && nowMs - calmSince >= probeBackoffMs;
            if (evidence || longCalm) lower(nowMs, evidence);
        }
    }

    private void raise(long now, int fps) {
        baseline = meanRecent();
        judgedFrom = level;
        level++;
        startJudging(Judging.RAISE, now);
        note = "fps " + fps + " is below " + target + ": trying " + BoostLevel.name(level);
    }

    private void lower(long now, boolean evidence) {
        judgedFrom = level;
        level--;
        startJudging(Judging.LOWER, now);
        note = evidence
                ? "fps has room to spare: trying " + BoostLevel.name(level)
                : "calm for a while: checking whether " + BoostLevel.name(level) + " is enough";
    }

    private void startJudging(Judging what, long now) {
        judging = what;
        lastChangeAt = now;
        skipLeft = SETTLE_SAMPLES;
        judgeCount = 0;
        judgeSum = 0;
        belowCount = 0;
        headroomCount = 0;
        calmSince = -1;
    }

    private void judge(long now, int fps) {
        if (skipLeft > 0) {
            skipLeft--;
            return;
        }
        judgeSum += fps;
        judgeCount++;
        if (judgeCount < JUDGE_SAMPLES) return;

        double after = judgeSum / judgeCount;
        Judging what = judging;
        judging = Judging.NONE;
        belowCount = 0;
        headroomCount = 0;
        calmSince = -1;

        if (what == Judging.RAISE) {
            double measured = after / baseline - 1.0;
            if (measured >= MIN_GAIN[level]) {
                gain[level] = measured;
                raiseLockMs = FUTILE_LOCK_MS;
                note = BoostLevel.name(level) + " helps: " + format(baseline) + " to " + format(after) + " fps ("
                        + percent(measured) + "), kept";
            } else {
                level = judgedFrom;
                lastChangeAt = now;
                raiseLockedUntil = now + raiseLockMs;
                lockFps = after;
                note = "shedding does not help here (" + format(baseline) + " to " + format(after)
                        + " fps), so it was put back; not trying again for " + (raiseLockMs / 1000) + " s";
                raiseLockMs = Math.min(raiseLockMs * 2, FUTILE_LOCK_MAX_MS);
            }
        } else if (after >= target * SLACK) {
            probeBackoffMs = PROBE_AFTER_MS;
            note = "back to " + BoostLevel.name(level) + ", fps holds at " + format(after);
        } else {
            level = judgedFrom;
            lastChangeAt = now;
            lowerBlockedUntil = now + probeBackoffMs;
            note = "fps fell to " + format(after) + " without it, so " + BoostLevel.name(level) + " stays; not checking again for "
                    + (probeBackoffMs / 1000) + " s";
            probeBackoffMs = Math.min(probeBackoffMs * 2, PROBE_MAX_MS);
        }
    }

    private void remember(int fps) {
        if (recentCount < recent.length) {
            recent[recentCount++] = fps;
        } else {
            System.arraycopy(recent, 1, recent, 0, recent.length - 1);
            recent[recent.length - 1] = fps;
        }
    }

    private double meanRecent() {
        if (recentCount == 0) return 1;
        double sum = 0;
        for (int i = 0; i < recentCount; i++) sum += recent[i];
        return sum / recentCount;
    }

    private static String format(double fps) {
        return String.format(Locale.ROOT, "%.0f", fps);
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%+.0f%%", fraction * 100);
    }
}
