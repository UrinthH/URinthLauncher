package dev.urinth.sunflare;

/**
 * The numbers behind the screen-space god rays (the same idea as the "Godrays" of Sildur's Vibrant shaders): the bright sky is
 * smeared towards the sun, so every gap between leaves, trunks and hills becomes a soft ray that points at the sun.
 * The smear is done on a small picture in two stages, each stored once:
 * <ul>
 *   <li>fine passes: pass k adds the picture to a copy of itself shrunk towards the sun; the shrinks double from pass to pass, so n
 *       passes add up 2^n samples spaced evenly over the first part of the way to the sun (the "band"), for n small draws;</li>
 *   <li>one band pass: the result is added to copies of itself, each one band further out (scale band^j), so the bands tile the
 *       whole way with no gap.</li>
 * </ul>
 * The sample steps come out finer than the blur of the small picture, so no lines show, and nothing is rounded off to zero
 * (adding dozens of dim layers in an 8-bit picture does that). Plain maths, unit tested.
 */
final class GodRayMath {
    private GodRayMath() {}

    /** Fewest and most fine passes. Each one doubles the samples of a band. */
    static final int MIN_PASSES = 4;
    static final int MAX_PASSES = 6;
    /** The settings talk in layers (samples along a ray in a band); this is the range they use. */
    static final int MIN_LAYERS = 16;
    static final int MAX_LAYERS = 64;
    /** How much of the way to the sun the fine passes cover (shrinks add up to this). */
    static final double FINE_REACH = 0.30;
    /** Bands in the band pass at full quality and at low quality (the band count sets how far the rays reach). */
    static final int BANDS = 7;
    static final int BANDS_LOW = 5;
    /** Largest area the rays are drawn in, as a radius around the point they come from, in screen heights. */
    static final double MAX_RADIUS = 0.72;

    /** How many samples per band for a quality between 0 and 1, given how many the setting allows. */
    static int layerCount(int configured, double quality) {
        int cap = Math.max(MIN_LAYERS, Math.min(MAX_LAYERS, configured));
        int n = (int) Math.round(MIN_LAYERS + (cap - MIN_LAYERS) * SunMath.clamp(quality, 0.0, 1.0));
        return Math.max(MIN_LAYERS, Math.min(cap, n));
    }

    /**
     * Number of fine passes for the setting (2^passes is the number of samples in a band). Quality does not change it: these passes
     * are tiny, the savings are made by drawing a smaller area.
     */
    static int passes(int configured) {
        int cap = Math.max(MIN_LAYERS, Math.min(MAX_LAYERS, configured));
        int p = (int) Math.round(Math.log(cap) / Math.log(2.0));
        return Math.max(MIN_PASSES, Math.min(MAX_PASSES, p));
    }

    /** Bands for the current quality. */
    static int bandCount(double quality) {
        return quality < 0.5 ? BANDS_LOW : BANDS;
    }

    /**
     * Fine pass k (0-based, of n) draws its copy shrunk towards the sun to {@code 1 - shrink[k]} of its size. The shrinks double
     * from pass to pass and add up to nearly {@link #FINE_REACH}. {@code mix[k]} is the share of the shrunk copy in the result
     * (the picture itself gets the rest); both shares are multiples of 1/255 so they can be vertex colours. The widest pass
     * gets a smaller share, so the far end of the band counts a little less than the near end.
     */
    static void smear(int n, double[] shrink, double[] mix) {
        for (int k = 0; k < n; k++) {
            shrink[k] = FINE_REACH * Math.pow(2.0, k) / Math.pow(2.0, n);
            double m = k == n - 1 ? 0.42 : 0.5;
            mix[k] = Math.round(m * 255.0) / 255.0;
        }
    }

    /** The scale where the fine passes end: the smallest copy of the picture they produce (all shrinks combined). */
    static double bandScale(int n) {
        double s = 1.0;
        for (int k = 0; k < n; k++) s *= 1.0 - FINE_REACH * Math.pow(2.0, k) / Math.pow(2.0, n);
        return s;
    }

