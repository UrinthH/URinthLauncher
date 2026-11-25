package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FlareConfigTest {
    @Test
    void parsingFallsBack() {
        assertEquals(2, FlareConfig.parseMode("9", 2));
        assertEquals(3, FlareConfig.parseMode(" 3 ", 2));
        assertEquals(2, FlareConfig.parseMode(null, 2));
        assertEquals(2, FlareConfig.parseMode("x", 2));
    }

    @Test
    void cyclesThroughAllModesAndKeepsThePacksDefaultAsFull() throws IOException {
        Path dir = Files.createTempDirectory("sunflare-config");
        FlareConfig c = new FlareConfig(dir.resolve("urinth-sunflare.properties"));
        assertEquals(FlareConfig.FULL, c.mode());
        assertEquals(0.35, c.power(), 1e-12);
        assertEquals(FlareConfig.STRONG, c.cycle());
        assertEquals(0.50, c.power(), 1e-12);
        assertEquals(FlareConfig.OFF, c.cycle());
        assertEquals(0.0, c.power(), 1e-12);
        assertEquals(FlareConfig.SUBTLE, c.cycle());
        assertEquals(0.20, c.power(), 1e-12);
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("urinth-sunflare.properties"));
        again.load();
        assertEquals(FlareConfig.SUBTLE, again.mode());
    }

    @Test
    void setModeAndNamesWorkForTheCommand() throws IOException {
        FlareConfig c = new FlareConfig(Files.createTempDirectory("sunflare-cmd").resolve("c.properties"));
        assertEquals(FlareConfig.OFF, FlareConfig.modeFromName("OFF"));
        assertEquals(FlareConfig.STRONG, FlareConfig.modeFromName(" strong "));
        assertEquals(-1, FlareConfig.modeFromName("max"));
        assertEquals(-1, FlareConfig.modeFromName(null));
        assertEquals(FlareConfig.SUBTLE, c.setMode(FlareConfig.SUBTLE));
        assertEquals(FlareConfig.SUBTLE, c.setMode(9));
        assertEquals(FlareConfig.SUBTLE, c.mode());
    }

    @Test
    void godRaysFollowTheBeamLevelAndTheFrameGuardIsSaved() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sunflare-guard");
        FlareConfig c = new FlareConfig(dir.resolve("c.properties"));
        assertEquals(80, c.targetFps(), "the effects may cost a 90 FPS phone at most down to 80");
        assertEquals(FlareConfig.BEAMS_EXTREME, c.beams(), "extreme is the default");
        assertEquals("Extreme", FlareConfig.BEAM_NAMES[c.beams()]);
        c.setBeams(FlareConfig.BEAMS_OFF);
        assertEquals(0.0, c.godRayDensity(), 0.0);
        assertEquals(0, c.godRayLayers());
        double last = 0;
        for (int level = 1; level < FlareConfig.BEAM_NAMES.length; level++) {
            c.setBeams(level);
            assertTrue(c.godRayDensity() > last);
            assertTrue(c.godRayLayers() >= GodRayMath.MIN_LAYERS && c.godRayLayers() <= GodRayMath.MAX_LAYERS);
            last = c.godRayDensity();
        }
        assertEquals(30, c.setTargetFps(5));
        assertEquals(FlareConfig.MAX_TARGET_FPS, c.setTargetFps(999));
        assertEquals(250, FlareConfig.MAX_TARGET_FPS);
        assertEquals(0, c.setTargetFps(0));
        c.setTargetFps(75);
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("c.properties"));
        again.load();
        assertEquals(75, again.targetFps());
        assertEquals(c.beams(), again.beams());
    }

    @Test
    void higherBeamLevelsMakeTheSmallPictureBiggerAndNeverSmaller() {
        FlareConfig c = new FlareConfig(java.nio.file.Path.of("unused"));
        int last = Integer.MAX_VALUE;
        for (int level = 1; level < FlareConfig.BEAM_NAMES.length; level++) {
            c.setBeams(level);
            assertTrue(c.godRayDivisor() <= last, "level " + level);
            assertTrue(c.godRayDivisor() >= 6 && c.godRayDivisor() <= 12);
            last = c.godRayDivisor();
        }
        c.setBeams(FlareConfig.BEAMS_EXTREME);
        assertEquals(64, c.godRayLayers());
        assertEquals(6, c.godRayDivisor());
        assertEquals(FlareConfig.BEAMS_EXTREME, FlareConfig.beamsFromName("EXTREME"));
    }

    @Test
    void aFileSavedByAnOlderBuildGetsTheNewBeamAndFrameGuardDefaultsOnce() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sunflare-migrate");
        java.nio.file.Path file = dir.resolve("c.properties");
        java.nio.file.Files.writeString(file, "mode=3\nbeams=2\nstyle=pack\ntargetFps=90\n");
        FlareConfig c = new FlareConfig(file);
        c.load();
        assertEquals(FlareConfig.STRONG, c.mode(), "the flare setting is kept");
        assertEquals(false, c.realStyle(), "so is the style");
        assertEquals(FlareConfig.BEAMS_EXTREME, c.beams());
        assertEquals(80, c.targetFps());
        // after saving, the new values are the person's own and are kept
        c.setBeams(FlareConfig.BEAMS_OFF);
        c.setTargetFps(60);
        c.save();
        FlareConfig again = new FlareConfig(file);
        again.load();
        assertEquals(FlareConfig.BEAMS_OFF, again.beams());
        assertEquals(60, again.targetFps());
    }

    @Test
    void cheaperLeavesAreOnByDefaultCycleAndAreSaved() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempDirectory("sunflare-leaves").resolve("c.properties");
        FlareConfig c = new FlareConfig(file);
        assertEquals(FlareConfig.LEAVES_DEFAULT, c.leaves());
        assertEquals(2, FlareConfig.LEAVES_DEFAULT, "balanced");
        assertEquals(FlareConfig.LEAVES_MAX + 1, FlareConfig.LEAVES_NAMES.length);
        c.setLeaves(99);
        assertEquals(FlareConfig.LEAVES_DEFAULT, c.leaves(), "out of range is ignored");
        c.setLeaves(FlareConfig.LEAVES_MAX);
        assertEquals(0, c.cycleLeaves(), "wraps round to off");
        assertEquals(1, c.cycleLeaves());
        c.save();
        FlareConfig again = new FlareConfig(file);
        again.load();
        assertEquals(1, again.leaves());
        java.nio.file.Files.writeString(file, "version=2\nleaves=17\n");
        again.load();
        assertEquals(FlareConfig.LEAVES_MAX, again.leaves(), "a bad value is clamped");
    }

    @Test
    void cloudHelpersAndBlockUpdateSettingsHaveDefaultsAndAreSaved() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempDirectory("sunflare-render").resolve("c.properties");
        FlareConfig c = new FlareConfig(file);
        assertTrue(c.cloudFix(), "the clouds fix is on by default");
        assertEquals(ChunkThreads.AUTO, c.chunkHelpers());
        assertEquals(BlockUpdates.AUTO, c.blockUpdates());
        c.setCloudFix(false);
        c.setChunkHelpers(4);
        c.setBlockUpdates(BlockUpdates.INSTANT);
        c.save();
        String text = java.nio.file.Files.readString(file);
        assertTrue(text.contains("cloudFix=off"), text);
        assertTrue(text.contains("chunkHelpers=4"), text);
        assertTrue(text.contains("blockUpdates=instant"), text);
        FlareConfig again = new FlareConfig(file);
        again.load();
        assertFalse(again.cloudFix());
        assertEquals(4, again.chunkHelpers());
        assertEquals(BlockUpdates.INSTANT, again.blockUpdates());
    }

    @Test
    void aFileFromBeforeTheseSettingsKeepsTheNewDefaultsAndItsOwnValues() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempDirectory("sunflare-older").resolve("c.properties");
        java.nio.file.Files.writeString(file, "version=2\nmode=3\nbeams=3\nstyle=real\ntargetFps=90\nleaves=1\nperfLog=on\n");
        FlareConfig c = new FlareConfig(file);
        c.load();
        assertEquals(3, c.beams());
        assertEquals(90, c.targetFps());
        assertEquals(1, c.leaves());
        assertTrue(c.cloudFix());
        assertEquals(ChunkThreads.AUTO, c.chunkHelpers());
        assertEquals(BlockUpdates.AUTO, c.blockUpdates());
    }

    @Test
    void badValuesOfTheNewSettingsFallBack() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempDirectory("sunflare-bad").resolve("c.properties");
        FlareConfig c = new FlareConfig(file);
        java.nio.file.Files.writeString(file, "version=2\ncloudFix=maybe\nchunkHelpers=lots\nblockUpdates=turbo\n");
        c.load();
        assertTrue(c.cloudFix(), "anything but off is on");
        assertEquals(ChunkThreads.AUTO, c.chunkHelpers());
        assertEquals(BlockUpdates.AUTO, c.blockUpdates());
        java.nio.file.Files.writeString(file, "version=2\nchunkHelpers=5\nblockUpdates=SMOOTH\n");
        c.load();
        assertEquals(4, c.chunkHelpers(), "a number between two choices becomes the closest one");
        assertEquals(BlockUpdates.SMOOTH, c.blockUpdates(), "the name is not case sensitive");
        java.nio.file.Files.writeString(file, "version=2\nchunkHelpers=0\n");
        c.load();
        assertEquals(ChunkThreads.AUTO, c.chunkHelpers());
    }

    @Test
    void anOlderFileStillGetsTheNewSettingsRead() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempDirectory("sunflare-v1").resolve("c.properties");
        java.nio.file.Files.writeString(file, "mode=2\nbeams=1\ncloudFix=off\nchunkHelpers=3\nblockUpdates=instant\n");
        FlareConfig c = new FlareConfig(file);
        c.load();
        assertEquals(FlareConfig.BEAMS_EXTREME, c.beams(), "the beams of an old file are reset, as before");
        assertFalse(c.cloudFix(), "the new settings in it are still read");
        assertEquals(3, c.chunkHelpers());
        assertEquals(BlockUpdates.INSTANT, c.blockUpdates());
    }

    @Test
    void theSettersTakeTheClosestChoiceOrIgnoreNonsense() {
        FlareConfig c = new FlareConfig(java.nio.file.Path.of("unused.properties"));
        assertEquals(4, c.setChunkHelpers(5));
        assertEquals(ChunkThreads.AUTO, c.setChunkHelpers(-3));
        assertEquals(6, c.setChunkHelpers(60));
        assertEquals(BlockUpdates.INSTANT, c.setBlockUpdates(BlockUpdates.INSTANT));
        assertEquals(BlockUpdates.INSTANT, c.setBlockUpdates(9), "out of range is ignored");
        assertEquals(BlockUpdates.INSTANT, c.setBlockUpdates(-1), "out of range is ignored");
        assertFalse(c.setCloudFix(false));
        assertTrue(c.setCloudFix(true));
    }

    @Test
    void applyingHandsTheSettingsToTheCodeThatUsesThem() {
        FlareConfig c = new FlareConfig(java.nio.file.Path.of("unused.properties"));
        try {
            c.setCloudFix(false);
            c.setChunkHelpers(6);
            c.setBlockUpdates(BlockUpdates.SMOOTH);
            c.applyRenderSettings();
            assertFalse(CloudFar.enabled());
            assertEquals(6, ChunkThreads.setting());
            assertEquals(BlockUpdates.SMOOTH, BlockUpdates.mode());
            c.setCloudFix(true);
            c.setChunkHelpers(ChunkThreads.AUTO);
            c.setBlockUpdates(BlockUpdates.INSTANT);
            c.applyRenderSettings();
            assertTrue(CloudFar.enabled());
            assertEquals(ChunkThreads.AUTO, ChunkThreads.setting());
            assertEquals(BlockUpdates.INSTANT, BlockUpdates.mode());
        } finally {
            CloudFar.setEnabled(true);
            ChunkThreads.setSetting(ChunkThreads.AUTO);
            BlockUpdates.reset();
        }
    }
}
