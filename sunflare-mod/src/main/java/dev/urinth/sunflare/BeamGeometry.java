package dev.urinth.sunflare;

import java.util.Arrays;

/**
 * Turns light shafts, floating dust and light patches into textured quads (world space, depth tested by the GPU). Plain maths:
 * matrices come in as column-major float[16], so it is unit tested without Minecraft.
 *
 * Phone GPUs pay for every pixel a translucent layer covers, so the shafts are chosen against a pixel budget: strongest first,
 * until their estimated screen area is used up. The choice is never abrupt: every shaft has a brightness level that eases
 * up and down, so a shaft that wins or loses its place fades instead of popping (this is what stops flicker).
 *
 * Each shaft runs from where the light lands, up along the sun direction: first the clearly lit air, then a long faint tail
 * that carries on towards the sun, so the shaft visibly belongs to the sun. A ribbon is two quads (8 vertices).
 *
 * Vertex layout: x, y, z, u, v, r, g, b.
 */
final class BeamGeometry {
    static final int FLOATS_PER_VERTEX = 8;
    static final int MAX_SELECTED = 140;
    static final int MAX_PATCHES = 48;
    static final double NEAR = 0.4;

    static final class Params {
        /** Peak brightness of a shaft (0..1 per channel) before the sun-facing and distance factors. */
        double strength;
        double red, green, blue;
        /** Share of the screen the ribbons may cover, added up (overlaps count twice). */
        double areaFraction;
        int screenW, screenH;
        final double[] sun = new double[3];
        /** 0..1 strength of the open-air dust shafts (they are strongest at sunset). */
        double hazeScale = 1.0;
        /** Seconds since the last frame, for the brightness easing. */
        double dt = 1.0 / 60.0;
        /** Beams are drawn out to this many blocks (10 chunks = 160), whatever the render distance is. */
        double range = 160.0;
        double time;
    }

    /** Brightness level per shaft id, eased over time. */
    static final class Levels {
        private final int[] keys = new int[4096];
        private final float[] level = new float[4096];
        private final int[] stamp = new int[4096];
        int frame;

        private int slot(int id) {
            if (id == 0) id = 1;
            int h = (id * 0x9E3779B1) >>> 20 & 4095;
            for (int k = 0; k < 16; k++) {
                int s = (h + k) & 4095;
                if (keys[s] == id) return s;
                if (keys[s] == 0 || frame - stamp[s] > 1200) {
                    keys[s] = id;
                    level[s] = 0f;
                    return s;
                }
            }
            keys[h] = id;
            level[h] = 0f;
            return h;
        }

        float update(int id, boolean wanted, double dt) {
            int s = slot(id);
            stamp[s] = frame;
            double rate = wanted ? 3.0 : 1.6;
            level[s] += ((wanted ? 1f : 0f) - level[s]) * (float) (1.0 - Math.exp(-rate * dt));
            return level[s];
        }
    }

    final float[] vertices = new float[(MAX_SELECTED * 8 + MAX_PATCHES * 4 + BeamDust.COUNT * 4) * FLOATS_PER_VERTEX];
    final Levels levels = new Levels();
    int vertexCount;
    int selected;
    int patches;
    int dustDrawn;
    double usedArea;

    private final double[] score = new double[BeamField.MAX_SHAFTS];
    private final double[] area = new double[BeamField.MAX_SHAFTS];
    private final double[][] seg = new double[BeamField.MAX_SHAFTS][6];
    private final long[] order = new long[BeamField.MAX_SHAFTS];
    private final int[] shaftOf = new int[BeamField.MAX_SHAFTS];
    private final boolean[] wanted = new boolean[BeamField.MAX_SHAFTS];
    private final float[] lvl = new float[BeamField.MAX_SHAFTS];

    static double smooth(double e0, double e1, double x) {
        return SunMath.smoothstep(e0, e1, x);
    }

