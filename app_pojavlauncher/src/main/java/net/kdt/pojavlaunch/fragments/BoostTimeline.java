package net.kdt.pojavlaunch.fragments;

/**
 * The clock of the Performance Mode boost animation, as plain functions of time so it can be tested
 * without a device. All times are milliseconds from the moment the switch is tapped.
 *
 * <pre>
 *      0 - 2000  hack-in: the camera roams across the screen, zooming from point to point, while
 *                binary digits rain down, the picture glitches and starts to crack
 *   1500 - 2000  the screen splits into panels that shudder
 *   2000 - 3400  suck: the panels, the digits and light streaks spiral into a black hole in the
 *                middle while the camera dives into it and shakes harder and harder
 *   3400         big bang: flash, fracture lines, shock rings; the camera punches back out fast
 *   3400 - 5600  the panels of the launcher blast outward and fall apart
 *   4200 - 6800  the emblem (a hexagon with a performance gauge and a bolt) draws itself and powers up
 *   6800 - 7400  the emblem flies into the card's own icon
 *   7400 - 8000  the card pulses, everything fades
 * </pre>
 */
public final class BoostTimeline {
    public static final float DURATION_MS = 8000f;
    public static final float LOCK_END = 450f;
    public static final float CRACK_START = 1500f;
    public static final float ROAM_END = 2000f;
    public static final float SUCK_START = 2000f;
    public static final float BANG_AT = 3400f;
    public static final float BUILD_START = 4200f;
    public static final float GAUGE_START = 4800f;
    public static final float GAUGE_END = 6300f;
    public static final float SWEEP_START = 6000f;
    public static final float FLY_START = 6800f;
    public static final float FLY_END = 7400f;
    public static final float FADE_START = 7500f;

    /** Where the camera dives into, and where the emblem sits, as fractions of the screen. */
    public static final float CENTER_X = 0.5f;
    public static final float CENTER_Y = 0.47f;

    public static final float ZOOM_HOLD = 2.2f;
    public static final float ZOOM_PEAK = 3.6f;
    private static final float ZOOM_PUNCH = 0.88f;

    // Camera waypoints of the roaming phase. A negative position means "the Performance Mode card".
    private static final float[] WP_TIME = {0f, 450f, 900f, 1350f, 1750f, ROAM_END};
    private static final float[] WP_ZOOM = {1.0f, 1.8f, 2.2f, 2.0f, 2.4f, ZOOM_HOLD};
    private static final float[] WP_X = {-1f, -1f, 0.27f, 0.73f, 0.70f, CENTER_X};
    private static final float[] WP_Y = {-1f, -1f, 0.30f, 0.38f, 0.70f, CENTER_Y};
    private static final float CARD_MIN = 0.22f;
    private static final float CARD_MAX = 0.78f;

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

    /** A small deterministic scrambler, so "random" digits and glitches are the same on every run. */
    public static int hash(int x) {
        x *= 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return x;
    }

    // ---- camera ---------------------------------------------------------------------------

    private static float waypoint(float[] values, int index, float card) {
        float v = values[index];
        if (v >= 0f) return v;
        return card < CARD_MIN ? CARD_MIN : (card > CARD_MAX ? CARD_MAX : card);
    }

    private static float roam(float t, float[] values, float card) {
        int last = WP_TIME.length - 1;
        if (t >= WP_TIME[last]) return waypoint(values, last, card);
        int i = 0;
        while (i < last - 1 && t >= WP_TIME[i + 1]) i++;
        float p = easeInOutCubic(window(t, WP_TIME[i], WP_TIME[i + 1] - WP_TIME[i]));
        float a = waypoint(values, i, card);
        float b = waypoint(values, i + 1, card);
        return a + (b - a) * p;
    }

    /**
     * How far the zoom pulls its focus point towards the middle of the screen, 0 to 1. The picture
     * is scaled about the focus point F, then moved so F lands at F + (centre - F) * pull. With
     * this slope the picture's edges never come inside the screen while the focus stays within the
     * card limits, so no empty border shows during the roam.
     */
    public static float cameraPull(float zoom) {
        return Math.min(1f, 0.7f * (zoom - 1f));
    }

    /** Camera zoom: roams, dives into the middle, then punches back out fast at the bang. */
    public static float cameraZoom(float t) {
        if (t < SUCK_START) return roam(t, WP_ZOOM, 0f);
        if (t < BANG_AT) {
            return ZOOM_HOLD + (ZOOM_PEAK - ZOOM_HOLD) * easeInCubic(window(t, SUCK_START, BANG_AT - SUCK_START));
        }
        float age = t - BANG_AT;
        if (age < 260f) return ZOOM_PEAK + (ZOOM_PUNCH - ZOOM_PEAK) * easeOutCubic(age / 260f);
        return ZOOM_PUNCH + (1f - ZOOM_PUNCH) * easeOutCubic(window(t, BANG_AT + 260f, 600f));
    }

    /** Point the camera looks at, as a fraction of the screen width. {@code card} is the card's centre. */
    public static float cameraFocusX(float t, float cardX) {
        return t < ROAM_END ? roam(t, WP_X, cardX) : CENTER_X;
    }

    public static float cameraFocusY(float t, float cardY) {
        return t < ROAM_END ? roam(t, WP_Y, cardY) : CENTER_Y;
    }

