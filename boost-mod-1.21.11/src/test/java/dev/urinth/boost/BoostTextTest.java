package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class BoostTextTest {
    private static boolean any(List<String> lines, String part) {
        for (String line : lines) {
            if (line.contains(part)) return true;
        }
        return false;
    }

    @Test
    public void aFreshAutomaticRuntimeSaysItIsWaitingAndTakingNothingAway() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        List<String> lines = BoostText.status(runtime);
        assertEquals("URinth Boost: automatic, level now off", lines.get(0));
        assertTrue(lines.get(1).contains("no frame rate reading yet"));
        assertTrue(lines.get(1).contains("aim 50"));
        assertTrue(any(lines, "Nothing is taken away right now"));
        assertEquals(BoostText.COMMANDS, lines.get(lines.size() - 1));
    }

    @Test
    public void aHeldModeListsWhatItTakesAway() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        List<String> lines = BoostText.status(runtime);
        assertEquals("URinth Boost: held at max", lines.get(0));
        assertTrue(any(lines, "at most 48 new particles per tick"));
        assertTrue(any(lines, "new particles beyond 20 blocks"));
        assertTrue(any(lines, "dropped items beyond 14 blocks"));
        assertTrue(any(lines, "Players themselves are always drawn."));
        assertFalse(any(lines, "aim"));
    }

    @Test
    public void lightOnlyMentionsWhatItReallyLimits() {
        String text = BoostText.describe(Limits.forLevel(1));
        assertTrue(text.contains("at most 300 new particles per tick"));
        assertTrue(text.contains("name tags beyond 56 blocks"));
        assertFalse(text.contains("small mobs"));
        assertFalse(text.contains("armor and held items"));
    }

    @Test
    public void aSwitchedOffModSaysWhy() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        runtime.disable("the entity hook failed: boom");
        List<String> lines = BoostText.status(runtime);
        assertTrue(lines.get(0).contains("switched itself off"));
        assertEquals("Reason: the entity hook failed: boom", lines.get(1));
        assertEquals(2, lines.size());
    }

    @Test
    public void theLastDecisionOfTheAutomaticModeIsShown() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        for (int second = 0; second <= 8; second++) runtime.tick(second * 1000L, 10, true);
        List<String> lines = BoostText.status(runtime);
        assertTrue(any(lines, "Last decision: "));
        assertTrue(any(lines, "trying light"));
    }

    @Test
    public void countsUseThousandsSeparators() {
        BoostStats stats = new BoostStats();
        for (int i = 0; i < 1234; i++) stats.call(BoostStats.PARTICLE);
        stats.act(BoostStats.PARTICLE);
        String text = BoostText.counts(stats);
        assertTrue(text.contains("particles 1,234 / 1"), text);
        assertTrue(text.contains("entities 0 / 0"), text);
    }

    @Test
    public void aBenchResultWithAGainSaysByHowMuch() {
        Bench.Result result = new Bench.Result(40, 50, 38, 42, 48, 52, 0.02, 0.02);
        List<String> lines = BoostText.benchResult(result);
        assertTrue(any(lines, "Boost off: 40 fps (between 38 and 42)"));
        assertTrue(any(lines, "Boost max: 50 fps (between 48 and 52)"));
        assertTrue(any(lines, "25% faster here"));
        assertTrue(any(lines, "not a promise"));
    }

    @Test
    public void aBenchResultWithNoDifferenceSaysSoHonestly() {
        Bench.Result result = new Bench.Result(45, 45, 44, 46, 44, 46, 0.01, 0.01);
        List<String> lines = BoostText.benchResult(result);
        assertTrue(any(lines, "No measurable difference"));
        assertFalse(any(lines, "faster here"));
    }

    @Test
    public void anUnstableBenchResultIsNotPresentedAsAGain() {
        Bench.Result result = new Bench.Result(40, 60, 30, 50, 50, 70, 0.3, 0.02);
        List<String> lines = BoostText.benchResult(result);
        assertTrue(any(lines, "not reliable"));
        assertFalse(any(lines, "faster here"));
    }

    @Test
    public void aBenchWithoutReadingsHasNoResult() {
        Bench.Result result = new Bench.Result(0, 0, 0, 0, 0, 0, 0, 0);
        List<String> lines = BoostText.benchResult(result);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("no frame rate readings"));
    }

    @Test
    public void theProgressLineShowsSecondsAndWhichSideIsRunning() {
        assertEquals("Boost test: 12 s left, stand still (now max)", BoostText.benchProgress(12, BoostLevel.MAX));
    }
}
