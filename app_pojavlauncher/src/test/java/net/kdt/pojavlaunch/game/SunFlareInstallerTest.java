package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

public class SunFlareInstallerTest {
    private static final String FABRIC = "fabric-loader-0.16.10-1.21.4";
    private static final byte[] JAR = {1, 2, 3, 4, 5};

    private static SunFlareInstaller.Source jar(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    private static File gameDir() throws IOException {
        return Files.createTempDirectory("sunflare").toFile();
    }

    @Test
    public void onlyFabric1214IsSupported() {
        assertTrue(SunFlareInstaller.appliesTo(FABRIC));
        assertTrue(SunFlareInstaller.appliesTo("Fabric-Loader-0.16.9-1.21.4"));
        assertFalse(SunFlareInstaller.appliesTo("1.21.4"));
        assertFalse(SunFlareInstaller.appliesTo("fabric-loader-0.16.10-1.21.1"));
        assertFalse(SunFlareInstaller.appliesTo("1.21.4-forge-54.0.0"));
        assertFalse(SunFlareInstaller.appliesTo(null));
    }

    @Test
    public void installsThenRecognisesItself() throws IOException {
        File dir = gameDir();
        assertEquals(SunFlareInstaller.Result.INSTALLED, SunFlareInstaller.sync(dir, FABRIC, true, jar(JAR)));
        File installed = new File(dir, "mods/" + SunFlareInstaller.FILE_NAME);
        assertArrayEquals(JAR, Files.readAllBytes(installed.toPath()));
        assertFalse(new File(dir, "mods/" + SunFlareInstaller.FILE_NAME + ".tmp").exists());
        assertEquals(SunFlareInstaller.Result.ALREADY_PRESENT, SunFlareInstaller.sync(dir, FABRIC, true, jar(JAR)));
    }

    @Test
    public void replacesAnOldCopy() throws IOException {
        File dir = gameDir();
        SunFlareInstaller.sync(dir, FABRIC, true, jar(new byte[] {9}));
        assertEquals(SunFlareInstaller.Result.INSTALLED, SunFlareInstaller.sync(dir, FABRIC, true, jar(JAR)));
        assertArrayEquals(JAR, Files.readAllBytes(new File(dir, "mods/" + SunFlareInstaller.FILE_NAME).toPath()));
    }

    @Test
    public void switchingOffRemovesOnlyItsOwnFile() throws IOException {
        File dir = gameDir();
        SunFlareInstaller.sync(dir, FABRIC, true, jar(JAR));
        File other = new File(dir, "mods/sodium.jar");
        Files.write(other.toPath(), new byte[] {7});
        assertEquals(SunFlareInstaller.Result.REMOVED, SunFlareInstaller.sync(dir, FABRIC, false, jar(JAR)));
        assertFalse(new File(dir, "mods/" + SunFlareInstaller.FILE_NAME).exists());
        assertTrue(other.exists());
        assertEquals(SunFlareInstaller.Result.NOT_NEEDED, SunFlareInstaller.sync(dir, FABRIC, false, jar(JAR)));
    }

    @Test
    public void otherVersionsAreLeftAlone() throws IOException {
        File dir = gameDir();
        assertEquals(SunFlareInstaller.Result.WRONG_VERSION, SunFlareInstaller.sync(dir, "1.20.1", true, jar(JAR)));
        assertFalse(new File(dir, "mods").exists());
    }

    @Test
    public void missingOrEmptyBundleIsReportedNotThrown() throws IOException {
        File dir = gameDir();
        assertEquals(SunFlareInstaller.Result.NOT_BUNDLED, SunFlareInstaller.sync(dir, FABRIC, true, () -> {
            throw new FileNotFoundException("no asset");
        }));
        assertEquals(SunFlareInstaller.Result.NOT_BUNDLED, SunFlareInstaller.sync(dir, FABRIC, true, jar(new byte[0])));
        assertFalse(new File(dir, "mods/" + SunFlareInstaller.FILE_NAME).exists());
    }
}
