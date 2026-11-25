package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The far-plane arithmetic behind Cloud Fix. The matrix itself is the mixin's; these are the four numbers it works from. */
class CloudFarTest {
    private static final float NEAR = 0.05f;

    @BeforeEach
    @AfterEach
    void reset() {
        CloudFar.resetReports();
        CloudFar.setEnabled(true);
    }

    /** Depth of a point d blocks in front of the camera, after the divide: -1 at the near plane, 1 at the far plane, beyond that is cut. */
    private static double ndc(float m22, float m32, double d) {
        return (-m22 * d + m32) / d;
    }

    @Test
    void thePlanesComeBackOutOfTheMatrixEntries() {
        float[] fars = {64f, 128f, 192f, 256f, 320f, 512f, 768f};
        for (float far : fars) {
            float m22 = CloudFar.m22For(NEAR, far);
            float m32 = CloudFar.m32For(NEAR, far);
            assertTrue(CloudFar.isPerspective(m22, -1f, m32, 0f), "perspective at " + far);
            assertEquals(far, CloudFar.farOf(m22, m32), far * 0.01f, "far " + far);
            assertEquals(NEAR, CloudFar.nearOf(m22, m32), 0.002f, "near with far " + far);
        }
    }

    @Test
    void matchesWhatTheGameAndJomlMakeForRenderDistanceTwo() {
        // JOML: m22 = (far + near) / (near - far), m32 = 2 * far * near / (near - far); 2 chunks = 32 blocks, far plane 4x = 128
        float m22 = -1.00078201f;
        float m32 = -0.10003907f;
        assertEquals(128f, CloudFar.farOf(m22, m32), 0.5f);
        assertEquals(0.05f, CloudFar.nearOf(m22, m32), 0.001f);
        assertEquals(CloudFar.REACH, CloudFar.farFor(CloudFar.farOf(m22, m32)));
    }

    @Test
    void onlyOrdinaryPerspectiveMatricesCount() {
        float m22 = CloudFar.m22For(NEAR, 128f);
        float m32 = CloudFar.m32For(NEAR, 128f);
        assertTrue(CloudFar.isPerspective(m22, -1f, m32, 0f));
        assertFalse(CloudFar.isPerspective(m22, 0f, m32, 1f), "orthographic");
        assertFalse(CloudFar.isPerspective(1f, 0f, 0f, 1f), "identity");
        assertFalse(CloudFar.isPerspective(0f, 0f, 0f, 0f), "zeros");
        assertFalse(CloudFar.isPerspective(-1f, -1f, -0.1f, 0f), "no far plane (infinite projection)");
        assertFalse(CloudFar.isPerspective(Float.NaN, -1f, m32, 0f));
        assertFalse(CloudFar.isPerspective(m22, -1f, Float.NaN, 0f));
        assertFalse(CloudFar.isPerspective(m22, -1f, m32, 0.5f), "m33 of a perspective matrix is 0");
        assertFalse(CloudFar.isPerspective(m22, -1f, 0.1f, 0f), "a positive m32 is not a depth range");
    }

    @Test
    void theCloudsNeverReachLessThanTheCameraDoes() {
        assertEquals(CloudFar.REACH, CloudFar.farFor(128f));
        assertEquals(CloudFar.REACH, CloudFar.farFor(192f));
        assertEquals(CloudFar.REACH, CloudFar.farFor(256f));
        assertEquals(CloudFar.REACH, CloudFar.farFor(271.9f));
        assertEquals(CloudFar.REACH, CloudFar.farFor(CloudFar.REACH));
        assertEquals(320f, CloudFar.farFor(320f));
        assertEquals(768f, CloudFar.farFor(768f));
        assertEquals(4096f, CloudFar.farFor(4096f));
    }

    @Test
    void aFarPlaneThatIsNotADistanceIsLeftAlone() {
        assertTrue(Float.isNaN(CloudFar.farFor(Float.NaN)));
        assertEquals(Float.POSITIVE_INFINITY, CloudFar.farFor(Float.POSITIVE_INFINITY));
        assertEquals(0f, CloudFar.farFor(0f));
        assertEquals(-5f, CloudFar.farFor(-5f));
    }

    @Test
    void theReachIsPastWhereSodiumsCloudsHaveFadedIntoTheFog() {
        // Sodium: the clouds are drawn 32 cells out at the least, and its fog ends at 8 blocks per cell.
        assertTrue(CloudFar.REACH > 32 * 8);
        // and it is still a short distance, not "everything": the clouds are not drawn a long way farther than they are seen
        assertTrue(CloudFar.REACH < 512f);
    }

    @Test
    void theLongerCameraKeepsTheNearPlaneAndShowsCloudsTheOldOneCutOff() {
        float oldM22 = CloudFar.m22For(NEAR, 128f);
        float oldM32 = CloudFar.m32For(NEAR, 128f);
        float far = CloudFar.farOf(oldM22, oldM32);
        float near = CloudFar.nearOf(oldM22, oldM32);
        float wanted = CloudFar.farFor(far);
        float m22 = CloudFar.m22For(near, wanted);
        float m32 = CloudFar.m32For(near, wanted);

        assertEquals(wanted, CloudFar.farOf(m22, m32), 1.0f);
        assertEquals(near, CloudFar.nearOf(m22, m32), 0.002f);
        assertTrue(CloudFar.isPerspective(m22, -1f, m32, 0f));

        // 250 blocks along the view is cut by the old camera (depth past 1) and drawn by the new one
        assertTrue(ndc(oldM22, oldM32, 250.0) > 1.0, "the old camera cuts it off");
        assertTrue(ndc(m22, m32, 250.0) < 1.0, "the new camera draws it");
        // what both cameras draw is still drawn, and in the same order front to back
        double previous = -2.0;
        for (double d = 1.0; d <= 250.0; d += 7.0) {
            double depth = ndc(m22, m32, d);
            assertTrue(depth > -1.0 && depth < 1.0, "inside the depth range at " + d);
            assertTrue(depth > previous, "farther is deeper at " + d);
            previous = depth;
        }
    }

    @Test
    void aCameraThatAlreadyReachesFarEnoughIsNotChanged() {
        float m22 = CloudFar.m22For(NEAR, 768f);
        float m32 = CloudFar.m32For(NEAR, 768f);
        float far = CloudFar.farOf(m22, m32);
        assertEquals(far, CloudFar.farFor(far));
    }

    @Test
    void theFeatureIsOnUntilTheSettingTurnsItOff() {
        assertTrue(CloudFar.enabled());
        CloudFar.setEnabled(false);
        assertFalse(CloudFar.enabled());
        CloudFar.setEnabled(true);
        assertTrue(CloudFar.enabled());
    }

    @Test
    void theLogHearsOfEachNewReachOnceAndOnlyAFewTimes() {
        String first = CloudFar.report(128f, 272f);
        assertNotNull(first);
        assertTrue(first.contains("128") && first.contains("272"), first);
        assertNull(CloudFar.report(128.2f, 272f), "the same numbers again are not news");
        assertNull(CloudFar.report(128f, 272f));

        String second = CloudFar.report(768f, 768f);
        assertNotNull(second);
        assertTrue(second.contains("768") && second.contains("already"), second);

        assertNotNull(CloudFar.report(192f, 272f));
        assertNotNull(CloudFar.report(256f, 272f));
        assertNull(CloudFar.report(64f, 272f), "four lines is the most a game log gets");
        assertNull(CloudFar.report(32f, 272f));
    }
}
