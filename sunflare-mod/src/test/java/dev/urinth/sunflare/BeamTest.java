package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sun beams: where rays slip through gaps, and how the ribbons are chosen and built. No Minecraft classes needed. */
class BeamTest {
    private static final double[] SUN = {Math.sqrt(0.5), Math.sqrt(0.5), 0.0};

    /** Ground below y = 0. Optionally a leaf slab at 10 <= y < 12 with a 4 x 4 hole at the origin. */
    private static BeamField.Occluder world(boolean canopy) {
        return (sx, sy, sz, dx, dy, dz, maxLen) -> {
            for (double t = 0; t <= maxLen; t += 0.1) {
                double x = sx + dx * t, y = sy + dy * t, z = sz + dz * t;
                if (y < 0) return t;
                if (canopy && y >= 10 && y < 12 && !(Math.abs(x) <= 2 && Math.abs(z) <= 2)) return t;
            }
            return -1;
        };
    }

    private static BeamField castAround(BeamField.Occluder occ, double cx, double cy, double cz) {
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(occ, cx, cy, cz, BeamField.SIDE * BeamField.SIDE);
        f.extract(cx, cy, cz);
        return f;
    }

    @Test
    void openGroundHasNoShafts() {
        BeamField f = castAround(world(false), -12, 1.7, 0);
        assertEquals(0, f.count());
    }

    @Test
    void aGapInTheCanopyMakesShaftsThatLandWhereTheLightLands() {
        BeamField f = castAround(world(true), -12, 1.7, 0);
        assertTrue(f.count() > 0, "a hole in a roof must make shafts");
        for (int i = 0; i < f.count(); i++) {
            BeamField.Shaft s = f.get(i);
            // light leaves the hole (x near 0, y 10..12) going down and away from the sun: it lands about 11 blocks along -x
            assertTrue(s.az > -4 && s.az < 4 && s.bz > -4 && s.bz < 4, "inside the hole's width, z=" + s.az + "," + s.bz);
            assertTrue(s.ay > 8.5 && s.ay < 13.5, "starts at the canopy, y=" + s.ay);
            assertTrue(s.by > -0.5 && s.by < 2.0, "ends at the ground, y=" + s.by);
            assertTrue(s.bx < -4 && s.bx > -17, "lands down-sun of the hole, x=" + s.bx);
            // and it is parallel to the light
            double dx = s.ax - s.bx, dy = s.ay - s.by, dz = s.az - s.bz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(1.0, (dx * SUN[0] + dy * SUN[1] + dz * SUN[2]) / len, 1e-6);
            assertTrue(s.weight > 0 && s.weight <= 1.0);
        }
    }

    @Test
    void shaftsStayPutWhenThePlayerWalks() {
        BeamField.Occluder occ = world(true);
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(occ, -12, 1.7, 0, BeamField.SIDE * BeamField.SIDE);
        f.extract(-12, 1.7, 0);
        int before = f.count();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (int i = 0; i < f.count(); i++) {
            minX = Math.min(minX, f.get(i).bx);
            maxX = Math.max(maxX, f.get(i).bx);
        }
        // walk three blocks sideways and recast everything
        f.step(occ, -12, 1.7, 3, BeamField.SIDE * BeamField.SIDE);
        f.extract(-12, 1.7, 3);
        assertTrue(f.count() > 0 && f.count() >= before / 2);
        for (int i = 0; i < f.count(); i++) {
            assertTrue(f.get(i).bx > minX - 1.5 && f.get(i).bx < maxX + 1.5, "same patch of ground");
        }
    }

    @Test
    void aBigSunMoveStartsAgainButASmallOneKeepsTheCells() {
        BeamField f = new BeamField();
        f.setSun(SUN);
        f.step(world(true), -12, 1.7, 0, 100);
        int cells = f.cellCount();
        assertTrue(cells > 0);
        double[] nudge = {Math.cos(Math.toRadians(45.2)), Math.sin(Math.toRadians(45.2)), 0};
        f.setSun(nudge);
        assertEquals(cells, f.cellCount(), "0.2 degrees is not worth starting again");
        f.setSun(new double[] {0.0, 1.0, 0.0});
        assertEquals(0, f.cellCount(), "a different sun needs different rays");
    }

