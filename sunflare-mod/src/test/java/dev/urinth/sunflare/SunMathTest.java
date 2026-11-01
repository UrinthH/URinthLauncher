package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SunMathTest {
    @Test
    void skyAngleMatchesVanilla() {
        assertEquals(0.0, SunMath.skyAngle(6000), 1e-9);
        assertEquals(0.5, SunMath.skyAngle(18000), 1e-9);
    }

    @Test
    void sunRisesEastAndSetsWest() {
        double[] d = new double[3];
        SunMath.sunDirection(6000, d);
        assertEquals(1.0, d[1], 1e-9);
        SunMath.sunDirection(0, d);
        assertTrue(d[0] > 0.9);
        SunMath.sunDirection(12000, d);
        assertTrue(d[0] < -0.9);
    }

    @Test
    void projectionFollowsTheCamera() {
        double[] d = new double[3];
        double[] a = new double[2];
        double[] b = new double[2];
        SunMath.sunDirection(0, d);
        assertTrue(SunMath.project(d, -90, -10, 70, 16.0 / 9, a));
        assertEquals(0.5, a[0], 0.02);
        // turning left (yaw down) moves a fixed sun to the right on the screen
        assertTrue(SunMath.project(d, -100, -10, 70, 16.0 / 9, b));
        assertTrue(b[0] > a[0]);
        // looking away from it
        assertFalse(SunMath.project(d, 90, 0, 70, 16.0 / 9, a));
    }

    @Test
    void edgeFadeAndLayers() {
        assertEquals(1.0, SunMath.edgeFade(0.5, 0.5), 1e-9);
        assertEquals(0.0, SunMath.edgeFade(-0.1, 0.5), 1e-9);
        double[] c = new double[2];
        FlareLayers.center(0, 0.8, 0.7, c);
        assertEquals(0.8, c[0], 1e-9);
        assertEquals(0.7, c[1], 1e-9);
        assertEquals(1.0, FlareLayers.profile(0), 1e-9);
        assertEquals(0.0, FlareLayers.profile(1), 1e-9);
        assertEquals(FlareLayers.COUNT, FlareLayers.POS.length);
        assertEquals(FlareLayers.COUNT, FlareLayers.SIZE.length);
        assertEquals(FlareLayers.COUNT, FlareLayers.COLOR.length);
    }

    @Test
    void configParsingFallsBack() {
        assertEquals(2, FlareConfig.parseMode("9", 2));
        assertEquals(1, FlareConfig.parseMode(" 1 ", 2));
        assertEquals(2, FlareConfig.parseMode(null, 2));
        assertEquals(2, FlareConfig.parseMode("x", 2));
    }
}
