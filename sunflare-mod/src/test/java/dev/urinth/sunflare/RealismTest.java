package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The extras that go beyond the pack: aperture-shaped ghosts, diffraction spikes, a hot core and the rainbow halo. */
class RealismTest {
    private static int index(String name) {
        for (int i = 0; i < FlareSet.COUNT; i++) if (FlareSet.ELEMENTS[i].name.equals(name)) return i;
        throw new AssertionError("no element " + name);
    }

    private static int texel(byte[] atlas, int element, double dx, double dy) {
        int slot = FlareAtlas.slot(element);
        int x = (slot % FlareAtlas.COLS) * FlareAtlas.CELL + (int) ((dx + 1) / 2 * FlareAtlas.CELL);
        int y = (slot / FlareAtlas.COLS) * FlareAtlas.CELL + (int) ((dy + 1) / 2 * FlareAtlas.CELL);
        return atlas[y * FlareAtlas.WIDTH + x] & 0xFF;
    }

    @Test
    void everyExtraIsInTheSetAndFitsTheAtlas() {
        for (String n : new String[] {"S", "C", "HR", "HG", "HB"}) assertTrue(index(n) >= 0);
        assertEquals(FlareSet.COUNT, FlareAtlas.SINGLE_COUNT + FlareAtlas.PAIR_COUNT);
        assertTrue(FlareAtlas.SINGLE_COUNT <= FlareAtlas.COLS * FlareAtlas.ROWS);
    }

    @Test
    void hexGhostsHaveCornersNotARoundOutline() {
        byte[] atlas = FlareAtlas.buildSingles();
        int e = index("4");
        // Along a corner direction (straight up) the hexagon reaches further than along a flat side direction (to the right).
        double r = 0.88;
        assertTrue(texel(atlas, e, 0, -r) > texel(atlas, e, r, 0), "corner brighter than flat side at the same distance");
        // and the outline is a hexagon: the hexagon's own corner (0.866, 0.5) and its edge midpoint (1, 0)*0.866 are both on it
        assertEquals(texel(atlas, e, 0.866 * r, 0.5 * r), texel(atlas, e, 0, r), 3, "corners match");
    }

    @Test
    void spikesAreBrighterAlongTheirAxesThanBetweenThem() {
        byte[] atlas = FlareAtlas.buildSingles();
        int e = index("S");
        double r = 0.35;
        int onAxis = texel(atlas, e, r, 0);                                    // 0 degrees
        int between = texel(atlas, e, r * Math.cos(Math.PI / 6), r * Math.sin(Math.PI / 6)); // 30 degrees, midway between spikes
        assertTrue(onAxis > 2 * between, "spike " + onAxis + " vs gap " + between);
    }

    @Test
    void haloRingsAreRedInsideBlueOutsideAndStayTheSameSizeAtScreenCentre() {
        FlareElement red = FlareSet.ELEMENTS[index("HR")];
        FlareElement blue = FlareSet.ELEMENTS[index("HB")];
        double aspect = 16 / 9.0;
        for (double scale : new double[] {1.0, 0.0, 0.5}) {
            assertTrue(red.mainGeo.radiusV(red.main.radius, scale) < blue.mainGeo.radiusV(blue.main.radius, scale));
            assertEquals(red.mainGeo.radiusV(red.main.radius, 1.0), red.mainGeo.radiusV(red.main.radius, scale), 1e-12);
            assertEquals(red.mainGeo.radiusU(red.main.radius, aspect, 1.0), red.mainGeo.radiusU(red.main.radius, aspect, scale), 1e-12);
        }
        // a ring: dark in the middle, bright near the edge
        assertEquals(0.0, FlareLut.lookup(red.main, 0.1), 1e-3);
        assertTrue(FlareLut.lookup(red.main, 0.85) > 0.0);
    }

    @Test
    void extrasAreFaintAndFollowTheStrengthSetting() {
        FlareFrame f = new FlareFrame();
        double aspect = 1612 / 720.0;
        f.compute(0.3, 0.7, aspect, 0.35, 1.0, 0.0, 0.0);
        double ring = 0.0;
        for (int i = 0; i < f.count; i++) {
            if (FlareSet.ELEMENTS[f.draws[i].element].name.startsWith("H")) {
                FlareFrame.Draw d = f.draws[i];
                ring = Math.max(ring, Math.max(d.red, Math.max(d.green, d.blue)));
            }
        }
        assertTrue(ring > 0.0 && ring < 0.10, "halo peak at 35% strength should be faint, was " + ring);
        f.compute(0.3, 0.7, aspect, 0.0, 1.0, 0.0, 0.0);
        assertEquals(0, f.count, "off draws nothing");
        f.compute(0.3, 0.7, aspect, 0.35, 1.0, 1.0, 0.0);
        assertEquals(0, f.count, "night draws nothing");
    }
}
