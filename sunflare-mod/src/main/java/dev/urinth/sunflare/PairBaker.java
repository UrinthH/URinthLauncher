package dev.urinth.sunflare;

/**
 * Bakes the crescent pairs (a glow minus a second, offset glow). Their shape depends on where the sun is, so they are
 * re-baked each frame, into a small 64x64 texture. About 4k table look-ups for all four pairs.
 */
final class PairBaker {
    private final double[] scratch = new double[FlareAtlas.PAIR_CELL * FlareAtlas.PAIR_CELL];

    /**
     * Writes the pair into its cell of {@code out} (PAIR_SIZE wide, grey bytes, row 0 = top).
     *
     * @return the brightest value, which the caller uses as the colour level of the quad; 0 if nothing is visible
     */
    double bake(FlareElement e, FlareFrame.Draw d, double lx, double ly, double aspect, double sm, double flareScale, byte[] out) {
        int n = FlareAtlas.pairTexels(d.element);
        double sA = e.smOutside ? 1.0 : Math.pow(sm, e.main.smExponent());
        double sB = e.smOutside ? 1.0 : Math.pow(sm, e.sub.smExponent());
        double outer = e.smOutside ? sm : 1.0;
        double cxB = e.subGeo.centerU(lx);
        double cyB = e.subGeo.centerV(ly);
        double rxB = e.subGeo.radiusU(e.sub.radius, aspect, flareScale);
        double ryB = e.subGeo.radiusV(e.sub.radius, flareScale);

        double max = 0.0;
        for (int y = 0; y < n; y++) {
            double dy = (y + 0.5) / n * 2.0 - 1.0;
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2.0 - 1.0;
                double rA = Math.sqrt(dx * dx + dy * dy);
                double v = 0.0;
                if (rA < 1.0) {
                    double a = FlareLut.lookup(e.main, rA) * sA;
                    if (a > 0.0) {
                        double u = d.cx + dx * d.rx;
                        double w = d.cy - dy * d.ry;
                        double ex = (u - cxB) / rxB;
                        double ey = (w - cyB) / ryB;
                        double b = FlareLut.lookup(e.sub, Math.sqrt(ex * ex + ey * ey)) * sB;
                        v = Math.max(0.0, a - b) * outer;
                    }
                }
                scratch[y * n + x] = v;
                if (v > max) max = v;
            }
        }

        int slot = FlareAtlas.pairSlot(d.element);
        int ox = (slot % FlareAtlas.PAIR_COLS) * FlareAtlas.PAIR_CELL;
        int oy = (slot / FlareAtlas.PAIR_COLS) * FlareAtlas.PAIR_CELL;
        double inv = max > 1e-9 ? 255.0 / max : 0.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                out[(oy + y) * FlareAtlas.PAIR_SIZE + ox + x] = (byte) Math.round(scratch[y * n + x] * inv);
            }
        }
        return max;
    }
}
