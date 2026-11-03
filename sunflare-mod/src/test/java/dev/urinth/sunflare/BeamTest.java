package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The sun beams: where rays slip through gaps, how shafts keep their identity, and how the quads are built. No Minecraft classes. */
class BeamTest {
    private static final double[] SUN = {Math.sqrt(0.5), Math.sqrt(0.5), 0.0};
    private static final int ALL = BeamField.SIDE * BeamField.SIDE * 4;

    /** Ground below y = 0 (unless sky only). Optionally a leaf slab at 10 <= y < 12 with a 4 x 4 hole at the origin. */
    private static BeamField.Occluder world(boolean canopy, boolean ground) {
        return new BeamField.Occluder() {
            boolean up;

            @Override
            public double firstHit(double sx, double sy, double sz, double dx, double dy, double dz, double maxLen) {
                for (double t = 0; t <= maxLen; t += 0.1) {
                    double x = sx + dx * t, y = sy + dy * t, z = sz + dz * t;
                    if (ground && y < 0) {
                        up = true;
                        return t;
                    }
                    if (canopy && y >= 10 && y < 12 && !(Math.abs(x) <= 2 && Math.abs(z) <= 2)) {
                        up = false;
                        return t;
                    }
                }
                up = false;
                return -1;
            }

            @Override
            public boolean lastHitUp() {
                return up;
            }
        };
    }

    private static BeamField cast(BeamField.Occluder occ, double cx, double cy, double cz) {
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(occ, cx, cy, cz, ALL);
        for (int i = 0; i < 12; i++) f.extract(cx, cy, cz);
        return f;
    }

    private static int gapShafts(BeamField f) {
        int n = 0;
        for (int i = 0; i < f.count(); i++) if (!f.get(i).haze) n++;
        return n;
    }

    @Test
    void openGroundHasNoGapShafts() {
        BeamField f = cast(world(false, true), -12, 1.7, 0);
        assertEquals(0, gapShafts(f));
    }

    @Test
    void aGapInTheCanopyMakesShaftsThatLandWhereTheLightLands() {
        BeamField f = cast(world(true, true), -12, 1.7, 0);
        assertTrue(gapShafts(f) > 0, "a hole in a roof must make shafts");
        for (int i = 0; i < f.count(); i++) {
            BeamField.Shaft s = f.get(i);
            if (s.haze || s.tier != 0) continue;
            assertTrue(s.bz > -4 && s.bz < 4, "inside the hole's width, z=" + s.bz);
            assertTrue(s.by > -0.5 && s.by < 1.0, "lands on the ground, y=" + s.by);
            assertTrue(s.bx < -4 && s.bx > -17, "lands down-sun of the hole, x=" + s.bx);
            assertTrue(s.weight > 0 && s.weight <= 1.0);
            assertTrue(s.tail > s.len * 0.5, "a long tail carries the shaft on towards the sun");
        }
    }

    @Test
    void shaftsKeepTheirIdentityWhenThePlayerWalksSoNothingFlickers() {
        BeamField.Occluder occ = world(true, true);
        BeamField f = cast(occ, -12, 1.7, 0);
        Map<Integer, Double> before = new HashMap<>();
        for (int i = 0; i < f.count(); i++) if (!f.get(i).haze) before.put(f.get(i).id, f.get(i).presence);
        assertTrue(before.size() > 3);
        // walk 3 blocks sideways, recast everything, refresh
        f.step(occ, -12, 1.7, 3, ALL);
        f.extract(-12, 1.7, 3);
        int kept = 0;
        for (int i = 0; i < f.count(); i++) {
            BeamField.Shaft s = f.get(i);
            if (before.containsKey(s.id)) {
                kept++;
                assertTrue(s.presence >= 0.8, "one refresh may dim a shaft by 0.18 at most, never pop it out: " + s.presence);
            }
        }
        assertTrue(kept >= before.size() * 0.8, "kept " + kept + " of " + before.size());
    }

