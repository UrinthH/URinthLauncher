package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlareModelTest {
    private static final double ASPECT = 16.0 / 9;

    @Test
    void everyProfileIsBoundedAndZeroAtTheEdge() {
        for (FlareElement e : FlareSet.ELEMENTS) {
            check(e.main);
            if (e.sub != null) check(e.sub);
        }
    }

    private static void check(FlareProfile p) {
        for (int i = 0; i <= 100; i++) {
            double v = p.value(i / 100.0, 0.25);
            assertTrue(v >= 0.0 && Double.isFinite(v), "profile value " + v);
        }
        assertEquals(0.0, p.value(1.0, 0.25), 1e-12);
        assertTrue(FlareLut.peak(p) > 0.0);
    }

    /** One baked picture must serve every sunmask: value(rp, sm) = value(rp, 1) * sm^k. */
    @Test
    void sunmaskIsSeparableForEveryProfileThatUsesItInside() {
        for (FlareElement e : FlareSet.ELEMENTS) {
            for (FlareProfile p : new FlareProfile[] {e.main, e.sub}) {
                if (p == null || !p.smInside) continue;
                for (double sm : new double[] {0.05, 0.15, 0.25, 0.4}) {
                    for (int i = 0; i < 50; i++) {
                        double rp = i / 50.0;
                        double expected = p.value(rp, 1.0) * Math.pow(sm, p.smExponent());
                        assertEquals(expected, p.value(rp, sm), 1e-9 * Math.max(1.0, expected), e.name + " rp=" + rp);
                    }
                }
            }
        }
    }

    @Test
    void firstElementsSitOnTheSun() {
        FlareElement e = FlareSet.ELEMENTS[0];
        assertEquals(0.3, e.mainGeo.centerU(0.3), 1e-12);
        assertEquals(0.7, e.mainGeo.centerV(0.7), 1e-12);
    }

    @Test
    void ghostsMirrorThroughTheScreenCentre() {
        FlareElement.Geo g = FlareElement.Geo.ghost(1.0, 0.0);
        assertEquals(0.7, g.centerU(0.3), 1e-12);
        assertEquals(0.3, g.centerV(0.7), 1e-12);
    }

    @Test
    void theBigGlowIsAboutThePacksSize() {
        // flare1: radius 0.5 / (1.7 * aspect) across, 0.5 / 1.7 of the height up
        FlareFrame f = new FlareFrame();
        f.compute(0.5, 0.5, ASPECT, 0.25, 1.0, 0.0, 0.0);
        assertTrue(f.count > 10);
        FlareFrame.Draw d = f.draws[0];
        double fs = f.flareScale;
        assertEquals(0.5 / (ASPECT * 1.7 * fs), d.rx, 1e-12);
        assertEquals(0.5 / (1.7 * fs), d.ry, 1e-12);
    }

    @Test
    void nothingIsDrawnWithoutSunlight() {
        FlareFrame f = new FlareFrame();
        f.compute(0.5, 0.5, ASPECT, 0.25, 0.0, 0.0, 0.0);
        assertEquals(0, f.count);
        f.compute(0.5, 0.5, ASPECT, 0.25, 1.0, 1.0, 0.0);
        assertEquals(0, f.count);
        f.compute(0.5, 0.5, ASPECT, 0.25, 1.0, 0.0, 1.0);
        assertEquals(0, f.count);
        f.compute(0.5, 0.5, ASPECT, 0.0, 1.0, 0.0, 0.0);
        assertEquals(0, f.count);
    }

    @Test
    void edgeGlintsOnlyShowNearTheSides() {
        FlareFrame f = new FlareFrame();
        f.compute(0.5, 0.5, ASPECT, 0.25, 1.0, 0.0, 0.0);
        assertFalse(contains(f, "8"));
        assertFalse(contains(f, "9"));
        f.compute(0.93, 0.5, ASPECT, 0.25, 1.0, 0.0, 0.0);
        assertTrue(contains(f, "8"));
        assertTrue(contains(f, "9"));
    }

    private static boolean contains(FlareFrame f, String name) {
        for (int i = 0; i < f.count; i++) {
            if (FlareSet.ELEMENTS[f.draws[i].element].name.equals(name)) return true;
        }
        return false;
    }

    private static void assertFalse(boolean b) {
        org.junit.jupiter.api.Assertions.assertFalse(b);
    }

    @Test
    void centreSunDarkensTheSceneAHalfScreenAwayDoesNot() {
        FlareFrame f = new FlareFrame();
        f.compute(0.5, 0.5, ASPECT, 0.25, 1.0, 0.0, 0.0);
        assertEquals(0.25, f.dim, 1e-12);
        f.compute(1.0, 0.5, ASPECT, 0.25, 1.0, 0.0, 0.0);
        assertEquals(0.0, f.dim, 1e-12);
    }

    @Test
    void atlasHoldsEverythingAndSlotsDoNotClash() {
        assertEquals(FlareSet.COUNT, FlareAtlas.SINGLE_COUNT + FlareAtlas.PAIR_COUNT);
        boolean[] used = new boolean[FlareAtlas.COLS * FlareAtlas.ROWS];
        for (int i = 0; i < FlareSet.COUNT; i++) {
            int s = FlareAtlas.slot(i);
            if (s < 0) continue;
            assertTrue(!used[s]);
            used[s] = true;
        }
        byte[] atlas = FlareAtlas.buildSingles();
        assertEquals(FlareAtlas.WIDTH * FlareAtlas.HEIGHT, atlas.length);
        // the centre of element 0's cell is its brightest point
        int cx = (FlareAtlas.slot(0) % FlareAtlas.COLS) * FlareAtlas.CELL + FlareAtlas.CELL / 2;
        int cy = (FlareAtlas.slot(0) / FlareAtlas.COLS) * FlareAtlas.CELL + FlareAtlas.CELL / 2;
        assertTrue((atlas[cy * FlareAtlas.WIDTH + cx] & 0xFF) > 240);
    }

    @Test
    void crescentsAreBakedAndNeverNegative() {
        FlareFrame f = new FlareFrame();
        f.compute(0.3, 0.7, ASPECT, 0.25, 1.0, 0.0, 0.0);
        PairBaker baker = new PairBaker();
        byte[] out = new byte[FlareAtlas.PAIR_SIZE * FlareAtlas.PAIR_SIZE];
        int baked = 0;
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            if (!d.pair) continue;
            double max = baker.bake(FlareSet.ELEMENTS[d.element], d, 0.3, 0.7, ASPECT, f.sm, f.flareScale, out);
            assertTrue(max >= 0.0 && Double.isFinite(max));
            baked++;
        }
        assertTrue(baked > 0);
    }

    @Test
    void everyDrawnElementHasASensibleSupport() {
        FlareFrame f = new FlareFrame();
        f.compute(0.4, 0.6, ASPECT, 0.35, 1.0, 0.0, 0.0);
        assertTrue(f.count > 10);
        for (int i = 0; i < f.count; i++) {
            double s = f.draws[i].support;
            assertTrue(s > 0.0 && s <= 1.0, "support " + s);
        }
    }

    @Test
    void smallGlintsAreSplitFromTheBigGlows() {
        FlareFrame f = new FlareFrame();
        f.compute(0.4, 0.6, ASPECT, 0.35, 1.0, 0.0, 0.0);
        int small = 0;
        int big = 0;
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            double area = 4.0 * d.rx * d.ry * d.support * d.support;
            assertEquals(area < FlareFrame.SMALL_AREA, d.small);
            if (d.small) small++; else big++;
        }
        assertTrue(small > 0 && big > 0);
        // the big soft halo (element 1B) is never drawn directly to the screen
        for (int i = 0; i < f.count; i++) {
            if (FlareSet.ELEMENTS[f.draws[i].element].name.equals("1B")) assertTrue(!f.draws[i].small);
        }
    }

    @Test
    void coverageCoversTheSunAndEverySmallElement() {
        FlareFrame f = new FlareFrame();
        f.compute(0.8, 0.3, ASPECT, 0.35, 1.0, 0.0, 0.0);
        FlareCoverage c = new FlareCoverage();
        int tiles = c.compute(f);
        assertTrue(tiles > 0 && tiles <= FlareCoverage.GX * FlareCoverage.GY);
        // the tile holding the sun is on (screen y runs down, v runs up)
        int gx = (int) (0.8 * FlareCoverage.GX);
        int gy = (int) ((1.0 - 0.3) * FlareCoverage.GY);
        assertTrue(c.cells[gy * FlareCoverage.GX + gx]);
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            if (!d.small || d.cx < 0 || d.cx >= 1 || d.cy < 0 || d.cy >= 1) continue;
            int tx = (int) (d.cx * FlareCoverage.GX);
            int ty = (int) ((1.0 - d.cy) * FlareCoverage.GY);
            assertTrue(c.cells[ty * FlareCoverage.GX + tx], "small element " + FlareSet.ELEMENTS[d.element].name);
        }
    }

    @Test
    void nothingIsCoveredWithoutAFlare() {
        FlareFrame f = new FlareFrame();
        f.compute(0.5, 0.5, ASPECT, 0.35, 0.0, 0.0, 0.0);
        assertEquals(0, new FlareCoverage().compute(f));
    }
}
