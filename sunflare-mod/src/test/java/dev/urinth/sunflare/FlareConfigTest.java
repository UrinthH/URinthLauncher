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
        assertEquals(0.25, c.power(), 1e-12);
        assertEquals(FlareConfig.STRONG, c.cycle());
        assertEquals(0.40, c.power(), 1e-12);
        assertEquals(FlareConfig.OFF, c.cycle());
        assertEquals(0.0, c.power(), 1e-12);
        assertEquals(FlareConfig.SUBTLE, c.cycle());
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("urinth-sunflare.properties"));
        again.load();
        assertEquals(FlareConfig.SUBTLE, again.mode());
    }
}
