package dev.urinth.sunflare;

/**
 * The numbers behind the screen-space god rays (the same idea as the "Godrays" of Sildur's Vibrant shaders): the bright sky is
 * smeared towards the sun, so every gap between leaves, trunks and hills becomes a soft ray that points at the sun. The
 * smear is done by drawing the small sky picture several times, each time a little shrunk towards the sun and added up.
 * Plain maths, unit tested.
 */
final class GodRayMath {
    private GodRayMath() {}

    /** The rays reach this far from a pixel towards the sun (Sildur: 0.04 * 23). */
    static final double REACH = 0.92;
    static final int MIN_LAYERS = 6;
    static final int MAX_LAYERS = 28;

    /**
     * Scale and weight of each layer. Layer k shows the sky picture shrunk towards the sun by {@code scale[k]} (1 = not at all),
     * so a pixel adds up the sky along the line from itself to the sun. Closer samples count more. Weights add up to 1.
     */
    static void layers(int n, double[] scale, double[] weight) {
        double sum = 0.0;
        for (int k = 0; k < n; k++) {
            double t = (k + 0.5) / n;
            scale[k] = 1.0 - REACH * t;
            weight[k] = Math.pow(1.0 - 0.75 * t, 1.3);
            sum += weight[k];
        }
        for (int k = 0; k < n; k++) weight[k] /= sum;
    }

    /** How many layers for a quality between 0 and 1, given how many the setting allows. */
    static int layerCount(int configured, double quality) {
        int n = (int) Math.round(MIN_LAYERS + (configured - MIN_LAYERS) * SunMath.clamp(quality, 0.0, 1.0));
        return Math.max(MIN_LAYERS, Math.min(Math.min(MAX_LAYERS, configured), n));
    }

    /**
     * 1 when the sun is on screen (u, v in 0..1), fading to 0 when it is 0.3 of a screen outside. The rays are drawn from the
     * picture that is on screen, so a sun far outside it has nothing to shine through.
     */
    static double visibility(double u, double v) {
        double ox = Math.max(0.0, Math.max(-u, u - 1.0));
        double oy = Math.max(0.0, Math.max(-v, v - 1.0));
        return 1.0 - SunMath.smoothstep(0.0, 0.3, Math.hypot(ox, oy));
    }

    /** Daylight factor: 1 with the sun up, easing to 0 as it sets, and 0 at night or in rain. */
    static double daylight(double sunHeight, double night, double rain) {
        return (1.0 - SunMath.clamp(night, 0.0, 1.0)) * (1.0 - SunMath.clamp(rain, 0.0, 1.0)) * SunMath.smoothstep(-0.03, 0.10, sunHeight);
    }

    /**
     * Brightness of the rays by distance r from the sun, in screen heights: strong near the sun, fading out far from it (the
     * shader pack does the same with pow(align, 30) + pow(align, 16) + pow(align, 2)).
     */
    static double falloff(double r) {
        double q = r / 0.62;
        return Math.pow(1.0 / (1.0 + q * q), 1.35);
    }

    /** Half size of the falloff texture in screen heights. Bigger than the screen can ever need. */
    static final double FALLOFF_RADIUS = 3.6;

    /** Grey bytes of the falloff texture (size x size). It is exactly 0 on its border. */
    static byte[] falloffTexture(int size) {
        byte[] px = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = ((x + 0.5) / size * 2.0 - 1.0) * FALLOFF_RADIUS;
                double dy = ((y + 0.5) / size * 2.0 - 1.0) * FALLOFF_RADIUS;
                double rn = Math.hypot(dx, dy) / FALLOFF_RADIUS;
                double v = falloff(Math.hypot(dx, dy)) * (1.0 - SunMath.smoothstep(0.6, 1.0, rn));
                px[y * size + x] = (byte) Math.round(SunMath.clamp(v, 0.0, 1.0) * 255.0);
            }
        }
        return px;
    }
}
