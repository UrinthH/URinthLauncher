package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The numbers behind the screen-space god rays, and the guard that keeps the frame rate. No Minecraft classes. */
class GodRayTest {
    @Test
    void passCountFollowsTheSettingWithinLimits() {
        assertEquals(6, GodRayMath.passes(64));
        assertEquals(5, GodRayMath.passes(32));
        assertEquals(GodRayMath.MIN_PASSES, GodRayMath.passes(16));
        assertEquals(GodRayMath.MIN_PASSES, GodRayMath.passes(2), "never fewer than the minimum");
        assertEquals(GodRayMath.MAX_PASSES, GodRayMath.passes(500), "never more than the maximum");
        assertEquals(GodRayMath.BANDS, GodRayMath.bandCount(1.0));
        assertTrue(GodRayMath.bandCount(0.2) < GodRayMath.bandCount(1.0), "low quality: fewer bands");
    }

    @Test
    void layerCountFollowsQualityWithinTheSettingsLimits() {
        assertEquals(48, GodRayMath.layerCount(48, 1.0));
        assertEquals(GodRayMath.MIN_LAYERS, GodRayMath.layerCount(48, 0.0));
        assertEquals(GodRayMath.MIN_LAYERS, GodRayMath.layerCount(2, 1.0));
        assertEquals(GodRayMath.MAX_LAYERS, GodRayMath.layerCount(500, 1.0));
    }

    @Test
    void finePassesDoubleTheShrinkAndStayInsideTheBand() {
        for (int n : new int[] {4, 5, 6}) {
            double[] shrink = new double[n], mix = new double[n];
            GodRayMath.smear(n, shrink, mix);
            double total = 0;
            for (int k = 0; k < n; k++) {
                if (k > 0) assertEquals(2.0, shrink[k] / shrink[k - 1], 1e-9, "each pass shrinks twice as far");
                total += shrink[k];
                assertTrue(mix[k] > 0.2 && mix[k] <= 0.51);
                assertEquals(mix[k] * 255.0, Math.rint(mix[k] * 255.0), 1e-9, "a whole byte, so it can be a vertex colour");
            }
            assertEquals(GodRayMath.FINE_REACH * (1.0 - Math.pow(2.0, -n)), total, 1e-9);
            double scale = GodRayMath.bandScale(n);
            assertTrue(scale < 1.0 - 0.9 * total && scale > 1.0 - total, "the smallest copy: " + scale);
        }
    }

    @Test
    void bandWeightsAreWholeBytesAddUpToOneAndFavourTheNearBands() {
        for (int n : new int[] {4, 5, 6}) {
            for (int count : new int[] {GodRayMath.BANDS_LOW, GodRayMath.BANDS}) {
                double[] scale = new double[count], weight = new double[count];
                GodRayMath.bands(n, count, scale, weight);
                double sum = 0;
                for (int j = 0; j < count; j++) {
                    assertEquals(Math.pow(GodRayMath.bandScale(n), j), scale[j], 1e-12);
                    assertEquals(weight[j] * 255.0, Math.rint(weight[j] * 255.0), 1e-9);
                    if (j > 1) assertTrue(weight[j] <= weight[j - 1] + 1e-9, "n=" + n + " band " + j);
                    sum += weight[j];
                }
                assertEquals(1.0, sum, 1e-9);
                assertEquals(1.0, scale[0], 1e-12, "band 0 is the picture itself");
            }
        }
    }

    @Test
    void fineAndBandPassesTogetherSampleEvenlyAlongTheWholeReach() {
        for (int n : new int[] {4, 5, 6}) {
            double[] shrink = new double[n], mix = new double[n];
            GodRayMath.smear(n, shrink, mix);
            double[] scale = new double[GodRayMath.BANDS], weight = new double[GodRayMath.BANDS];
            GodRayMath.bands(n, GodRayMath.BANDS, scale, weight);
            java.util.TreeSet<Double> all = new java.util.TreeSet<>();
            for (int bits = 0; bits < (1 << n); bits++) {
                double s = 1.0;
                for (int k = 0; k < n; k++) if ((bits >> k & 1) != 0) s *= 1.0 - shrink[k];
                for (double band : scale) all.add(1.0 - s * band);
            }
            assertEquals(0.0, all.first(), 1e-12, "the pixel itself is sampled");
            assertTrue(all.last() > 0.85, "the reach is long: " + all.last());
            double biggest = 0.0;
            Double prev = null;
            for (double d : all) {
                if (prev != null) biggest = Math.max(biggest, d - prev);
                prev = d;
            }
            // the steps between samples: at most the finest pass's own width, whatever the band (the bands tile, no holes)
            assertTrue(biggest <= 1.3 * GodRayMath.FINE_REACH / (1 << (n - 1)) / 2.0 + 0.012, "n=" + n + " biggest step " + biggest);
            assertTrue(biggest < 0.05, "n=" + n + " biggest step " + biggest);
        }
    }