    @Test
    void aShaftFadesOutOverSeveralRefreshesInsteadOfVanishing() {
        BeamField.Occluder occ = world(true, true);
        BeamField f = cast(occ, -12, 1.7, 0);
        int n0 = f.count();
        assertTrue(n0 > 0);
        // jump far away: the old shafts are no longer seen
        f.step(occ, 400, 1.7, 0, ALL);
        f.extract(400, 1.7, 0);
        assertTrue(f.count() >= n0 / 2, "first refresh after losing them must keep most of them (they fade)");
        double sum = 0;
        for (int i = 0; i < f.count(); i++) sum += f.get(i).presence;
        assertTrue(sum / f.count() < 1.0);
        for (int i = 0; i < 12; i++) f.extract(400, 1.7, 0);
        assertEquals(0, gapShafts(f), "after enough refreshes the shafts of the place we left are gone");
    }

    @Test
    void aSmallSunMoveKeepsTheRaysAndABigOneRebuildsInTheBackground() {
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(world(true, true), -12, 1.7, 0, 100);
        int cells = f.cellCount();
        assertTrue(cells > 0);
        double a = Math.toRadians(45.2);
        f.setSun(new double[] {Math.cos(a), Math.sin(a), 0});
        assertFalse(f.rebuilding(), "0.2 degrees is not worth starting again");
        assertEquals(cells, f.cellCount());
        f.setSun(new double[] {0.0, 1.0, 0.0});
        assertTrue(f.rebuilding(), "a different sun needs different rays");
        assertEquals(cells, f.cellCount(), "while the new rays are cast, the old ones keep being served");
    }

    @Test
    void openSkyStillGetsDustShaftsSoSmallRenderDistancesDoNotCutThemOff() {
        // no ground at all: this is what a client with render distance 4 sees past its loaded chunks
        BeamField f = cast(world(false, false), 0, 70, 0);
        int haze = 0;
        for (int i = 0; i < f.count(); i++) {
            BeamField.Shaft s = f.get(i);
            if (!s.haze) continue;
            haze++;
            assertEquals(70 - 1.6, s.by, 0.5, "they stand at about the player's ground level");
            assertTrue(s.tier >= 1);
        }
        assertTrue(haze > 0, "dust shafts exist in open air");
        // and they reach far: at least one is beyond 60 blocks
        boolean far = false;
        for (int i = 0; i < f.count(); i++) {
            BeamField.Shaft s = f.get(i);
            if (Math.hypot(s.bx, s.bz) > 60) far = true;
        }
        assertTrue(far, "the far grid reaches past 60 blocks");
    }

    @Test
    void litTellsSunFromShade() {
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(world(true, true), -12, 1.7, 0, ALL);
        // far from the hole, under the canopy slab: shade
        assertEquals(0, f.lit(-12, 1.0, 9));
        // above the canopy: sun
        assertEquals(1, f.lit(-12, 14.0, 9));
        // far outside the cast area: unknown
        assertEquals(-1, f.lit(300, 1, 300));
    }

    // ---- geometry ---------------------------------------------------------------------------------------------------

