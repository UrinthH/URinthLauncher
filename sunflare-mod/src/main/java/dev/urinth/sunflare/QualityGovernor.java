package dev.urinth.sunflare;

/**
 * Keeps the effects from costing frames. It watches how long frames take and turns the quality of the effects down when the
 * frames get longer than the target (for 90 FPS: 11.1 ms), and slowly up again when there is room. A hitch (loading chunks, a
 * garbage collection) is ignored: one long frame must not make the rays flicker. Plain maths, unit tested.
 */
final class QualityGovernor {
    static final double MIN_QUALITY = 0.3;
    /** How many frames between decisions. */
    private static final int STEP_FRAMES = 20;

    private double targetMillis;
    private double average;
    private double quality = 1.0;
    private int frames;

    QualityGovernor(double targetFps) {
        setTargetFps(targetFps);
    }

    /** 0 turns the governor off (quality stays at 1). */
    void setTargetFps(double fps) {
        targetMillis = fps <= 0.0 ? 0.0 : 1000.0 / fps;
    }

    double targetMillis() {
        return targetMillis;
    }

    double quality() {
        return quality;
    }

    double averageMillis() {
        return average;
    }

    /** Call once per frame with how long the last frame took, in milliseconds. */
    void frame(double millis) {
        if (targetMillis <= 0.0) {
            quality = 1.0;
            return;
        }
        if (millis > 6.0 * targetMillis) return; // a hitch, not the effects
        double ms = Math.max(0.5, millis);
        average = average == 0.0 ? ms : average * 0.96 + ms * 0.04;
        if (++frames < STEP_FRAMES) return;
        frames = 0;
        double ratio = average / targetMillis;
        if (ratio > 1.07) {
            quality = Math.max(MIN_QUALITY, quality - Math.min(0.25, (ratio - 1.0) * 1.5));
        } else if (ratio < 1.03) {
            quality = Math.min(1.0, quality + 0.03);
        }
    }
}
