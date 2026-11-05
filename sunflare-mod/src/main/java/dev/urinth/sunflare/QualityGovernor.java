package dev.urinth.sunflare;

/**
 * Keeps the effects from costing too many frames. It measures what a frame costs without the effects (the baseline) and with
 * them, and turns the quality down when the effects add more than they are allowed to, and slowly up when there is room.
 * What they may add is the room between the baseline and the target frame time (80 FPS: 12.5 ms), but never less than
 * {@link #MIN_BUDGET_MILLIS}, so in a heavy scene they cannot take a big share either. Until it has seen a frame without
 * the effects it holds the whole frame time to the target. A hitch (loading chunks, a garbage collection) is ignored.
 *
 * <p>Lowering the quality only helps with the part of the cost that grows with the size of the picture. On a phone GPU a good part
 * of the god rays is a fixed cost (switching between render targets, reading the finished frame back), which no quality setting
 * can shrink. So when the quality is already at its lowest and the effects still cost too much, the guard goes on in two more
 * steps: the rays are made only every 2nd to 4th frame (the frames in between show the last picture, moved with the camera, which
 * is cheap), and if even that is too much, the rays are paused for a while (a pause costs nothing, so the frame rate comes back
 * to what the phone does without them) and tried again later, each time after a longer wait. When there is room again it
 * goes back the same way, slowly, and the waits get longer the more often it had to turn back. Plain maths, unit tested.
 */
final class QualityGovernor {
    static final double MIN_QUALITY = 0.1;
    /** The effects may always add this much, even when the frame is already over the target. */
    static final double MIN_BUDGET_MILLIS = 1.0;
    /** The rays are made at least every this many frames (1 = every frame) before they are paused. */
    static final int MAX_INTERVAL = 4;
    /** The first pause lasts this many frames; every further pause in a row doubles it, up to 8 times as long. */
    static final int PAUSE_FRAMES = 600;
    private static final int MAX_PAUSE_DOUBLINGS = 3;
    /**
     * Frames per decision. It is a multiple of 1, 2, 3 and 4 on purpose: with the rays made every n-th frame the cost repeats every n
     * frames, and a decision that looks at a whole number of those cycles sees the true average. (With 20 frames and a cycle of 4
     * it always looked right after the expensive frame and thought the rays cost more than they do.)
     */
    private static final int STEP_FRAMES = 24;
    private static final int BASELINE_FRAMES = 30;
    /** How many decisions with room to spare it takes to go one step back (it doubles when going back did not last). */
    private static final int HOLD_MIN = 3;
    private static final int HOLD_MAX = 48;

    /** What to do with the rays in this frame. */
    enum Plan {
        /** Make the whole picture. */
        FULL,
        /** Show the picture of the last full frame, moved with the camera. */
        REUSE,
        /** Draw nothing. */
        PAUSE
    }

    private double targetMillis;
    private double baseline;
    private int baselineFrames;
    private double active;
    private double quality = 1.0;
    private double windowSum;
    private int windowFrames;

    private int interval = 1;
    private int sinceFull;
    private boolean paused;
    private int pauseFrames;
    private int relapses;
    private int overSteps;
    private int roomSteps;
    private int stepsSinceChange;
    private boolean justRelaxed;
    private int hold = HOLD_MIN;

    QualityGovernor(double targetFps) {
        setTargetFps(targetFps);
    }

    /** The lowest frame rate wanted with the effects on. 0 turns the governor off (quality stays at 1). */
    void setTargetFps(double fps) {
        targetMillis = fps <= 0.0 ? 0.0 : 1000.0 / fps;
    }

    double targetMillis() {
        return targetMillis;
    }

    /** What the effects may add to a frame now, in milliseconds. */
    double budgetMillis() {
        return Math.max(MIN_BUDGET_MILLIS, targetMillis - baseline);
    }

    double quality() {
        return quality;
    }

    double baselineMillis() {
        return baseline;
    }

    /** The rays are made every this many frames (1 = every frame). */
    int interval() {
        return interval;
    }

    boolean paused() {
        return paused;
    }

    /** What the effects add to a frame at the moment, in milliseconds (0 when not known). */
    double effectMillis() {
        return active == 0.0 ? 0.0 : Math.max(0.0, active - baseline);
    }

