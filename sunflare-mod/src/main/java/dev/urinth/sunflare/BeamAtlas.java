package dev.urinth.sunflare;

/**
 * One small grey texture holding the soft shapes the sun beams are drawn with, so every ribbon, dust speck and light patch is
 * a single textured quad (4 vertices) instead of a pile of coloured triangles. All shapes are 0 on their border, so they blend
 * into nothing and neighbouring cells of the atlas never bleed into each other.
 *
 * Layout (128 x 64): ribbon 64 x 64 at (0, 0) (top half the faint tail rising towards the lit part, bottom half the lit part
 * ending softly on the ground), dust dot 32 x 32 at (64, 0), ground patch 32 x 32 at (96, 0).
 */
final class BeamAtlas {
    private BeamAtlas() {}

    static final int WIDTH = 128;
    static final int HEIGHT = 64;

    /** {u0, v0, u1, v1} rectangles. */
    static final double[] TAIL = {0.0, 0.0, 0.5, 0.5};
    static final double[] CORE = {0.0, 0.5, 0.5, 1.0};
    static final double[] DOT = {0.5, 0.0, 0.75, 0.5};
    static final double[] PATCH = {0.75, 0.0, 1.0, 0.5};

    /** Brightness across a ribbon (x = -1..1): a smooth bell that is exactly 0 at both edges. */
    static double across(double x) {
        double a = Math.abs(x);
        if (a >= 1.0) return 0.0;
        double g = Math.exp(-3.4 * a * a);
        double edge = Math.exp(-3.4);
        return (g - edge) / (1.0 - edge);
    }

    /** The faint tail, t = 0 at its far end (towards the sun) up to 1 where the lit part starts: rises smoothly from nothing. */
    static double tail(double t) {
        double s = SunMath.smoothstep(0.0, 1.0, t);
        return s * s;
    }

    /** The lit part, t = 0 where it starts down to 1 on the ground: full strength, then a soft landing. */
    static double core(double t) {
        return 1.0 - SunMath.smoothstep(0.86, 1.0, t);
    }

    /** A round soft disc (r = 0..1). */
    static double disc(double r, double sharp) {
        if (r >= 1.0) return 0.0;
        double t = 1.0 - r * r;
        return Math.pow(t, sharp);
    }

    static byte[] build() {
        byte[] px = new byte[WIDTH * HEIGHT];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                double u = (x + 0.5) / 64.0 * 2.0 - 1.0;
                double along = y < 32 ? tail((y + 0.5) / 32.0) : core((y - 32 + 0.5) / 32.0);
                set(px, x, y, across(u) * along);
            }
        }
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = (x + 0.5) / 32.0 * 2.0 - 1.0;
                double dy = (y + 0.5) / 32.0 * 2.0 - 1.0;
                double r = Math.sqrt(dx * dx + dy * dy);
                set(px, 64 + x, y, disc(r, 3.0));
                set(px, 96 + x, y, disc(r, 1.6));
            }
        }
        return px;
    }

    private static void set(byte[] px, int x, int y, double v) {
        px[y * WIDTH + x] = (byte) Math.round(SunMath.clamp(v, 0.0, 1.0) * 255.0);
    }
}
