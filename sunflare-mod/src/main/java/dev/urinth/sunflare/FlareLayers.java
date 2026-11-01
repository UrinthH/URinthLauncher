package dev.urinth.sunflare;

/**
 * The 13 elements of the lens flare, taken from the Open4Es shader pack (final.vsh / final.fsh).
 * Each element is a soft disc on the line through the sun and the screen centre.
 * Only the lens flare is used. None of the pack's lighting, shadows or colour grading is.
 */
public final class FlareLayers {
    private FlareLayers() {}

    public static final int COUNT = 13;

    /** Position along the sun -> screen centre line. -1 is on the sun itself, 0 is the centre, 1 the mirror point. */
    public static final double[] POS = {
        -1.0, -1.0, -1.0, -1.0, 0.99, 0.49, 0.39, 0.45, 0.20, -0.10, -0.19, 0.20, 0.40
    };

    /** Radius as a fraction of the screen height. */
    public static final double[] SIZE = {
        0.4, 0.6, 0.5, 0.7, 0.4, 0.5, 0.02, 0.01, 0.03, 0.05, 0.01, 0.1, 0.1
    };

    /** r, g, b, strength. */
    public static final double[][] COLOR = {
        {1.0, 1.0, 0.9, 0.15},
        {0.9, 1.0, 0.7, 0.15},
        {1.0, 0.7, 0.0, 0.15},
        {0.9, 0.1, 0.0, 0.15},
        {0.2, 0.06, 0.6, 0.4},
        {0.4, 0.03, 1.0, 0.2},
        {0.4, 0.9, 0.9, 1.0},
        {0.4, 0.4, 1.0, 1.0},
        {0.3, 0.4, 1.0, 1.0},
        {1.5, 0.85, 0.45, 0.3},
        {1.3, 0.8, 0.4, 0.5},
        {1.3, 1.0, 1.0, 0.4},
        {1.0, 1.0, 1.0, 0.1},
    };

    /** Centre of element i in 0..1 screen space (v up) for a sun at (sunU, sunV). */
    public static void center(int i, double sunU, double sunV, double[] out) {
        out[0] = 0.5 + (0.5 - sunU) * POS[i];
        out[1] = 0.5 + (0.5 - sunV) * POS[i];
    }

    /** Brightness of a disc at distance r (0 = centre, 1 = edge): the shader's smoothstep(0, size, size - dist). */
    public static double profile(double r) {
        return SunMath.smoothstep(0.0, 1.0, 1.0 - r);
    }
}