    private static void camera(float[] view, float[] proj, double fwdX, double fwdZ) {
        double rx = -fwdZ, rz = fwdX;
        double[][] rows = {{rx, 0, rz}, {0, 1, 0}, {-fwdX, 0, -fwdZ}};
        java.util.Arrays.fill(view, 0f);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) view[c * 4 + r] = (float) rows[r][c];
        view[15] = 1f;
        double tn = Math.tan(Math.toRadians(35));
        java.util.Arrays.fill(proj, 0f);
        proj[0] = (float) (1 / (tn * 2));
        proj[5] = (float) (1 / tn);
        proj[10] = -1f;
        proj[11] = -1f;
    }

    private static BeamGeometry.Params params(double strength, double area) {
        BeamGeometry.Params p = new BeamGeometry.Params();
        p.strength = strength;
        p.areaFraction = area;
        p.red = 1;
        p.green = 0.8;
        p.blue = 0.5;
        p.screenW = 1000;
        p.screenH = 500;
        p.dt = 1.0;
        System.arraycopy(SUN, 0, p.sun, 0, 3);
        return p;
    }

    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    /** Builds several frames so the brightness easing has settled. */
    private static int settle(BeamGeometry g, BeamField f, BeamDust d, double cx, double cy, double cz, float[] v, float[] p, BeamGeometry.Params pr) {
        int n = 0;
        for (int i = 0; i < 12; i++) n = g.build(f, d, cx, cy, cz, v, p, IDENTITY, pr);
        return n;
    }

    @Test
    void quadsStayWithinTheAreaBudgetAndBrightnessLimits() {
        BeamField f = cast(world(true, true), -22, 1.7, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, 1, 0);
        BeamGeometry g = new BeamGeometry();
        BeamGeometry.Params p = params(0.3, 0.4);
        int n = settle(g, f, null, -22, 1.7, 0, view, proj, p);
        assertTrue(g.selected > 0, "something to draw");
        assertTrue(n > 0 && n % 4 == 0);
        assertTrue(g.usedArea <= 0.4 * 1000 * 500 + 1e-6);
        for (int i = 0; i < n; i++) {
            int o = i * BeamGeometry.FLOATS_PER_VERTEX;
            for (int k = 0; k < BeamGeometry.FLOATS_PER_VERTEX; k++) {
                assertFalse(Float.isNaN(g.vertices[o + k]) || Float.isInfinite(g.vertices[o + k]));
            }
            for (int k = 3; k < 5; k++) assertTrue(g.vertices[o + k] >= 0f && g.vertices[o + k] <= 1f, "uv");
            for (int k = 5; k < 8; k++) assertTrue(g.vertices[o + k] >= 0f && g.vertices[o + k] <= 1f, "colour");
        }
    }

    @Test
    void colourFollowsTheSunTintRedStrongestBlueWeakest() {
        BeamField f = cast(world(true, true), -22, 1.7, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, 1, 0);
        BeamGeometry g = new BeamGeometry();
        BeamGeometry.Params p = params(0.4, 0.5);
        double[] c = new double[3];
        SunTint.colour(0.2, c);
        p.red = c[0];
        p.green = c[1];
        p.blue = c[2];
        int n = settle(g, f, null, -22, 1.7, 0, view, proj, p);
        assertTrue(n > 0);
        for (int i = 0; i < n; i++) {
            int o = i * BeamGeometry.FLOATS_PER_VERTEX;
            assertTrue(g.vertices[o + 5] >= g.vertices[o + 6] && g.vertices[o + 6] >= g.vertices[o + 7], "orange: r >= g >= b");
        }
    }

    @Test
    void beamsDoNotNeedToBeLookedAt() {
        BeamField f = cast(world(true, true), -2, 1.7, 0);
        BeamGeometry g = new BeamGeometry();
        float[] view = new float[16], proj = new float[16];
        // the shafts are down-sun, to the -x side. Look away from them (+x), sideways, and straight at them.
        double[][] dirs = {{1, 0}, {0, 1}, {0, -1}, {-1, 0}};
        int[] drawn = new int[4];
        for (int k = 0; k < dirs.length; k++) {
            camera(view, proj, dirs[k][0], dirs[k][1]);
            g = new BeamGeometry();
            drawn[k] = settle(g, f, null, -2, 1.7, 0, view, proj, params(0.3, 0.5));
        }
        // anything in front of or beside the camera is drawn, whichever way it faces: the sideways views and the one facing the
        // shafts all have beams, and facing away still gets the far tails and dust shafts that rise past the camera
        assertTrue(drawn[1] > 0 && drawn[2] > 0 && drawn[3] > 0, "sideways and towards: " + java.util.Arrays.toString(drawn));
    }

    @Test
    void farShaftsAreDrawnOutToTheRangeNotTheRenderDistance() {
        BeamField f = cast(world(false, false), 0, 70, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, 1, 0);
        BeamGeometry.Params p = params(0.4, 0.6);
        BeamGeometry g = new BeamGeometry();
        p.range = 160;
        int far = settle(g, f, null, 0, 70, 0, view, proj, p);
        p.range = 40;
        g = new BeamGeometry();
        int near = settle(g, f, null, 0, 70, 0, view, proj, p);
        assertTrue(far >= near, "a longer range never draws less");
    }

    @Test
    void aBiggerBudgetNeverDrawsFewerShaftsAndZeroDrawsNone() {
        BeamField f = cast(world(true, true), -22, 1.7, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, 1, 0);
        BeamGeometry g = new BeamGeometry();
        assertEquals(0, settle(g, f, null, -22, 1.7, 0, view, proj, params(0.3, 0.0)));
        settle(g, f, null, -22, 1.7, 0, view, proj, params(0.3, 0.05));
        int small = g.selected;
        g = new BeamGeometry();
        settle(g, f, null, -22, 1.7, 0, view, proj, params(0.3, 0.6));
        assertTrue(g.selected >= small);
    }

    @Test
    void brightnessEasesInsteadOfPopping() {
        BeamGeometry.Levels l = new BeamGeometry.Levels();
        double dt = 1.0 / 60.0;
        float last = 0f;
        for (int i = 0; i < 60; i++) {
            l.frame++;
            float v = l.update(42, true, dt);
            assertTrue(v - last < 0.06f, "no jump upwards: " + (v - last));
            last = v;
        }
        assertTrue(last > 0.9f);
        for (int i = 0; i < 30; i++) {
            l.frame++;
            float v = l.update(42, false, dt);
            assertTrue(last - v < 0.05f, "no jump downwards");
            last = v;
        }
        assertTrue(last > 0.0f && last < 0.7f);
    }

    @Test
    void distanceFadeIsZeroAtTheLensAndAtTheRangeAndSmoothBetween() {
        assertEquals(0.0, BeamGeometry.distanceFade(0.2, 2, 160), 1e-9);
        assertEquals(0.0, BeamGeometry.distanceFade(160, 2, 160), 1e-9);
        for (double d = 1; d < 160; d += 3) {
            double v = BeamGeometry.distanceFade(d, 2, 160);
            assertTrue(v >= 0 && v <= 1.0);
        }
        assertTrue(BeamGeometry.distanceFade(60, 2, 160) > 0.95);
    }

    // ---- dust and the texture -----------------------------------------------------------------------------------------

    @Test
    void dustLivesOnlyInSunlitAirAndFadesInGently() {
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(world(true, true), -12, 1.7, 0, ALL);
        BeamDust d = new BeamDust();
        // standing in the shade under the canopy: no dust at all
        for (int i = 0; i < 200; i++) d.update(0.05, -12, 1.7, 9, f, i * 0.05);
        // there is some sunlit air within 14 blocks of that spot too (the hole), so allow a few, but they must really be lit
        for (int i = 0; i < BeamDust.COUNT; i++) {
            if (d.alive(i) && d.alpha[i] > 0.5f) assertEquals(1, f.lit(d.x[i], d.y[i], d.z[i]));
        }
        BeamField open = cast(world(false, true), 0, 1.7, 0);
        BeamDust d2 = new BeamDust();
        for (int i = 0; i < 200; i++) d2.update(0.05, 0, 1.7, 0, open, i * 0.05);
        assertTrue(d2.aliveCount() > 20, "dust in open sunlight: " + d2.aliveCount());
        for (int i = 0; i < BeamDust.COUNT; i++) {
            if (!d2.alive(i)) continue;
            double dx = d2.x[i], dy = d2.y[i] - 1.7, dz = d2.z[i];
            assertTrue(dx * dx + dy * dy + dz * dz <= BeamDust.RADIUS * BeamDust.RADIUS + 1e-6);
            double t = d2.twinkle(i, 3.3);
            assertTrue(t >= 0.34 && t <= 1.0);
        }
    }

    @Test
    void atlasShapesAreZeroOnTheirEdgesSoNothingBleeds() {
        byte[] px = BeamAtlas.build();
        assertEquals(BeamAtlas.WIDTH * BeamAtlas.HEIGHT, px.length);
        for (int y = 0; y < 64; y++) {
            assertTrue((px[y * BeamAtlas.WIDTH] & 0xFF) < 3, "ribbon left edge row " + y);
        }
        assertTrue((px[16 * BeamAtlas.WIDTH + 64] & 0xFF) < 3, "dust dot left edge");
        assertTrue((px[16 * BeamAtlas.WIDTH + 96] & 0xFF) < 12, "patch left edge");
        // the middle of the lit part is bright, the middle of the dust dot is bright
        assertTrue((px[40 * BeamAtlas.WIDTH + 32] & 0xFF) > 200);
        assertTrue((px[16 * BeamAtlas.WIDTH + 64 + 16] & 0xFF) > 200);
        // a ribbon is a bell across: centre brighter than the quarter point
        assertTrue(BeamAtlas.across(0) > BeamAtlas.across(0.5));
        assertEquals(0.0, BeamAtlas.across(1.0), 1e-12);
        assertEquals(0.0, BeamAtlas.tail(0.0), 1e-12);
    }
}
