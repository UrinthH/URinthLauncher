package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(240, c.setTargetFps(999));
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
            assertTrue(c.godRayDivisor() >= 3 && c.godRayDivisor() <= 6);
            last = c.godRayDivisor();
        }
        c.setBeams(FlareConfig.BEAMS_EXTREME);
        assertEquals(12, c.godRayLayers());
        assertEquals(4, c.godRayDivisor());
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
}
