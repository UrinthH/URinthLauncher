package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptimizerConfigTest {
    @Test
    void theDefaultsAreTheSafeOnes() {
        OptimizerConfig c = OptimizerConfig.defaults(false);
        assertTrue(c.adaptive());
        assertEquals(60, c.targetFps());
        assertEquals(5, c.minDistance());
        assertTrue(c.cull());
        assertEquals(OptimizerConfig.PARTICLES_AUTO, c.particles());
        assertTrue(c.uploads());
        assertEquals(8, c.simulationCap());
        assertTrue(c.serverGovernor());
        assertEquals(OptimizerConfig.AUTO_THREADS, c.workerThreads());
        assertEquals(OptimizerConfig.MEMORY_FULL, c.memoryGuard());
        assertTrue(c.diagnostics());
    }

    @Test
    void aDedicatedServerIsLeftAloneUnlessAsked() {
        OptimizerConfig c = OptimizerConfig.defaults(true);
        assertEquals(0, c.simulationCap());
        assertFalse(c.serverGovernor());
        assertEquals(OptimizerConfig.VANILLA_THREADS, c.workerThreads());
        assertEquals(OptimizerConfig.MEMORY_OFF, c.memoryGuard());
        assertFalse(c.diagnostics());
        // it is still the same mod: the client settings are the same
        assertTrue(c.adaptive());
    }

    @Test
    void theFileTheModWritesIsReadBackAsTheSame() {
        OptimizerConfig a = OptimizerConfig.defaults(false);
        a.setAdaptive(false);
        a.setTargetFps(90);
        a.setMinDistance(7);
        a.setCull(false);
        a.cycleParticles();
        a.setUploads(false);
        a.cycleSimulationCap();
        a.setServerGovernor(false);
        a.cycleThreads();
        a.setMemoryGuard(OptimizerConfig.MEMORY_SOFT);
        OptimizerConfig b = OptimizerConfig.fromText(a.toText(), false);
        assertEquals(a.toText(), b.toText());
        assertFalse(b.adaptive());
        assertEquals(90, b.targetFps());
        assertEquals(7, b.minDistance());
        assertFalse(b.cull());
        assertFalse(b.uploads());
        assertFalse(b.serverGovernor());
        assertEquals(OptimizerConfig.MEMORY_SOFT, b.memoryGuard());
    }

    @Test
    void theMemoryGuardHasNamesAndAnUnknownOneIsTheDefault() {
        assertEquals(OptimizerConfig.MEMORY_OFF, OptimizerConfig.fromText("memory-guard=off", false).memoryGuard());
        assertEquals(OptimizerConfig.MEMORY_SOFT, OptimizerConfig.fromText("memory-guard=Soft", false).memoryGuard());
        assertEquals(OptimizerConfig.MEMORY_FULL, OptimizerConfig.fromText("memory-guard= FULL ", true).memoryGuard());
        assertEquals(OptimizerConfig.MEMORY_FULL, OptimizerConfig.fromText("memory-guard=loud", false).memoryGuard());
        assertEquals(OptimizerConfig.MEMORY_OFF, OptimizerConfig.fromText("memory-guard=loud", true).memoryGuard());
        assertEquals("off", OptimizerConfig.memoryGuardName(0));
        assertEquals("soft", OptimizerConfig.memoryGuardName(1));
        assertEquals("full", OptimizerConfig.memoryGuardName(2));
        assertEquals("full", OptimizerConfig.memoryGuardName(99));
        assertEquals("off", OptimizerConfig.memoryGuardName(-5));
    }

    @Test
    void theMemoryGuardButtonGoesFullSoftOffAndBack() {
        OptimizerConfig c = OptimizerConfig.defaults(false);
        assertEquals(OptimizerConfig.MEMORY_FULL, c.memoryGuard());
        c.cycleMemoryGuard();
        assertEquals(OptimizerConfig.MEMORY_SOFT, c.memoryGuard());
        c.cycleMemoryGuard();
        assertEquals(OptimizerConfig.MEMORY_OFF, c.memoryGuard());
        c.cycleMemoryGuard();
        assertEquals(OptimizerConfig.MEMORY_FULL, c.memoryGuard());
    }

    @Test
    void theMemoryGuardSetterClamps() {
        OptimizerConfig c = OptimizerConfig.defaults(false);
        c.setMemoryGuard(99);
        assertEquals(OptimizerConfig.MEMORY_FULL, c.memoryGuard());
        c.setMemoryGuard(-4);
        assertEquals(OptimizerConfig.MEMORY_OFF, c.memoryGuard());
    }

    @Test
    void theFreezeLogCanBeSwitchedOff() {
        assertTrue(OptimizerConfig.fromText("", false).diagnostics());
        assertFalse(OptimizerConfig.fromText("freeze-log=off", false).diagnostics());
        assertTrue(OptimizerConfig.fromText("freeze-log=on", true).diagnostics());
        assertTrue(OptimizerConfig.fromText("freeze-log=maybe", false).diagnostics());
        String text = OptimizerConfig.fromText("freeze-log=false", false).toText();
        assertTrue(text.contains("freeze-log=false"), text);
        assertFalse(OptimizerConfig.fromText(text, false).diagnostics());
    }

    @Test
    void theFileNeverMentionsTheRenderDistanceOptionOrResolution() {
        // the standing rule: the player's own settings are not the mod's to change
        String text = OptimizerConfig.defaults(false).toText().toLowerCase(java.util.Locale.ROOT);
        assertFalse(text.contains("resolution"));
        assertFalse(text.contains("options.txt=") || text.contains("render-distance="));
    }

    @Test
    void missingOrUnreadableGivesTheDefaults() {
        String defaults = OptimizerConfig.defaults(false).toText();
        assertEquals(defaults, OptimizerConfig.fromText(null, false).toText());
        assertEquals(defaults, OptimizerConfig.fromText("", false).toText());
        assertEquals(defaults, OptimizerConfig.fromText("# only a comment\n", false).toText());
        assertEquals(defaults, OptimizerConfig.fromText("target-fps=fast\nmin-distance=\nparticles=lots\nworker-threads=many\n"
            + "adaptive-distance=maybe\nsimulation-cap=x\n", false).toText());
        assertEquals(defaults, OptimizerConfig.fromText("adaptive-distance=\\u00zz", false).toText());
    }

    @Test
    void valuesAreClamped() {
        OptimizerConfig c = OptimizerConfig.fromText("target-fps=9999\nmin-distance=-4\nsimulation-cap=99\n", false);
        assertEquals(240, c.targetFps());
        assertEquals(2, c.minDistance());
        assertEquals(32, c.simulationCap());
        OptimizerConfig d = OptimizerConfig.fromText("target-fps=1\nmin-distance=500\nsimulation-cap=1\n", false);
        assertEquals(20, d.targetFps());
        assertEquals(16, d.minDistance());
        assertEquals(2, d.simulationCap());
        assertEquals(0, OptimizerConfig.fromText("simulation-cap=0", false).simulationCap());
        assertEquals(0, OptimizerConfig.fromText("simulation-cap=-3", false).simulationCap());
    }

    @Test
    void wordsForTheOnOffSettings() {
        assertFalse(OptimizerConfig.fromText("adaptive-distance=off", false).adaptive());
        assertFalse(OptimizerConfig.fromText("adaptive-distance=NO", false).adaptive());
        assertFalse(OptimizerConfig.fromText("adaptive-distance=0", false).adaptive());
        assertTrue(OptimizerConfig.fromText("adaptive-distance=Yes", true).adaptive());
        assertTrue(OptimizerConfig.fromText("server-governor=on", true).serverGovernor());
    }

    @Test
    void threadsAndParticlesHaveNames() {
        assertEquals(OptimizerConfig.AUTO_THREADS, OptimizerConfig.fromText("worker-threads=auto", true).workerThreads());
        assertEquals(OptimizerConfig.VANILLA_THREADS, OptimizerConfig.fromText("worker-threads=vanilla", false).workerThreads());
        assertEquals(3, OptimizerConfig.fromText("worker-threads=3", false).workerThreads());
        assertEquals(OptimizerConfig.VANILLA_THREADS, OptimizerConfig.fromText("worker-threads=0", false).workerThreads());
        assertEquals(OptimizerConfig.PARTICLES_ALWAYS, OptimizerConfig.fromText("particles=Always", false).particles());
        assertEquals(OptimizerConfig.PARTICLES_OFF, OptimizerConfig.fromText("particles=off", false).particles());
        assertEquals("auto", OptimizerConfig.threadsName(-1));
        assertEquals("vanilla", OptimizerConfig.threadsName(0));
        assertEquals("4", OptimizerConfig.threadsName(4));
        assertEquals("always", OptimizerConfig.particlesName(2));
    }

    @Test
    void cyclingVisitsEveryOfferedValueAndComesBack() {
        OptimizerConfig c = OptimizerConfig.defaults(false);
        for (int round = 0; round < 2; round++) {
            int seen = 0;
            int start = c.targetFps();
            do {
                c.cycleTargetFps();
                seen++;
            } while (c.targetFps() != start && seen < 50);
            assertEquals(OptimizerConfig.TARGET_FPS.length, seen);
        }
        int start = c.workerThreads();
        int n = 0;
        do {
            c.cycleThreads();
            n++;
        } while (c.workerThreads() != start && n < 50);
        assertEquals(OptimizerConfig.THREADS.length, n);
    }

    @Test
    void aHandEditedValueOutsideTheListCyclesToTheNextBiggerOne() {
        assertEquals(60, OptimizerConfig.next(OptimizerConfig.TARGET_FPS, 50));
        assertEquals(30, OptimizerConfig.next(OptimizerConfig.TARGET_FPS, 200));
        assertEquals(30, OptimizerConfig.next(OptimizerConfig.TARGET_FPS, 120));
    }

    @Test
    void settersClamp() {
        OptimizerConfig c = OptimizerConfig.defaults(false);
        assertEquals(240, c.setTargetFps(1000));
        assertEquals(20, c.setTargetFps(-1));
        assertEquals(16, c.setMinDistance(99));
        assertEquals(2, c.setMinDistance(0));
    }

    @Test
    void theRunningSettingsNeverComeBackNull() {
        OptimizerConfig.setCurrent(null);
        assertTrue(OptimizerConfig.current() != null);
        OptimizerConfig mine = OptimizerConfig.defaults(false);
        OptimizerConfig.setCurrent(mine);
        assertTrue(OptimizerConfig.current() == mine);
        OptimizerConfig.setCurrent(null);
    }
}
