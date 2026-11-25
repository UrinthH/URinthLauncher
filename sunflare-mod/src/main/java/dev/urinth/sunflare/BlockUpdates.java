package dev.urinth.sunflare;

import java.util.Locale;

/**
 * How fast a block you place or break changes on screen.
 *
 * <p>Sodium rebuilds the chunk section of a changed block on a helper thread and shows the result a frame or two later, so a block
 * you break can stay on screen for a moment (it never makes the frame wait, which is why it is the default). Sodium has a faster path
 * for blocks the player changes: the frame waits for the rebuild and shows the new block at once. Its own setting turns that path off
 * for everything. This brings it back for the blocks the player changes (Sodium tells the two apart; blocks changed by the server,
 * by light or by water keep the smooth way), and in Auto only while the frame rate is good enough that a few milliseconds of waiting
 * cannot be seen.
 *
 * <p>Plain logic, unit tested.
 */
public final class BlockUpdates {
    private BlockUpdates() {}

    /** Sodium's way: never wait for a rebuild. */
    public static final int SMOOTH = 0;
    /** Instant while the frame rate is good, Sodium's way when the game is already slow. */
    public static final int AUTO = 1;
    /** Always wait for the rebuild of a block the player changed. */
    public static final int INSTANT = 2;
    public static final String[] NAMES = {"Smooth", "Auto", "Instant"};

    /** Auto is instant while the frames take this long or less on average (50 FPS). */
    public static final double AUTO_MAX_MS = 20.0;
    /** The moving average of the frame time reacts to the last 32 frames or so. */
    private static final double SMOOTHING = 1.0 / 32.0;

    private static volatile int mode = AUTO;
    /** Moving average of the frame time in ms, 0 until a frame was seen. */
    private static volatile double averageMs;
    /** Set when Sodium starts to schedule a rebuild, read when it asks whether this one may be instant. Render thread only. */
    private static volatile boolean grantThisOne;
    private static boolean announced;

    public static int mode() {
        return mode;
    }

    public static void setMode(int value) {
        if (value >= SMOOTH && value <= INSTANT) mode = value;
    }

    /** The mode of a name of the settings file ("smooth", "auto", "instant", any case), or the fallback. */
    public static int fromName(String name, int fallback) {
        if (name == null) return fallback;
        String text = name.trim();
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(text)) return i;
        }
        return fallback;
    }

    /** The name of a mode as the settings file keeps it. */
    public static String toName(int value) {
        return NAMES[Math.max(SMOOTH, Math.min(INSTANT, value))].toLowerCase(Locale.ROOT);
    }

    /** Call once per frame with the time the last frame took, in milliseconds. */
    static void noteFrame(double ms) {
        if (!(ms > 0.0) || Double.isInfinite(ms)) return;
        double average = averageMs;
        averageMs = average <= 0.0 ? ms : average + (ms - average) * SMOOTHING;
    }

    /** Whether a rebuild the player caused may be instant, in this mode with this average frame time. */
    public static boolean instantNow(int mode, double averageMs) {
        if (mode == INSTANT) return true;
        return mode == AUTO && averageMs > 0.0 && averageMs <= AUTO_MAX_MS;
    }

    /**
     * Sodium is about to schedule the rebuild of a section.
     * @param byPlayer whether the block was changed by the player (Sodium's own flag for this)
     */
    public static void begin(boolean byPlayer) {
        grantThisOne = byPlayer && instantNow(mode, averageMs);
    }

    /** Whether the rebuild Sodium is scheduling now may be instant. */
    public static boolean grant() {
        return grantThisOne;
    }

    /** True the first time it is asked after a start: the log says once that this hook ran. */
    public static boolean firstGrant() {
        if (announced) return false;
        announced = true;
        return true;
    }

    /** For tests: back to the state of a new game. */
    static void reset() {
        mode = AUTO;
        averageMs = 0.0;
        grantThisOne = false;
        announced = false;
    }
}
