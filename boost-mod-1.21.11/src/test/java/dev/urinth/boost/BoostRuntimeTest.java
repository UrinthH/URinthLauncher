package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BoostRuntimeTest {
    /** Ticks the runtime once a second, from second {@code from} to second {@code to}, at one frame rate. */
    private static void run(BoostRuntime runtime, int from, int to, int fps) {
        for (int second = from; second <= to; second++) runtime.tick(second * 1000L, fps, true);
    }

    @Test
    public void fixedModesHoldTheirLevelFromTheStart() {
        assertEquals(0, new BoostRuntime(BoostMode.OFF, 50).level());
        assertEquals(1, new BoostRuntime(BoostMode.LIGHT, 50).level());
        assertEquals(2, new BoostRuntime(BoostMode.STRONG, 50).level());
        assertEquals(3, new BoostRuntime(BoostMode.MAX, 50).level());
        assertEquals(0, new BoostRuntime(BoostMode.AUTO, 50).level());
    }

    @Test
    public void offTakesNothingAwayButStillCountsThatTheHooksWereReached() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.OFF, 50);
        assertFalse(runtime.dropParticle(1e9));
        assertFalse(runtime.hideEntity(EntityKind.ITEM, 1e9));
        assertFalse(runtime.hideNameTag(1e9));
        assertFalse(runtime.hideLayers(1e9));
        assertEquals(1L, runtime.stats().calls(BoostStats.PARTICLE));
        assertEquals(1L, runtime.stats().calls(BoostStats.ENTITY));
        assertEquals(1L, runtime.stats().calls(BoostStats.NAME_TAG));
        assertEquals(1L, runtime.stats().calls(BoostStats.LAYERS));
        assertFalse(runtime.stats().anyActed());
    }

    @Test
    public void theParticleBudgetStartsAgainEveryTick() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        int budget = runtime.limits().particlesPerTick;
        runtime.newTick();
        for (int i = 0; i < budget; i++) assertFalse(runtime.dropParticle(1), "particle " + i);
        assertTrue(runtime.dropParticle(1));
        assertTrue(runtime.dropParticle(1));
        runtime.newTick();
        assertFalse(runtime.dropParticle(1));
    }

    @Test
    public void farParticlesAreDroppedWithoutUsingTheBudget() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        int budget = runtime.limits().particlesPerTick;
        double far = runtime.limits().particleDistSq + 1;
        runtime.newTick();
        for (int i = 0; i < 1000; i++) assertTrue(runtime.dropParticle(far));
        for (int i = 0; i < budget; i++) assertFalse(runtime.dropParticle(1));
        assertTrue(runtime.dropParticle(1));
    }

    @Test
    public void lightHasNoParticleBudgetBelowItsGenerousCap() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.LIGHT, 50);
        runtime.newTick();
        for (int i = 0; i < runtime.limits().particlesPerTick; i++) assertFalse(runtime.dropParticle(100));
        assertTrue(runtime.dropParticle(100));
    }

    @Test
    public void playersAndUnlistedThingsAreNeverHidden() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        assertFalse(runtime.hideEntity(EntityKind.KEEP, 1e12));
        assertFalse(runtime.hideEntity(EntityKind.KEEP, 0));
    }

    @Test
    public void everyKindOfEntityUsesItsOwnDistance() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        Limits l = runtime.limits();
        assertTrue(runtime.hideEntity(EntityKind.ITEM, l.itemDistSq + 1));
        assertFalse(runtime.hideEntity(EntityKind.ITEM, l.itemDistSq - 1));
        assertTrue(runtime.hideEntity(EntityKind.ARMOR_STAND, l.standDistSq + 1));
        assertFalse(runtime.hideEntity(EntityKind.ARMOR_STAND, l.standDistSq - 1));
        assertTrue(runtime.hideEntity(EntityKind.MOB, l.mobDistSq + 1));
        assertFalse(runtime.hideEntity(EntityKind.MOB, l.mobDistSq - 1));
    }

    @Test
    public void lightLeavesMobsAloneAndStrongDoesNot() {
        BoostRuntime light = new BoostRuntime(BoostMode.LIGHT, 50);
        assertFalse(light.hideEntity(EntityKind.MOB, 1e9));
        BoostRuntime strong = new BoostRuntime(BoostMode.STRONG, 50);
        assertTrue(strong.hideEntity(EntityKind.MOB, 1e9));
    }

    @Test
    public void nameTagsAndArmorHaveTheirOwnDistances() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        Limits l = runtime.limits();
        assertTrue(runtime.hideNameTag(l.nameTagDistSq + 1));
        assertFalse(runtime.hideNameTag(l.nameTagDistSq - 1));
        assertTrue(runtime.hideLayers(l.layerDistSq + 1));
        assertFalse(runtime.hideLayers(l.layerDistSq - 1));
    }

    @Test
    public void whatWasTakenAwayIsCounted() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        runtime.newTick();
        runtime.dropParticle(1);
        runtime.dropParticle(1e9);
        runtime.hideEntity(EntityKind.ITEM, 1e9);
        assertEquals(2L, runtime.stats().calls(BoostStats.PARTICLE));
        assertEquals(1L, runtime.stats().acted(BoostStats.PARTICLE));
        assertEquals(1L, runtime.stats().acted(BoostStats.ENTITY));
        assertTrue(runtime.stats().anyActed());
    }

    @Test
    public void theFirstSecondsAfterJoiningAreIgnored() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        run(runtime, 0, 7, 10);
        assertEquals(0, runtime.level());
        run(runtime, 8, 8, 10);
        assertEquals(1, runtime.level());
    }

    @Test
    public void theFrameRateIsReadOncePerSecondNoMatterHowOftenTheGameTicks() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        runtime.tick(9_000, 30, true);
        runtime.tick(9_050, 99, true);
        runtime.tick(9_500, 99, true);
        assertEquals(30, runtime.lastFps());
        runtime.tick(10_000, 77, true);
        assertEquals(77, runtime.lastFps());
    }

    @Test
    public void leavingTheWorldGoesBackToLevelZero() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        run(runtime, 0, 8, 10);
        assertEquals(1, runtime.level());
        runtime.tick(9_000, 10, false);
        assertEquals(0, runtime.level());
        // Joining again starts with the warm-up again.
        run(runtime, 10, 17, 10);
        assertEquals(0, runtime.level());
    }

    @Test
    public void aFixedModeSurvivesLeavingTheWorld() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.STRONG, 50);
        runtime.tick(1_000, 60, false);
        assertEquals(2, runtime.level());
    }

    @Test
    public void switchingModeAppliesTheNewLevelAndForgetsTheAutomaticState() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        run(runtime, 0, 8, 10);
        assertEquals(1, runtime.level());
        runtime.setMode(BoostMode.MAX);
        assertEquals(3, runtime.level());
        runtime.setMode(BoostMode.AUTO);
        assertEquals(0, runtime.level());
        assertEquals("", runtime.governorNote());
    }

    @Test
    public void disablingSwitchesEverythingOffForGood() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        assertNull(runtime.problem());
        runtime.disable("boom");
        assertEquals("boom", runtime.problem());
        assertEquals(0, runtime.level());
        assertFalse(runtime.dropParticle(1e9));
        assertFalse(runtime.hideEntity(EntityKind.ITEM, 1e9));
        runtime.setMode(BoostMode.MAX);
        assertEquals(0, runtime.level());
        runtime.tick(1_000, 10, true);
        assertEquals(0, runtime.level());
        assertFalse(runtime.startBench());
        runtime.disable("second reason");
        assertEquals("boom", runtime.problem());
    }

    @Test
    public void theBenchOverridesTheModeAndHandsBackAResult() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        run(runtime, 0, 8, 60);
        assertTrue(runtime.startBench());
        assertEquals(0, runtime.level());
        boolean sawMax = false;
        int second = 9;
        while (runtime.bench().active() && second < 200) {
            int level = runtime.level();
            if (level == BoostLevel.MAX) sawMax = true;
            runtime.tick(second * 1000L, level == BoostLevel.MAX ? 50 : 40, true);
            second++;
        }
        assertTrue(sawMax);
        assertFalse(runtime.bench().active());
        Bench.Result result = runtime.lastBench();
        assertNotNull(result);
        assertEquals(0.25, result.gain, 0.01);
        assertEquals(0, runtime.level());
    }

    @Test
    public void changingModeCancelsABench() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        run(runtime, 0, 8, 60);
        runtime.startBench();
        runtime.setMode(BoostMode.OFF);
        assertFalse(runtime.bench().active());
        assertEquals(0, runtime.level());
    }

    @Test
    public void leavingTheWorldCancelsABench() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        run(runtime, 0, 8, 60);
        runtime.startBench();
        runtime.tick(9_000, 60, false);
        assertFalse(runtime.bench().active());
        assertNull(runtime.lastBench());
    }

    @Test
    public void targetsAreKeptInsideTheLimits() {
        assertEquals(BoostConfig.MIN_TARGET, new BoostRuntime(BoostMode.AUTO, 1).target());
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50);
        runtime.setTarget(9_999);
        assertEquals(BoostConfig.MAX_TARGET, runtime.target());
        runtime.setTarget(45);
        assertEquals(45, runtime.target());
    }


    // ---- the limit on other players ----

    private static int[] ids(int n) {
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = 100 + i;
        return ids;
    }

    /** Player 100 is the nearest, then 101, and so on. */
    private static double[] dist(int n) {
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = (i + 1) * (i + 1);
        return d;
    }

    @Test
    public void levelsWithoutAPlayerLimitDrawEverybody() {
        for (BoostMode mode : new BoostMode[] {BoostMode.AUTO, BoostMode.OFF, BoostMode.LIGHT}) {
            BoostRuntime runtime = new BoostRuntime(mode, 50);
            assertEquals(Limits.UNLIMITED, runtime.effectivePlayerCap(), mode.word());
            assertFalse(runtime.wantsPlayerRanks(), mode.word());
            assertFalse(runtime.hidePlayer(105), mode.word());
        }
    }

    @Test
    public void theAutomaticPlayerLimitFollowsTheLevel() {
        assertEquals(Limits.forLevel(2).maxPlayers, new BoostRuntime(BoostMode.STRONG, 50).effectivePlayerCap());
        BoostRuntime max = new BoostRuntime(BoostMode.MAX, 50);
        int cap = Limits.forLevel(3).maxPlayers;
        assertEquals(cap, max.effectivePlayerCap());
        assertTrue(max.wantsPlayerRanks());
        max.updatePlayers(ids(50), dist(50), 50);
        assertEquals(50 - cap, max.playersLeftOut());
        assertEquals(50, max.playersAround());
        assertFalse(max.hidePlayer(100), "the nearest is drawn");
        assertTrue(max.hidePlayer(149), "the farthest is left out");
        assertEquals(2L, max.stats().calls(BoostStats.PLAYER));
        assertEquals(1L, max.stats().acted(BoostStats.PLAYER));
    }

    @Test
    public void aCrowdThatFitsTheLimitIsDrawnWhole() {
        BoostRuntime max = new BoostRuntime(BoostMode.MAX, 50);
        int cap = max.effectivePlayerCap();
        max.updatePlayers(ids(cap), dist(cap), cap);
        assertEquals(0, max.playersLeftOut());
        assertFalse(max.hidePlayer(100 + cap - 1));
    }

    @Test
    public void aNumberIsALimitEvenWhileTheAutomaticModeIsIdle() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50, 10);
        assertEquals(0, runtime.level(), "automatic has not taken anything away");
        assertEquals(10, runtime.effectivePlayerCap());
        runtime.updatePlayers(ids(30), dist(30), 30);
        assertEquals(20, runtime.playersLeftOut());
        assertTrue(runtime.hidePlayer(129));
        assertFalse(runtime.hidePlayer(100));
        assertFalse(runtime.hidePlayer(999), "somebody who was not listed is always drawn");
    }

    @Test
    public void offNeverLeavesAPlayerOut() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50, BoostConfig.PLAYERS_OFF);
        assertEquals(Limits.UNLIMITED, runtime.effectivePlayerCap());
        runtime.updatePlayers(ids(60), dist(60), 60);
        assertEquals(0, runtime.playersLeftOut());
        assertFalse(runtime.hidePlayer(159));
    }

    @Test
    public void whenBoostIsOffNoLimitAppliesWhateverNumberWasChosen() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.OFF, 50, 10);
        assertTrue(runtime.takesNothingAway());
        assertEquals(Limits.UNLIMITED, runtime.effectivePlayerCap());
        runtime.updatePlayers(ids(30), dist(30), 30);
        assertEquals(0, runtime.playersLeftOut());
    }

    @Test
    public void changingTheSettingOrTheModeDrawsEverybodyAtOnce() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        runtime.updatePlayers(ids(60), dist(60), 60);
        assertTrue(runtime.playersLeftOut() > 0);
        runtime.setPlayersSetting(BoostConfig.PLAYERS_OFF);
        assertEquals(0, runtime.playersLeftOut());
        runtime.setPlayersSetting(BoostConfig.PLAYERS_AUTO);
        runtime.updatePlayers(ids(60), dist(60), 60);
        assertTrue(runtime.playersLeftOut() > 0);
        runtime.setMode(BoostMode.OFF);
        assertEquals(0, runtime.playersLeftOut());
    }

    @Test
    public void aNumberOutsideTheLimitsIsKeptInside() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50, 1);
        assertEquals(BoostConfig.MIN_PLAYERS, runtime.playersSetting());
        runtime.setPlayersSetting(5000);
        assertEquals(BoostConfig.MAX_PLAYERS, runtime.playersSetting());
    }

    @Test
    public void aFailedModLeavesNobodyOut() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        runtime.updatePlayers(ids(60), dist(60), 60);
        runtime.disable("boom");
        assertEquals(0, runtime.playersLeftOut());
        assertEquals(Limits.UNLIMITED, runtime.effectivePlayerCap());
        assertFalse(runtime.hidePlayer(159));
    }

    @Test
    public void leavingTheWorldDrawsEverybodyAgain() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.MAX, 50);
        runtime.updatePlayers(ids(60), dist(60), 60);
        runtime.tick(1_000, 60, false);
        assertEquals(0, runtime.playersLeftOut());
        assertEquals(0, runtime.playersAround());
    }

    @Test
    public void theOffHalfOfTheBenchTestHasNoPlayerLimitEither() {
        BoostRuntime runtime = new BoostRuntime(BoostMode.AUTO, 50, 10);
        run(runtime, 0, 8, 60);
        assertTrue(runtime.startBench());
        boolean sawOff = false;
        boolean sawOn = false;
        for (int second = 9; second < 400 && runtime.bench().active(); second++) {
            runtime.tick(second * 1000L, 60, true);
            if (!runtime.bench().active()) break;
            if (runtime.level() == BoostLevel.OFF) {
                sawOff = true;
                assertEquals(Limits.UNLIMITED, runtime.effectivePlayerCap(), "second " + second);
            } else {
                sawOn = true;
                assertEquals(10, runtime.effectivePlayerCap(), "second " + second);
            }
        }
        assertTrue(sawOff && sawOn, "the test went through both halves");
    }
}