    /** Camera roll in degrees: a gentle sway, then a spin into the black hole, snapped back at the bang. */
    public static float cameraRoll(float t) {
        if (t < BANG_AT) {
            float sway = (float) (2.2 * Math.sin(t * 0.004) * clamp01(t / 600f));
            float s = suck(t);
            return sway * (1f - s) + 14f * easeInCubic(s);
        }
        return 14f * (1f - easeOutCubic(window(t, BANG_AT, 320f)));
    }

    /** How fast the camera is zooming right now, 0 to 1: drives the radial zoom blur. */
    public static float zoomBlur(float t) {
        float speed = Math.abs(cameraZoom(t) - cameraZoom(Math.max(0f, t - 50f)));
        return clamp01(speed / 0.15f);
    }

    // ---- hacking, cracking, sucking ---------------------------------------------------------

    /** Strength of a glitch burst, 0 to 1: a short burst every now and then, never after the bang. */
    public static float glitch(float t) {
        if (t < 350f || t >= BANG_AT) return 0f;
        int slot = (int) (t / 150f);
        int h = hash(slot);
        if ((h & 3) != 0) return 0f;
        float frac = (t - slot * 150f) / 150f;
        float strength = 0.4f + 0.6f * ((h >>> 8) & 255) / 255f;
        return strength * (1f - frac);
    }

    /** Colour fringing strength on the whole picture; gone once the picture splits into panels. */
    public static float aberration(float t) {
        float base = 0.15f + 0.5f * zoomBlur(t) + 0.7f * glitch(t);
        return clamp01(base) * (1f - window(t, CRACK_START - 300f, 300f));
    }

    /** The picture splitting into loose, shuddering panels: 0 to 1 from the first crack. */
    public static float looseness(float t) {
        return easeOutCubic(window(t, CRACK_START, 500f));
    }

    /** Suck progress, 0 to 1, linear: the callers shape it. */
    public static float suck(float t) {
        return window(t, SUCK_START, BANG_AT - SUCK_START);
    }

    /** The black hole in the middle: grows towards the bang, and is gone a moment after it. */
    public static float singularity(float t) {
        if (t < BANG_AT) return easeInCubic(window(t, SUCK_START + 200f, BANG_AT - SUCK_START - 200f));
        return 1f - window(t, BANG_AT, 220f);
    }

    /** Opacity of the binary rain. */
    public static float rainAlpha(float t) {
        float in = window(t, 100f, 500f);
        float level = t < BANG_AT ? 0.9f : 0.9f * (1f - 0.6f * window(t, BANG_AT, 700f));
        return in * level * overall(t);
    }

    /** Scale of the rain around the centre: sucked into a point, then blown back out. */
    public static float rainScale(float t) {
        if (t < BANG_AT) return 1f - 0.92f * easeInCubic(suck(t));
        return 0.08f + 0.92f * easeOutCubic(window(t, BANG_AT, 700f));
    }

    /** Spin of the rain around the centre in degrees. */
    public static float rainSpin(float t) {
        if (t < BANG_AT) return 240f * easeInCubic(suck(t));
        return 240f * (1f - easeOutCubic(window(t, BANG_AT, 700f)));
    }

    /** Opacity of the panels while they fall apart after the bang. */
    public static float shardAlpha(float t) {
        return 1f - window(t, BANG_AT + 1100f, 1200f);
    }

    // ---- light and darkness -----------------------------------------------------------------

    /** Darkening of the live screen behind everything. */
    public static float scrim(float t) {
        float level = t < CRACK_START ? 0.3f * window(t, 0f, CRACK_START) : 0.3f + 0.67f * window(t, CRACK_START, 500f);
        float reveal = 0.37f * easeInOutCubic(window(t, FLY_START - 200f, FLY_END - FLY_START + 200f));
        float fadeOut = 1f - window(t, FADE_START, DURATION_MS - FADE_START);
        return (level - reveal) * fadeOut;
    }

    /** Edge darkening, 0 to 1. */
    public static float vignette(float t) {
        if (t < BANG_AT) return clamp01(0.45f * window(t, 0f, 1000f) + 0.55f * suck(t));
        return 0.35f + 0.65f * (1f - window(t, BANG_AT, 800f));
    }

    /** Colour flash: builds just before the bang, peaks at it and drains. */
    public static float flash(float t) {
        if (t < BANG_AT - 140f) return 0f;
        if (t < BANG_AT) return clamp01((t - (BANG_AT - 140f)) / 140f);
        return 1f - easeOutCubic(window(t, BANG_AT, 520f));
    }

    /** Camera shake amount, 0 to 1: builds all the way up to the bang, then dies down. */
    public static float shake(float t) {
        if (t < SUCK_START) return 0.12f + 0.2f * window(t, 0f, SUCK_START);
        if (t < BANG_AT) return 0.32f + 0.68f * easeInCubic(suck(t));
        return 1f - easeOutCubic(window(t, BANG_AT, 1100f));
    }

    /** Seconds since the bang (panels, rings, sparks, fracture lines), 0 before it. */
    public static float popAge(float t) {
        return Math.max(0f, (t - BANG_AT) / 1000f);
    }

    // ---- emblem ---------------------------------------------------------------------------

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

    /** Opacity of the whole effect while it fades away at the end. */
    public static float overall(float t) {
        return 1f - window(t, FADE_START, DURATION_MS - FADE_START);
    }
}