    /**
     * The band pass: band j shows the picture shrunk to {@code scale[j]} = bandScale^j (band 0 is the picture itself), so the bands
     * start exactly where the one before ended. Closer bands count more. The weights add up to 1 and are multiples of 1/255.
     */
    static void bands(int n, int count, double[] scale, double[] weight) {
        double band = bandScale(n);
        double reach = 1.0 - Math.pow(band, count);
        double sum = 0.0;
        for (int j = 0; j < count; j++) {
            scale[j] = Math.pow(band, j);
            double t = (1.0 - scale[j]) / reach;
            weight[j] = Math.pow(1.0 - 0.75 * t, 1.3);
            sum += weight[j];
        }
        int total = 0;
        int[] bytes = new int[count];
        for (int j = 0; j < count; j++) {
            bytes[j] = (int) Math.round(weight[j] / sum * 255.0);
            total += bytes[j];
        }
        bytes[0] += 255 - total; // the nearest band takes the rounding, so the weights add up to exactly 1
        for (int j = 0; j < count; j++) weight[j] = bytes[j] / 255.0;
    }

    /** Radius (screen heights) of the area the rays are drawn in. It shrinks when frames are too slow, which is what saves time. */
    static double regionRadius(double quality) {
        return MAX_RADIUS * (0.4 + 0.6 * SunMath.clamp(quality, 0.0, 1.0));
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
     * out[2] = 0..1: 1 when the sun is in front of the player, easing out when it is at the side, and {@link #OPPOSITE_GAIN} at
     * most when it is behind (then the point is the opposite point of the sky), a little lower when far outside the picture.
     */
    static void origin(double vx, double vy, double vz, double projX, double projY, double[] out) {
        double facing = -vz;
        double nx;
        double ny;
        double gain;
        if (facing <= -BEHIND_FROM) {
            // behind the player: the rays come from the opposite point of the sky (the faint rays that point away from the sun
            // in real life), so they are there whichever way the player looks; they are weaker than with the sun in front
            double back = -facing;
            nx = projX * -vx / back;
            ny = projY * -vy / back;
            gain = OPPOSITE_GAIN * SunMath.smoothstep(BEHIND_FROM, BEHIND_FROM + 0.25, back);
        } else {
            if (facing > 0.02) {
                nx = projX * vx / facing;
                ny = projY * vy / facing;
            } else {
                // at the side: only the direction on the screen is known, so put the point far out in that direction
                nx = projX * vx * 50.0;
                ny = projY * vy * 50.0;
            }
            gain = SunMath.smoothstep(-BEHIND_FROM, 0.15, facing);
        }
        double raw = Math.max(Math.abs(nx), Math.abs(ny));
        if (raw > ORIGIN_LIMIT) {
            double k = ORIGIN_LIMIT / raw;
            nx *= k;
            ny *= k;
        }
        out[0] = nx * 0.5 + 0.5;
        out[1] = ny * 0.5 + 0.5;
        double far = SunMath.smoothstep(1.0, 2.5, raw);
        out[2] = gain * (1.0 - 0.35 * far);
    }

    /** With the sun this far behind the player (cosine of the angle), the rays switch to the opposite point of the sky. */
    static final double BEHIND_FROM = 0.1;
    /** Strength of the rays from the opposite point, compared with the sun in front. */
    static final double OPPOSITE_GAIN = 0.6;

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

    /**
     * Brightness at rho (0 at the sun, 1 at the edge of the drawn area): the same look as {@link #falloff} (the area is
     * {@link #MAX_RADIUS} screen heights), but eased to exactly 0 at the edge so the area has no visible border.
     */
    static double areaFalloff(double rho) {
        if (rho >= 1.0) return 0.0;
        return falloff(rho * MAX_RADIUS) * (1.0 - SunMath.smoothstep(0.55, 1.0, rho));
    }

    /** Grey bytes of the falloff texture (size x size) across the whole drawn area (-1..1 on both axes). It is 0 on its border. */
    static byte[] falloffTexture(int size) {
        byte[] px = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size * 2.0 - 1.0;
                double dy = (y + 0.5) / size * 2.0 - 1.0;
                px[y * size + x] = (byte) Math.round(SunMath.clamp(areaFalloff(Math.hypot(dx, dy)), 0.0, 1.0) * 255.0);
            }
        }
        return px;
    }
}
