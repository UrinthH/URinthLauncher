package net.kdt.pojavlaunch.utils;

/**
 * The decisions of the Anti Crash guard, with no Android code in it so they can be tested on a plain JVM.
 *
 * Android keeps the reason the last game process ended (Android 11 and newer). Two of those reasons point
 * at something the launcher can change before the next start:
 * <ul>
 *   <li><b>Android closed the game because the phone ran out of memory.</b> The next start takes an eighth
 *       off the RAM setting for each such close in a row (at most three, never below 512 MB), so the phone
 *       has room again. The steps come back off when the RAM setting is changed or the switch is turned
 *       off and on.</li>
 *   <li><b>The game crashed inside Java's optimising compiler</b> (the crashing thread is a "CompilerThread").
 *       Such crashes come from a compiler bug on that phone, not from the game, and the simple compiler
 *       does not have it. The next starts use it until the switch is turned off and on.</li>
 * </ul>
 * Nothing else is changed, and a close for any other reason changes nothing.
 */
public final class AntiCrashPolicy {
    /** The game ended normally, or Android has no record. */
    public static final int EXIT_NORMAL = 0;
    /** Android closed the game process because the phone ran out of memory. */
    public static final int EXIT_LOW_MEMORY = 1;
    /** The game crashed natively, in one of the JVM's compiler threads. */
    public static final int EXIT_JIT_CRASH = 2;
    /** Any other way of going wrong; the guard does not act on these. */
    public static final int EXIT_OTHER = 3;

    /** Each close for low memory takes off an eighth of the RAM setting, up to this many times. */
    public static final int MAX_STEPS = 3;
    /** The RAM setting is never lowered below this (a setting that is already lower is left alone). */
    public static final int FLOOR_MB = 512;
    private static final int STEP_DIVISOR = 8;
    private static final int SLIDER_STEP_MB = 8;

    private AntiCrashPolicy() {}

    /** The newest finished game process: when it ended and what {@link #classify} made of it. */
    public static final class Exit {
        public final long timestampMs;
        public final int kind;

        public Exit(long timestampMs, int kind) {
            this.timestampMs = timestampMs;
            this.kind = kind;
        }
    }

    /** What the guard remembers between launches. */
    public static final class State {
        public final int lowMemorySteps;
        public final boolean jitFallback;
        /** The end time of the newest exit already counted, so one close is never counted twice. */
        public final long handledAtMs;
        /** The RAM setting the steps were counted against; a different setting means the player changed it. */
        public final int allocationMb;

        public State(int lowMemorySteps, boolean jitFallback, long handledAtMs, int allocationMb) {
            this.lowMemorySteps = Math.max(0, Math.min(lowMemorySteps, MAX_STEPS));
            this.jitFallback = jitFallback;
            this.handledAtMs = Math.max(0L, handledAtMs);
            this.allocationMb = Math.max(0, allocationMb);
        }

        public static State empty() {
            return new State(0, false, 0L, 0);
        }

        /** The same, with the steps taken off the RAM setting forgotten (the player turned that switch off and on). */
        public State withoutLowMemorySteps() {
            return new State(0, jitFallback, handledAtMs, allocationMb);
        }

        /** The same, with the simple-JIT fallback forgotten (the player turned that switch off and on). */
        public State withoutJitFallback() {
            return new State(lowMemorySteps, false, handledAtMs, allocationMb);
        }
    }

    /** What the next launch does. */
    public static final class Decision {
        public final int allocationMb;
        public final boolean simpleJit;
        public final State next;

        Decision(int allocationMb, boolean simpleJit, State next) {
            this.allocationMb = allocationMb;
            this.simpleJit = simpleJit;
            this.next = next;
        }
    }

    /**
     * Which kind of ending an Android exit record is.
     *
     * @param lowMemoryKill Android says the low memory killer closed the process
     * @param nativeCrash Android says the process crashed natively
     * @param crashSummary the readable summary of the crash dump (it names the crashing thread), or null
     * @param otherAbnormal the exit was abnormal for some other reason (an ANR, a Java crash...)
     */
    public static int classify(boolean lowMemoryKill, boolean nativeCrash, String crashSummary, boolean otherAbnormal) {
        if (lowMemoryKill) return EXIT_LOW_MEMORY;
        if (nativeCrash) {
            // HotSpot names its compiler threads "C1 CompilerThread0" and "C2 CompilerThread0"; Linux keeps
            // 15 characters of a thread name, so the dump has "C2 CompilerThre".
            if (crashSummary != null && crashSummary.contains("CompilerThre")) return EXIT_JIT_CRASH;
            return EXIT_OTHER;
        }
        return otherAbnormal ? EXIT_OTHER : EXIT_NORMAL;
    }

    /** The RAM setting after {@code steps} closes for low memory: an eighth off per step, not below the floor. */
    public static int reducedAllocation(int requestedMb, int steps) {
        int count = Math.max(0, Math.min(steps, MAX_STEPS));
        if (count == 0 || requestedMb <= FLOOR_MB) return requestedMb;
        int reduced = requestedMb - count * (requestedMb / STEP_DIVISOR);
        // Keep to the settings slider's 8 MB steps, so what the game log says is a value the slider has.
        reduced = (reduced / SLIDER_STEP_MB) * SLIDER_STEP_MB;
        return Math.min(requestedMb, Math.max(FLOOR_MB, reduced));
    }

    /**
     * Decides the next launch.
     *
     * @param before what was remembered
     * @param requestedMb the RAM setting the player chose
     * @param exit the newest finished game process, or null when Android keeps no record
     * @param memoryGuardOn the low-memory switch
     * @param jitGuardOn the compiler-crash switch
     */
    public static Decision decide(State before, int requestedMb, Exit exit, boolean memoryGuardOn, boolean jitGuardOn) {
        int steps = before.lowMemorySteps;
        boolean jit = before.jitFallback;
        long handled = before.handledAtMs;

        // A different RAM setting is a new start: the player has answered the problem themselves, so the
        // steps go, and a close that happened before the change is not counted against the new setting.
        boolean ramChanged = before.allocationMb != 0 && before.allocationMb != requestedMb;
        if (ramChanged) steps = 0;
        // A guard that is off keeps no memory of anything.
        if (!memoryGuardOn) steps = 0;
        if (!jitGuardOn) jit = false;

        if (exit != null && exit.timestampMs > handled) {
            if (memoryGuardOn && !ramChanged && exit.kind == EXIT_LOW_MEMORY) steps = Math.min(steps + 1, MAX_STEPS);
            if (jitGuardOn && exit.kind == EXIT_JIT_CRASH) jit = true;
            handled = exit.timestampMs;
        }
        State next = new State(steps, jit, handled, requestedMb);
        return new Decision(reducedAllocation(requestedMb, steps), jit, next);
    }
}
