package dev.urinth.sunflare;

/**
 * Keeps the effects from costing too many frames. It measures what a frame costs without the effects (the baseline) and with
 * them, and turns the quality down when the effects add more than they are allowed to, and slowly up when there is room.
 * What they may add is the room between the baseline and the target frame time (80 FPS: 12.5 ms), but never less than
 * {@link #MIN_BUDGET_MILLIS}, so in a heavy scene they cannot take a big share either. Until it has seen a frame without
 * the effects it holds the whole frame time to the target. A hitch (loading chunks, a garbage collection) is ignored.
 * Plain maths, unit tested.
 */
final class QualityGovernor {
    static final double MIN_QUALITY = 0.1;
    /** The effects may always add this much, even when the frame is already over the target. */
    static final double MIN_BUDGET_MILLIS = 1.0;
    private static final int STEP_FRAMES = 20;
    private static final int BASELINE_FRAMES = 30;

    private double targetMillis;
    private double baseline;
    private int baselineFrames;
    private double active;
    private double quality = 1.0;
    private int frames;

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

    /** Call once per frame with how long the last frame took (ms) and whether the effects were drawn in it. */
    void frame(double millis, boolean effectsDrawn) {
        if (targetMillis <= 0.0) {
            quality = 1.0;
            return;
        }
        if (millis > 6.0 * targetMillis) return; // a hitch, not the effects
        double ms = Math.max(0.5, millis);
        if (!effectsDrawn) {
            baseline = baselineFrames == 0 ? ms : baseline * 0.97 + ms * 0.03;
            if (baselineFrames < 100000) baselineFrames++;
            return;
        }
        active = active == 0.0 ? ms : active * 0.96 + ms * 0.04;
        if (++frames < STEP_FRAMES) return;
        frames = 0;
        if (baselineFrames >= BASELINE_FRAMES) {
            double budget = budgetMillis();
            double over = active - baseline - budget;
            if (over > 0.15 * budget) {
                quality = Math.max(MIN_QUALITY, quality - Math.min(0.25, 0.1 + over / budget * 0.1));
            } else if (over < -0.35 * budget) {
                quality = Math.min(1.0, quality + 0.03);
            }
        } else {
            double ratio = active / targetMillis;
            if (ratio > 1.07) quality = Math.max(MIN_QUALITY, quality - Math.min(0.25, (ratio - 1.0) * 1.5));
            else if (ratio < 1.03) quality = Math.min(1.0, quality + 0.03);
        }
    }
}