    /** Distance fade: nothing right in front of the lens (more room for wide shafts), and a long soft fade out to the range. */
    static double distanceFade(double d, double width, double range) {
        return smooth(0.6 * width + 1.5, 2.0 * width + 4.0, d) * (1.0 - smooth(range * 0.65, range, d));
    }

    private static double viewZ(float[] m, double x, double y, double z) {
        return m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    private int put(float[] t, double x, double y, double z, double u, double v, double level, Params p, int idx) {
        vertices[idx++] = (float) (t[0] * x + t[4] * y + t[8] * z + t[12]);
        vertices[idx++] = (float) (t[1] * x + t[5] * y + t[9] * z + t[13]);
        vertices[idx++] = (float) (t[2] * x + t[6] * y + t[10] * z + t[14]);
        vertices[idx++] = (float) u;
        vertices[idx++] = (float) v;
        double l = Math.max(0.0, Math.min(1.0, level));
        vertices[idx++] = (float) (l * p.red);
        vertices[idx++] = (float) (l * p.green);
        vertices[idx++] = (float) (l * p.blue);
        vertexCount++;
        return idx;
    }

    /**
     * @param view  world-to-view matrix (column-major), used to cull and to size the ribbons
     * @param proj  the projection the GPU will apply
     * @param t     transform from camera-relative world coordinates to the coordinates handed to the GPU (the inverse of the
     *              GPU's model-view matrix times the view matrix)
     * @return number of vertices written to {@link #vertices}
     */
    int build(BeamField field, BeamDust dust, double camX, double camY, double camZ, float[] view, float[] proj, float[] t, Params p) {
        vertexCount = 0;
        selected = 0;
        patches = 0;
        dustDrawn = 0;
        usedArea = 0;
        levels.frame++;
        int n = field.count();
        int cand = 0;
        double halfW = p.screenW * 0.5;
        double halfH = p.screenH * 0.5;
        double focal = proj[5] * halfH;
        for (int i = 0; i < n; i++) {
            BeamField.Shaft s = field.get(i);
            if (s.haze && p.hazeScale <= 0.001) continue;
            double total = s.len + s.tail;
            double ax = s.bx + p.sun[0] * total - camX, ay = s.by + p.sun[1] * total - camY, az = s.bz + p.sun[2] * total - camZ;
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
            double nearest = Math.min(Math.min(Math.sqrt(ax * ax + ay * ay + az * az), Math.sqrt(bx * bx + by * by + bz * bz)), dMid);
            if (nearest > p.range) continue;
            double cosT = (mx * p.sun[0] + my * p.sun[1] + mz * p.sun[2]) / dMid;
            // dust scatters light mostly forwards, but also well enough sideways and back: beams stay visible whichever way you face
            double phase = 0.55 + 0.45 * Math.pow(Math.max(0.0, cosT), 2.0);
            double sc = s.weight * s.presence * phase * distanceFade(nearest, s.width, p.range) * (s.haze ? p.hazeScale : 1.0);
            if (sc < 0.01) continue;

            double lenPx = Math.hypot((nxa - nxb) * halfW, (nya - nyb) * halfH);
            double zMid = Math.min(-NEAR, (za + zb) * 0.5);
            double widthPx = s.width * focal / -zMid;
            score[cand] = sc;
            // the faint tail covers its pixels at a fraction of the strength, and the soft edges are not full width either
            double fullLen = s.len + s.tail;
            double effective = (s.len + 0.3 * s.tail) / Math.max(1e-6, fullLen);
            area[cand] = 0.55 * effective * Math.min(lenPx, p.screenH * 2.0) * Math.min(widthPx, p.screenH);
            double[] g = seg[cand];
            g[0] = ax; g[1] = ay; g[2] = az; g[3] = bx; g[4] = by; g[5] = bz;
            shaftOf[cand] = i;
            order[cand] = ((long) (sc * 1_000_000.0) << 20) | cand;
            cand++;
        }
        if (cand > 0) {
            Arrays.sort(order, 0, cand);
            double budget = p.areaFraction * p.screenW * p.screenH;
            Arrays.fill(wanted, 0, cand, false);
            int chosen = 0;
            for (int k = cand - 1; k >= 0; k--) {
                int c = (int) (order[k] & 0xFFFFF);
                if (chosen >= MAX_SELECTED || area[c] > 0.6 * budget || usedArea + area[c] > budget) continue;
                usedArea += area[c];
                wanted[c] = true;
                chosen++;
            }
            // ease every candidate's level towards "drawn" or "not drawn"
            for (int c = 0; c < cand; c++) lvl[c] = levels.update(field.get(shaftOf[c]).id, wanted[c], p.dt);
            // draw the strongest first, including shafts that are still fading out
            for (int k = cand - 1; k >= 0 && selected < MAX_SELECTED; k--) {
                int c = (int) (order[k] & 0xFFFFF);
                if (lvl[c] < 0.02f) continue;
                emitRibbon(field.get(shaftOf[c]), c, lvl[c], camX, camY, camZ, t, p);
                selected++;
            }
            for (int k = cand - 1; k >= 0 && patches < MAX_PATCHES; k--) {
                int c = (int) (order[k] & 0xFFFFF);
                BeamField.Shaft s = field.get(shaftOf[c]);
                if (!s.pool || lvl[c] < 0.05f) continue;
                emitPatch(s, lvl[c], camX, camY, camZ, t, p);
            }
        }
        if (dust != null) emitDust(dust, camX, camY, camZ, view, t, p);
        return vertexCount;
    }

    private void emitRibbon(BeamField.Shaft s, int c, double level, double camX, double camY, double camZ, float[] t, Params p) {
        // visible part (clipped to the near plane) only decides which way the ribbon turns to face the camera
        double[] g = seg[c];
        double dx = g[3] - g[0], dy = g[4] - g[1], dz = g[5] - g[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3) return;
        double mx = (g[0] + g[3]) * 0.5, my = (g[1] + g[4]) * 0.5, mz = (g[2] + g[5]) * 0.5;
        double dMid = Math.sqrt(mx * mx + my * my + mz * mz);
        // sideways = axis x view direction: the ribbon turns about its own axis to face the camera
        double cx = dy * mz - dz * my;
        double cy = dz * mx - dx * mz;
        double cz = dx * my - dy * mx;
        double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6) return;
        double endOn = smooth(0.06, 0.30, cl / (len * dMid));
        if (endOn <= 0.0) return;
        double rx = cx / cl, ry = cy / cl, rz = cz / cl;
        double half = s.width * 0.58;
        double cosT = (mx * p.sun[0] + my * p.sun[1] + mz * p.sun[2]) / dMid;
        double phase = 0.55 + 0.45 * Math.pow(Math.max(0.0, cosT), 2.0);
        double base = p.strength * level * s.presence * endOn * phase * (0.45 + 0.55 * Math.min(1.0, s.weight * 2.5));

        // full ribbon, top (towards the sun) to landing point, relative to the camera
        double total = s.len + s.tail;
        double bx = s.bx - camX, by = s.by - camY, bz = s.bz - camZ;
        double tx = bx + p.sun[0] * total, ty = by + p.sun[1] * total, tz = bz + p.sun[2] * total;
        double cxm = bx + p.sun[0] * s.len, cym = by + p.sun[1] * s.len, czm = bz + p.sun[2] * s.len;
        double[] r = {tx, ty, tz, cxm, cym, czm, bx, by, bz};
        int idx = vertexCount * FLOATS_PER_VERTEX;
        for (int q = 0; q < 2; q++) {
            double[] rect = q == 0 ? BeamAtlas.TAIL : BeamAtlas.CORE;
            int a = q * 3;
            int b = a + 3;
            for (int k = 0; k < 4; k++) {
                boolean bottom = k == 1 || k == 2;
                boolean right = k >= 2;
                int o = bottom ? b : a;
                double side = right ? half : -half;
                double x = r[o] + rx * side, y = r[o + 1] + ry * side, z = r[o + 2] + rz * side;
                double d = Math.sqrt(x * x + y * y + z * z);
                double lv = base * distanceFade(d, s.width, p.range);
                idx = put(t, x, y, z, right ? rect[2] : rect[0], bottom ? rect[3] : rect[1], lv, p, idx);
            }
        }
    }

