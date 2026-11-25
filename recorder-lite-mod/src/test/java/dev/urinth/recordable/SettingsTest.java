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
            s.recordSound = false;
            s.saveToGallery = false;
            s.maxFileMb = 2000;
            s.replaySeconds = 60;
            s.autoClip = true;
            s.overlayCorner = 3;
            s.overlayScale = 150;
            s.microphone = true;
            Path f = dir.resolve("sub").resolve("settings.json");
            s.save(f);
            Settings t = Settings.load(f);
            assertEquals(Settings.Preset.QUALITY, t.preset);
            assertEquals(24, t.fpsCap);
            assertFalse(t.hardwareEncoder);
            assertFalse(t.showIndicator);
            assertTrue(t.swapChroma);
            assertEquals(777, t.minFreeMb);
            assertFalse(t.recordSound);
            assertFalse(t.saveToGallery);
            assertEquals(2000, t.maxFileMb);
            assertEquals(60, t.replaySeconds);
            assertTrue(t.autoClip);
            assertEquals(3, t.overlayCorner);
            assertEquals(150, t.overlayScale);
            assertTrue(t.microphone);
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
            assertEquals(90, s.fps(), "the frame rate never exceeds the highest choice");
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
        assertEquals(30, s.fps(), "the preset decides until the player picks something");
        int[] seen = new int[Governor.TIERS.length];
        for (int i = 0; i < seen.length; i++) {
            s.cycleFps();
            seen[i] = s.fps();
        }
        assertEquals(Arrays.toString(Governor.TIERS), Arrays.toString(seen), "90 comes first, then every lower choice");
        assertEquals(90, seen[0]);
        s.cycleFps();
        assertEquals(0, s.fpsCap, "after the lowest choice it is back to what the preset says");
        assertEquals(30, s.fps());
    }

    @Test
    void moreFramesPerSecondGetMoreBitsButNotInProportion() {
        Settings s = new Settings();
        assertEquals(4000, s.kbps());
        s.fpsCap = 24;
        assertEquals(4000, s.kbps(), "never fewer bits than the preset says");
        s.fpsCap = 60;
        assertEquals(5600, s.kbps());
        s.fpsCap = 90;
        assertEquals(7200, s.kbps());
        s.fpsCap = 45;
        assertTrue(s.kbps() > 4000 && s.kbps() < 5600, "" + s.kbps());
    }

    @Test
    void aFileFromTheFirstReleaseGetsAFreshChanceForTheHardwareEncoder() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Settings old = Settings.load(write(dir, "{ \"hardwareEncoder\": false, \"preset\": \"quality\" }"));
            assertTrue(old.hardwareEncoder, "the first release may have switched it off after a crash that was fixed since");
            assertEquals(Settings.Preset.QUALITY, old.preset);
            assertEquals(Settings.VERSION, old.version);
            Settings current = Settings.load(write(dir, "{ \"version\": " + Settings.VERSION + ", \"hardwareEncoder\": false }"));
            assertFalse(current.hardwareEncoder, "a choice made with this version stays");
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void theOtherButtonsCycleThroughTheirChoices() {
        Settings s = new Settings();
        int[] replay = new int[Settings.REPLAY_CHOICES.length];
        for (int i = 0; i < replay.length; i++) {
            s.cycleReplay();
            replay[i] = s.replaySeconds;
        }
        assertEquals(30, replay[0]);
        assertEquals(0, replay[replay.length - 1], "it wraps back to off");
        int[] max = new int[Settings.MAX_FILE_CHOICES.length];
        for (int i = 0; i < max.length; i++) {
            s.cycleMaxFile();
            max[i] = s.maxFileMb;
        }
        assertEquals(500, max[0]);
        assertEquals(0, max[max.length - 1], "it wraps back to no limit");
        for (int i = 0; i < 4; i++) {
            s.cycleCorner();
        }
        assertEquals(0, s.overlayCorner, "four corners and round again");
        int steps = 0;
        do {
            s.cycleScale();
            steps++;
            assertTrue(s.overlayScale >= 50 && s.overlayScale <= 200, "" + s.overlayScale);
        } while (s.overlayScale != 100 && steps < 20);
        assertTrue(steps < 20, "the size cycle comes back to 100");
    }

    @Test
    void overlayAndLimitValuesAreBroughtBackIntoRange() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Settings s = Settings.load(write(dir, "{\"overlayCorner\": 9, \"overlayScale\": 5000, \"replaySeconds\": 99999, \"maxFileMb\": -3}"));
            assertEquals(3, s.overlayCorner);
            assertEquals(200, s.overlayScale);
            assertEquals(300, s.replaySeconds);
            assertEquals(0, s.maxFileMb);
        } finally {
            TestFiles.deleteTree(dir);
        }
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