    @Test
    void regionShrinksWithQualityButStaysBig() {
        assertEquals(GodRayMath.MAX_RADIUS, GodRayMath.regionRadius(1.0), 1e-12);
        assertTrue(GodRayMath.regionRadius(0.3) < GodRayMath.regionRadius(1.0));
        assertTrue(GodRayMath.regionRadius(0.0) >= 0.39 * GodRayMath.MAX_RADIUS);
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
    void falloffIsBrightestAtTheSunSmoothAndZeroOnTheTextureBorder() {
        assertEquals(1.0, GodRayMath.areaFalloff(0.0), 1e-12);
        double last = 1.0;
        for (double rho = 0.05; rho < 1.0; rho += 0.05) {
            double f = GodRayMath.areaFalloff(rho);
            assertTrue(f < last, "falls steadily");
            last = f;
        }
        assertEquals(0.0, GodRayMath.areaFalloff(1.0), 1e-12);
        assertEquals(0.0, GodRayMath.areaFalloff(3.0), 1e-12);
        // near the sun it is the same look as the plain falloff
        assertEquals(GodRayMath.falloff(0.2), GodRayMath.areaFalloff(0.2 / GodRayMath.MAX_RADIUS), 1e-12);
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
    void budgetIsTheRoomUnderTheTargetButNeverLessThanTheMinimum() {
        QualityGovernor g = new QualityGovernor(80);
        assertEquals(12.5, g.targetMillis(), 1e-9);
        for (int i = 0; i < 100; i++) g.frame(11.1, false);
        assertEquals(12.5 - 11.1, g.budgetMillis(), 0.05, "90 FPS plain: the effects may take it down to 80");
        for (int i = 0; i < 3000; i++) g.frame(25.0, false);
        assertEquals(QualityGovernor.MIN_BUDGET_MILLIS, g.budgetMillis(), 1e-9, "a heavy scene still leaves a small budget");
    }

    @Test
    void governorKeepsQualityWhenTheEffectsStayInsideTheBudget() {
        QualityGovernor g = new QualityGovernor(80);
        for (int i = 0; i < 100; i++) g.frame(11.1, false);
        for (int i = 0; i < 400; i++) g.frame(12.2, true); // +1.1 ms, budget is 1.4 ms
        assertEquals(1.0, g.quality(), 1e-9);
    }

    @Test
    void governorTurnsQualityDownWhenTheEffectsCostMoreThanTheBudgetAndBackUpWhenThereIsRoom() {
        QualityGovernor g = new QualityGovernor(80);
        for (int i = 0; i < 100; i++) g.frame(11.1, false);
        for (int i = 0; i < 400; i++) g.frame(16.0, true); // +4.9 ms
        assertTrue(g.quality() < 0.5, "expensive effects lower the quality: " + g.quality());
        assertTrue(g.quality() >= QualityGovernor.MIN_QUALITY);
        for (int i = 0; i < 4000; i++) g.frame(11.5, true); // cheap again
        assertEquals(1.0, g.quality(), 1e-9, "cheap effects bring it back");
    }

    @Test
    void governorWithoutABaselineHoldsTheWholeFrameToTheTarget() {
        QualityGovernor g = new QualityGovernor(80);
        for (int i = 0; i < 400; i++) g.frame(12.4, true);
        assertEquals(1.0, g.quality(), 1e-9, "on target: untouched");
        for (int i = 0; i < 400; i++) g.frame(18.0, true);
        assertTrue(g.quality() < 0.7, "slow frames lower the quality: " + g.quality());
    }

    @Test
    void governorIgnoresOneOffHitchesAndCanBeSwitchedOff() {
        QualityGovernor g = new QualityGovernor(80);
        for (int i = 0; i < 100; i++) g.frame(11.1, false);
        for (int i = 0; i < 300; i++) g.frame(i % 50 == 0 ? 120.0 : 11.6, true);
        assertEquals(1.0, g.quality(), 1e-9, "a chunk-loading hitch is not the effects' fault");
        QualityGovernor off = new QualityGovernor(0);
        for (int i = 0; i < 300; i++) off.frame(40.0, true);
        assertEquals(1.0, off.quality(), 1e-9);
    }
}
