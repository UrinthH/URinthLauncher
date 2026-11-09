package dev.urinth.optimizer;

/**
 * The distance the game draws right now, shared between the controller (one tick at a time) and the game's own code that the
 * mixins reach: the fog (render thread, once per frame) and the chunk culling (chunk worker threads).
 *
 * <p>Both are derived from one number, the fog end in blocks. The fog moves smoothly (32 blocks a second) so a change is never a
 * visible jump, and the culling follows it: when the distance grows the culling opens at once (the chunks are there before the fog
 * lets them show), when it shrinks the culling closes only after the fog has moved in over the chunks that are dropped. Nothing is
 * reloaded: chunks that were already built stay built and show again immediately.
 */
public final class SoftDistance {
    private SoftDistance() {}

    /** How fast the fog moves, in blocks a second. */
    static final float FOG_RATE = 32f;
    /** The fewest chunks the culling will ever allow. */
    static final int MIN_CULL = 2;

    private static volatile float target;        // blocks, 0 = off
    private static volatile float current;       // blocks, moved on the render thread
    private static volatile boolean cullOn = true;
    private static long lastNanos;

    /** Sets the distance to draw: {@code chunks} out of the player's {@code real} render distance. */
    static void apply(int chunks, int real, boolean culling) {
        cullOn = culling;
        target = chunks >= real ? (float) real * 16f : (float) chunks * 16f;
        if (chunks >= real && current <= 0f) target = 0f;
    }

    /** Back to the game's own behaviour (no world, the mod switched off). */
    static void reset() {
        target = 0f;
        current = 0f;
        cullOn = true;
        lastNanos = 0L;
    }

    /** True while the mod is drawing less than the player's distance, or still moving the fog back out. */
    static boolean active() {
        return target > 0f;
    }

    /** The fog end the mod wants, in blocks; 0 when it is not doing anything. */
    static float fogTarget() {
        return target;
    }

    /** The fog end being used right now, in blocks; 0 when it is not doing anything. */
    static float fogCurrent() {
        return current;
    }

    /** One step of the smooth movement. Pure, so it can be tested. */
    static float approach(float from, float to, float seconds, float rate) {
        float step = rate * Math.max(0f, seconds);
        if (from < to) return Math.min(to, from + step);
        return Math.max(to, from - step);
    }

    /**
     * Called by the game once per frame in place of its fog distance (render thread).
     *
     * @param vanilla the fog end the game wanted, in blocks (the render distance the player chose, times 16)
     * @return the fog end to use: never more than {@code vanilla}
     */
    public static float fogFor(float vanilla) {
        return fogAt(vanilla, System.nanoTime());
    }

    /** {@link #fogFor} with the time given, so a test can run the frames itself. */
    static float fogAt(float vanilla, long now) {
        float t = target;
        float seconds = lastNanos == 0L ? 0f : Math.min(0.25f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        if (t <= 0f) {
            current = vanilla;
            return vanilla;
        }
        float goal = Math.min(t, vanilla);
        float from = current <= 0f ? vanilla : current;
        float moved = approach(from, goal, seconds, FOG_RATE);
        if (moved >= vanilla && goal >= vanilla) {
            // fully back to the player's distance: stop interfering
            current = vanilla;
            if (t >= vanilla) target = 0f;
            return vanilla;
        }
        current = moved;
        return Math.min(vanilla, moved);
    }

    /**
     * The most chunks the culling may include, as the game's chunk graph asks for it (any thread). Large when the mod is not
     * limiting anything.
     */
    public static int cullLimit() {
        // the culling only ever goes with the fog: if the fog hook has been switched off, so is this
        if (!cullOn || !Hooks.usable("fog")) return Integer.MAX_VALUE;
        float t = target;
        if (t <= 0f) return Integer.MAX_VALUE;
        float c = current;
        float blocks = Math.max(t, c);
        return Math.max(MIN_CULL, (int) Math.ceil(blocks / 16f));
    }
}
