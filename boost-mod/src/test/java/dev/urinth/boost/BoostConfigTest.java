package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void theAimFollowsTheScreenWhenItsRateIsKnown() {
        assertEquals(85, BoostConfig.targetForScreen(90));
        assertEquals(56, BoostConfig.targetForScreen(60));
        assertEquals(113, BoostConfig.targetForScreen(120));
        assertEquals(135, BoostConfig.targetForScreen(144));
        assertEquals(BoostConfig.MAX_TARGET, BoostConfig.targetForScreen(480));
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.targetForScreen(0));
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.targetForScreen(-1));
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.targetForScreen(29));
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.targetForScreen(9000));
    }

    @Test
    public void theRefreshRateTextIsReadCarefully() {
        assertEquals(90, BoostConfig.screenHz("90"));
        assertEquals(90, BoostConfig.screenHz(" 90 "));
        assertEquals(0, BoostConfig.screenHz(null));
        assertEquals(0, BoostConfig.screenHz(""));
        assertEquals(0, BoostConfig.screenHz("fast"));
        assertEquals(0, BoostConfig.screenHz("90.5"));
        assertEquals(0, BoostConfig.screenHz("-5"));
        assertEquals(0, BoostConfig.screenHz("29"));
        assertEquals(0, BoostConfig.screenHz("100000"));
    }

    @Test
    public void aFileWithoutAnAimUsesTheScreenAndANumberThePlayerWroteStays() {
        assertEquals(85, BoostConfig.parse(null, 90).targetFps);
        assertEquals(85, BoostConfig.parse("mode=max\n", 90).targetFps);
        assertEquals(85, BoostConfig.parse("target-fps=fast\n", 90).targetFps);
        assertEquals(70, BoostConfig.parse("target-fps=70\n", 90).targetFps);
        assertEquals(75, BoostConfig.parse("mode=max\ntarget-fps=75\n", 90).targetFps, "a changed file is not the old default");
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.parse(null, 0).targetFps);
        assertEquals(BoostConfig.DEFAULT_TARGET, BoostConfig.parse("mode=max\n", 0).targetFps);
    }

    @Test
    public void theUntouchedFileOfTheFirstVersionFollowsTheScreenAndAsksToBeRewritten() {
        BoostConfig.Settings known = BoostConfig.parse(BoostConfig.LEGACY_DEFAULT_TEXT, 90);
        assertEquals(85, known.targetFps);
        assertTrue(known.upgraded);
        assertEquals(BoostMode.AUTO, known.mode);
        assertEquals(BoostConfig.PLAYERS_AUTO, known.players);

        BoostConfig.Settings unknown = BoostConfig.parse(BoostConfig.LEGACY_DEFAULT_TEXT, 0);
        assertEquals(75, unknown.targetFps);
        assertFalse(unknown.upgraded, "without the screen's rate there is nothing to follow");

        assertFalse(BoostConfig.parse("mode=auto\ntarget-fps=75\n", 90).upgraded);
        assertFalse(BoostConfig.parse(null, 90).upgraded);
        assertFalse(BoostConfig.parse(BoostConfig.render(BoostMode.AUTO, 85, BoostConfig.PLAYERS_AUTO), 90).upgraded);
    }

    @Test
    public void theFileWrittenForAScreenReadsBackTheSame() {
        BoostConfig.Settings s = BoostConfig.parse(BoostConfig.render(BoostMode.AUTO, 85, BoostConfig.PLAYERS_AUTO), 90);
        assertEquals(85, s.targetFps);
        assertEquals(85, BoostConfig.parse(BoostConfig.render(BoostMode.AUTO, 85, BoostConfig.PLAYERS_AUTO), 60).targetFps,
                "a number in the file wins over the screen");
    }
}
