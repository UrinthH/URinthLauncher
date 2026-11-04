package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The numbers behind the screen-space god rays, and the guard that keeps the frame rate. No Minecraft classes. */
class GodRayTest {
    @Test
    void layersAreShrunkTowardsTheSunAndTheirWeightsAddUpToOne() {
        for (int n : new int[] {6, 10, 16, 22, 28}) {
            double[] scale = new double[n], weight = new double[n];
            GodRayMath.layers(n, scale, weight);
            double sum = 0;
            for (int k = 0; k < n; k++) {
                assertTrue(scale[k] > 0.0 && scale[k] < 1.0, "scale " + scale[k]);
                if (k > 0) {
                    assertTrue(scale[k] < scale[k - 1], "each layer reaches further");
                    assertTrue(weight[k] < weight[k - 1], "nearer samples count more");
                }
                sum += weight[k];
            }
            assertEquals(1.0, sum, 1e-9);
            assertEquals(1.0 - GodRayMath.REACH * 0.5 / n, scale[0], 1e-9);
        }
    }

    @Test
    void layerCountFollowsQualityWithinTheSettingsLimits() {
        assertEquals(16, GodRayMath.layerCount(16, 1.0));
        assertEquals(GodRayMath.MIN_LAYERS, GodRayMath.layerCount(16, 0.0));
        int last = 0;
        for (double q = 0.0; q <= 1.0; q += 0.1) {
            int n = GodRayMath.layerCount(22, q);
            assertTrue(n >= last && n >= GodRayMath.MIN_LAYERS && n <= 22);
            last = n;
        }
        assertEquals(GodRayMath.MIN_LAYERS, GodRayMath.layerCount(3, 1.0), "never fewer than the minimum");
    }

    @Test
    void visibilityIsOneOnScreenAndFadesJustOutsideIt() {
        assertEquals(1.0, GodRayMath.visibility(0.5, 0.5), 1e-12);
        assertEquals(1.0, GodRayMath.visibility(1.0, 0.0), 1e-12);
        assertTrue(GodRayMath.visibility(1.1, 0.5) < 1.0 && GodRayMath.visibility(1.1, 0.5) > 0.0);
        assertEquals(0.0, GodRayMath.visibility(1.4, 0.5), 1e-12);
        assertEquals(0.0, GodRayMath.visibility(0.5, -0.4), 1e-12);
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
