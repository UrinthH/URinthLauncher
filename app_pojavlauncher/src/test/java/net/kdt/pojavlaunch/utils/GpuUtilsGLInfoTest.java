package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for GPU-family checks used by renderer compatibility filtering. */
public class GpuUtilsGLInfoTest {
    @Test
    public void maliDetectionRequiresMaliRendererAndArmVendor() {
        assertTrue(new GpuUtils.GLInfo("ARM", "Mali-G78", 3, false).isMali());
        assertTrue(new GpuUtils.GLInfo("ARM", "Immortalis-G715", 3, false).isMali());
        assertTrue(new GpuUtils.GLInfo("arm", "Mali-G610", 3, false).isMali());
        assertFalse(new GpuUtils.GLInfo("Qualcomm", "Adreno 740", 3, false).isMali());
        assertFalse(new GpuUtils.GLInfo("ARM", "Generic GLES Renderer", 3, false).isMali());
    }

    @Test
    public void maliDetectionHandlesUnknownGpuStringsSafely() {
        assertFalse(new GpuUtils.GLInfo(null, "Mali-G78", 3, false).isMali());
        assertFalse(new GpuUtils.GLInfo("ARM", null, 3, false).isMali());
    }

    @Test
    public void adrenoDetectionHandlesUnknownGpuStringsSafely() {
        assertTrue(new GpuUtils.GLInfo("Qualcomm", "Adreno (TM) 530", 3, false).isAdreno());
        assertTrue(new GpuUtils.GLInfo("Qualcomm", "Adreno (TM) 530", 3, false).isAdreno500Lower());
        assertFalse(new GpuUtils.GLInfo(null, "Adreno (TM) 530", 3, false).isAdreno());
        assertFalse(new GpuUtils.GLInfo("Qualcomm", null, 3, false).isAdreno500Lower());
    }

    @Test
    public void legacyIsArmMethodRemainsCompatible() {
        assertTrue(new GpuUtils.GLInfo("ARM", "Mali-G78", 3, false).isArm());
        assertFalse(new GpuUtils.GLInfo("Qualcomm", "Adreno 740", 3, false).isArm());
    }
}
