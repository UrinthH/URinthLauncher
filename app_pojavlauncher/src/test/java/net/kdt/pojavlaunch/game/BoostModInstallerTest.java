package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class BoostModInstallerTest {
    private static final String FABRIC = "fabric-loader-0.16.10-1.21.4";
    private static final String FABRIC_1211 = "fabric-loader-0.18.1-1.21.11";
    private static final byte[] V1 = {1, 2, 3, 4, 5};
    private static final byte[] V2 = {6, 7, 8, 9};

    private static BoostModInstaller.Source jar(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    private static File gameDir() throws IOException {
        return Files.createTempDirectory("boostmod").toFile();
    }

    private static File mods(File dir) {
        return new File(dir, "mods");
    }

    private static File installed(File dir) {
        return new File(mods(dir), BoostModInstaller.FILE_NAME);
    }

    private static File marker(File dir) {
        return new File(mods(dir), BoostModInstaller.MARKER_NAME);
    }

    private static void put(File dir, String name, byte[] bytes) throws IOException {
        File file = new File(mods(dir), name);
        assertTrue(mods(dir).isDirectory() || mods(dir).mkdirs());
        Files.write(file.toPath(), bytes);
    }

    private static byte[] jarWithModId(String id) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\n\t\"schemaVersion\": 1,\n\t\"id\": \"" + id + "\",\n\t\"version\": \"1\"\n}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    public void onlyFabric1214And12111AreSupported() {
        assertTrue(BoostModInstaller.appliesTo(FABRIC));
        assertTrue(BoostModInstaller.appliesTo("Fabric-Loader-0.16.9-1.21.4"));
        assertTrue(BoostModInstaller.appliesTo(FABRIC_1211));
        assertTrue(BoostModInstaller.appliesTo("Fabric-Loader-0.17.3-1.21.11"));
        assertFalse(BoostModInstaller.appliesTo("1.21.4"));
        assertFalse(BoostModInstaller.appliesTo("1.21.11"));
        assertFalse(BoostModInstaller.appliesTo("fabric-loader-0.16.10-1.21.1"));
        assertFalse(BoostModInstaller.appliesTo("fabric-loader-0.16.10-1.21.40"));
        assertFalse(BoostModInstaller.appliesTo("fabric-loader-0.16.10-1.21.4-pre1"));
        assertFalse(BoostModInstaller.appliesTo("fabric-loader-0.18.1-1.21.11-rc1"));
        assertFalse(BoostModInstaller.appliesTo("1.21.4-forge-54.0.0"));
        assertFalse(BoostModInstaller.appliesTo("1.21.11-forge-61.0.0"));
        assertFalse(BoostModInstaller.appliesTo(null));
    }

    @Test
    public void eachGameVersionGetsItsOwnJar() {
        assertEquals(BoostModInstaller.ASSET_PATH_1_21_4, BoostModInstaller.assetPathFor(FABRIC));
        assertEquals(BoostModInstaller.ASSET_PATH_1_21_11, BoostModInstaller.assetPathFor(FABRIC_1211));
        assertNull(BoostModInstaller.assetPathFor("fabric-loader-0.16.10-1.20.1"));
        assertNull(BoostModInstaller.assetPathFor(null));
    }

    @Test
    public void anInstanceMovedBetweenTheTwoVersionsGetsTheMatchingJar() throws IOException {
        File dir = gameDir();
        assertEquals(BoostModInstaller.Result.INSTALLED, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertEquals(BoostModInstaller.Result.UPDATED, BoostModInstaller.sync(dir, FABRIC_1211, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertEquals(BoostModInstaller.Result.UPDATED, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertArrayEquals(V1, Files.readAllBytes(installed(dir).toPath()));
    }

    @Test
    public void installsOnceAndRemembersIt() throws IOException {
        File dir = gameDir();
        assertEquals(BoostModInstaller.Result.INSTALLED, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertArrayEquals(V1, Files.readAllBytes(installed(dir).toPath()));
        assertTrue(marker(dir).isFile());
        assertFalse(new File(mods(dir), BoostModInstaller.FILE_NAME + ".tmp").exists());
        assertEquals(BoostModInstaller.Result.ALREADY_PRESENT, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
    }

    @Test
    public void theMarkerIsHiddenFromTheModsList() {
        assertTrue(BoostModInstaller.MARKER_NAME.startsWith("."));
    }

    @Test
    public void updatesACopyNobodyChanged() throws IOException {
        File dir = gameDir();
        BoostModInstaller.sync(dir, FABRIC, jar(V1));
        assertEquals(BoostModInstaller.Result.UPDATED, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertEquals(BoostModInstaller.Result.ALREADY_PRESENT, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
    }

    @Test
    public void neverOverwritesAFileThePlayerChanged() throws IOException {
        File dir = gameDir();
        BoostModInstaller.sync(dir, FABRIC, jar(V1));
        byte[] theirs = {42, 42};
        Files.write(installed(dir).toPath(), theirs);
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(theirs, Files.readAllBytes(installed(dir).toPath()));
    }

    @Test
    public void neverPutsItBackAfterThePlayerDeletedIt() throws IOException {
        File dir = gameDir();
        BoostModInstaller.sync(dir, FABRIC, jar(V1));
        assertTrue(installed(dir).delete());
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void respectsASwitchedOffMod() throws IOException {
        File dir = gameDir();
        BoostModInstaller.sync(dir, FABRIC, jar(V1));
        File off = new File(mods(dir), BoostModInstaller.FILE_NAME + ".disabled");
        assertTrue(installed(dir).renameTo(off));
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
        assertArrayEquals(V1, Files.readAllBytes(off.toPath()));

        // Switched on again later: it is still the launcher's unchanged copy, so it is brought up to date.
        assertTrue(off.renameTo(installed(dir)));
        assertEquals(BoostModInstaller.Result.UPDATED, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
    }

    @Test
    public void aSwitchedOffCopyWithoutMarkerIsAlsoRespected() throws IOException {
        File dir = gameDir();
        put(dir, BoostModInstaller.FILE_NAME + ".disabled", V1);
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void doesNotInstallWhenThePlayerHasItUnderAnotherName() throws IOException {
        File dir = gameDir();
        put(dir, "urinth-boost-1.0.0.jar", V1);
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
        assertFalse(marker(dir).exists());
    }

    @Test
    public void doesNotInstallWhenAnotherJarIsTheSameMod() throws IOException {
        File dir = gameDir();
        put(dir, "BoostMod-renamed.jar", jarWithModId("urinth_boost"));
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void installsNextToOtherModsAndLeavesThemAlone() throws IOException {
        File dir = gameDir();
        byte[] sodium = jarWithModId("sodium");
        put(dir, "sodium.jar", sodium);
        put(dir, "lithium.jar.disabled", V1);
        assertEquals(BoostModInstaller.Result.INSTALLED, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(sodium, Files.readAllBytes(new File(mods(dir), "sodium.jar").toPath()));
        assertArrayEquals(V1, Files.readAllBytes(new File(mods(dir), "lithium.jar.disabled").toPath()));
        assertEquals(BoostModInstaller.Result.ALREADY_PRESENT, BoostModInstaller.sync(dir, FABRIC, jar(V2)));
    }

    @Test
    public void removesItsOwnCopyWhenThePlayerAddsAnother() throws IOException {
        File dir = gameDir();
        BoostModInstaller.sync(dir, FABRIC, jar(V1));
        put(dir, "urinth-boost (1).jar", V1);
        assertEquals(BoostModInstaller.Result.REMOVED_DUPLICATE, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertFalse(installed(dir).exists());
        assertTrue(new File(mods(dir), "urinth-boost (1).jar").isFile());
        // And it stays out of the way afterwards.
        assertEquals(BoostModInstaller.Result.LEFT_ALONE, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
    }

    @Test
    public void aSwitchedOffOtherCopyDoesNotCostTheInstalledOne() throws IOException {
        File dir = gameDir();
        BoostModInstaller.sync(dir, FABRIC, jar(V1));
        put(dir, "urinth-boost-old.jar.disabled", V1);
        assertEquals(BoostModInstaller.Result.ALREADY_PRESENT, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertTrue(installed(dir).isFile());
    }

    @Test
    public void aCopyThePlayerBroughtThatMatchesIsAdoptedWithoutDuplicating() throws IOException {
        File dir = gameDir();
        put(dir, BoostModInstaller.FILE_NAME, V1);
        assertEquals(BoostModInstaller.Result.ALREADY_PRESENT, BoostModInstaller.sync(dir, FABRIC, jar(V1)));
        assertTrue(marker(dir).isFile());
    }

    @Test
    public void aMissingOrEmptyBundledJarChangesNothing() throws IOException {
        File dir = gameDir();
        assertEquals(BoostModInstaller.Result.NOT_BUNDLED, BoostModInstaller.sync(dir, FABRIC, () -> {
            throw new FileNotFoundException("no asset");
        }));
        assertEquals(BoostModInstaller.Result.NOT_BUNDLED, BoostModInstaller.sync(dir, FABRIC, jar(new byte[0])));
        assertFalse(mods(dir).exists());
    }

    @Test
    public void otherVersionsAreNotTouched() throws IOException {
        File dir = gameDir();
        put(dir, "sodium.jar", V1);
        assertEquals(BoostModInstaller.Result.WRONG_VERSION, BoostModInstaller.sync(dir, "1.21.4", jar(V2)));
        assertEquals(BoostModInstaller.Result.WRONG_VERSION, BoostModInstaller.sync(dir, "fabric-loader-0.16.10-1.20.1", jar(V2)));
        assertFalse(installed(dir).exists());
        assertFalse(marker(dir).exists());
    }

    @Test
    public void aMissingGameFolderIsAFailureNotACrash() {
        assertEquals(BoostModInstaller.Result.FAILED, BoostModInstaller.sync(null, FABRIC, jar(V1)));
    }
}
