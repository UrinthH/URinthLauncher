package dev.urinth.recordable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class SettingsTest {
    private static Path write(Path dir, String text) throws Exception {
        Path f = dir.resolve("settings.json");
        Files.write(f, text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    void defaultsAreTheBalancedPresetWithTheHardwareEncoder() {
        Settings s = new Settings();
        assertEquals(Settings.Preset.BALANCED, s.preset);
        assertEquals(480, s.height());
        assertEquals(30, s.fps());
        assertEquals(4000, s.kbps());
        assertTrue(s.hardwareEncoder);
        assertTrue(s.showIndicator);
        assertFalse(s.swapChroma);
    }

    @Test
    void noPresetAsksForMoreThanThirtyFramesPerSecond() {
        for (Settings.Preset p : Settings.Preset.values()) {
            assertTrue(p.fps <= 30, p.id);
            assertTrue(p.fps >= Governor.TIERS[Governor.TIERS.length - 1], p.id);
        }
    }

    @Test
    void saveAndLoadKeepEveryChoice() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Settings s = new Settings();
            s.preset = Settings.Preset.QUALITY;
            s.fpsCap = 24;
            s.hardwareEncoder = false;
            s.showIndicator = false;
            s.swapChroma = true;
            s.minFreeMb = 777;
            Path f = dir.resolve("sub").resolve("settings.json");
            s.save(f);
            Settings t = Settings.load(f);
            assertEquals(Settings.Preset.QUALITY, t.preset);
            assertEquals(24, t.fpsCap);
            assertFalse(t.hardwareEncoder);
            assertFalse(t.showIndicator);
            assertTrue(t.swapChroma);
            assertEquals(777, t.minFreeMb);
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void aMissingOrDamagedFileGivesTheDefaults() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            assertEquals(Settings.Preset.BALANCED, Settings.load(dir.resolve("nothing.json")).preset);
            for (String bad : new String[]{"", "{{{", "[1,2,3]", "{\"preset\": ", "not json at all", "{\"preset\" \"ultra\"}"}) {
                Settings s = Settings.load(write(dir, bad));
                assertEquals(Settings.Preset.BALANCED, s.preset, "for: " + bad);
                assertTrue(s.hardwareEncoder, "for: " + bad);
            }
            byte[] big = new byte[70 * 1024];
            Arrays.fill(big, (byte) ' ');
            Path f = dir.resolve("big.json");
            Files.write(f, big);
            assertEquals(Settings.Preset.BALANCED, Settings.load(f).preset);
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void aPartialFileChangesOnlyWhatItSays() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Settings s = Settings.load(write(dir, "{ \"preset\": \"ULTRA\" }"));
            assertEquals(Settings.Preset.ULTRA, s.preset);
            assertEquals(1080, s.height());
            assertTrue(s.hardwareEncoder);
            assertEquals(300, s.minFreeMb);
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void numbersOutOfRangeAreBroughtBack() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Settings s = Settings.load(write(dir, "{\"fpsCap\": 999, \"minFreeMb\": -5, \"preset\": \"nonsense\"}"));
            assertEquals(30, s.fps(), "the frame rate never exceeds what the preset allows");
            assertEquals(50, s.minFreeMb);
            assertEquals(Settings.Preset.BALANCED, s.preset);
            Settings t = Settings.load(write(dir, "{\"fpsCap\": \"abc\", \"hardwareEncoder\": \"maybe\"}"));
            assertEquals(0, t.fpsCap);
            assertTrue(t.hardwareEncoder);
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void theFrameRateButtonWalksDownTheChoicesAndWrapsToThePresetValue() {
        Settings s = new Settings();
        int[] seen = new int[Governor.TIERS.length];
        for (int i = 0; i < Governor.TIERS.length; i++) {
            seen[i] = s.fps();
            s.cycleFps();
        }
        assertEquals(Governor.TIERS[0], seen[0]);
        for (int i = 1; i < seen.length; i++) {
            assertTrue(seen[i] < seen[i - 1], Arrays.toString(seen));
        }
        assertEquals(0, s.fpsCap, "after the lowest choice it is back to what the preset says");
        assertEquals(30, s.fps());
    }

    @Test
    void presetsCycleAndAreFoundById() {
        Settings.Preset p = Settings.Preset.PERFORMANCE;
        for (int i = 0; i < Settings.Preset.values().length; i++) {
            assertEquals(p, Settings.Preset.byId(p.id.toUpperCase()));
            p = p.next();
        }
        assertEquals(Settings.Preset.PERFORMANCE, p);
        assertEquals(Settings.Preset.BALANCED, Settings.Preset.byId("???"));
    }

    @Test
    void theConfigFolderIsInsideTheGameFolder() {
        Path game = Path.of("game");
        assertTrue(Settings.configDir(game).startsWith(game));
    }
}
