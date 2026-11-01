package dev.urinth.sunflare;

/**
 * Which parts of the screen the flare actually changes. The big soft glows are drawn into a small picture, and only the
 * tiles marked here are copied onto the screen. A tile is skipped when the flare there is too faint to change an 8-bit pixel,
 * so those pixels are not touched at all.
 */
final class FlareCoverage {
    static final int GX = 16;
    static final int GY = 9;
    /** Below this (about a third of one output level) the flare cannot change the picture. */
    static final double THRESHOLD = 0.35 / 255.0;

    final boolean[] cells = new boolean[GX * GY];
    private final boolean[] raw = new boolean[GX * GY];
    private final double[] vertex = new double[(GX + 1) * (GY + 1)];

    /** Fills {@link #cells} for the draws of a frame (screen: u 0..1 to the right, v 0..1 up). Returns the tile count. */
    int compute(FlareFrame f) {
        java.util.Arrays.fill(raw, false);
        // the soft, large elements: sum of their brightness at the tile corners (an upper bound per channel)
        for (int gy = 0; gy <= GY; gy++) {
            double v = 1.0 - gy / (double) GY;
            for (int gx = 0; gx <= GX; gx++) {
                double u = gx / (double) GX;
                double sum = 0.0;
                for (int i = 0; i < f.count; i++) {
                    FlareFrame.Draw d = f.draws[i];
                    double dx = (u - d.cx) / d.rx;
                    double dy = (v - d.cy) / d.ry;
                    double rp = Math.sqrt(dx * dx + dy * dy);
                    if (rp >= d.support) continue;
                    FlareProfile p = FlareSet.ELEMENTS[d.element].main;
                    double level = Math.max(d.red, Math.max(d.green, d.blue));
                    sum += level * FlareLut.lookup(p, rp) / p.peakValue;
                }
                vertex[gy * (GX + 1) + gx] = sum;
            }
        }
        for (int gy = 0; gy < GY; gy++) {
            for (int gx = 0; gx < GX; gx++) {
                raw[gy * GX + gx] = vertex[gy * (GX + 1) + gx] >= THRESHOLD || vertex[gy * (GX + 1) + gx + 1] >= THRESHOLD
                        || vertex[(gy + 1) * (GX + 1) + gx] >= THRESHOLD || vertex[(gy + 1) * (GX + 1) + gx + 1] >= THRESHOLD;
            }
        }
        // small elements can sit inside a tile whose corners are all dark: mark every tile they touch
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            double x0 = d.cx - d.rx * d.support;
            double x1 = d.cx + d.rx * d.support;
            double y0 = 1.0 - d.cy - d.ry * d.support;
            double y1 = 1.0 - d.cy + d.ry * d.support;
            if ((x1 - x0) * GX > 1.5 && (y1 - y0) * GY > 1.5) continue;
            markRect(x0, y0, x1, y1);
        }
        // one tile of safety around everything
        int n = 0;
        for (int gy = 0; gy < GY; gy++) {
            for (int gx = 0; gx < GX; gx++) {
                boolean on = false;
                for (int oy = -1; oy <= 1 && !on; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int x = gx + ox;
                        int y = gy + oy;
                        if (x >= 0 && y >= 0 && x < GX && y < GY && raw[y * GX + x]) {
                            on = true;
                            break;
                        }
                    }
                }
                cells[gy * GX + gx] = on;
                if (on) n++;
            }
        }
        return n;
    }

    private void markRect(double x0, double y0, double x1, double y1) {
        int gx0 = Math.max(0, (int) Math.floor(x0 * GX));
        int gx1 = Math.min(GX - 1, (int) Math.floor(x1 * GX));
        int gy0 = Math.max(0, (int) Math.floor(y0 * GY));
        int gy1 = Math.min(GY - 1, (int) Math.floor(y1 * GY));
        for (int gy = gy0; gy <= gy1; gy++) {
            for (int gx = gx0; gx <= gx1; gx++) raw[gy * GX + gx] = true;
        }
    }
}
