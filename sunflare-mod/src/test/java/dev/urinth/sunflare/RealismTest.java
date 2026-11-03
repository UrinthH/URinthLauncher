package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Real style: a camera-like sun made of glows and faint round reflections, tinted by the colour of the sun. */
class RealismTest {
    private static final double ASPECT = 1612 / 720.0;

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

    private static boolean has(FlareFrame f, String name) {
        for (int i = 0; i < f.count; i++) if (FlareSet.ELEMENTS[f.draws[i].element].name.equals(name)) return true;
        return false;
    }

    private static FlareFrame real(double lx, double ly, double power, double vis, double edge, double reach, double night, double rain, double height) {
        FlareFrame f = new FlareFrame();
        f.real = true;
        f.compute(lx, ly, ASPECT, power, vis, edge, reach, night, rain, height);
        return f;
    }

    @Test
    void theAtlasHoldsEveryElement() {
        assertEquals(FlareSet.COUNT, FlareAtlas.SINGLE_COUNT + FlareAtlas.PAIR_COUNT);
        assertTrue(FlareAtlas.SINGLE_COUNT <= FlareAtlas.COLS * FlareAtlas.ROWS);
        assertTrue(FlareSet.COUNT > FlareSet.PACK_COUNT);
    }

    @Test
    void hexGhostsOfThePackHaveCornersNotARoundOutline() {
        byte[] atlas = FlareAtlas.buildSingles();
        int e = index("4");
        double r = 0.88;
        assertTrue(texel(atlas, e, 0, -r) > texel(atlas, e, r, 0), "corner brighter than flat side at the same distance");
        assertEquals(texel(atlas, e, 0.866 * r, 0.5 * r), texel(atlas, e, 0, r), 3, "corners match");
    }

    @Test
    void realElementsHaveNoStarNoRingAndNoHexagon() {
        byte[] atlas = FlareAtlas.buildSingles();
        for (int i = FlareSet.PACK_COUNT; i < FlareSet.COUNT; i++) {
            FlareElement e = FlareSet.ELEMENTS[i];
            assertEquals(FlareElement.Shape.ROUND, e.shape, e.name);
            assertFalse(e.isPair(), e.name);
            assertTrue(e.role != FlareElement.Role.PACK, e.name);
            // brightest in the middle and never rising again towards the rim: a ring would break this
            double last = Double.MAX_VALUE;
            for (int k = 0; k <= 40; k++) {
                double v = FlareLut.lookup(e.main, k / 40.0);
                assertTrue(v <= last + 1e-9, e.name + " must not get brighter outwards at " + k);
                last = v;
            }
            // the same brightness all the way round: no spikes
            int a = texel(atlas, i, 0.3, 0.0), b = texel(atlas, i, 0.0, 0.3), c = texel(atlas, i, 0.212, 0.212);
            assertEquals(a, b, 6, e.name);
            assertEquals(a, c, 6, e.name);
        }
    }

