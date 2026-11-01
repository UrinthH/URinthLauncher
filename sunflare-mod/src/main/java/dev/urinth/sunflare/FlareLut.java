package dev.urinth.sunflare;

import java.util.IdentityHashMap;
import java.util.Map;

/** Pre-computed brightness curves (profile value at sm = 1), so nothing heavy runs per pixel or per frame. */
final class FlareLut {
    private FlareLut() {}

    static final int N = 512;

    private static final Map<FlareProfile, double[]> TABLES = new IdentityHashMap<>();
    private static final Map<FlareProfile, Double> PEAKS = new IdentityHashMap<>();

    static {
        for (FlareElement e : FlareSet.ELEMENTS) {
            register(e.main);
            if (e.sub != null) register(e.sub);
        }
    }

    private static void register(FlareProfile p) {
        double[] t = new double[N];
        double peak = 0.0;
        for (int i = 0; i < N; i++) {
            t[i] = p.value(i / (double) (N - 1), 1.0);
            peak = Math.max(peak, t[i]);
        }
        TABLES.put(p, t);
        PEAKS.put(p, peak);
    }

    /** Largest value of the profile at sm = 1. */
    static double peak(FlareProfile p) {
        return PEAKS.get(p);
    }

    /** Profile value at sm = 1 for a normalised distance, linearly interpolated. 0 outside the ellipse. */
    static double lookup(FlareProfile p, double rp) {
        if (rp >= 1.0) return 0.0;
        double[] t = TABLES.get(p);
        double x = rp * (N - 1);
        int i = (int) x;
        double f = x - i;
        return t[i] + (t[i + 1] - t[i]) * f;
    }
}
