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


    @Test
    public void playersDefaultToAutoAlsoForOldFilesWithoutTheKey() {
        assertEquals(BoostConfig.PLAYERS_AUTO, BoostConfig.parse(null).players);
        assertEquals(BoostConfig.PLAYERS_AUTO, BoostConfig.parse(BoostConfig.DEFAULT_TEXT).players);
        assertEquals(BoostConfig.PLAYERS_AUTO, BoostConfig.parse("mode=max\ntarget-fps=60\n").players);
    }

    @Test
    public void playersCanBeAutoOffOrANumberAndSurviveWritingAndReading() {
        int[] settings = {BoostConfig.PLAYERS_AUTO, BoostConfig.PLAYERS_OFF, BoostConfig.MIN_PLAYERS, 25, BoostConfig.MAX_PLAYERS};
        for (int players : settings) {
            BoostConfig.Settings s = BoostConfig.parse(BoostConfig.render(BoostMode.STRONG, 61, players));
            assertEquals(players, s.players);
            assertEquals(BoostMode.STRONG, s.mode);
            assertEquals(61, s.targetFps);
        }
        assertEquals("auto", BoostConfig.playersWord(BoostConfig.PLAYERS_AUTO));
        assertEquals("off", BoostConfig.playersWord(BoostConfig.PLAYERS_OFF));
        assertEquals("25", BoostConfig.playersWord(25));
    }

    @Test
    public void playerNumbersAreKeptInsideTheLimitsAndBadOnesAreIgnored() {
        assertEquals(BoostConfig.MIN_PLAYERS, BoostConfig.parse("players=1").players);
        assertEquals(BoostConfig.MAX_PLAYERS, BoostConfig.parse("players=99999").players);
        assertEquals(BoostConfig.PLAYERS_OFF, BoostConfig.parse("players= OFF \n").players);
        assertEquals(BoostConfig.PLAYERS_AUTO, BoostConfig.parse("players=many").players);
        assertEquals(BoostMode.MAX, BoostConfig.parse("mode=max\nplayers=many\n").mode);
        assertEquals(BoostConfig.MIN_PLAYERS, BoostConfig.clampPlayers(0));
        assertEquals(BoostConfig.MAX_PLAYERS, BoostConfig.clampPlayers(1000));
    }

    @Test
    public void aBadTargetDoesNotCostTheOtherSettings() {
        BoostConfig.Settings s = BoostConfig.parse("mode=light\ntarget-fps=fast\nplayers=30\n");
        assertEquals(BoostMode.LIGHT, s.mode);
        assertEquals(BoostConfig.DEFAULT_TARGET, s.targetFps);
        assertEquals(30, s.players);
    }
}
