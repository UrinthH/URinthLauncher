package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SchedulerPolicyTest {
    @Test
    public void startsWithNothingLowered() {
        assertEquals(SchedulerPolicy.LEVEL_NONE, new SchedulerPolicy().level);
    }

    @Test
    public void risesAtOnceAndSkipsLevels() {
        SchedulerPolicy policy = new SchedulerPolicy();
        policy.step(0.07);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
        policy.step(0.20);
        assertEquals(SchedulerPolicy.LEVEL_STRONG, policy.level);

        SchedulerPolicy jump = new SchedulerPolicy();
        jump.step(0.5);
        assertEquals(SchedulerPolicy.LEVEL_STRONG, jump.level);
    }

    @Test
    public void fallsOneLevelOnlyAfterSeveralCalmReadings() {
        SchedulerPolicy policy = new SchedulerPolicy();
        policy.step(0.3);
        for (int i = 0; i < SchedulerPolicy.CALM_SAMPLES_TO_EASE - 1; i++) policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_STRONG, policy.level);
        policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
        for (int i = 0; i < SchedulerPolicy.CALM_SAMPLES_TO_EASE; i++) policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_NONE, policy.level);
    }

    @Test
    public void aBusyReadingResetsTheCalmCount() {
        SchedulerPolicy policy = new SchedulerPolicy();
        policy.step(0.07);
        for (int i = 0; i < SchedulerPolicy.CALM_SAMPLES_TO_EASE - 1; i++) policy.step(0.01);
        policy.step(0.04); // not calm enough to count, not busy enough to rise
        policy.step(0.01);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
    }

    @Test
    public void doesNotFlapAroundTheThreshold() {
        SchedulerPolicy policy = new SchedulerPolicy();
        for (int i = 0; i < 20; i++) policy.step(i % 2 == 0 ? 0.065 : 0.05);
        assertEquals(SchedulerPolicy.LEVEL_MILD, policy.level);
    }

    @Test
    public void waitFractionIsClamped() {
        assertEquals(0.1, SchedulerPolicy.waitFraction(0, 150_000_000L, 1_500_000_000L), 1e-9);
        assertEquals(0.0, SchedulerPolicy.waitFraction(500, 100, 1_000L), 0.0);
        assertEquals(0.0, SchedulerPolicy.waitFraction(0, 100, 0), 0.0);
        assertEquals(1.0, SchedulerPolicy.waitFraction(0, 9_000_000_000L, 1_000_000_000L), 0.0);
    }

    @Test
    public void parsesSchedstat() {
        assertEquals(5678, SchedulerPolicy.parseWaitNanos("1234 5678 90\n"));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos(""));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos("1234"));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos("a b c"));
        assertEquals(-1, SchedulerPolicy.parseWaitNanos(null));
    }

    @Test
    public void picksTheRightThreads() {
        assertTrue(SchedulerPolicy.isRenderThread("Render thread"));
        assertFalse(SchedulerPolicy.isRenderThread("Server thread"));
        assertTrue(SchedulerPolicy.isHelperThread("Worker-Main-3"));
        assertTrue(SchedulerPolicy.isHelperThread("IO-Worker-1"));
        assertFalse(SchedulerPolicy.isHelperThread("Render thread"));
        assertFalse(SchedulerPolicy.isHelperThread("Server thread"));
        assertFalse(SchedulerPolicy.isHelperThread("Netty Client IO"));
        assertFalse(SchedulerPolicy.isHelperThread("C2 CompilerThre"));
        assertFalse(SchedulerPolicy.isHelperThread(null));
    }

    @Test
    public void niceValues() {
        assertEquals(0, SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_NONE));
        assertEquals(4, SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_MILD));
        assertEquals(10, SchedulerPolicy.niceFor(SchedulerPolicy.LEVEL_STRONG));
    }

    @Test
    public void voiceChatThreadsAreRecognisedLoosely() {
        assertTrue(SchedulerPolicy.isVoiceThread("VoiceChatThread"));
        assertTrue(SchedulerPolicy.isVoiceThread("Microphone Thread"));
        assertTrue(SchedulerPolicy.isVoiceThread("audio capture"));
        assertTrue(SchedulerPolicy.isVoiceThread("SoundSourceThre"));
        assertFalse(SchedulerPolicy.isVoiceThread("Render thread"));
        assertFalse(SchedulerPolicy.isVoiceThread("Worker-Main-1"));
        assertFalse(SchedulerPolicy.isVoiceThread("Dynamic-1"));
        assertFalse(SchedulerPolicy.isVoiceThread(null));
    }

    @Test
    public void voiceThreadsAreNeverTreatedAsRenderOrHelperThreads() {
        assertFalse(SchedulerPolicy.isRenderThread("VoiceChatThread"));
        assertFalse(SchedulerPolicy.isHelperThread("VoiceChatThread"));
        assertTrue(SchedulerPolicy.VOICE_NICE < 0);
    }

    @Test
    public void voiceThreadsStayBelowTheRenderThread() {
        // The render thread is raised to -4 (THREAD_PRIORITY_DISPLAY).
        assertTrue(SchedulerPolicy.VOICE_NICE > -4);
        assertTrue(SchedulerPolicy.VOICE_NICE < 0);
    }

    @Test
    public void cpuTicksAreReadFromAThreadStatLine() {
        String line = "1234 (Render thread) S 1 2 3 0 -1 4194560 100 0 5 0 700 300 0 0 20 0 80 0 1234 5 6";
        assertEquals(1000L, SchedulerPolicy.parseCpuTicks(line));
        // A name with spaces and brackets must not shift the fields.
        String odd = "99 (Worker (1) x) R 1 2 3 0 -1 0 0 0 0 0 40 2 0 0 20 0 1 0 1 1 1";
        assertEquals(42L, SchedulerPolicy.parseCpuTicks(odd));
        assertEquals(-1L, SchedulerPolicy.parseCpuTicks("garbage"));
        assertEquals(-1L, SchedulerPolicy.parseCpuTicks("1 (x) S 1 2"));
        assertEquals(-1L, SchedulerPolicy.parseCpuTicks(null));
    }

    @Test
    public void meminfoValuesAreRead() {
        String text = "MemTotal:        2800000 kB\nMemAvailable:     640000 kB\nSwapTotal:       1048576 kB\nSwapFree:         100000 kB\n";
        assertEquals(640000L, SchedulerPolicy.parseMeminfoKb(text, "MemAvailable"));
        assertEquals(1048576L, SchedulerPolicy.parseMeminfoKb(text, "SwapTotal"));
        assertEquals(-1L, SchedulerPolicy.parseMeminfoKb(text, "Nope"));
        assertEquals(-1L, SchedulerPolicy.parseMeminfoKb(null, "MemTotal"));
    }

    @Test
    public void busiestThreadsAreListedAsPercentOfOneCore() {
        java.util.Map<String, Long> ticks = new java.util.HashMap<>();
        ticks.put("Render thread", 4200L);   // 42 s of CPU in 60 s -> 70%
        ticks.put("Server thread", 1800L);   // 30%
        ticks.put("idle", 0L);
        assertEquals("Render thread 70%, Server thread 30%", SchedulerPolicy.busiestThreads(ticks, 60_000, 100, 5));
        assertEquals("Render thread 70%", SchedulerPolicy.busiestThreads(ticks, 60_000, 100, 1));
        assertEquals("", SchedulerPolicy.busiestThreads(ticks, 0, 100, 5));
    }
}
