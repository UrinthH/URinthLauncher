package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The numbers behind the screen-space god rays, and the guard that keeps the frame rate. No Minecraft classes. */
class GodRayTest {
    @Test
    void passALayersFillOneBandAndTheirWeightsAddUpToOne() {
        for (int n : new int[] {4, 5, 7, 9, 12, 14}) {
            double[] scale = new double[n], weight = new double[n];
            GodRayMath.layers(n, scale, weight);
            double sum = 0;
            for (int k = 0; k < n; k++) {
                assertTrue(scale[k] > GodRayMath.BAND && scale[k] <= 1.0, "scale " + scale[k]);
                if (k > 0) {
                    assertTrue(scale[k] < scale[k - 1], "each layer reaches further");
                    assertTrue(weight[k] < weight[k - 1], "nearer samples count more");
                }
                sum += weight[k];
            }
            assertEquals(1.0, sum, 1e-9);
            assertEquals(1.0, scale[0], 1e-12, "the first layer is the picture itself");
            assertEquals(1.0 - (1.0 - GodRayMath.BAND) * (n - 1) / n, scale[n - 1], 1e-9);
        }
    }

    @Test
    void passBLayersAreOneBandFurtherOutEachAndTheirWeightsAddUpToOne() {
        double[] scale = new double[GodRayMath.BAND_LAYERS], weight = new double[GodRayMath.BAND_LAYERS];
        GodRayMath.bands(scale, weight);
        double sum = 0;
        for (int j = 0; j < GodRayMath.BAND_LAYERS; j++) {
            assertEquals(Math.pow(GodRayMath.BAND, j), scale[j], 1e-12);
            if (j > 0) assertTrue(weight[j] < weight[j - 1]);
            sum += weight[j];
        }
        assertEquals(1.0, sum, 1e-9);
        assertEquals(1.0, scale[0], 1e-12);
    }

    @Test
    void layerCountFollowsQualityWithinTheSettingsLimits() {
        assertEquals(12, GodRayMath.layerCount(12, 1.0));
        assertEquals(GodRayMath.MIN_LAYERS, GodRayMath.layerCount(12, 0.0));
        int last = 0;
        for (double q = 0.0; q <= 1.0; q += 0.1) {
            int n = GodRayMath.layerCount(12, q);
            assertTrue(n >= last && n >= GodRayMath.MIN_LAYERS && n <= 12);
            last = n;
        }
        assertEquals(GodRayMath.MIN_LAYERS, GodRayMath.layerCount(2, 1.0), "never fewer than the minimum");
        assertEquals(GodRayMath.MAX_LAYERS, GodRayMath.layerCount(40, 1.0), "never more than the maximum");
    }

    @Test
    void theTwoPassesTogetherSampleEvenlyAlongTheWholeReach() {
        assertEquals(0.92, GodRayMath.totalReach(), 0.01);
        for (int n : new int[] {5, 7, 9, 12}) {
            double[] a = new double[n], aw = new double[n];
            double[] b = new double[GodRayMath.BAND_LAYERS], bw = new double[GodRayMath.BAND_LAYERS];
            GodRayMath.layers(n, a, aw);
            GodRayMath.bands(b, bw);
            java.util.TreeSet<Double> all = new java.util.TreeSet<>();
            for (double x : a) for (double y : b) all.add(x * y);
            assertEquals(n * GodRayMath.BAND_LAYERS, all.size(), "every pair gives its own distance");
            assertTrue(1.0 - all.first() > 0.88, "the combined reach is long: " + (1.0 - all.first()));
            assertEquals(1.0, all.last(), 1e-12, "the pixel itself is sampled");
            double biggest = 0.0;
            Double prev = null;
            for (double d : all) {
                if (prev != null) biggest = Math.max(biggest, d - prev);
                prev = d;
            }
            // no step is wider than pass A's own step (the bands tile with no holes) ...
            assertTrue(biggest <= (1.0 - GodRayMath.BAND) / n + 1e-9, "n=" + n + " biggest step " + biggest);
            // ... and that is finer than a single pass of the same number of draws could do
            double onePass = GodRayMath.totalReach() / (n + GodRayMath.BAND_LAYERS);
            assertTrue(biggest < onePass, "n=" + n + ": " + biggest + " vs single pass " + onePass);
        }
    }

    @Test
    void raysComeFromTheSunWhenItIsOnScreen() {
        double[] o = new double[3];
        // sun straight ahead
        GodRayMath.origin(0.0, 0.0, -1.0, 1.43, 1.43, o);
        assertEquals(0.5, o[0], 1e-9);
        assertEquals(0.5, o[1], 1e-9);
        assertEquals(1.0, o[2], 1e-9);
        // sun up and to the right, still on screen
        GodRayMath.origin(0.3, 0.2, -0.93, 1.43, 1.43, o);
        assertTrue(o[0] > 0.5 && o[0] < 1.0 && o[1] > 0.5 && o[1] < 1.0, o[0] + "," + o[1]);
        assertEquals(1.0, o[2], 1e-9);
    }

