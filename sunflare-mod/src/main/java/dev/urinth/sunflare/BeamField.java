package dev.urinth.sunflare;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Finds where sunlight really passes through gaps in the blocks (leaves, branches, windows) and turns each gap into a light
 * shaft that lives in the world. Plain maths with the world hidden behind {@link Occluder}, so it is unit tested.
 *
 * Light travels along -sun. A flat grid of parallel rays (one per cell) is laid across the plane perpendicular to the sun
 * around the player. For each ray the distance to the first block is found, starting from the sun side. Where a ray goes
 * on much further than one of its neighbours, it slipped through a gap that the neighbour did not: the stretch between the
 * neighbour's block and its own end is a shaft of lit air.
 *
 * Rays are re-cast a few at a time every tick, never all at once, and positions are stored in world coordinates, so a shaft
 * stays where it is when the player moves.
 */
final class BeamField {
    interface Occluder {
        /** Distance from the start along the unit direction to the first block within maxLen, or a negative number if none. */
        double firstHit(double sx, double sy, double sz, double dx, double dy, double dz, double maxLen);
    }

    /** One lit stretch of air, top to bottom (world coordinates). */
    static final class Shaft {
        double ax, ay, az;
        double bx, by, bz;
        /** 0..1: longer and more tightly boxed in by shadow means stronger. */
        double weight;
        int id;
    }

    static final double CELL = 1.0;
    static final int HALF = 13;
    static final int SIDE = 2 * HALF + 1;
    /** Rays start this far from the player's plane towards the sun and run twice as far. */
    static final double REACH = 40.0;
    /** A neighbour must be blocked at least this much earlier for a ray to count as a gap. */
    static final double MIN_DEPTH = 3.0;
    static final double MAX_LEN = 26.0;
    static final double REBASE_COS = Math.cos(Math.toRadians(1.0));
    static final double W_STEP = 8.0;
    static final int MAX_SHAFTS = 700;

    private static final class Cell {
        double t;
        double w0;
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    private final double[] sun = new double[3];
    private final double[] u = new double[3];
    private final double[] v = new double[3];
    private boolean haveBasis;
    private int cursor;
    private long tick;
    private final Shaft[] pool = new Shaft[MAX_SHAFTS];
    private int count;

    BeamField() {
        for (int i = 0; i < pool.length; i++) pool[i] = new Shaft();
    }

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    /** Sets the sun direction. Small drifts are ignored so the cells stay the same rays; a big one starts again. */
    void setSun(double[] dir) {
        if (haveBasis && dir[0] * sun[0] + dir[1] * sun[1] + dir[2] * sun[2] > REBASE_COS) return;
        sun[0] = dir[0];
        sun[1] = dir[1];
        sun[2] = dir[2];
        // u = normalize(sun x up), v = sun x u
        double ux = sun[1] * 0.0 - sun[2] * 1.0;
        double uy = sun[2] * 0.0 - sun[0] * 0.0;
        double uz = sun[0] * 1.0 - sun[1] * 0.0;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-4) {
            ux = 1;
            uy = 0;
            uz = 0;
            ul = 1;
        }
        u[0] = ux / ul;
        u[1] = uy / ul;
        u[2] = uz / ul;
        v[0] = sun[1] * u[2] - sun[2] * u[1];
        v[1] = sun[2] * u[0] - sun[0] * u[2];
        v[2] = sun[0] * u[1] - sun[1] * u[0];
        cells.clear();
        count = 0;
        haveBasis = true;
    }

    void clear() {
        cells.clear();
        count = 0;
        haveBasis = false;
    }

