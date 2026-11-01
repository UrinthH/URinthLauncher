package dev.urinth.sunflare;

/** Pre-computed brightness curves (profile value at sm = 1), so nothing heavy runs per pixel or per frame. */
final class FlareLut {
    private FlareLut() {}

    static final int N = 512;

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
        p.table = t;
        p.peakValue = peak;
    }

    /** Largest value of the profile at sm = 1. */
    static double peak(FlareProfile p) {
        return p.peakValue;
    }

    /**
     * The smallest normalised radius that still holds everything brighter than {@code fraction} of the peak.
     * Scans in from the edge, so ring shaped profiles are handled too. Returns 0 if nothing reaches that.
     */
    static double support(FlareProfile p, double fraction) {
        double[] t = p.table;
        double level = fraction * p.peakValue;
        for (int i = N - 1; i >= 0; i--) {
            if (t[i] >= level) return Math.min(1.0, (i + 1) / (double) (N - 1));
        }
        return 0.0;
    }

    /** Profile value at sm = 1 for a normalised distance, linearly interpolated. 0 outside the ellipse. */
    static double lookup(FlareProfile p, double rp) {
        if (rp >= 1.0) return 0.0;
        double[] t = p.table;
        double x = rp * (N - 1);
        int i = (int) x;
        double f = x - i;
        return t[i] + (t[i + 1] - t[i]) * f;
    }
}
