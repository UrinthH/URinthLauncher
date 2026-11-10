package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

public class OptimizerModInstallerTest {
    private static final String FABRIC = "fabric-loader-0.16.10-1.21.4";
    private static final byte[] V1 = {1, 2, 3, 4, 5};
    private static final byte[] V2 = {6, 7, 8, 9};

    private static OptimizerModInstaller.Source jar(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    private static File gameDir() throws IOException {
        return Files.createTempDirectory("optimizermod").toFile();
    }

    private static File mods(File dir) {
        return new File(dir, "mods");
    }

    private static File installed(File dir) {
        return new File(mods(dir), OptimizerModInstaller.FILE_NAME);
    }

    private static File marker(File dir) {
        return new File(mods(dir), OptimizerModInstaller.MARKER_NAME);
    }

    private static void put(File dir, String name, byte[] bytes) throws IOException {
        File file = new File(mods(dir), name);
        assertTrue(mods(dir).isDirectory() || mods(dir).mkdirs());
        Files.write(file.toPath(), bytes);
    }

    private static byte[] jarWithModId(String id) throws IOException {
        return modJar(id, "1");
    }

    private static byte[] modJar(String id, String version) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\n\t\"schemaVersion\": 1,\n\t\"id\": \"" + id + "\",\n\t\"version\": \"" + version + "\"\n}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static File broken(File dir, String name) {
        return new File(mods(dir), name + OptimizerModInstaller.BROKEN_SUFFIX);
    }

    @Test
    public void onlyFabric1214IsSupported() {
        assertTrue(OptimizerModInstaller.appliesTo(FABRIC));
        assertTrue(OptimizerModInstaller.appliesTo("Fabric-Loader-0.16.9-1.21.4"));
        assertFalse(OptimizerModInstaller.appliesTo("1.21.4"));
        assertFalse(OptimizerModInstaller.appliesTo("fabric-loader-0.16.10-1.21.1"));
        assertFalse(OptimizerModInstaller.appliesTo("1.21.4-forge-54.0.0"));
        assertFalse(OptimizerModInstaller.appliesTo(null));
    }

