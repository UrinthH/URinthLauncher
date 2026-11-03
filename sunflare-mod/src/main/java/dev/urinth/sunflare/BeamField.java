package dev.urinth.sunflare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Finds where sunlight really passes through gaps in the blocks (leaves, branches, windows, cliffs) and keeps a list of light
 * shafts that live in the world. Plain maths with the world hidden behind {@link Occluder}, so it is unit tested.
 *
 * Light travels along -sun. Parallel rays (one per cell of a flat grid laid across the plane perpendicular to the sun) are cast
 * from the sun side and stop at the first block. A ray that travels much further than a neighbour slipped through a gap the
 * neighbour did not: the stretch above its landing point is lit air, a shaft. Three grids of different cell size (1, 4 and 12
 * blocks) cover the area around the player out to 160 blocks, so far away shafts exist too, whatever the render distance
 * (rays simply find nothing where no chunk is loaded). Where the light is unobstructed, a slow world-fixed noise makes faint
 * "dust" shafts in the open air, strongest when the sun is low.
 *
 * Nothing here flickers by construction. A shaft has a stable identity (from where it is, not from which ray found it), fades
 * in and out over about a second, and glides to new positions instead of jumping. When the sun has moved a few degrees a new
 * set of rays is cast in the background and swapped in only when it is complete.
 */
final class BeamField {
    interface Occluder {
        /** Distance from the start along the unit direction to the first block within maxLen, or a negative number if none. */
        double firstHit(double sx, double sy, double sz, double dx, double dy, double dz, double maxLen);

        /** Whether the block face found by the last {@link #firstHit} faces up (light lands on a floor). */
        default boolean lastHitUp() {
            return false;
        }
    }

    /** One lit stretch of air. The light lands at (bx, by, bz) and the shaft rises from there towards the sun. */
    static final class Shaft {
        int id;
        int tier;
        /** True for the faint open-air dust shafts, false for shafts through a gap in blocks. */
        boolean haze;
        double bx, by, bz;
        /** Length of the clearly lit part above the landing point. */
        double len;
        /** The faint extension beyond it, which reaches up towards the sun. */
        double tail;
        /** Ribbon width in blocks. */
        double width;
        /** 0..1. */
        double weight;
        /** 0..1: fades in and out as the shaft appears and disappears. */
        double presence;
        /** The light lands on a floor, so it makes a bright patch there. */
        boolean pool;
        int seen;
    }

    static final int TIERS = 3;
    static final double[] CELL = {1.0, 4.0, 12.0};
    static final double[] REACH = {40.0, 64.0, 96.0};
    static final double[] MIN_DEPTH = {3.0, 8.0, 20.0};
    static final double[] MAX_LEN = {26.0, 50.0, 90.0};
    static final double[] TAIL = {42.0, 80.0, 120.0};
    static final double[] WIDTH = {2.6, 8.5, 24.0};
    /** Shaft identity is taken from where it lands, rounded to this many blocks. */
    static final double[] ID_STEP = {2.0, 6.0, 16.0};
    static final int[] TIER_CAP = {600, 300, 300};
    static final int HALF = 13;
    static final int SIDE = 2 * HALF + 1;
    static final double REBASE_COS = Math.cos(Math.toRadians(3.0));
    static final double W_STEP = 8.0;
    static final int MAX_SHAFTS = 1200;
    /** The tier of each successive ray: the near grid gets half of them, the others a quarter each. */
    private static final int[] PATTERN = {0, 1, 0, 2};

    private static final class Cell {
        /** Where the ray stopped, measured along the sun direction from the world origin (bigger = closer to the sun). */
        double s;
        boolean open;
        boolean up;
    }

    /** A complete set of rays for one sun direction. */
    private static final class Gen {
        final double[] sun = new double[3];
        final double[] u = new double[3];
        final double[] v = new double[3];
        @SuppressWarnings("unchecked")
        final Map<Long, Cell>[] cells = new Map[TIERS];
        final int[] cursor = new int[TIERS];
        final int[] wrapped = new int[TIERS];

