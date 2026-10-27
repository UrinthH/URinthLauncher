package net.kdt.pojavlaunch.game;

/**
 * The numbers behind the Play animation: a tiny rounded square pops up in the middle of the
 * launcher, holds for a moment, then the "camera" zooms into it until it fills the screen.
 *
 * <pre>
 *     0 -  380 ms  the square pops in (a small overshoot, then settles)
 *   380 - 1000 ms  it holds and breathes while the launcher dims behind it
 *  1000 - 2000 ms  the zoom: the square grows exponentially (a steady camera push-in, not a
 *                  slow start and a sudden jump), the launcher behind scales up and fades out
 * </pre>
 *
 * <p>Growth is exponential on purpose: zooming a camera at a constant rate multiplies the size
 * by the same factor every moment, which reads as smooth. An ease in and out on the progress
 * keeps the start and the landing soft.
 *
 * <p>Plain Java with no Android classes, so it is covered by JVM unit tests.
 */
public final class LaunchZoomMath {
    public static final long POP_MS = 380;
    public static final long HOLD_END_MS = 1000;
    public static final long ZOOM_MS = 1000;
    public static final long TOTAL_MS = HOLD_END_MS + ZOOM_MS;

    /** How far the launcher behind the square scales up by the end of the zoom. */
    public static final float BACKDROP_MAX_SCALE = 7f;

    private LaunchZoomMath() {}

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float smooth(float p) {
        p = clamp01(p);
        return p * p * (3f - 2f * p);
    }

    /** 0 before the pop, rising to 1 with a little overshoot on the way, exactly 1 once it is done. */
    public static float pop(long elapsedMs) {
        float p = clamp01(elapsedMs / (float) POP_MS);
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float c1 = 1.7f;
        float c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    /** 0 until the zoom starts, then linear 0..1 over the zoom. */
    public static float zoomProgress(long elapsedMs) {
        return clamp01((elapsedMs - HOLD_END_MS) / (float) ZOOM_MS);
    }

    /** Ease in and out of the zoom: soft start, steady middle, soft landing. */
    public static float zoomEase(float u) {
        u = clamp01(u);
        return u < 0.5f ? 4f * u * u * u : 1f - (float) Math.pow(-2f * u + 2f, 3) / 2f;
    }

    /** Size of the square at zoom ease e: {@code base} at 0, {@code full} at 1, constant growth rate between. */
    public static float rectSize(float base, float full, float e) {
        if (base <= 0f) return full * clamp01(e);
        if (full <= base) return base;
        return base * (float) Math.exp(clamp01(e) * Math.log(full / base));
    }

    /** Scale of the launcher snapshot behind the square, growing at the same steady rate. */
    public static float backdropScale(float e) {
        return (float) Math.exp(clamp01(e) * Math.log(BACKDROP_MAX_SCALE));
    }

    /** The launcher behind the square: fully there until the zoom is under way, gone before it lands. */
    public static float backdropAlpha(float e) {
        return 1f - smooth((clamp01(e) - 0.08f) / 0.62f);
    }

    /** Corner radius: the full rounding while small, square corners by the time it fills the screen. */
    public static float cornerRadius(float radius, float e) {
        return radius * (1f - smooth(clamp01(e)));
    }

    /** Light streaks fly out from the square in the middle of the zoom and are gone at both ends. */
    public static float streakAmount(float u) {
        u = clamp01(u);
        return (float) Math.pow(Math.sin(Math.PI * u), 1.2);
    }
}
