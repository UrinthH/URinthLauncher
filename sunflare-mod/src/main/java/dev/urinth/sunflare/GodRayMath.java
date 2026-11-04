package dev.urinth.sunflare;

/**
 * The numbers behind the screen-space god rays (the same idea as the "Godrays" of Sildur's Vibrant shaders): the bright sky is
 * smeared towards the sun, so every gap between leaves, trunks and hills becomes a soft ray that points at the sun. The
 * smear is done by drawing the small sky picture several times, each time a little shrunk towards the sun and added up.
 * The smear is done in two passes, the second on the result of the first. Pass A has n fine layers that cover one "band" of
 * distances (the scales from 1 down to {@link #BAND}); pass B has {@link #BAND_LAYERS} layers, each one band further out
 * (the scales 1, BAND, BAND squared, ...). The scales of the two passes multiply, so together they sample the sky at n times
 * {@link #BAND_LAYERS} evenly spaced distances along the whole reach, for the cost of n + {@link #BAND_LAYERS} draws. One
 * pass with that many layers would show the steps between its layers as lines in the rays.
 * Plain maths, unit tested.
 */
final class GodRayMath {
    private GodRayMath() {}

    /** Scale where pass A ends: it covers the distances from the pixel up to 30% of the way to the sun. */
    static final double BAND = 0.70;
    /** Layers of pass B (BAND to the 7th power is 0.08, so the whole reach is about 92%, like Sildur's 0.04 * 23). */
    static final int BAND_LAYERS = 7;
    /** Layers of pass A. */
    static final int MIN_LAYERS = 4;
    static final int MAX_LAYERS = 14;

    /** Reach of both passes together, as a part of the way from a pixel to the sun. */
    static double totalReach() {
        return 1.0 - Math.pow(BAND, BAND_LAYERS);
    }

    /**
     * Pass A: scale and weight of each of the n layers. Layer k shows the picture shrunk towards the sun by {@code scale[k]}
     * (1 = not at all), evenly from 1 down to just above {@link #BAND}, so a pixel adds up the picture along the line from itself
     * to the sun. Closer samples count more. Weights add up to 1.
     */
    static void layers(int n, double[] scale, double[] weight) {
        double sum = 0.0;
        for (int k = 0; k < n; k++) {
            double t = (double) k / n;
            scale[k] = 1.0 - (1.0 - BAND) * t;
            weight[k] = Math.pow(1.0 - 0.75 * t * (1.0 - BAND) / totalReach(), 1.3);
            sum += weight[k];
        }
        for (int k = 0; k < n; k++) weight[k] /= sum;
    }

    /**
     * Pass B: the {@link #BAND_LAYERS} layers, one band further out each (scale BAND^j). Closer bands count more. Weights add up to 1.
     */
    static void bands(double[] scale, double[] weight) {
        double sum = 0.0;
        for (int j = 0; j < BAND_LAYERS; j++) {
            scale[j] = Math.pow(BAND, j);
            double t = (1.0 - scale[j]) / totalReach();
            weight[j] = Math.pow(1.0 - 0.75 * t, 1.3);
            sum += weight[j];
        }
        for (int j = 0; j < BAND_LAYERS; j++) weight[j] /= sum;
    }

    /** How many layers per pass for a quality between 0 and 1, given how many the setting allows. */
    static int layerCount(int configured, double quality) {
        int n = (int) Math.round(MIN_LAYERS + (configured - MIN_LAYERS) * SunMath.clamp(quality, 0.0, 1.0));
        return Math.max(MIN_LAYERS, Math.min(Math.min(MAX_LAYERS, configured), n));
    }

    /**
     * How far from the middle of the screen the point the rays come from may be, in clip space (1 is the screen edge). A sun
     * high above the player is far outside the picture, but the rays should still be there when the player looks at the
     * horizon, so the point is held just outside the edge and the rays keep pointing the right way.
     */
    static final double ORIGIN_LIMIT = 1.45;

    /**
     * Where the rays come from and how strongly they show. {@code vx, vy, vz} is the direction of the sun in view space (the
     * player looks along -z), {@code projX, projY} the first two diagonal entries of the projection matrix.
     * out[0], out[1] = the point in 0..1 screen coordinates (kept near the screen, see {@link #ORIGIN_LIMIT}),
     * out[2] = 0..1: 1 when the sun is in front of the player, easing out when it is at the side and gone when it is behind
     * (nothing can be smeared towards a sun behind the camera), a little lower when it is far outside the picture.
     */
    static void origin(double vx, double vy, double vz, double projX, double projY, double[] out) {
        double facing = -vz;
        double nx;
        double ny;
        if (facing > 0.02) {
            nx = projX * vx / facing;
            ny = projY * vy / facing;
        } else {
            // at the side or behind: only the direction on the screen is known, so put the point far out in that direction
            nx = projX * vx * 50.0;
            ny = projY * vy * 50.0;
        }
        double raw = Math.max(Math.abs(nx), Math.abs(ny));
        if (raw > ORIGIN_LIMIT) {
            double k = ORIGIN_LIMIT / raw;
            nx *= k;
            ny *= k;
        }
        out[0] = nx * 0.5 + 0.5;
        out[1] = ny * 0.5 + 0.5;
        double front = SunMath.smoothstep(-0.20, 0.15, facing);
        double far = SunMath.smoothstep(1.0, 2.5, raw);
        out[2] = front * (1.0 - 0.35 * far);
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
