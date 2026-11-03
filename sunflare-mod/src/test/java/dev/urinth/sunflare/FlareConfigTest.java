package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void beamRangeDefaultsToTenChunksIsClampedAndSaved() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sunflare-range");
        FlareConfig c = new FlareConfig(dir.resolve("c.properties"));
        assertEquals(10, c.beamRangeChunks());
        assertEquals(10, c.setBeamRange(99));
        assertEquals(2, c.setBeamRange(-5));
        c.setBeamRange(6);
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("c.properties"));
        again.load();
        assertEquals(6, again.beamRangeChunks());
    }
}
