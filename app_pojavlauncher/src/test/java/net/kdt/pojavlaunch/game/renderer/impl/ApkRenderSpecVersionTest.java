package net.kdt.pojavlaunch.game.renderer.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;

/** Minecraft version gating for installed renderer plugins. */
public class ApkRenderSpecVersionTest {
    @Test
    public void comparesNumericVersions() {
        assertEquals(Integer.valueOf(-1), ApkRenderSpec.compareMcVersions("1.8.9", "1.17"));
        assertEquals(Integer.valueOf(1), ApkRenderSpec.compareMcVersions("1.21.1", "1.17"));
        assertEquals(Integer.valueOf(0), ApkRenderSpec.compareMcVersions("1.17", "1.17.0"));
        assertEquals(Integer.valueOf(1), ApkRenderSpec.compareMcVersions("1.21.1-pre1", "1.21"));
    }

    @Test
    public void snapshotIdsAreNotCompared() {
        assertNull(ApkRenderSpec.compareMcVersions("25w10a", "1.17"));
    }

    @Test
    public void minimumAndMaximumVersionsAreEnforced() {
        ApkRenderSpec newOnly = new ApkRenderSpec("p", "d", "lib.so", "/x",
                "1.17", null, new HashMap<String, String>(), null);
        assertFalse(newOnly.supportsMinecraft("1.8.9"));
        assertTrue(newOnly.supportsMinecraft("1.21.1"));
        assertTrue(newOnly.supportsMinecraft("25w10a"));

        ApkRenderSpec capped = new ApkRenderSpec("p", "d", "lib.so", "/x",
                null, "1.20.4", new HashMap<String, String>(), null);
        assertFalse(capped.supportsMinecraft("1.21.1"));
        assertTrue(capped.supportsMinecraft("1.20.4"));
    }
}
