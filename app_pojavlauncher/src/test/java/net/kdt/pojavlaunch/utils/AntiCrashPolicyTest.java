package net.kdt.pojavlaunch.utils;

import static net.kdt.pojavlaunch.utils.AntiCrashPolicy.EXIT_JIT_CRASH;
import static net.kdt.pojavlaunch.utils.AntiCrashPolicy.EXIT_LOW_MEMORY;
import static net.kdt.pojavlaunch.utils.AntiCrashPolicy.EXIT_NORMAL;
import static net.kdt.pojavlaunch.utils.AntiCrashPolicy.EXIT_OTHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AntiCrashPolicyTest {
    private static final String COMPILER_CRASH = "Signal: SIGSEGV (SEGV_MAPERR)\nCrashed thread: 4821 \"C2 CompilerThre\"\n#00 pc 123 libjvm.so\n";
    private static final String OTHER_CRASH = "Signal: SIGABRT\nCrashed thread: 4800 \"Render thread\"\n#00 pc 55 libc.so\n";

    private static AntiCrashPolicy.Exit exit(long at, int kind) {
        return new AntiCrashPolicy.Exit(at, kind);
    }

    private static AntiCrashPolicy.Decision decide(AntiCrashPolicy.State before, int requested, AntiCrashPolicy.Exit exit) {
        return AntiCrashPolicy.decide(before, requested, exit, true, true);
    }

    @Test
    public void classifiesTheWaysAGameProcessCanEnd() {
        assertEquals(EXIT_NORMAL, AntiCrashPolicy.classify(false, false, null, false));
        assertEquals(EXIT_LOW_MEMORY, AntiCrashPolicy.classify(true, false, null, true));
        assertEquals(EXIT_JIT_CRASH, AntiCrashPolicy.classify(false, true, COMPILER_CRASH, true));
        assertEquals(EXIT_JIT_CRASH, AntiCrashPolicy.classify(false, true, "Crashed thread: 1 \"C1 CompilerThre\"", true));
        assertEquals(EXIT_OTHER, AntiCrashPolicy.classify(false, true, OTHER_CRASH, true));
        assertEquals(EXIT_OTHER, AntiCrashPolicy.classify(false, true, null, true));
        assertEquals(EXIT_OTHER, AntiCrashPolicy.classify(false, false, null, true));
    }

    @Test
    public void everyStepTakesAnEighthOffAndNeverGoesBelowTheFloorOrAboveTheRequest() {
        assertEquals(1024, AntiCrashPolicy.reducedAllocation(1024, 0));
        assertEquals(896, AntiCrashPolicy.reducedAllocation(1024, 1));
        assertEquals(768, AntiCrashPolicy.reducedAllocation(1024, 2));
        assertEquals(640, AntiCrashPolicy.reducedAllocation(1024, 3));
        // More steps than the limit count as the limit.
        assertEquals(640, AntiCrashPolicy.reducedAllocation(1024, 9));
        assertEquals(1024, AntiCrashPolicy.reducedAllocation(1024, -2));
        // The floor.
        assertEquals(512, AntiCrashPolicy.reducedAllocation(600, 3));
        assertEquals(512, AntiCrashPolicy.reducedAllocation(512, 3));
        // A setting that is already below the floor is left alone.
        assertEquals(400, AntiCrashPolicy.reducedAllocation(400, 3));
        // Always a multiple of the slider's 8 MB step.
        assertEquals(872, AntiCrashPolicy.reducedAllocation(1000, 1));
        assertEquals(0, AntiCrashPolicy.reducedAllocation(1000, 1) % 8);
        assertEquals(0, AntiCrashPolicy.reducedAllocation(1500, 2) % 8);
    }

    @Test
    public void aFirstGameWithNoRecordChangesNothing() {
        AntiCrashPolicy.Decision decision = decide(AntiCrashPolicy.State.empty(), 1024, null);
        assertEquals(1024, decision.allocationMb);
        assertFalse(decision.simpleJit);
        assertEquals(0, decision.next.lowMemorySteps);
        assertEquals(1024, decision.next.allocationMb);
    }

    @Test
    public void aNormalEndingChangesNothing() {
        AntiCrashPolicy.Decision decision = decide(AntiCrashPolicy.State.empty(), 1024, exit(100, EXIT_NORMAL));
        assertEquals(1024, decision.allocationMb);
        assertEquals(100L, decision.next.handledAtMs);
    }

    @Test
    public void aLowMemoryCloseLowersTheNextStartAndIsCountedOnce() {
        AntiCrashPolicy.Decision first = decide(AntiCrashPolicy.State.empty(), 1024, exit(100, EXIT_LOW_MEMORY));
        assertEquals(896, first.allocationMb);
        assertEquals(1, first.next.lowMemorySteps);
        // Starting again before anything else happened: the same close must not be counted twice.
        AntiCrashPolicy.Decision again = decide(first.next, 1024, exit(100, EXIT_LOW_MEMORY));
        assertEquals(896, again.allocationMb);
        assertEquals(1, again.next.lowMemorySteps);
        // A newer close counts.
        AntiCrashPolicy.Decision second = decide(again.next, 1024, exit(200, EXIT_LOW_MEMORY));
        assertEquals(768, second.allocationMb);
        assertEquals(2, second.next.lowMemorySteps);
    }

    @Test
    public void theStepsStopAtTheLimit() {
        AntiCrashPolicy.State state = AntiCrashPolicy.State.empty();
        for (int i = 1; i <= 6; i++) state = decide(state, 1024, exit(i * 100L, EXIT_LOW_MEMORY)).next;
        assertEquals(AntiCrashPolicy.MAX_STEPS, state.lowMemorySteps);
        assertEquals(640, decide(state, 1024, null).allocationMb);
    }

    @Test
    public void aNormalEndingAfterTheReductionKeepsItUntilThePlayerActs() {
        AntiCrashPolicy.State state = decide(AntiCrashPolicy.State.empty(), 1024, exit(100, EXIT_LOW_MEMORY)).next;
        AntiCrashPolicy.Decision afterNormal = decide(state, 1024, exit(200, EXIT_NORMAL));
        assertEquals(896, afterNormal.allocationMb);
        assertEquals(1, afterNormal.next.lowMemorySteps);
        // An ending for another reason does not undo it either.
        assertEquals(896, decide(afterNormal.next, 1024, exit(300, EXIT_OTHER)).allocationMb);
    }

    @Test
    public void changingTheRamSettingStartsOver() {
        AntiCrashPolicy.State state = decide(AntiCrashPolicy.State.empty(), 1024, exit(100, EXIT_LOW_MEMORY)).next;
        AntiCrashPolicy.Decision changed = decide(state, 900, null);
        assertEquals(900, changed.allocationMb);
        assertEquals(0, changed.next.lowMemorySteps);
        assertEquals(900, changed.next.allocationMb);
    }

    @Test
    public void aCloseThePlayerAlreadyAnsweredByLoweringTheSettingIsNotCountedAgain() {
        // The game was closed for low memory at 100, the player then lowered the setting from 1024 to 900,
        // and this is the first start since: the close is already dealt with.
        AntiCrashPolicy.State before = new AntiCrashPolicy.State(0, false, 0L, 1024);
        AntiCrashPolicy.Decision decision = decide(before, 900, exit(100, EXIT_LOW_MEMORY));
        assertEquals(900, decision.allocationMb);
        assertEquals(0, decision.next.lowMemorySteps);
        assertEquals(100L, decision.next.handledAtMs);
        // The next close is counted as usual.
        assertEquals(1, decide(decision.next, 900, exit(200, EXIT_LOW_MEMORY)).next.lowMemorySteps);
    }

    @Test
    public void aCompilerCrashSwitchesToTheSimpleJitAndItStays() {
        AntiCrashPolicy.Decision crash = decide(AntiCrashPolicy.State.empty(), 1024, exit(100, EXIT_JIT_CRASH));
        assertTrue(crash.simpleJit);
        assertEquals(1024, crash.allocationMb);
        AntiCrashPolicy.Decision later = decide(crash.next, 1024, exit(200, EXIT_NORMAL));
        assertTrue(later.simpleJit);
        assertTrue(decide(later.next, 1024, null).simpleJit);
    }

    @Test
    public void aCompilerCrashStillCountsWhenThePlayerChangedTheRamSettingMeanwhile() {
        AntiCrashPolicy.State before = new AntiCrashPolicy.State(0, false, 0L, 1024);
        assertTrue(decide(before, 900, exit(100, EXIT_JIT_CRASH)).simpleJit);
    }

    @Test
    public void aSwitchThatIsOffForgetsAndIgnoresItsKindOfEnding() {
        AntiCrashPolicy.State both = new AntiCrashPolicy.State(2, true, 100L, 1024);
        // Memory guard off: the steps are forgotten, a new low-memory close is not counted, the compiler part keeps working.
        AntiCrashPolicy.Decision memoryOff = AntiCrashPolicy.decide(both, 1024, exit(200, EXIT_LOW_MEMORY), false, true);
        assertEquals(1024, memoryOff.allocationMb);
        assertEquals(0, memoryOff.next.lowMemorySteps);
        assertTrue(memoryOff.simpleJit);
        // Compiler guard off: the fallback is forgotten and a new compiler crash is not counted.
        AntiCrashPolicy.Decision jitOff = AntiCrashPolicy.decide(both, 1024, exit(200, EXIT_JIT_CRASH), true, false);
        assertFalse(jitOff.simpleJit);
        assertFalse(jitOff.next.jitFallback);
        assertEquals(768, jitOff.allocationMb);
        // Turning a guard back on starts it from nothing.
        assertEquals(1024, decide(memoryOff.next, 1024, null).allocationMb);
    }

    @Test
    public void forgettingOneGuardKeepsTheOther() {
        AntiCrashPolicy.State both = new AntiCrashPolicy.State(2, true, 100L, 1024);
        AntiCrashPolicy.State noSteps = both.withoutLowMemorySteps();
        assertEquals(0, noSteps.lowMemorySteps);
        assertTrue(noSteps.jitFallback);
        assertEquals(100L, noSteps.handledAtMs);
        assertEquals(1024, noSteps.allocationMb);
        AntiCrashPolicy.State noJit = both.withoutJitFallback();
        assertEquals(2, noJit.lowMemorySteps);
        assertFalse(noJit.jitFallback);
        assertEquals(100L, noJit.handledAtMs);
        // Forgetting must not make an old close count again.
        assertEquals(1024, decide(noSteps, 1024, exit(100, EXIT_LOW_MEMORY)).allocationMb);
    }

    @Test
    public void aDamagedStateIsClamped() {
        AntiCrashPolicy.State state = new AntiCrashPolicy.State(99, true, -5L, -3);
        assertEquals(AntiCrashPolicy.MAX_STEPS, state.lowMemorySteps);
        assertEquals(0L, state.handledAtMs);
        assertEquals(0, state.allocationMb);
    }
}