    private void emitPatch(BeamField.Shaft s, double level, double camX, double camY, double camZ, float[] t, Params p) {
        double rx = s.bx - camX, ry = s.by - camY, rz = s.bz - camZ;
        double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double fade = distanceFade(d, s.width, p.range);
        if (fade <= 0.0 || d < 1.0) return;
        double hx = p.sun[0], hz = p.sun[2];
        double hl = Math.sqrt(hx * hx + hz * hz);
        if (hl < 1e-3) {
            hx = 1;
            hz = 0;
            hl = 1;
        }
        hx /= hl;
        hz /= hl;
        double radius = s.tier == 0 ? 1.25 : 3.6;
        double elong = Math.min(2.6, 1.0 / Math.max(0.38, p.sun[1]));
        double lift = 0.04 + 0.0007 * d;
        double bright = p.strength * level * s.presence * fade * (0.5 + 0.5 * Math.min(1.0, s.weight * 2.5)) * 1.25;
        double ax = hx * radius * elong, az = hz * radius * elong; // along the sun's horizontal direction
        double bx = -hz * radius, bz = hx * radius;                  // across it
        double[] rect = BeamAtlas.PATCH;
        int idx = vertexCount * FLOATS_PER_VERTEX;
        idx = put(t, rx - ax - bx, ry + lift, rz - az - bz, rect[0], rect[1], bright, p, idx);
        idx = put(t, rx - ax + bx, ry + lift, rz - az + bz, rect[0], rect[3], bright, p, idx);
        idx = put(t, rx + ax + bx, ry + lift, rz + az + bz, rect[2], rect[3], bright, p, idx);
        idx = put(t, rx + ax - bx, ry + lift, rz + az - bz, rect[2], rect[1], bright, p, idx);
        patches++;
    }

