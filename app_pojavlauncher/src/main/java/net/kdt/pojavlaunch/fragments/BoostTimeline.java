package net.kdt.pojavlaunch.fragments;

/**
 * The clock of the Performance Mode boost animation, as plain functions of time so it can be tested
 * without a device. All times are milliseconds from the moment the switch is tapped.
 *
 * <pre>
 *      0 - 1000  charge: rings tighten and spin faster, the boost meter fills
 *   1000 - 1450  zoom: the whole thing rushes toward the viewer
 *   1450 - 1800  flash and burst: white flash, shock rings and sparks fly out
 *   1550 - 2300  the "PERFORMANCE MODE" title pops in and holds
 *   2300 - 2700  everything fades away
 * </pre>
 */
public final class BoostTimeline {
    public static final float DURATION_MS = 2700f;
    public static final float CHARGE_END = 1000f;
    public static final float ZOOM_END = 1450f;
    public static final float FLASH_END = 1800f;
    public static final float TEXT_IN = 1550f;
    public static final float FADE_START = 2300f;

    public static final float MAX_ZOOM = 14f;
    public static final float SCRIM_MAX = 0.80f;

    private BoostTimeline() {}

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float easeInCubic(float p) {
        return p * p * p;
    }

    public static float easeOutCubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    public static float easeOutBack(float p) {
        float c1 = 1.8f;
        float c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    /** 0 to 1 over the charge phase, starting slowly and speeding up. */
    public static float charge(float t) {
        return easeInCubic(clamp01(t / CHARGE_END));
    }

    /** Linear 0 to 1 over the charge phase (the number on the boost meter). */
    public static float meter(float t) {
        return clamp01(t / CHARGE_END);
    }

    /** Scale factor of the zoom: 1 until the zoom starts, then rushing up to MAX_ZOOM. */
    public static float zoom(float t) {
        float q = clamp01((t - CHARGE_END) / (ZOOM_END - CHARGE_END));
        return 1f + (MAX_ZOOM - 1f) * easeInCubic(q);
    }

    /** Strength of the zoom's speed lines, 0 to 1. */
    public static float speedLines(float t) {
        if (t < CHARGE_END - 150f) return 0f;
        if (t < ZOOM_END) return clamp01((t - (CHARGE_END - 150f)) / (ZOOM_END - CHARGE_END + 150f));
        return 1f - clamp01((t - ZOOM_END) / 250f);
    }

    /** White flash: builds just before the burst, peaks at it, then drains. */
    public static float flash(float t) {
        if (t < ZOOM_END - 140f) return 0f;
        if (t < ZOOM_END) return clamp01((t - (ZOOM_END - 140f)) / 140f);
        return 1f - easeOutCubic(clamp01((t - ZOOM_END) / (FLASH_END - ZOOM_END)));
    }

    /** Darkening of the screen behind the effect. */
    public static float scrim(float t) {
        float in = clamp01(t / 500f);
        float out = 1f - clamp01((t - FADE_START) / (DURATION_MS - FADE_START));
        return SCRIM_MAX * Math.min(in, out);
    }

    /** Opacity of the whole effect while it fades away at the end. */
    public static float overall(float t) {
        return 1f - clamp01((t - FADE_START) / (DURATION_MS - FADE_START));
    }

    /** The burst starts at this time; before it, nothing flies outward. */
    public static boolean burstStarted(float t) {
        return t >= ZOOM_END;
    }

    /** Seconds since the burst, 0 before it. */
    public static float burstAge(float t) {
        return Math.max(0f, (t - ZOOM_END) / 1000f);
    }

    /** Scale of the title: pops in with an overshoot. 0 before it appears. */
    public static float titleScale(float t) {
        float p = clamp01((t - TEXT_IN) / 380f);
        return p <= 0f ? 0f : easeOutBack(p);
    }

    public static float titleAlpha(float t) {
        float in = clamp01((t - TEXT_IN) / 220f);
        return in * overall(t);
    }
}
