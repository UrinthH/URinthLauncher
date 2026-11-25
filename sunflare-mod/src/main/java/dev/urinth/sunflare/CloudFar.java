package dev.urinth.sunflare;

/**
 * Clouds that stay visible when the render distance is short.
 *
 * <p>The game draws nothing farther away than four times the render distance (the "far plane" of the camera): 128 blocks at render
 * distance 2. The clouds are 192 blocks up, so from the ground nearly all of them are beyond that and are cut off. While Sodium
 * draws the clouds, this gives that one draw call a camera that reaches at least {@link #REACH} blocks, which is where Sodium's
 * clouds have faded into the fog. Everything else keeps the camera the game made, so the world is not drawn any farther and costs
 * nothing more.
 *
 * <p>Only the arithmetic is here, on the four numbers of the projection matrix that carry the near and far plane (m22, m23, m32,
 * m33), so it needs neither Minecraft nor JOML and is unit tested; the mixin does the matrix itself.
 */
public final class CloudFar {
    private CloudFar() {}

    /**
     * The least far plane, in blocks, the clouds are drawn with. Sodium's clouds fade into the fog and are fully faded at 256 blocks
     * (render distance of the clouds 32 cells, fog at 8 blocks per cell), so a little more than that never ends in a visible edge.
     */
    public static final float REACH = 272.0f;

    private static final float EPSILON = 1.0e-4f;
    private static final int MAX_REPORTS = 4;

    private static volatile boolean enabled = true;

    // The report below is only called from the render thread, once per frame, so these are plain fields.
    private static int reportedFar = -1;
    private static int reportedWanted = -1;
    private static int reports;

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /**
     * Whether these entries are those of an ordinary perspective projection (OpenGL depth, as the game makes it) with a far plane that
     * can be read from them. Orthographic and identity matrices are not, and neither is a projection with no far plane at all.
     */
    public static boolean isPerspective(float m22, float m23, float m32, float m33) {
        return Math.abs(m23 + 1.0f) < EPSILON && Math.abs(m33) < EPSILON && m22 < -1.0f && m32 < 0.0f
            && !Float.isNaN(m22) && !Float.isNaN(m32);
    }

    /** The near plane of a perspective projection. */
    public static float nearOf(float m22, float m32) {
        return m32 / (m22 - 1.0f);
    }

    /** The far plane of a perspective projection. */
    public static float farOf(float m22, float m32) {
        return m32 / (m22 + 1.0f);
    }

    /**
     * The far plane the clouds are drawn with, given the camera's: the camera's own when that reaches {@link #REACH} already, otherwise
     * {@link #REACH}. A far plane that is not a number or not a distance is returned as it is.
     */
    public static float farFor(float far) {
        if (!(far > 0.0f) || Float.isInfinite(far)) return far;
        return far >= REACH ? far : REACH;
    }

    /** Entry m22 of a perspective projection with these planes. */
    public static float m22For(float near, float far) {
        return (far + near) / (near - far);
    }

    /** Entry m32 of a perspective projection with these planes. */
    public static float m32For(float near, float far) {
        return 2.0f * far * near / (near - far);
    }

    /**
     * A line for the game log the first few times the camera reaches a different distance (and never more than a handful of lines),
     * otherwise null. It is how a log shows whether the clouds hook ran and what it found.
     */
    public static String report(float far, float wanted) {
        int f = Math.round(far);
        int w = Math.round(wanted);
        if ((f == reportedFar && w == reportedWanted) || reports >= MAX_REPORTS) return null;
        reportedFar = f;
        reportedWanted = w;
        reports++;
        return w > f
            ? "[Clouds] the camera reaches " + f + " blocks, the clouds are drawn with a reach of " + w + " blocks"
            : "[Clouds] the camera reaches " + f + " blocks already, the clouds are drawn as the game makes them";
    }

    /** For tests: forget what was reported. */
    static void resetReports() {
        reportedFar = -1;
        reportedWanted = -1;
        reports = 0;
    }
}