    private void emitDust(BeamDust dust, double camX, double camY, double camZ, float[] view, float[] t, Params p) {
        double rightX = view[0], rightY = view[4], rightZ = view[8];
        double upX = view[1], upY = view[5], upZ = view[9];
        double[] rect = BeamAtlas.DOT;
        int idx = vertexCount * FLOATS_PER_VERTEX;
        for (int i = 0; i < BeamDust.COUNT; i++) {
            if (!dust.alive(i) || dust.alpha[i] < 0.03f) continue;
            double x = dust.x[i] - camX, y = dust.y[i] - camY, z = dust.z[i] - camZ;
            double d = Math.sqrt(x * x + y * y + z * z);
            double fade = smooth(0.8, 2.5, d) * (1.0 - smooth(BeamDust.RADIUS * 0.6, BeamDust.RADIUS, d));
            if (fade <= 0.0) continue;
            double sz = 0.045 + 0.03 * (dust.phase[i] % 1.0);
            double lv = p.strength * 2.2 * dust.alpha[i] * dust.twinkle(i, p.time) * fade;
            idx = put(t, x - rightX * sz - upX * sz, y - rightY * sz - upY * sz, z - rightZ * sz - upZ * sz, rect[0], rect[3], lv, p, idx);
            idx = put(t, x - rightX * sz + upX * sz, y - rightY * sz + upY * sz, z - rightZ * sz + upZ * sz, rect[0], rect[1], lv, p, idx);
            idx = put(t, x + rightX * sz + upX * sz, y + rightY * sz + upY * sz, z + rightZ * sz + upZ * sz, rect[2], rect[1], lv, p, idx);
            idx = put(t, x + rightX * sz - upX * sz, y + rightY * sz - upY * sz, z + rightZ * sz - upZ * sz, rect[2], rect[3], lv, p, idx);
            dustDrawn++;
        }
    }
}