    @Test
    public void installsOnceAndRemembersIt() throws IOException {
        File dir = gameDir();
        assertEquals(OptimizerModInstaller.Result.INSTALLED, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
        assertArrayEquals(V1, Files.readAllBytes(installed(dir).toPath()));
        assertTrue(marker(dir).isFile());
        assertFalse(new File(mods(dir), OptimizerModInstaller.FILE_NAME + ".tmp").exists());
        assertEquals(OptimizerModInstaller.Result.ALREADY_PRESENT, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
    }

    @Test
    public void theMarkerIsHiddenFromTheModsList() {
        assertTrue(OptimizerModInstaller.MARKER_NAME.startsWith("."));
    }

    @Test
    public void updatesACopyNobodyChanged() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        assertEquals(OptimizerModInstaller.Result.UPDATED, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertEquals(OptimizerModInstaller.Result.ALREADY_PRESENT, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
    }

    @Test
    public void neverOverwritesAFileThePlayerChanged() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        byte[] theirs = {42, 42};
        Files.write(installed(dir).toPath(), theirs);
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(theirs, Files.readAllBytes(installed(dir).toPath()));
    }

    @Test
    public void neverPutsItBackAfterThePlayerDeletedIt() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        assertTrue(installed(dir).delete());
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void respectsASwitchedOffMod() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        File off = new File(mods(dir), OptimizerModInstaller.FILE_NAME + ".disabled");
        assertTrue(installed(dir).renameTo(off));
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
        assertArrayEquals(V1, Files.readAllBytes(off.toPath()));

        // Switched on again later: it is still the launcher's unchanged copy, so it is brought up to date.
        assertTrue(off.renameTo(installed(dir)));
        assertEquals(OptimizerModInstaller.Result.UPDATED, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
    }

    @Test
    public void aSwitchedOffCopyWithoutMarkerIsAlsoRespected() throws IOException {
        File dir = gameDir();
        put(dir, OptimizerModInstaller.FILE_NAME + ".disabled", V1);
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void doesNotInstallWhenThePlayerHasItUnderAnotherName() throws IOException {
        File dir = gameDir();
        put(dir, "urinth-optimizer-1.0.0.jar", V1);
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
        assertFalse(marker(dir).exists());
    }

    @Test
    public void doesNotInstallWhenAnotherJarIsTheSameMod() throws IOException {
        File dir = gameDir();
        put(dir, "OptimizerMod-renamed.jar", jarWithModId("urinth_optimizer"));
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void installsNextToOtherModsAndLeavesThemAlone() throws IOException {
        File dir = gameDir();
        byte[] sodium = jarWithModId("sodium");
        put(dir, "sodium.jar", sodium);
        put(dir, "lithium.jar.disabled", V1);
        assertEquals(OptimizerModInstaller.Result.INSTALLED, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(sodium, Files.readAllBytes(new File(mods(dir), "sodium.jar").toPath()));
        assertArrayEquals(V1, Files.readAllBytes(new File(mods(dir), "lithium.jar.disabled").toPath()));
        assertEquals(OptimizerModInstaller.Result.ALREADY_PRESENT, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
    }

    @Test
    public void removesItsOwnCopyWhenThePlayerAddsAnother() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        put(dir, "urinth-optimizer (1).jar", V1);
        assertEquals(OptimizerModInstaller.Result.REMOVED_DUPLICATE, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
        assertFalse(installed(dir).exists());
        assertTrue(new File(mods(dir), "urinth-optimizer (1).jar").isFile());
        // And it stays out of the way afterwards.
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
    }

    @Test
    public void aSwitchedOffOtherCopyDoesNotCostTheInstalledOne() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        put(dir, "urinth-optimizer-old.jar.disabled", V1);
        assertEquals(OptimizerModInstaller.Result.ALREADY_PRESENT, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
        assertTrue(installed(dir).isFile());
    }

    @Test
    public void aCopyThePlayerBroughtThatMatchesIsAdoptedWithoutDuplicating() throws IOException {
        File dir = gameDir();
        put(dir, OptimizerModInstaller.FILE_NAME, V1);
        assertEquals(OptimizerModInstaller.Result.ALREADY_PRESENT, OptimizerModInstaller.sync(dir, FABRIC, jar(V1)));
        assertTrue(marker(dir).isFile());
    }

    @Test
    public void aMissingOrEmptyBundledJarChangesNothing() throws IOException {
        File dir = gameDir();
        assertEquals(OptimizerModInstaller.Result.NOT_BUNDLED, OptimizerModInstaller.sync(dir, FABRIC, () -> {
            throw new FileNotFoundException("no asset");
        }));
        assertEquals(OptimizerModInstaller.Result.NOT_BUNDLED, OptimizerModInstaller.sync(dir, FABRIC, jar(new byte[0])));
        assertFalse(mods(dir).exists());
    }

    @Test
    public void otherVersionsAreNotTouched() throws IOException {
        File dir = gameDir();
        put(dir, "sodium.jar", V1);
        assertEquals(OptimizerModInstaller.Result.WRONG_VERSION, OptimizerModInstaller.sync(dir, "1.21.4", jar(V2)));
        assertEquals(OptimizerModInstaller.Result.WRONG_VERSION, OptimizerModInstaller.sync(dir, "fabric-loader-0.16.10-1.20.1", jar(V2)));
        assertFalse(installed(dir).exists());
        assertFalse(marker(dir).exists());
    }


    // ---- a build that stops the game at start --------------------------------------------------------------------------------

    @Test
    public void replacesAKnownBrokenBuildThePlayerPutThereUnderAnotherName() throws IOException {
        File dir = gameDir();
        byte[] old = modJar("urinth_optimizer", "1.0.0");
        put(dir, "urinth-optimizer-1.0.0.jar", old);
        assertEquals(OptimizerModInstaller.Result.REPLACED_BROKEN, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertTrue(marker(dir).isFile());
        // the old file is out of the loader's way but not lost
        assertFalse(new File(mods(dir), "urinth-optimizer-1.0.0.jar").exists());
        assertArrayEquals(old, Files.readAllBytes(broken(dir, "urinth-optimizer-1.0.0.jar").toPath()));
        // and the next start has nothing left to do
        assertEquals(OptimizerModInstaller.Result.ALREADY_PRESENT, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
    }

    @Test
    public void replacesAKnownBrokenBuildUnderAnyFileName() throws IOException {
        File dir = gameDir();
        put(dir, "OptimizerMod-renamed.jar", modJar("urinth_optimizer", "1.0.0"));
        assertEquals(OptimizerModInstaller.Result.REPLACED_BROKEN, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertFalse(new File(mods(dir), "OptimizerMod-renamed.jar").exists());
    }

    @Test
    public void replacesAKnownBrokenBuildAtTheInstalledNameThatNoMarkerVouchesFor() throws IOException {
        File dir = gameDir();
        put(dir, OptimizerModInstaller.FILE_NAME, modJar("urinth_optimizer", "1.0.0"));
        assertEquals(OptimizerModInstaller.Result.REPLACED_BROKEN, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertTrue(broken(dir, OptimizerModInstaller.FILE_NAME).isFile());
    }

    @Test
    public void replacesAKnownBrokenBuildThePlayerSwappedInAfterTheLauncherInstalledItsOwn() throws IOException {
        File dir = gameDir();
        OptimizerModInstaller.sync(dir, FABRIC, jar(V1));
        Files.write(installed(dir).toPath(), modJar("urinth_optimizer", "1.0.0"));
        assertEquals(OptimizerModInstaller.Result.REPLACED_BROKEN, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
    }

    @Test
    public void aLaunchersOwnCopyIsStillJustUpdated() throws IOException {
        File dir = gameDir();
        // even if the launcher's own earlier copy happens to be a known-broken version, the ordinary update rule applies
        OptimizerModInstaller.sync(dir, FABRIC, jar(modJar("urinth_optimizer", "1.0.0")));
        assertEquals(OptimizerModInstaller.Result.UPDATED, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(V2, Files.readAllBytes(installed(dir).toPath()));
        assertFalse(broken(dir, OptimizerModInstaller.FILE_NAME).exists());
    }

    @Test
    public void aHealthyCopyUnderAnotherNameIsNotTouched() throws IOException {
        File dir = gameDir();
        byte[] newer = modJar("urinth_optimizer", "1.1.0");
        put(dir, "urinth-optimizer-1.1.0.jar", newer);
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(newer, Files.readAllBytes(new File(mods(dir), "urinth-optimizer-1.1.0.jar").toPath()));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void aBrokenBuildBesideAHealthyOneIsMovedAsideAndNothingIsInstalled() throws IOException {
        File dir = gameDir();
        byte[] newer = modJar("urinth_optimizer", "1.1.0");
        put(dir, "urinth-optimizer-1.1.0.jar", newer);
        put(dir, "urinth-optimizer-1.0.0.jar", modJar("urinth_optimizer", "1.0.0"));
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(new File(mods(dir), "urinth-optimizer-1.0.0.jar").exists());
        assertTrue(broken(dir, "urinth-optimizer-1.0.0.jar").isFile());
        assertArrayEquals(newer, Files.readAllBytes(new File(mods(dir), "urinth-optimizer-1.1.0.jar").toPath()));
        assertFalse(installed(dir).exists());
    }

    @Test
    public void aSwitchedOffBrokenBuildStaysSwitchedOff() throws IOException {
        File dir = gameDir();
        byte[] old = modJar("urinth_optimizer", "1.0.0");
        put(dir, OptimizerModInstaller.FILE_NAME + ".disabled", old);
        assertEquals(OptimizerModInstaller.Result.LEFT_ALONE, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertFalse(installed(dir).exists());
        assertArrayEquals(old, Files.readAllBytes(new File(mods(dir), OptimizerModInstaller.FILE_NAME + ".disabled").toPath()));
    }

    @Test
    public void otherModsAreNeverTakenForABrokenBuild() throws IOException {
        File dir = gameDir();
        byte[] sodium = modJar("sodium", "1.0.0");
        put(dir, "sodium.jar", sodium);
        put(dir, "not-a-zip.jar", V1);
        assertEquals(OptimizerModInstaller.Result.INSTALLED, OptimizerModInstaller.sync(dir, FABRIC, jar(V2)));
        assertArrayEquals(sodium, Files.readAllBytes(new File(mods(dir), "sodium.jar").toPath()));
        assertArrayEquals(V1, Files.readAllBytes(new File(mods(dir), "not-a-zip.jar").toPath()));
        assertFalse(broken(dir, "sodium.jar").exists());
    }

    @Test
    public void readsTheVersionOfTheModOnly() throws IOException {
        File dir = gameDir();
        put(dir, "a.jar", modJar("urinth_optimizer", "1.0.0"));
        put(dir, "b.jar", modJar("sodium", "0.6.13"));
        put(dir, "c.jar", V1);
        assertEquals("1.0.0", OptimizerModInstaller.modVersion(new File(mods(dir), "a.jar")));
        assertEquals(null, OptimizerModInstaller.modVersion(new File(mods(dir), "b.jar")));
        assertEquals(null, OptimizerModInstaller.modVersion(new File(mods(dir), "c.jar")));
    }

    @Test
    public void aMissingGameFolderIsAFailureNotACrash() {
        assertEquals(OptimizerModInstaller.Result.FAILED, OptimizerModInstaller.sync(null, FABRIC, jar(V1)));
    }
}