    /**
     * Call once for every frame in which the rays could be drawn.
     * @param cacheUsable whether there is a picture of an earlier frame that can be shown again
     */
    Plan plan(boolean cacheUsable) {
        if (paused) return Plan.PAUSE;
        if (interval <= 1 || !cacheUsable || sinceFull >= interval - 1) {
            sinceFull = 0;
            return Plan.FULL;
        }
        sinceFull++;
        return Plan.REUSE;
    }

    /** A short line for the status command and the stutter log. */
    String describe() {
        return String.format(java.util.Locale.ROOT, "quality %.2f, rays every %d frame(s)%s, effects +%.1f ms of %.1f ms (plain frame %.1f ms)",
            quality, interval, paused ? ", PAUSED" : "", effectMillis(), budgetMillis(), baseline);
    }

    /** Call once per frame with how long the last frame took (ms) and whether the effects were drawn in it. */
    void frame(double millis, boolean effectsDrawn) {
        if (targetMillis <= 0.0) {
            quality = 1.0;
            interval = 1;
            paused = false;
            return;
        }
        if (millis > 6.0 * targetMillis) return; // a hitch, not the effects
        double ms = Math.max(0.5, millis);
        if (paused) {
            if (!effectsDrawn) learnBaseline(ms); // a paused frame is a plain frame
            if (--pauseFrames <= 0) resume();
            return;
        }
        if (!effectsDrawn) {
            learnBaseline(ms);
            return;
        }
        windowSum += ms;
        if (++windowFrames < STEP_FRAMES) return;
        double mean = windowSum / windowFrames;
        windowSum = 0.0;
        windowFrames = 0;
        active = active == 0.0 ? mean : active * 0.7 + mean * 0.3; // smooth over a few windows
        if (baselineFrames >= BASELINE_FRAMES) {
            decide();
        } else {
            double ratio = active / targetMillis;
            if (ratio > 1.07) quality = Math.max(MIN_QUALITY, quality - Math.min(0.25, (ratio - 1.0) * 1.5));
            else if (ratio < 1.03) quality = Math.min(1.0, quality + 0.03);
        }
    }

    private void learnBaseline(double ms) {
        baseline = baselineFrames == 0 ? ms : baseline * 0.97 + ms * 0.03;
        if (baselineFrames < 100000) baselineFrames++;
    }

    private void decide() {
        double budget = budgetMillis();
        double over = active - baseline - budget;
        stepsSinceChange++;
        if (over > 0.15 * budget) {
            roomSteps = 0;
            if (quality > MIN_QUALITY + 1e-9) {
                overSteps = 0;
                quality = Math.max(MIN_QUALITY, quality - Math.min(0.25, 0.1 + over / budget * 0.1));
            } else if (++overSteps >= 2) {
                // the quality cannot go lower and the effects still cost too much: fewer full pictures, then a pause
                overSteps = 0;
                escalate();
            }
        } else if (over < -0.35 * budget) {
            overSteps = 0;
            quality = Math.min(1.0, quality + 0.03);
            if (interval > 1 && ++roomSteps >= hold) {
                roomSteps = 0;
                interval--;
                stepsSinceChange = 0;
                justRelaxed = true;
            }
        } else {
            overSteps = 0;
            roomSteps = 0;
        }
        if (stepsSinceChange > 30) {
            // it has been calm for a long while: the next trouble is a new one, not a relapse
            relapses = 0;
            hold = HOLD_MIN;
        }
    }

    private void escalate() {
        if (justRelaxed && stepsSinceChange < 6) hold = Math.min(HOLD_MAX, hold * 2); // going back did not last: wait longer next time
        justRelaxed = false;
        stepsSinceChange = 0;
        active = 0.0;
        windowSum = 0.0;
        windowFrames = 0;
        if (interval < MAX_INTERVAL) {
            interval++;
            return;
        }
        paused = true;
        pauseFrames = PAUSE_FRAMES << Math.min(relapses, MAX_PAUSE_DOUBLINGS);
        relapses++;
    }

    /** After a pause: try again at the cheapest setting; if it is still too much, the next pause is longer. */
    private void resume() {
        paused = false;
        interval = MAX_INTERVAL;
        sinceFull = 0;
        quality = MIN_QUALITY;
        active = 0.0;
        windowSum = 0.0;
        windowFrames = 0;
        overSteps = 0;
        roomSteps = 0;
        stepsSinceChange = 0;
        justRelaxed = false;
    }
}