    @Test
    void theColourIsTheSunsOrangeNotARainbow() {
        FlareFrame f = real(0.7, 0.62, 0.35, 1.0, 1.0, 1.0, 0.0, 0.0, 0.15);
        assertTrue(f.count > 5);
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            String name = FlareSet.ELEMENTS[d.element].name;
            if (name.equals("GP4")) continue; // the one faint cool reflection
            assertTrue(d.red >= d.green - 1e-9 && d.green >= d.blue - 1e-9, name + " is warm: " + d.red + "," + d.green + "," + d.blue);
        }
    }

    @Test
    void theFlareColourFollowsTheSunLikeTheBeamsDo() {
        FlareFrame low = real(0.7, 0.62, 0.35, 1.0, 1.0, 1.0, 0.0, 0.0, 0.08);
        FlareFrame high = real(0.7, 0.62, 0.35, 1.0, 1.0, 1.0, 0.0, 0.0, 0.9);
        int g3 = index("G3");
        double lowRatio = 0, highRatio = 0;
        for (int i = 0; i < low.count; i++) if (low.draws[i].element == g3) lowRatio = low.draws[i].blue / low.draws[i].red;
        for (int i = 0; i < high.count; i++) if (high.draws[i].element == g3) highRatio = high.draws[i].blue / high.draws[i].red;
        assertTrue(lowRatio > 0 && highRatio > 0);
        assertTrue(lowRatio < highRatio, "a low sun is more orange: " + lowRatio + " vs " + highRatio);
    }

    @Test
    void theGlowKeepsGoingWhenTheSunIsJustOffTheScreenButTheReflectionsDoNot() {
        FlareFrame f = real(1.15, 0.6, 0.35, 1.0, 0.0, SunMath.glowReach(1.15, 0.6), 0.0, 0.0, 0.4);
        assertTrue(has(f, "G3"), "the halo still reaches into the picture");
        assertTrue(has(f, "G2"));
        for (int i = 0; i < f.count; i++) {
            assertFalse(FlareSet.ELEMENTS[f.draws[i].element].name.startsWith("GP"), "no reflections without the sun on screen");
        }
        FlareFrame on = real(0.6, 0.6, 0.35, 1.0, 1.0, 1.0, 0.0, 0.0, 0.4);
        assertTrue(has(on, "GP1") && has(on, "GP3"));
    }

    @Test
    void aLowSunBehindThePlayerStillWarmsThePictureButANoonSunDoesNot() {
        FlareFrame sunset = real(0.5, 0.5, 0.35, 1.0, 0.0, 0.0, 0.0, 0.0, 0.05);
        assertTrue(has(sunset, "GV"), "the sunset veil");
        assertEquals(1, sunset.count, "and nothing else when the sun is out of view");
        FlareFrame noon = real(0.5, 0.5, 0.35, 1.0, 0.0, 0.0, 0.0, 0.0, 0.95);
        assertEquals(0, noon.count);
        // behind a hill nothing is seen at all
        assertEquals(0, real(0.5, 0.5, 0.35, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05).count);
    }

    @Test
    void theVeilIsFaint() {
        FlareFrame f = real(0.5, 0.5, 0.35, 1.0, 0.0, 0.0, 0.0, 0.0, 0.02);
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            assertTrue(Math.max(d.red, Math.max(d.green, d.blue)) < 0.10, "veil peak " + d.red);
        }
    }

    @Test
    void realStyleFollowsStrengthNightAndRain() {
        assertEquals(0, real(0.3, 0.7, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.3).count, "off draws nothing");
        assertEquals(0, real(0.3, 0.7, 0.35, 1.0, 1.0, 1.0, 1.0, 0.0, 0.3).count, "night draws nothing");
        assertEquals(0, real(0.3, 0.7, 0.35, 1.0, 1.0, 1.0, 0.0, 1.0, 0.3).count, "rain draws nothing");
        double strong = peak(real(0.3, 0.7, 0.5, 1.0, 1.0, 1.0, 0.0, 0.0, 0.3));
        double subtle = peak(real(0.3, 0.7, 0.2, 1.0, 1.0, 1.0, 0.0, 0.0, 0.3));
        assertTrue(strong > subtle);
    }

    private static double peak(FlareFrame f) {
        double p = 0;
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            p = Math.max(p, Math.max(d.red, Math.max(d.green, d.blue)));
        }
        return p;
    }

    @Test
    void reflectionsAreFaintAndTheCoreIsTheBrightestPart() {
        FlareFrame f = real(0.3, 0.7, 0.35, 1.0, 1.0, 1.0, 0.0, 0.0, 0.3);
        double core = 0, ghost = 0;
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            FlareElement e = FlareSet.ELEMENTS[d.element];
            double top = Math.max(d.red, Math.max(d.green, d.blue));
            if (e.name.equals("G1")) core = top;
            if (e.role == FlareElement.Role.GHOST) ghost = Math.max(ghost, top);
        }
        assertTrue(core > 0.5, "the core is hot: " + core);
        assertTrue(ghost > 0.04 && ghost < 0.35, "the reflections are faint but there: " + ghost);
    }

    @Test
    void realStyleDrawsFewerPixelsThanThePackAtTheSameStrength() {
        FlareFrame pack = new FlareFrame();
        pack.compute(0.7, 0.62, ASPECT, 0.35, 1.0, 0.0, 0.0);
        FlareFrame real = real(0.7, 0.62, 0.35, 1.0, 1.0, 1.0, 0.0, 0.0, 0.3);
        assertTrue(area(real) <= area(pack) * 1.1, "real " + area(real) + " vs pack " + area(pack));
    }

    private static double area(FlareFrame f) {
        double a = 0;
        for (int i = 0; i < f.count; i++) {
            FlareFrame.Draw d = f.draws[i];
            a += 4 * d.rx * d.ry * d.support * d.support;
        }
        return a;
    }

    @Test
    void styleIsSavedAndDefaultsToReal() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sunflare-style");
        FlareConfig c = new FlareConfig(dir.resolve("c.properties"));
        assertTrue(c.realStyle());
        assertFalse(c.toggleStyle());
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("c.properties"));
        again.load();
        assertFalse(again.realStyle());
        assertTrue(again.toggleStyle());
    }
}
