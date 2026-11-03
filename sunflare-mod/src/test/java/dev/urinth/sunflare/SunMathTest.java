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
    void nightFollowsThePacksUniform() {
        assertEquals(0.0, SunMath.nightFactor(6000), 1e-9);   // noon
        assertEquals(0.0, SunMath.nightFactor(0), 1e-9);      // sunrise
        assertEquals(0.3, SunMath.nightFactor(13000), 1e-9);  // dusk
        assertEquals(1.0, SunMath.nightFactor(18000), 1e-9);  // midnight
        assertEquals(1.0, SunMath.nightFactor(18000 + 24000L * 5), 1e-9);
    }

    @Test
    void edgeFadeIsSmooth() {
        assertEquals(1.0, SunMath.edgeFade(0.5, 0.5), 1e-9);
        assertEquals(0.0, SunMath.edgeFade(-0.1, 0.5), 1e-9);
        double near = SunMath.edgeFade(0.05, 0.5);
        assertTrue(near > 0.0 && near < 1.0);
    }

    @Test
    void wideProjectionWorksOffScreenAndSaysWhenTheSunIsBehind() {
        double[] a = new double[2];
        double[] d = {0.0, 0.0, 1.0};
        // looking straight at it: centre of the screen
        assertTrue(SunMath.projectWide(d, 0, 0, 70, 16.0 / 9, a) > 0.99);
        assertEquals(0.5, a[0], 1e-9);
        assertEquals(0.5, a[1], 1e-9);
        // 60 degrees to the side: well outside the screen, but still given
        double fwd = SunMath.projectWide(d, 60, 0, 70, 16.0 / 9, a);
        assertTrue(fwd > 0.05 && (a[0] < 0 || a[0] > 1));
        assertTrue(SunMath.glowReach(a[0], a[1]) < 1.0);
        // behind: negative, no position
        assertTrue(SunMath.projectWide(d, 180, 0, 70, 16.0 / 9, a) < 0.05);
        assertEquals(0.0, SunMath.glowReach(5.0, 0.5), 1e-12);
    }

    @Test
    void glowReachIsOneOnScreenAndFadesSmoothlyOutside() {
        assertEquals(1.0, SunMath.glowReach(0.3, 0.8), 1e-12);
        assertEquals(1.0, SunMath.glowReach(0.0, 1.0), 1e-12);
        double last = 1.0;
        for (double u = 1.0; u < 1.8; u += 0.05) {
            double r = SunMath.glowReach(u, 0.5);
            assertTrue(r <= last + 1e-12);
            last = r;
        }
        assertEquals(0.0, SunMath.glowReach(1.8, 0.5), 1e-12);
    }
}
