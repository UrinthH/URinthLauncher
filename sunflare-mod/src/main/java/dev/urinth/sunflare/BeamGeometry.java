package dev.urinth.sunflare;

import java.util.Arrays;

/**
 * Turns light shafts into camera-facing ribbons (world space, depth tested by the GPU). Plain maths: matrices come in as
 * column-major float[16], so it is unit tested without Minecraft.
 *
 * Phone GPUs pay for every pixel a translucent layer covers, so the ribbons are chosen against a pixel budget: the strongest
 * shafts first, until their estimated screen area is used up. Shafts that would cover the screen from right in front of the
 * camera fade out instead of filling it.
 */
final class BeamGeometry {
    static final int FLOATS_PER_VERTEX = 7;
    static final int MAX_SELECTED = 256;
    /** Rows along the shaft x columns across: 3 x 4 quads... 6 quads of 4 vertices each. */
    static final int VERTS_PER_SHAFT = 24;
    static final double NEAR = 0.4;
    /** Ribbon width in blocks: wider than one cell, so neighbours melt together into one soft curtain. */
    static final double WIDTH = 1.7;

    static final class Params {
        /** Peak brightness of a shaft (0..1 per channel) before the sun-facing and distance factors. */
        double strength;
        double red, green, blue;
        /** Share of the screen the ribbons may cover, added up (overlaps count twice). */
        double areaFraction;
        int screenW, screenH;
        final double[] sun = new double[3];
    }

    final float[] vertices = new float[MAX_SELECTED * VERTS_PER_SHAFT * FLOATS_PER_VERTEX];
    int vertexCount;
    int selected;
    double usedArea;

    private final double[] score = new double[BeamField.MAX_SHAFTS];
    private final double[] area = new double[BeamField.MAX_SHAFTS];
    private final double[][] seg = new double[BeamField.MAX_SHAFTS][6];
    private final long[] order = new long[BeamField.MAX_SHAFTS];

    static double smooth(double e0, double e1, double x) {
        return SunMath.smoothstep(e0, e1, x);
    }

    /** Distance fade: nothing right in front of the lens, and a long soft fade out in the distance. */
    static double distanceFade(double d) {
        return smooth(2.0, 7.0, d) * (1.0 - smooth(34.0, 52.0, d));
    }

