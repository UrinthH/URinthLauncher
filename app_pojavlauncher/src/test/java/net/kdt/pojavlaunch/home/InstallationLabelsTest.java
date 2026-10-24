package net.kdt.pojavlaunch.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InstallationLabelsTest {
    @Test
    public void findsTheMinecraftVersion() {
        assertEquals("1.21.4", InstallationLabels.minecraftVersion("fabric-loader-0.17.3-1.21.4"));
        assertEquals("1.20.1", InstallationLabels.minecraftVersion("1.20.1-forge-47.2.0"));
        assertEquals("1.21.4", InstallationLabels.minecraftVersion("1.21.4-OptiFine_HD_U_J1"));
        assertEquals("1.20.1", InstallationLabels.minecraftVersion("quilt-loader-0.26.0-1.20.1"));
        assertEquals("1.8.9", InstallationLabels.minecraftVersion("1.8.9"));
    }

    @Test
    public void fallsBackToTheWholeId() {
        assertEquals("neoforge-21.1.5", InstallationLabels.minecraftVersion("neoforge-21.1.5"));
        assertEquals("24w36a", InstallationLabels.minecraftVersion("24w36a"));
    }

    @Test
    public void noIdGivesNull() {
        assertNull(InstallationLabels.minecraftVersion(null));
        assertNull(InstallationLabels.minecraftVersion("  "));
    }
}