    /** Re-casts up to {@code rays} rays around the camera. */
    void step(Occluder occ, double camX, double camY, double camZ, int rays) {
        if (!haveBasis) return;
        tick++;
        int ci = (int) Math.round((camX * u[0] + camY * u[1] + camZ * u[2]) / CELL);
        int cj = (int) Math.round((camX * v[0] + camY * v[1] + camZ * v[2]) / CELL);
        double w0 = Math.round((camX * sun[0] + camY * sun[1] + camZ * sun[2]) / W_STEP) * W_STEP;
        for (int n = 0; n < rays; n++) {
            int idx = cursor++ % (SIDE * SIDE);
            int i = ci - HALF + idx % SIDE;
            int j = cj - HALF + idx / SIDE;
            double px = u[0] * i * CELL + v[0] * j * CELL + sun[0] * (w0 + REACH);
            double py = u[1] * i * CELL + v[1] * j * CELL + sun[1] * (w0 + REACH);
            double pz = u[2] * i * CELL + v[2] * j * CELL + sun[2] * (w0 + REACH);
            double hit = occ.firstHit(px, py, pz, -sun[0], -sun[1], -sun[2], 2 * REACH);
            Cell c = cells.get(key(i, j));
            if (c == null) {
                c = new Cell();
                cells.put(key(i, j), c);
            }
            c.t = hit < 0 ? 2 * REACH + 1 : hit;
            c.w0 = w0;
        }
        if ((tick & 63) == 0) evict(ci, cj);
    }

    private void evict(int ci, int cj) {
        Iterator<Map.Entry<Long, Cell>> it = cells.entrySet().iterator();
        while (it.hasNext()) {
            long k = it.next().getKey();
            int i = (int) (k >> 32);
            int j = (int) k;
            if (Math.abs(i - ci) > HALF + 2 || Math.abs(j - cj) > HALF + 2) it.remove();
        }
    }

    /** Rebuilds the shaft list from the rays cast so far. */
    void extract(double camX, double camY, double camZ) {
        count = 0;
        if (!haveBasis) return;
        int ci = (int) Math.round((camX * u[0] + camY * u[1] + camZ * u[2]) / CELL);
        int cj = (int) Math.round((camX * v[0] + camY * v[1] + camZ * v[2]) / CELL);
        for (int j = cj - HALF; j <= cj + HALF; j++) {
            for (int i = ci - HALF; i <= ci + HALF; i++) {
                Cell c = cells.get(key(i, j));
                if (c == null || c.t < MIN_DEPTH) continue;
                double bottom = Math.min(c.t, 2 * REACH);
                double deepestBlocked = -1;
                int blocked = 0;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        if (di == 0 && dj == 0) continue;
                        Cell nb = cells.get(key(i + di, j + dj));
                        if (nb == null || nb.w0 != c.w0) continue;
                        if (nb.t < c.t - MIN_DEPTH) {
                            blocked++;
                            if (nb.t > deepestBlocked) deepestBlocked = nb.t;
                        }
                    }
                }
                if (blocked == 0) continue;
                double top = deepestBlocked + 0.3;
                bottom -= 0.3;
                double len = bottom - top;
                if (len < MIN_DEPTH - 1.0) continue;
                if (len > MAX_LEN) {
                    top = bottom - MAX_LEN;
                    len = MAX_LEN;
                }
                if (count >= MAX_SHAFTS) return;
                Shaft s = pool[count++];
                double bx = u[0] * i * CELL + v[0] * j * CELL + sun[0] * (c.w0 + REACH);
                double by = u[1] * i * CELL + v[1] * j * CELL + sun[1] * (c.w0 + REACH);
                double bz = u[2] * i * CELL + v[2] * j * CELL + sun[2] * (c.w0 + REACH);
                // the ray runs along -sun from (bx, by, bz)
                s.ax = bx - sun[0] * top;
                s.ay = by - sun[1] * top;
                s.az = bz - sun[2] * top;
                s.bx = bx - sun[0] * bottom;
                s.by = by - sun[1] * bottom;
                s.bz = bz - sun[2] * bottom;
                s.weight = (len / MAX_LEN) * (0.4 + 0.6 * blocked / 8.0);
                s.id = i * 73856093 ^ j * 19349663;
            }
        }
    }

    int count() {
        return count;
    }

    Shaft get(int i) {
        return pool[i];
    }

    int cellCount() {
        return cells.size();
    }
}