        Gen(double[] dir) {
            for (int t = 0; t < TIERS; t++) cells[t] = new HashMap<>();
            System.arraycopy(dir, 0, sun, 0, 3);
            // u = normalize(sun x up), v = sun x u
            double ux = -sun[2];
            double uz = sun[0];
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1e-4) {
                ux = 1;
                uz = 0;
                ul = 1;
            }
            u[0] = ux / ul;
            u[1] = 0;
            u[2] = uz / ul;
            v[0] = sun[1] * u[2] - sun[2] * u[1];
            v[1] = sun[2] * u[0] - sun[0] * u[2];
            v[2] = sun[0] * u[1] - sun[1] * u[0];
        }

        boolean complete() {
            for (int t = 0; t < TIERS; t++) if (wrapped[t] < 1) return false;
            return true;
        }
    }

    private Gen cur;
    private Gen next;
    private int patternCursor;
    private long tick;
    private int extractNo;
    private final Map<Integer, Shaft> live = new HashMap<>();
    private Shaft[] list = new Shaft[0];
    private int count;

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    /** Tells the field where the sun is now. A drift of a few degrees starts a new set of rays in the background. */
    void setSun(double[] dir) {
        if (cur == null) {
            cur = new Gen(dir);
            return;
        }
        double c = dot(cur.sun, dir);
        if (next == null) {
            if (c < REBASE_COS) next = new Gen(dir);
        } else if (dot(next.sun, dir) < REBASE_COS) {
            next = new Gen(dir);
        }
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    void clear() {
        cur = null;
        next = null;
        live.clear();
        list = new Shaft[0];
        count = 0;
    }

    boolean ready() {
        return cur != null;
    }

    /** Re-casts up to {@code rays} rays around the camera. */
    void step(Occluder occ, double camX, double camY, double camZ, int rays) {
        Gen g = next != null ? next : cur;
        if (g == null) return;
        tick++;
        for (int n = 0; n < rays; n++) {
            int tier = PATTERN[patternCursor++ & 3];
            double cell = CELL[tier];
            int ci = (int) Math.round((camX * g.u[0] + camY * g.u[1] + camZ * g.u[2]) / cell);
            int cj = (int) Math.round((camX * g.v[0] + camY * g.v[1] + camZ * g.v[2]) / cell);
            int idx = g.cursor[tier]++;
            if (idx >= SIDE * SIDE) {
                g.cursor[tier] = 1;
                g.wrapped[tier]++;
                idx = 0;
            }
            int i = ci - HALF + idx % SIDE;
            int j = cj - HALF + idx / SIDE;
            double reach = REACH[tier];
            double w0 = Math.round((camX * g.sun[0] + camY * g.sun[1] + camZ * g.sun[2]) / W_STEP) * W_STEP;
            double px = g.u[0] * i * cell + g.v[0] * j * cell + g.sun[0] * (w0 + reach);
            double py = g.u[1] * i * cell + g.v[1] * j * cell + g.sun[1] * (w0 + reach);
            double pz = g.u[2] * i * cell + g.v[2] * j * cell + g.sun[2] * (w0 + reach);
            double hit = occ.firstHit(px, py, pz, -g.sun[0], -g.sun[1], -g.sun[2], 2 * reach);
            Cell c = g.cells[tier].get(key(i, j));
            if (c == null) {
                c = new Cell();
                g.cells[tier].put(key(i, j), c);
            }
            c.open = hit < 0;
            c.s = c.open ? w0 - reach - 1.0 : (w0 + reach) - hit;
            c.up = !c.open && occ.lastHitUp();
        }
        if (next != null && next.complete()) {
            cur = next;
            next = null;
        }
        if ((tick & 63) == 0) evict(camX, camY, camZ);
    }

    private void evict(double camX, double camY, double camZ) {
        for (Gen g : new Gen[] {cur, next}) {
            if (g == null) continue;
            for (int t = 0; t < TIERS; t++) {
                int ci = (int) Math.round((camX * g.u[0] + camY * g.u[1] + camZ * g.u[2]) / CELL[t]);
                int cj = (int) Math.round((camX * g.v[0] + camY * g.v[1] + camZ * g.v[2]) / CELL[t]);
                Iterator<Map.Entry<Long, Cell>> it = g.cells[t].entrySet().iterator();
                while (it.hasNext()) {
                    long k = it.next().getKey();
                    if (Math.abs((int) (k >> 32) - ci) > HALF + 2 || Math.abs((int) k - cj) > HALF + 2) it.remove();
                }
            }
        }
    }

    // ---- turning rays into shafts --------------------------------------------------------------------------------------

    private static double hash01(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    private static double valueNoise(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash01(xi, yi) * (1 - fx) + hash01(xi + 1, yi) * fx;
        double b = hash01(xi, yi + 1) * (1 - fx) + hash01(xi + 1, yi + 1) * fx;
        return a * (1 - fy) + b * fy;
    }

    /** Slow, world-fixed 0..1 pattern that decides where the open air shows dust shafts. */
    static double hazeNoise(double worldX, double worldZ) {
        return 0.62 * valueNoise(worldX / 70.0, worldZ / 70.0) + 0.38 * valueNoise(worldX / 26.0 + 11.0, worldZ / 26.0 + 5.0);
    }

    private static int idOf(int tier, double x, double y, double z) {
        double q = ID_STEP[tier];
        int h = tier;
        h = h * 31 + (int) Math.round(x / q);
        h = h * 31 + (int) Math.round(y / q);
        h = h * 31 + (int) Math.round(z / q);
        return h;
    }

    /** Rebuilds the shaft list from the rays cast so far. Call a few times a second. */
    void extract(double camX, double camY, double camZ) {
        extractNo++;
        Gen g = cur;
        if (g != null) {
            for (int tier = 0; tier < TIERS; tier++) extractTier(g, tier, camX, camY, camZ);
        }
        Iterator<Map.Entry<Integer, Shaft>> it = live.entrySet().iterator();
        while (it.hasNext()) {
            Shaft s = it.next().getValue();
            if (s.seen != extractNo) {
                s.presence -= 0.18;
                if (s.presence <= 0.0) it.remove();
            }
        }
        List<Shaft> out = new ArrayList<>(live.size());
        for (Shaft s : live.values()) {
            if (s.presence > 0.0 && out.size() < MAX_SHAFTS) out.add(s);
        }
        list = out.toArray(new Shaft[0]);
        count = list.length;
    }

    private void extractTier(Gen g, int tier, double camX, double camY, double camZ) {
        double cell = CELL[tier];
        Map<Long, Cell> cells = g.cells[tier];
        int ci = (int) Math.round((camX * g.u[0] + camY * g.u[1] + camZ * g.u[2]) / cell);
        int cj = (int) Math.round((camX * g.v[0] + camY * g.v[1] + camZ * g.v[2]) / cell);
        int made = 0;
        for (int j = cj - HALF; j <= cj + HALF && made < TIER_CAP[tier]; j++) {
            for (int i = ci - HALF; i <= ci + HALF && made < TIER_CAP[tier]; i++) {
                Cell c = cells.get(key(i, j));
                if (c == null) continue;
                double deepest = -1e18;
                int blocked = 0;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        if (di == 0 && dj == 0) continue;
                        Cell nb = cells.get(key(i + di, j + dj));
                        if (nb == null || nb.open) continue;
                        if (nb.s > c.s + MIN_DEPTH[tier]) {
                            blocked++;
                            if (nb.s > deepest) deepest = nb.s;
                        }
                    }
                }
                boolean haze = false;
                double len;
                double weight;
                double sBottom = c.s + 0.3;
                if (c.open) {
                    // The ray found nothing (open sea, a drop, or chunks the client has not loaded because the render distance
                    // is small). Do not let that cut the beams: dust shafts still stand in that air, on roughly the player's
                    // ground level, so they show out to the full range whatever the render distance is.
                    if (g.sun[1] < 0.12 || blocked > 0) continue;
                    sBottom = (camY - 1.6 - (g.u[1] * i * cell + g.v[1] * j * cell)) / g.sun[1];
                }
                double lx = g.u[0] * i * cell + g.v[0] * j * cell;
                double lz = g.u[2] * i * cell + g.v[2] * j * cell;
                if (blocked > 0) {
                    len = deepest - sBottom;
                    if (len < MIN_DEPTH[tier] - 1.0) continue;
                    if (len > MAX_LEN[tier]) len = MAX_LEN[tier];
                    weight = Math.min(1.0, len / MAX_LEN[tier]) * (0.4 + 0.6 * blocked / 8.0);
                } else if (tier >= 1) {
                    double bx = lx + g.sun[0] * sBottom;
                    double bz = lz + g.sun[2] * sBottom;
                    double n = hazeNoise(bx, bz);
                    if (n <= 0.55) continue;
                    haze = true;
                    len = MAX_LEN[tier] * 0.8;
                    weight = Math.pow((n - 0.55) / 0.45, 1.5) * 0.6;
                } else {
                    continue;
                }
                double bx = lx + g.sun[0] * sBottom;
                double by = g.u[1] * i * cell + g.v[1] * j * cell + g.sun[1] * sBottom;
                double bz = lz + g.sun[2] * sBottom;
                int id = idOf(tier, bx + g.sun[0] * len, by + g.sun[1] * len, bz + g.sun[2] * len);
                Shaft s = live.get(id);
                boolean fresh = s == null;
                if (fresh) {
                    s = new Shaft();
                    s.id = id;
                    s.bx = bx;
                    s.by = by;
                    s.bz = bz;
                    s.len = len;
                    s.weight = weight;
                    live.put(id, s);
                } else {
                    // glide to the new numbers instead of jumping
                    s.bx += (bx - s.bx) * 0.35;
                    s.by += (by - s.by) * 0.35;
                    s.bz += (bz - s.bz) * 0.35;
                    s.len += (len - s.len) * 0.35;
                    s.weight += (weight - s.weight) * 0.35;
                }
                s.tier = tier;
                s.haze = haze;
                s.tail = TAIL[tier];
                s.width = WIDTH[tier];
                s.pool = !haze && !c.open && c.up && tier <= 1;
                s.presence = Math.min(1.0, s.presence + 0.12);
                s.seen = extractNo;
                made++;
            }
        }
    }

    /**
     * Is this point in direct sunlight, as far as the rays cast so far can tell? 1 = yes, 0 = in shade, -1 = no ray there yet.
     * Used to keep floating dust inside the sunlit air only.
     */
    int lit(double x, double y, double z) {
        Gen g = cur;
        if (g == null) return -1;
        int i = (int) Math.round((x * g.u[0] + y * g.u[1] + z * g.u[2]) / CELL[0]);
        int j = (int) Math.round((x * g.v[0] + y * g.v[1] + z * g.v[2]) / CELL[0]);
        Cell c = g.cells[0].get(key(i, j));
        if (c == null) return -1;
        if (c.open) return 1;
        double s = x * g.sun[0] + y * g.sun[1] + z * g.sun[2];
        return s > c.s + 0.05 ? 1 : 0;
    }

    int count() {
        return count;
    }

    Shaft get(int i) {
        return list[i];
    }

    /** Rays cast so far in the set being served (tests). */
    int cellCount() {
        int n = 0;
        if (cur != null) for (int t = 0; t < TIERS; t++) n += cur.cells[t].size();
        return n;
    }

    boolean rebuilding() {
        return next != null;
    }
}
