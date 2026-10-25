package net.kdt.pojavlaunch.fragments;

/**
 * The clock of the Performance Mode boost animation, as plain functions of time so it can be tested
 * without a device. All times are milliseconds from the moment the switch is tapped.
 *
 * <pre>
 *      0 -  450  lock-on: targeting brackets close in on the Performance Mode card
 *    450 - 1300  photo zoom: the screen is zoomed in like a camera, with zoom blur and colour fringing
 *   1300 - 1650  shutter: flash, glitch, the frozen picture dissolves, a shock ring leaves the centre
 *   1350 - 2450  the emblem (a hexagon with a performance gauge and a bolt) draws itself and powers up
 *   2450 - 2900  the emblem flies into the card's own icon
 *   2900 - 3300  the card pulses, everything fades
 * </pre>
 */
public final class BoostTimeline {
    public static final float DURATION_MS = 3300f;
    public static final float LOCK_END = 450f;
    public static final float ZOOM_END = 1300f;
    public static final float FLASH_AT = 1300f;
    public static final float SNAPSHOT_GONE = 1480f;
    public static final float BUILD_START = 1350f;
    public static final float GAUGE_START = 1550f;
    public static final float GAUGE_END = 2200f;
    public static final float SWEEP_START = 2000f;
    public static final float FLY_START = 2450f;
    public static final float FLY_END = 2900f;
    public static final float FADE_START = 3000f;

    public static final float ZOOM_MAX = 2.6f;
    public static final float SCRIM_MAX = 0.78f;

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

    public static float easeInOutCubic(float p) {
        return p < 0.5f ? 4f * p * p * p : 1f - (float) Math.pow(-2f * p + 2f, 3) / 2f;
    }

    public static float easeOutBack(float p) {
        float c1 = 1.7f;
        float c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    private static float window(float t, float start, float length) {
        return clamp01((t - start) / length);
    }

    /** Targeting brackets closing in: 0 to 1 over the lock-on phase. */
    public static float lock(float t) {
        return easeOutCubic(window(t, 0f, LOCK_END));
    }

    /** Camera zoom of the frozen screen: a slow drift while locking, then a smooth rush in. */
    public static float zoomScale(float t) {
        float drift = 0.04f * lock(t);
        float rush = (ZOOM_MAX - 1.04f) * easeInOutCubic(window(t, LOCK_END, ZOOM_END - LOCK_END));
        return 1f + drift + rush;
    }

    /** Zoom blur strength, 0 to 1: grows as the zoom speeds up, gone after the shutter. */
    public static float zoomBlur(float t) {
        if (t >= FLASH_AT) return 0f;
        return easeInCubic(window(t, LOCK_END, ZOOM_END - LOCK_END));
    }

    /** Colour fringing strength: follows the blur and kicks at the shutter. */
    public static float aberration(float t) {
        if (t < FLASH_AT) return zoomBlur(t);
        return 1f - window(t, FLASH_AT, 180f);
    }

    /** Opacity of the frozen picture: opaque until the shutter, then it dissolves. */
    public static float snapshotAlpha(float t) {
        if (t <= FLASH_AT) return 1f;
        return 1f - window(t, FLASH_AT, SNAPSHOT_GONE - FLASH_AT);
    }

    /** True while the glitch slices are shown, just around the shutter. */
    public static boolean glitching(float t) {
        return t >= FLASH_AT - 90f && t <= FLASH_AT + 150f;
    }

    /** Darkening of the live screen behind everything. */
    public static float scrim(float t) {
        float early = 0.35f * clamp01(t / ZOOM_END);
        float late = (SCRIM_MAX - 0.35f) * window(t, FLASH_AT, 250f);
        float fadeOut = 1f - window(t, FADE_START - 100f, DURATION_MS - FADE_START + 100f);
        return (early + late) * fadeOut;
    }

    /** Edge darkening of the frozen picture, 0 to 1. */
    public static float vignette(float t) {
        return clamp01(0.5f * lock(t) + 0.5f * zoomBlur(t));
    }

    /** Colour flash: builds just before the shutter, peaks at it and drains. */
    public static float flash(float t) {
        if (t < FLASH_AT - 110f) return 0f;
        if (t < FLASH_AT) return clamp01((t - (FLASH_AT - 110f)) / 110f);
        return 1f - easeOutCubic(window(t, FLASH_AT, 380f));
    }

    /** Camera shake amount, 0 to 1. */
    public static float shake(float t) {
        if (t < FLASH_AT) return 0.25f * zoomBlur(t);
        return 1f - easeOutCubic(window(t, FLASH_AT, 420f));
    }

    /** The emblem's outline drawing itself, 0 to 1. */
    public static float frame(float t) {
        return easeOutCubic(window(t, BUILD_START, 450f));
    }

    /** The gauge filling, 0 to 1. */
    public static float gauge(float t) {
        return easeInOutCubic(window(t, GAUGE_START, GAUGE_END - GAUGE_START));
    }

    /** The bolt powering up; overshoots a little, 0 before it starts. */
    public static float core(float t) {
        float p = window(t, GAUGE_START + 100f, 420f);
        return p <= 0f ? 0f : easeOutBack(p);
    }

    /** Position of the light sweep across the emblem, 0 to 1. */
    public static float sweep(float t) {
        return window(t, SWEEP_START, 420f);
    }

    /** The emblem popping in: 0 before it starts, overshoots slightly past 1. */
    public static float emblemPop(float t) {
        float p = window(t, BUILD_START, 480f);
        return p <= 0f ? 0f : 0.45f + 0.55f * easeOutBack(p);
    }

    /** The emblem's opacity: fades in quickly, and out as it lands in the card icon. */
    public static float emblemAlpha(float t) {
        float in = window(t, BUILD_START, 160f);
        float out = 1f - window(t, FLY_END - 140f, 140f);
        return in * out;
    }

    /** Progress of the flight into the card icon, 0 to 1. */
    public static float fly(float t) {
        return easeInOutCubic(window(t, FLY_START, FLY_END - FLY_START));
    }

    /** The emblem has landed: the card should show its new state. */
    public static boolean arrived(float t) {
        return t >= FLY_END;
    }

    /** 0 to 1 over the pulse that follows landing. */
    public static float arrival(float t) {
        return window(t, FLY_END, DURATION_MS - FLY_END - 100f);
    }

    /** Seconds since the emblem's pop (sparks, shock rings), 0 before it. */
    public static float popAge(float t) {
        return Math.max(0f, (t - FLASH_AT) / 1000f);
    }

    /** Opacity of the whole effect while it fades away at the end. */
    public static float overall(float t) {
        return 1f - window(t, FADE_START, DURATION_MS - FADE_START);
    }
}