    private static double viewZ(float[] m, double x, double y, double z) {
        return m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    /**
     * @param view  world-to-view matrix (column-major), used to cull and to size the ribbons
     * @param proj  the projection the GPU will apply
     * @param t     transform from camera-relative world coordinates to the coordinates handed to the GPU (the inverse of the
     *              GPU's model-view matrix times the view matrix)
     * @return number of vertices written to {@link #vertices}
     */
    int build(BeamField field, double camX, double camY, double camZ, float[] view, float[] proj, float[] t, Params p) {
        vertexCount = 0;
        selected = 0;
        usedArea = 0;
        int n = field.count();
        int cand = 0;
        double halfW = p.screenW * 0.5;
        double halfH = p.screenH * 0.5;
        double focal = proj[5] * halfH;
        for (int i = 0; i < n; i++) {
            BeamField.Shaft s = field.get(i);
            double ax = s.ax - camX, ay = s.ay - camY, az = s.az - camZ;
            double bx = s.bx - camX, by = s.by - camY, bz = s.bz - camZ;
            double za = viewZ(view, ax, ay, az);
            double zb = viewZ(view, bx, by, bz);
            if (za > -NEAR && zb > -NEAR) continue;
            if (za > -NEAR) {
                double k = (-NEAR - za) / (zb - za);
                ax += (bx - ax) * k; ay += (by - ay) * k; az += (bz - az) * k;
                za = -NEAR;
            } else if (zb > -NEAR) {
                double k = (-NEAR - zb) / (za - zb);
                bx += (ax - bx) * k; by += (ay - by) * k; bz += (az - bz) * k;
                zb = -NEAR;
            }
            double xa = view[0] * ax + view[4] * ay + view[8] * az + view[12];
            double ya = view[1] * ax + view[5] * ay + view[9] * az + view[13];
            double xb = view[0] * bx + view[4] * by + view[8] * bz + view[12];
            double yb = view[1] * bx + view[5] * by + view[9] * bz + view[13];
            double nxa = proj[0] * xa / -za, nya = proj[5] * ya / -za;
            double nxb = proj[0] * xb / -zb, nyb = proj[5] * yb / -zb;
            if ((nxa > 1.3 && nxb > 1.3) || (nxa < -1.3 && nxb < -1.3) || (nya > 1.3 && nyb > 1.3) || (nya < -1.3 && nyb < -1.3)) continue;

            double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
            double dMid = Math.sqrt(mx * mx + my * my + mz * mz);
            if (dMid < 1e-3) continue;
            double cosT = (mx * p.sun[0] + my * p.sun[1] + mz * p.sun[2]) / dMid;
            double phase = 0.15 + 0.85 * Math.pow(Math.max(0.0, cosT), 2.0);
            double nearest = Math.min(Math.sqrt(ax * ax + ay * ay + az * az), Math.sqrt(bx * bx + by * by + bz * bz));
            double fade = distanceFade(Math.max(nearest, dMid * 0.5));
            double sc = s.weight * phase * fade;
            if (sc < 0.02) continue;

            double lenPx = Math.hypot((nxa - nxb) * halfW, (nya - nyb) * halfH);
            double zMid = Math.min(-NEAR, (za + zb) * 0.5);
            double widthPx = WIDTH * focal / -zMid;
            score[cand] = sc;
            area[cand] = Math.min(lenPx, p.screenH * 2.0) * Math.min(widthPx, p.screenH);
            double[] g = seg[cand];
            g[0] = ax; g[1] = ay; g[2] = az; g[3] = bx; g[4] = by; g[5] = bz;
            order[cand] = ((long) (sc * 1_000_000.0) << 20) | cand;
            cand++;
        }
        if (cand == 0) return 0;
        Arrays.sort(order, 0, cand);
        double budget = p.areaFraction * p.screenW * p.screenH;
        for (int k = cand - 1; k >= 0 && selected < MAX_SELECTED; k--) {
            int c = (int) (order[k] & 0xFFFFF);
            if (area[c] > 0.5 * budget) continue;
            if (usedArea + area[c] > budget) continue;
            usedArea += area[c];
            emit(c, t, vertices, p);
            selected++;
        }
        return vertexCount;
    }

    private static final double[] ROW_AT = {0.0, 0.22, 0.75, 1.0};
    private static final double[] ROW_I = {0.0, 1.0, 0.8, 0.0};
    private static final double[] COL_AT = {-1.0, 0.0, 1.0};
    private static final double[] COL_I = {0.0, 1.0, 0.0};
    /** The four corners of a cell as {row offset, column offset}. */
    private static final int[][] CORNER = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private void emit(int c, float[] t, float[] out, Params p) {
        double[] g = seg[c];
        double ax = g[0], ay = g[1], az = g[2];
        double dx = g[3] - ax, dy = g[4] - ay, dz = g[5] - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3) return;
        double mx = ax + dx * 0.5, my = ay + dy * 0.5, mz = az + dz * 0.5;
        double dMid = Math.sqrt(mx * mx + my * my + mz * mz);
        // sideways = axis x view direction: the ribbon turns about its own axis to face the camera
        double cx = dy * mz - dz * my;
        double cy = dz * mx - dx * mz;
        double cz = dx * my - dy * mx;
        double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6) return;
        double endOn = smooth(0.08, 0.35, cl / (len * dMid));
        if (endOn <= 0.0) return;
        double rx = cx / cl, ry = cy / cl, rz = cz / cl;
        double halfW = WIDTH * 0.5;
        double cosT = (mx * p.sun[0] + my * p.sun[1] + mz * p.sun[2]) / dMid;
        double phase = 0.15 + 0.85 * Math.pow(Math.max(0.0, cosT), 2.0);
        double base = p.strength * endOn * phase * (0.4 + 0.6 * Math.min(1.0, score[c] * 3.0));

        int idx = vertexCount * FLOATS_PER_VERTEX;
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 2; col++) {
                for (int[] q : CORNER) {
                    int row = r + q[0];
                    int cc = col + q[1];
                    double f = ROW_AT[row];
                    double across = COL_AT[cc] * halfW;
                    double x = ax + dx * f + rx * across;
                    double y = ay + dy * f + ry * across;
                    double z = az + dz * f + rz * across;
                    double level = Math.min(1.0, base * ROW_I[row] * COL_I[cc] * distanceFade(Math.sqrt(x * x + y * y + z * z)));
                    out[idx++] = (float) (t[0] * x + t[4] * y + t[8] * z + t[12]);
                    out[idx++] = (float) (t[1] * x + t[5] * y + t[9] * z + t[13]);
                    out[idx++] = (float) (t[2] * x + t[6] * y + t[10] * z + t[14]);
                    out[idx++] = (float) (level * p.red);
                    out[idx++] = (float) (level * p.green);
                    out[idx++] = (float) (level * p.blue);
                    out[idx++] = 1f;
                    vertexCount++;
                }
            }
        }
    }
}
