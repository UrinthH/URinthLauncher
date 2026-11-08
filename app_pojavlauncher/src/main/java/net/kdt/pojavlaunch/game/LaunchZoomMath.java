package net.kdt.pojavlaunch.game;

/**
 * The numbers behind the Play animation, which has two halves that mirror each other.
 *
 * <pre>
 *  Play tapped    the launcher's screen shrinks into a small, wide, rounded window in the middle
 *                 of a clean white page (SHRINK_MS). The window shows the game's loading screen.
 *  game starting  the window stays where it is while Java and Minecraft start up.
 *  game is up     the window zooms to full screen, fast (EXPAND_MS), then the game shows through
 *                 (FADE_MS).
 * </pre>
 *
 * <p>All of it hangs on one number, the <i>expansion</i> {@code e}: 0 is the small window and 1 is
 * the full screen. The shrink plays e from 1 down to 0, the zoom plays it from 0 up to 1, and
 * everything else (the window's size, its corner radius, its shadow and edge, the caption under
 * it) is a function of e, so the two halves always match and the two screens that show them (the
 * launcher's and the game's) cannot drift apart.
 *
 * <p>The window is the whole screen drawn smaller, not a cropped piece of it, so it has the
 * screen's own wide shape. Its size changes at a constant rate (the camera zooms, it does not
 * crawl and then jump), which is why {@link #windowScale} is exponential.
 *
 * <p>Plain Java with no Android classes, so it is covered by JVM unit tests.
 */
public final class LaunchZoomMath {
    /** How big the small window is, compared with the whole screen. */
    public static final float MINI_SCALE = 0.60f;

    /** The launcher's screen shrinks into the window over this long. */
    public static final long SHRINK_MS = 460;
    /** The window zooms to full screen over this long. Short on purpose: it is the "game is up" moment. */
    public static final long EXPAND_MS = 360;
    /** The white page and the window fade away to the game over this long, after the zoom. */
    public static final long FADE_MS = 200;

    private LaunchZoomMath() {}

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Ease in and out, with a gentle start and landing. */
    public static float smooth(float p) {
        p = clamp01(p);
        return p * p * (3f - 2f * p);
    }

    /** Like {@link #smooth} but with a longer, softer start and landing. */
    public static float smoother(float p) {
        p = clamp01(p);
        return p * p * p * (p * (p * 6f - 15f) + 10f);
    }

    // ---- the shrink (Play tapped) -----------------------------------------------------------

    /** 0..1 over the shrink. */
    public static float shrinkProgress(long elapsedMs) {
        return clamp01(elapsedMs / (float) SHRINK_MS);
    }

    /** The expansion during the shrink: 1 (the full screen) falling to 0 (the small window). */
    public static float shrinkExpansion(float progress) {
        return 1f - smoother(progress);
    }

    /**
     * The loading screen fading in over the launcher's picture inside the window: not there at
     * the start, all there before the shrink is over. The launcher's picture stays fully opaque
     * underneath, so the cross-fade never dips.
     */
    public static float hudAlpha(float progress) {
        return smooth((progress - 0.25f) / 0.45f);
    }

    // ---- the zoom (game is up) --------------------------------------------------------------

    /** 0..1 over the zoom. */
    public static float expandProgress(long elapsedMs) {
        return clamp01(elapsedMs / (float) EXPAND_MS);
    }

    /**
     * Fast start, soft landing: most of the distance is covered in the first part of the zoom
     * (it reads as a quick push-in), and it settles gently onto the full screen.
     */
    public static float expandEase(float u) {
        u = clamp01(u);
        float rest = 1f - u;
        float out = 1f - rest * rest * rest * rest;
        return 0.55f * out + 0.45f * smooth(u);
    }

    // ---- what the expansion looks like ------------------------------------------------------

    /**
     * The window's size compared with the full screen at expansion {@code e}: {@code mini} at 0,
     * exactly 1 at 1, and the same factor per step in between.
     */
    public static float windowScale(float mini, float e) {
        if (mini <= 0f || mini >= 1f) return 1f;
        return (float) Math.pow(mini, 1f - clamp01(e));
    }

    /** Corner radius: the full rounding on the small window, square corners on the full screen. */
    public static float cornerRadius(float radius, float e) {
        return radius * (1f - smooth(e));
    }

    /** The soft shadow under the window: there on the small window, gone on the full screen. */
    public static float shadowAlpha(float e) {
        return 1f - smooth(e);
    }

    /** The thin green edge of the window. */
    public static float edgeAlpha(float e) {
        return 1f - clamp01(e * 1.25f);
    }

    /** The status text under the window: only while the window is small. */
    public static float captionAlpha(float e) {
        return 1f - clamp01(e / 0.3f);
    }

    // ---- where things go --------------------------------------------------------------------

    /**
     * Whether a view of this size is the whole screen, or close enough to it (a window that keeps
     * clear of a camera cut-out is a little narrower than the screen). A smaller window (split
     * screen, say) or a bigger one is not, and is then laid out by its own size instead.
     */
    public static boolean coversScreen(int viewWidth, int viewHeight, int screenWidth, int screenHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return false;
        if (viewWidth > screenWidth + 2 || viewHeight > screenHeight + 2) return false;
        return viewWidth >= screenWidth * 0.85f && viewHeight >= screenHeight * 0.85f;
    }

    /**
     * Where the text under the window starts: a gap below the window, but never so low that it
     * runs off the bottom of the view.
     */
    public static float captionTop(float windowBottom, float gap, float captionHeight, float viewHeight) {
        float wanted = windowBottom + gap;
        float lowest = viewHeight - captionHeight - gap;
        return Math.min(wanted, Math.max(0f, lowest));
    }
}