    @Test
    void raysStayWhenTheSunIsHighAboveThePictureAndPointTheRightWay() {
        double[] o = new double[3];
        // the player looks at the horizon, the sun is at 70 degrees: far above the top edge
        double el = Math.toRadians(70);
        GodRayMath.origin(0.0, Math.sin(el), -Math.cos(el), 1.43, 1.43, o);
        assertTrue(o[1] > 1.0, "the point is above the screen: " + o[1]);
        assertTrue(o[1] <= 0.5 + GodRayMath.ORIGIN_LIMIT * 0.5 + 1e-9, "but held near it: " + o[1]);
        assertEquals(0.5, o[0], 1e-9, "straight above, so no sideways shift");
        assertTrue(o[2] > 0.6, "the rays still show: " + o[2]);
        // up and to the left: the point is up and to the left
        GodRayMath.origin(-0.5, 0.8, -0.33, 1.43, 1.43, o);
        assertTrue(o[0] < 0.5 && o[1] > 0.5);
        // never further out than the limit, whatever the direction
        for (double a = 0; a < Math.PI * 2; a += 0.3) {
            GodRayMath.origin(Math.cos(a), Math.sin(a) * 0.9, -0.05, 1.43, 1.43, o);
            assertTrue(Math.abs(o[0] - 0.5) <= GodRayMath.ORIGIN_LIMIT * 0.5 + 1e-9);
            assertTrue(Math.abs(o[1] - 0.5) <= GodRayMath.ORIGIN_LIMIT * 0.5 + 1e-9);
        }
    }

    @Test
    void raysEaseOutAtTheSideAndAreGoneWhenTheSunIsBehindThePlayer() {
        double[] o = new double[3];
        GodRayMath.origin(1.0, 0.0, 0.0, 1.43, 1.43, o); // exactly at the side
        assertTrue(o[2] > 0.0 && o[2] < 1.0, "easing out: " + o[2]);
        GodRayMath.origin(0.0, 0.5, 0.87, 1.43, 1.43, o); // behind and above
        assertEquals(0.0, o[2], 1e-9);
        GodRayMath.origin(0.0, 0.0, 1.0, 1.43, 1.43, o); // exactly behind
        assertEquals(0.0, o[2], 1e-9);
        // continuous: no jump when the sun passes the side
        double[] a = new double[3], b = new double[3];
        GodRayMath.origin(0.8, 0.3, -0.0201, 1.43, 1.43, a);
        GodRayMath.origin(0.8, 0.3, -0.0199, 1.43, 1.43, b);
        assertEquals(a[0], b[0], 1e-3);
        assertEquals(a[1], b[1], 1e-3);
    }

    @Test
    void daylightIsZeroAtNightInRainAndBelowTheHorizon() {
        assertEquals(1.0, GodRayMath.daylight(0.5, 0.0, 0.0), 1e-12);
        assertEquals(0.0, GodRayMath.daylight(0.5, 1.0, 0.0), 1e-12);
        assertEquals(0.0, GodRayMath.daylight(0.5, 0.0, 1.0), 1e-12);
        assertEquals(0.0, GodRayMath.daylight(-0.2, 0.0, 0.0), 1e-12);
        assertTrue(GodRayMath.daylight(0.03, 0.0, 0.0) > 0.0 && GodRayMath.daylight(0.03, 0.0, 0.0) < 1.0, "eases out at sunset");
    }

    @Test
    void falloffIsBrightestAtTheSunAndZeroOnTheTextureBorder() {
        assertEquals(1.0, GodRayMath.falloff(0.0), 1e-12);
        double last = 1.0;
        for (double r = 0.1; r < 3.0; r += 0.1) {
            double f = GodRayMath.falloff(r);
            assertTrue(f < last);
            last = f;
        }
        int n = 64;
        byte[] px = GodRayMath.falloffTexture(n);
        assertTrue((px[(n / 2) * n + n / 2] & 0xFF) > 240);
        for (int i = 0; i < n; i++) {
            assertEquals(0, px[i] & 0xFF);
            assertEquals(0, px[(n - 1) * n + i] & 0xFF);
            assertEquals(0, px[i * n] & 0xFF);
            assertEquals(0, px[i * n + n - 1] & 0xFF);
        }
    }

    @Test
    void governorTurnsQualityDownWhenFramesAreTooLongAndBackUpWhenThereIsRoom() {
        QualityGovernor g = new QualityGovernor(90);
        for (int i = 0; i < 400; i++) g.frame(11.1);
        assertEquals(1.0, g.quality(), 1e-9, "on target: untouched");
        for (int i = 0; i < 400; i++) g.frame(16.0);
        assertTrue(g.quality() < 0.7, "slow frames lower the quality: " + g.quality());
        assertTrue(g.quality() >= QualityGovernor.MIN_QUALITY);
        for (int i = 0; i < 2000; i++) g.frame(8.0);
        assertEquals(1.0, g.quality(), 1e-9, "fast frames bring it back");
    }

    @Test
    void governorIgnoresOneOffHitchesAndCanBeSwitchedOff() {
        QualityGovernor g = new QualityGovernor(90);
        for (int i = 0; i < 300; i++) g.frame(i % 50 == 0 ? 120.0 : 11.0);
        assertEquals(1.0, g.quality(), 1e-9, "a chunk-loading hitch is not the effects' fault");
        QualityGovernor off = new QualityGovernor(0);
        for (int i = 0; i < 300; i++) off.frame(40.0);
        assertEquals(1.0, off.quality(), 1e-9);
    }
}
