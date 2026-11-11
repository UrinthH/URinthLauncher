package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class BoostConfigTest {
    @Test
    public void aMissingFileGivesTheDefaults() {
        BoostConfig.Settings s = BoostConfig.parse(null);
        assertEquals(BoostMode.AUTO, s.mode);
        assertEquals(BoostConfig.DEFAULT_TARGET, s.targetFps);
    }

    @Test
    public void theDefaultTextReadsBackAsTheDefaults() {
        BoostConfig.Settings s = BoostConfig.parse(BoostConfig.DEFAULT_TEXT);
        assertEquals(BoostMode.AUTO, s.mode);
        assertEquals(BoostConfig.DEFAULT_TARGET, s.targetFps);
    }

    @Test
    public void readsModeAndTarget() {
        BoostConfig.Settings s = BoostConfig.parse("mode=strong\ntarget-fps=70\n");
        assertEquals(BoostMode.STRONG, s.mode);
        assertEquals(70, s.targetFps);
    }

    @Test
    public void everyModeSurvivesWritingAndReading() {
        for (BoostMode mode : BoostMode.values()) {
            BoostConfig.Settings s = BoostConfig.parse(BoostConfig.render(mode, 61));
            assertEquals(mode, s.mode);
            assertEquals(61, s.targetFps);
        }
    }

    @Test
    public void targetsAreKeptInsideTheLimits() {
        assertEquals(BoostConfig.MIN_TARGET, BoostConfig.parse("target-fps=1").targetFps);
        assertEquals(BoostConfig.MAX_TARGET, BoostConfig.parse("target-fps=9999").targetFps);
        assertEquals(BoostConfig.MIN_TARGET, BoostConfig.clampTarget(-5));
        assertEquals(BoostConfig.MAX_TARGET, BoostConfig.clampTarget(100000));
    }

    @Test
    public void aDamagedFileNeverBreaksAnything() {
        assertEquals(BoostMode.AUTO, BoostConfig.parse("mode=banana").mode);
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.parse("target-fps=fast").targetFps);
        assertEquals(BoostMode.MAX, BoostConfig.parse("mode=max\ntarget-fps=fast").mode);
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.parse("\u0000\u0001 not a file").targetFps);
        assertEquals(BoostMode.AUTO, BoostConfig.parse("").mode);
    }

    @Test
    public void commentsAndOtherKeysAreIgnored() {
        BoostConfig.Settings s = BoostConfig.parse("# mode=max\nother=1\nmode=light\n");
        assertEquals(BoostMode.LIGHT, s.mode);
    }
}