    private static void camera(float[] view, float[] proj, double fwdX, double fwdZ) {
        // looks horizontally along (fwdX, 0, fwdZ); right = forward x up
        double rx = -fwdZ, rz = fwdX;
        double[][] rows = {{rx, 0, rz}, {0, 1, 0}, {-fwdX, 0, -fwdZ}};
        java.util.Arrays.fill(view, 0f);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) view[c * 4 + r] = (float) rows[r][c];
        view[15] = 1f;
        double tn = Math.tan(Math.toRadians(35));
        java.util.Arrays.fill(proj, 0f);
        proj[0] = (float) (1 / (tn * 2));
        proj[5] = (float) (1 / tn);
    }

    private static BeamGeometry.Params params(double strength, double area) {
        BeamGeometry.Params p = new BeamGeometry.Params();
        p.strength = strength;
        p.areaFraction = area;
        p.red = 1;
        p.green = 0.9;
        p.blue = 0.7;
        p.screenW = 1000;
        p.screenH = 500;
        System.arraycopy(SUN, 0, p.sun, 0, 3);
        return p;
    }

    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    @Test
    void ribbonsAreBuiltWithinTheAreaBudgetAndBrightnessLimits() {
        BeamField f = castAround(world(true), -22, 1.7, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, 1, 0); // looks towards the sun and the shafts
        BeamGeometry g = new BeamGeometry();
        BeamGeometry.Params p = params(0.3, 0.4);
        int n = g.build(f, -22, 1.7, 0, view, proj, IDENTITY, p);
        assertTrue(g.selected > 0, "something to draw");
        assertEquals(g.selected * BeamGeometry.VERTS_PER_SHAFT, n);
        assertTrue(g.usedArea <= 0.4 * 1000 * 500 + 1e-6);
        for (int i = 0; i < n; i++) {
            int o = i * BeamGeometry.FLOATS_PER_VERTEX;
            for (int k = 0; k < 6; k++) assertFalse(Float.isNaN(g.vertices[o + k]) || Float.isInfinite(g.vertices[o + k]));
            for (int k = 3; k < 6; k++) assertTrue(g.vertices[o + k] >= 0f && g.vertices[o + k] <= 1f);
            assertEquals(1f, g.vertices[o + 6]);
        }
    }

    @Test
    void aBiggerBudgetNeverDrawsFewerShaftsAndZeroDrawsNone() {
        BeamField f = castAround(world(true), -22, 1.7, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, 1, 0);
        BeamGeometry g = new BeamGeometry();
        int none = g.build(f, -22, 1.7, 0, view, proj, IDENTITY, params(0.3, 0.0));
        assertEquals(0, none);
        g.build(f, -22, 1.7, 0, view, proj, IDENTITY, params(0.3, 0.05));
        int small = g.selected;
        g.build(f, -22, 1.7, 0, view, proj, IDENTITY, params(0.3, 0.8));
        assertTrue(g.selected >= small);
        assertTrue(g.selected <= BeamGeometry.MAX_SELECTED);
    }

    @Test
    void shaftsBehindTheCameraAreNotDrawn() {
        BeamField f = castAround(world(true), -22, 1.7, 0);
        float[] view = new float[16], proj = new float[16];
        camera(view, proj, -1, 0); // looks away from the shafts
        BeamGeometry g = new BeamGeometry();
        assertEquals(0, g.build(f, -22, 1.7, 0, view, proj, IDENTITY, params(0.3, 0.8)));
    }

    @Test
    void nothingRightInFrontOfTheLensAndNothingFarAway() {
        assertEquals(0.0, BeamGeometry.distanceFade(1.0), 1e-12);
        assertEquals(1.0, BeamGeometry.distanceFade(15.0), 1e-12);
        assertEquals(0.0, BeamGeometry.distanceFade(60.0), 1e-12);
    }

    @Test
    void beamLevelsCycleAndPersist() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sunflare-beams");
        FlareConfig c = new FlareConfig(dir.resolve("c.properties"));
        assertEquals(FlareConfig.BEAMS_DEFAULT, c.beams());
        assertEquals(3, c.cycleBeams());
        assertEquals(0, c.cycleBeams());
        assertEquals(0.0, c.beamStrength(), 1e-12);
        assertEquals(0.0, c.beamArea(), 1e-12);
        assertEquals(1, c.cycleBeams());
        assertTrue(c.beamRays() > 0);
        assertEquals(-1, FlareConfig.beamsFromName("max"));
        assertEquals(2, FlareConfig.beamsFromName(" MEDIUM "));
        c.setBeams(3);
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("c.properties"));
        again.load();
        assertEquals(3, again.beams());
        assertEquals(FlareConfig.FULL, again.mode());
    }
}
