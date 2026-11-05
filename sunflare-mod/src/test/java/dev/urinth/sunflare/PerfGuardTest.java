package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The frame-rate guard when the effects have a cost that no quality setting can shrink (what a phone GPU does with the god rays),
 * the stutter counters and log, and the Perf Guard setting. No Minecraft classes.
 */
class PerfGuardTest {
    /** A pretend phone: a plain frame takes {@code base} ms; a full ray frame adds {@code full}, a reused one {@code reuse}. */
    private static final class Sim {
        final QualityGovernor g;
        final double base;
        double full;
        double reuse;

        Sim(double targetFps, double base, double full, double reuse) {
            this.g = new QualityGovernor(targetFps);
            this.base = base;
            this.full = full;
            this.reuse = reuse;
            for (int i = 0; i < 100; i++) g.frame(base, false);
        }

        /** One frame, as GodRays does it. @return how long the frame took */
        double step() {
            QualityGovernor.Plan p = g.plan(true);
            if (p == QualityGovernor.Plan.PAUSE) {
                g.frame(base, false);
                return base;
            }
            double ms = base + (p == QualityGovernor.Plan.FULL ? full : reuse);
            g.frame(ms, true);
            return ms;
        }

        /** Average frame rate over the next n frames. */
        double fps(int n) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += step();
            return 1000.0 / (sum / n);
        }
    }

    @Test
    void anEffectWithAFixedCostIsHeldToTheBudgetByReusingFramesOrPausing() {
        // 90 FPS plain; the rays cost 5 ms in a full frame whatever the quality is: that is what takes a phone from 90 to 60
        Sim s = new Sim(80, 1000.0 / 90.0, 5.0, 0.4);
        s.fps(6000); // let the guard settle
        double fps = s.fps(6000);
        assertTrue(fps >= 78.0, "90 FPS may become 80, not 60: " + fps);
    }

    @Test
    void anEffectThatIsFarTooExpensiveIsPausedAndTriedAgainEachTimeAfterALongerWait() {
        Sim s = new Sim(80, 1000.0 / 90.0, 12.0, 1.2);
        java.util.List<Integer> pauses = new java.util.ArrayList<>();
        int run = 0;
        int pausedFrames = 0;
        int total = 30000;
        for (int i = 0; i < total; i++) {
            s.step();
            if (s.g.paused()) {
                run++;
                if (i >= total / 2) pausedFrames++;
            } else if (run > 0) {
                pauses.add(run);
                run = 0;
            }
        }
        assertTrue(pauses.size() >= 3, "it tries again: " + pauses);
        assertTrue(pauses.get(1) > pauses.get(0), "each wait is longer: " + pauses);
        assertEquals(QualityGovernor.PAUSE_FRAMES, pauses.get(0).intValue() + 0, "the first pause is short");
        assertTrue(pausedFrames > (total / 2) * 0.6, "most of the time it stays out of the way: " + pausedFrames);
        double fps = s.fps(6000);
        assertTrue(fps >= 79.0, "the frame rate stays up: " + fps);
    }

    @Test
    void cheapEffectsStayOnEveryFrameAtFullQuality() {
        Sim s = new Sim(80, 1000.0 / 90.0, 0.8, 0.3);
        for (int i = 0; i < 20000; i++) {
            s.step();
            assertFalse(s.g.paused(), "never paused");
        }
        assertEquals(1, s.g.interval());
        assertEquals(1.0, s.g.quality(), 1e-9);
    }

    @Test
    void whenTheSceneGetsCheaperAgainTheEffectsComeBackFullyButSlowly() {
        Sim s = new Sim(80, 1000.0 / 90.0, 12.0, 1.2);
        s.fps(8000);
        assertTrue(s.g.paused() || s.g.interval() > 1, "heavy scene: held back");
        s.full = 0.8;
        s.reuse = 0.3;
        s.fps(30000);
        assertFalse(s.g.paused());
        assertEquals(1, s.g.interval(), "every frame again");
        assertEquals(1.0, s.g.quality(), 1e-9, "full quality again");
    }

    @Test
    void theFramesBetweenTwoFullOnesAreReusedAndAMissingPictureForcesAFullOne() {
        Sim s = new Sim(80, 1000.0 / 90.0, 12.0, 1.2);
        while (s.g.interval() < 2) s.step(); // stops as soon as the guard asks for every 2nd frame
        assertEquals(2, s.g.interval());
        assertFalse(s.g.paused());
        QualityGovernor.Plan first = s.g.plan(true);
        QualityGovernor.Plan second = s.g.plan(true);
        QualityGovernor.Plan third = s.g.plan(true);
        QualityGovernor.Plan fourth = s.g.plan(true);
        assertTrue(first != second && third != fourth && first == third && second == fourth, first + " " + second + " " + third + " " + fourth);
        assertTrue(first == QualityGovernor.Plan.FULL || second == QualityGovernor.Plan.FULL);
        // no picture to show (first frame, window resized): always a full frame
        for (int i = 0; i < 6; i++) assertEquals(QualityGovernor.Plan.FULL, s.g.plan(false));
    }

    @Test
    void withTheGuardOffNothingIsPausedOrReused() {
        QualityGovernor g = new QualityGovernor(0);
        for (int i = 0; i < 5000; i++) {
            assertEquals(QualityGovernor.Plan.FULL, g.plan(true));
            g.frame(40.0, true);
        }
        assertEquals(1, g.interval());
        assertEquals(1.0, g.quality(), 1e-9);
    }

    @Test
    void switchingTheGuardOffWhilePausedBringsTheEffectsBack() {
        Sim s = new Sim(80, 1000.0 / 90.0, 12.0, 1.2);
        s.fps(3000);
        assertTrue(s.g.paused() || s.g.interval() > 1);
        s.g.setTargetFps(0);
        s.g.frame(11.0, true);
        assertFalse(s.g.paused());
        assertEquals(1, s.g.interval());
        assertEquals(QualityGovernor.Plan.FULL, s.g.plan(true));
    }

    @Test
    void describeSaysWhatTheGuardIsDoing() {
        Sim s = new Sim(80, 1000.0 / 90.0, 12.0, 1.2);
        String start = s.g.describe();
        assertTrue(start.contains("every 1 frame"), start);
        s.fps(8000);
        String later = s.g.describe();
        assertTrue(later.contains("PAUSED") || later.contains("every 2") || later.contains("every 3") || later.contains("every 4"), later);
    }

    // ---- the stutter counters -----------------------------------------------------------------------------------------

    @Test
    void aSlowFrameAmongSmoothOnesIsAHitchAndAVerySlowOneIsAFreeze() {
        FrameStats f = new FrameStats();
        for (int i = 0; i < 300; i++) assertEquals(FrameStats.NORMAL, f.frame(11.0));
        assertEquals(FrameStats.NORMAL, f.frame(30.0), "a bit slow is not a hitch");
        assertEquals(FrameStats.HITCH, f.frame(60.0));
        assertEquals(FrameStats.FREEZE, f.frame(150.0));
        assertEquals(1, f.hitches());
        assertEquals(1, f.freezes());
        assertEquals(150.0, f.worstMillis(), 1e-9);
    }

    @Test
    void aSceneThatIsSlowAnywayHasNoHitchesJustBecauseFramesAreLong() {
        FrameStats f = new FrameStats();
        for (int i = 0; i < 300; i++) f.frame(38.0);
        assertEquals(FrameStats.NORMAL, f.frame(45.0), "45 ms among 38 ms frames is not a stutter");
        assertEquals(0, f.hitches());
    }

    @Test
    void theLoadingFramesAtTheStartAreNotCounted() {
        FrameStats f = new FrameStats();
        for (int i = 0; i < FrameStats.WARMUP - 1; i++) assertEquals(FrameStats.NORMAL, f.frame(500.0));
        assertEquals(0, f.hitches() + f.freezes());
    }

    @Test
    void averageAndOnePercentLowFollowTheLastSixHundredFrames() {
        FrameStats f = new FrameStats();
        for (int i = 0; i < FrameStats.WINDOW; i++) f.frame(i % 100 == 0 ? 50.0 : 10.0);
        assertEquals(1000.0 / ((6 * 50.0 + 594 * 10.0) / 600.0), f.avgFps(), 0.01);
        assertEquals(20.0, f.lowFps(), 0.01, "the slowest hundredth took 50 ms");
        for (int i = 0; i < FrameStats.WINDOW; i++) f.frame(20.0);
        assertEquals(50.0, f.avgFps(), 1e-6, "old frames are forgotten");
        f.reset();
        assertEquals(0, f.frames());
        assertEquals(0.0, f.avgFps(), 0.0);
    }

    // ---- the stutter log ----------------------------------------------------------------------------------------------

    private static String waitForLog(Path file, String needle) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (Files.exists(file)) {
                String text = Files.readString(file);
                if (text.contains(needle)) return text;
            }
            Thread.sleep(50);
        }
        return Files.exists(file) ? Files.readString(file) : "";
    }

    @Test
    void aFreezeIsWrittenToTheLogWithGcHeapAndGuardState() throws Exception {
        Path dir = Files.createTempDirectory("urinth-perf");
        Path file = dir.resolve("logs").resolve("urinth-perf.log");
        PerfMonitor m = new PerfMonitor(file, () -> true, () -> "guard says hello");
        for (int i = 0; i < 300; i++) m.frame(11.0);
        assertFalse(Files.exists(file), "smooth frames write nothing");
        m.frame(180.0);
        String text = waitForLog(file, "FREEZE");
        assertTrue(text.contains("FREEZE 180.0 ms"), text);
        assertTrue(text.contains("GC +"), text);
        assertTrue(text.contains("heap "), text);
        assertTrue(text.contains("guard says hello"), text);
        assertEquals(1, m.stats().freezes());
    }

    @Test
    void theLogIsRateLimitedCanBeSwitchedOffAndNeverGrowsPastItsLimit() throws Exception {
        Path dir = Files.createTempDirectory("urinth-perf2");
        Path file = dir.resolve("p.log");
        boolean[] on = {true};
        PerfMonitor m = new PerfMonitor(file, () -> on[0], () -> "s");
        for (int i = 0; i < 300; i++) m.frame(11.0);
        m.frame(200.0);
        m.frame(210.0); // within a quarter second: counted, not written
        m.frame(220.0);
        waitForLog(file, "FREEZE");
        Thread.sleep(400);
        m.frame(230.0);
        String text = waitForLog(file, "230.0");
        assertEquals(2, text.lines().count(), text);
        assertTrue(text.contains("2 more hitches"), text);
        assertEquals(4, m.stats().freezes(), "all of them are counted");
        on[0] = false;
        Thread.sleep(400);
        m.frame(240.0);
        Thread.sleep(200);
        assertEquals(2, Files.readString(file).lines().count(), "off: nothing more is written");
        // a full file is left alone
        Files.writeString(file, "x".repeat((int) PerfMonitor.MAX_LOG_BYTES + 10));
        on[0] = true;
        Thread.sleep(400);
        m.frame(250.0);
        Thread.sleep(300);
        assertEquals((int) PerfMonitor.MAX_LOG_BYTES + 10, Files.size(file));
    }

    @Test
    void theSummaryHasTheNumbersTheCommandShows() {
        PerfMonitor m = new PerfMonitor(null, () -> false, () -> "state here");
        for (int i = 0; i < 300; i++) m.frame(11.0);
        m.frame(150.0);
        String[] lines = m.summary();
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("FPS avg") && lines[0].contains("1% low") && lines[0].contains("freezes 1"), lines[0]);
        assertTrue(lines[1].contains("GC ") && lines[1].contains("heap ") && lines[1].contains("state here"), lines[1]);
    }

    // ---- the Perf Guard setting ---------------------------------------------------------------------------------------

    @Test
    void thePerfGuardButtonCyclesThroughTheFrameRatesAndKeepsTheLogSetting() throws IOException {
        Path dir = Files.createTempDirectory("urinth-guard");
        FlareConfig c = new FlareConfig(dir.resolve("c.properties"));
        assertEquals(80, c.targetFps());
        assertEquals(90, c.cycleTargetFps());
        assertEquals(0, c.cycleTargetFps(), "after the highest comes Off");
        assertEquals(60, c.cycleTargetFps());
        assertEquals(72, c.cycleTargetFps());
        assertEquals(80, c.cycleTargetFps());
        assertEquals("Off", FlareConfig.fpsName(0));
        assertEquals("80 FPS", FlareConfig.fpsName(80));
        assertTrue(c.perfLog(), "the stutter log is on by default");
        c.setPerfLog(false);
        c.setTargetFps(72);
        c.save();
        FlareConfig again = new FlareConfig(dir.resolve("c.properties"));
        again.load();
        assertFalse(again.perfLog());
        assertEquals(72, again.targetFps());
        // a custom value in between (set with /sunflare fps) goes on to the next choice
        c.setTargetFps(75);
        assertEquals(80, c.cycleTargetFps());
    }

    @Test
    void thePerfGuardButtonHasItsOwnSpotUnderTheLeavesButtonAndItsLabel() {
        for (int width : new int[] {320, 400, 537, 806}) {
            int[] lv = SettingsScreens.bounds(SettingsScreens.Kind.SODIUM, width, SettingsScreens.LEAVES);
            int[] pf = SettingsScreens.bounds(SettingsScreens.Kind.SODIUM, width, SettingsScreens.PERF);
            assertEquals(lv[0], pf[0]);
            assertTrue(pf[1] >= lv[1] + lv[3], "no overlap at " + width);
            assertTrue(pf[0] + pf[2] <= width - 4);
        }
        assertEquals("Perf Guard: 80 FPS", SettingsScreens.label(SettingsScreens.PERF, 80, 112));
        assertEquals("Guard: Off", SettingsScreens.label(SettingsScreens.PERF, 0, 86));
    }
}
