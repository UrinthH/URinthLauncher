package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The thread plan, the upload budget, the particle gate and the frame rate target. */
class SmallPartsTest {
    // ---- threads ----

    @Test
    void automaticThreadsAreHalfTheCoresBetweenTwoAndFour() {
        assertEquals(4, ThreadPlan.workers(8, OptimizerConfig.AUTO_THREADS));
        assertEquals(4, ThreadPlan.workers(12, OptimizerConfig.AUTO_THREADS));
        assertEquals(3, ThreadPlan.workers(6, OptimizerConfig.AUTO_THREADS));
        assertEquals(2, ThreadPlan.workers(4, OptimizerConfig.AUTO_THREADS));
        assertEquals(2, ThreadPlan.workers(3, OptimizerConfig.AUTO_THREADS));
    }

    @Test
    void aTwoCoreDeviceIsLeftAlone() {
        assertEquals(0, ThreadPlan.workers(2, OptimizerConfig.AUTO_THREADS));
        assertEquals(0, ThreadPlan.workers(1, OptimizerConfig.AUTO_THREADS));
    }

    @Test
    void vanillaMeansTheGamesOwnChoice() {
        assertEquals(0, ThreadPlan.workers(8, OptimizerConfig.VANILLA_THREADS));
    }

    @Test
    void aNumberIsUsedAsIs() {
        assertEquals(3, ThreadPlan.workers(8, 3));
        assertEquals(6, ThreadPlan.workers(4, 6));
        assertEquals(255, ThreadPlan.workers(8, 9999));
    }

    @Test
    void thePropertyIsTheOneTheGameReads() {
        assertEquals("max.bg.threads", ThreadPlan.PROPERTY);
    }

    // ---- upload budget ----

    @Test
    void nothingWaitingMeansNoBudget() {
        assertEquals(0L, UploadBudget.nanos(60, 0));
        assertEquals(0L, UploadBudget.nanos(60, 1));
    }

    @Test
    void aFewMeshesGetAFewMilliseconds() {
        long ns = UploadBudget.nanos(60, 10);
        assertTrue(ns >= 1_200_000L && ns <= 4_000_000L, "budget " + ns);
    }

    @Test
    void aBigBacklogGetsTwiceAsMuch() {
        assertEquals(UploadBudget.nanos(60, 10) * 2, UploadBudget.nanos(60, 100));
    }

    @Test
    void aFloodOfMeshesIsNotHeldBack() {
        assertEquals(0L, UploadBudget.nanos(60, UploadBudget.FLOOD));
        assertEquals(0L, UploadBudget.nanos(60, 5000));
    }

    @Test
    void theBudgetStaysWithinLimitsForAnyFrameRate() {
        for (int fps : new int[] {-5, 0, 20, 30, 60, 120, 240, 1000}) {
            for (int pending : new int[] {2, 20, 49, 300}) {
                long ns = UploadBudget.nanos(fps, pending);
                assertTrue(ns >= 1_200_000L && ns <= 8_000_000L, fps + " fps, " + pending + " waiting: " + ns);
            }
        }
    }

    // ---- particles ----

    @Test
    void particlesAreReducedOnlyAsTheModeSays() {
        assertTrue(ParticleGate.decide(OptimizerConfig.PARTICLES_ALWAYS, false));
        assertTrue(ParticleGate.decide(OptimizerConfig.PARTICLES_AUTO, true));
        assertFalse(ParticleGate.decide(OptimizerConfig.PARTICLES_AUTO, false));
        assertFalse(ParticleGate.decide(OptimizerConfig.PARTICLES_OFF, true));
    }

    @Test
    void aShortageOfMemoryThinsTheParticlesLikeALowFrameRateDoes() {
        try {
            OptimizerConfig.setCurrent(OptimizerConfig.defaults(false)); // particles: auto
            ParticleGate.setLow(false);
            ParticleGate.setShortOfMemory(false);
            assertFalse(ParticleGate.reduce());
            ParticleGate.setShortOfMemory(true);
            assertTrue(ParticleGate.reduce());
            ParticleGate.setShortOfMemory(false);
            assertFalse(ParticleGate.reduce());
            ParticleGate.setLow(true);
            assertTrue(ParticleGate.reduce());
        } finally {
            ParticleGate.setLow(false);
            ParticleGate.setShortOfMemory(false);
            OptimizerConfig.setCurrent(null);
        }
    }

    @Test
    void whoSwitchedTheParticleGateOffIsLeftAloneEvenInAShortage() {
        try {
            OptimizerConfig off = OptimizerConfig.fromText("particles=off", false);
            OptimizerConfig.setCurrent(off);
            ParticleGate.setShortOfMemory(true);
            ParticleGate.setLow(true);
            assertFalse(ParticleGate.reduce());
        } finally {
            ParticleGate.setLow(false);
            ParticleGate.setShortOfMemory(false);
            OptimizerConfig.setCurrent(null);
        }
    }

    // ---- target frame rate ----

    @Test
    void theTargetIsNotMoreThanTheGameCanShow() {
        assertEquals(60, TargetFps.effective(60, 260, false, 0));
        assertEquals(30, TargetFps.effective(60, 30, false, 0));
        assertEquals(60, TargetFps.effective(120, 260, true, 60));
        assertEquals(60, TargetFps.effective(90, 120, true, 60));
        assertEquals(90, TargetFps.effective(90, 260, false, 60)); // no v-sync: the refresh rate does not limit
        assertEquals(60, TargetFps.effective(60, 260, true, 0));   // refresh unknown
        assertEquals(20, TargetFps.effective(60, 5, false, 0));
    }
}
