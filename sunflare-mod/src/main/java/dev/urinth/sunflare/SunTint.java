package dev.urinth.sunflare;

/**
 * The colour of sunlight for a given sun height, shared by the sun beams and the lens flare so they always match: nearly white
 * high in the sky, golden in the afternoon and a deep orange at sunrise and sunset. Plain maths.
 */
final class SunTint {
    private SunTint() {}

    /** 0 with the sun high, 1 right at the horizon. */
    static double warmth(double sunHeight) {
        return 1.0 - SunMath.smoothstep(0.04, 0.60, sunHeight);
    }

    /** RGB of the sunlight (each 0..1, red always the strongest). */
    static void colour(double sunHeight, double[] out) {
        // never fully white: even a high sun has a golden cast, and the low sun goes deep orange
        double w = Math.pow(0.25 + 0.75 * warmth(sunHeight), 0.85);
        out[0] = 1.0;
        out[1] = 0.94 - 0.40 * w;
        out[2] = 0.82 - 0.58 * w;
    }

    /** Hazier air at low sun makes glow and beams stronger: 1 high in the sky, up to about 1.7 at the horizon. */
    static double boost(double sunHeight) {
        return 1.0 + 0.7 * warmth(sunHeight);
    }
}
