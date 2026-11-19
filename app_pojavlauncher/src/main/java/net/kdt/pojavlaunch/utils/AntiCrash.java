package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.prefs.AdvancedPerformance;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

/**
 * The Anti Crash guard: before a start, looks at how the last game process ended and, for the two endings
 * the launcher can do something about, changes this start (see {@link AntiCrashPolicy}). Only active while
 * Performance Mode is on, like everything on the Advanced settings screen.
 *
 * Runs in the game process, before the Java arguments are built. It changes nothing that is stored in
 * Settings: the RAM change lives only in this process, and what it remembers is in one small file.
 */
public final class AntiCrash {
    private static final String TAG = "AntiCrash";
    private static volatile boolean sSimpleJit;
    /** Whether this game process already decided; the launch code can run twice (after the low-memory dialog). */
    private static boolean sApplied;

    private AntiCrash() {}

    /** True when this start must use the simple JIT because the last crash was inside the optimising compiler. */
    public static boolean simpleJitForced() {
        return sSimpleJit;
    }

    /**
     * Decide this start. Call before the Java arguments are built. Never throws. Does its work once per game
     * process: a second call (the launch code runs again after the low-memory dialog) would take the already
     * lowered RAM value for a change the player made, and forget the steps.
     */
    public static synchronized void apply(Context context) {
        try {
            if (!LauncherPreferences.PREF_PERFORMANCE_MODE || sApplied) return;
            sApplied = true;
            AdvancedPerformance settings = LauncherPreferences.PREF_ADVANCED;
            File dir = context.getFilesDir();
            int requested = LauncherPreferences.PREF_RAM_ALLOCATION;
            AntiCrashPolicy.Decision decision = AntiCrashPolicy.decide(AntiCrashStore.read(dir), requested,
                    GameExitReporter.newestExit(context),
                    settings.on(AdvancedPerformance.KEY_LOW_MEMORY_GUARD),
                    settings.on(AdvancedPerformance.KEY_JIT_CRASH_GUARD));
            AntiCrashStore.write(dir, decision.next);
            sSimpleJit = decision.simpleJit;
            if (decision.allocationMb != requested) {
                LauncherPreferences.PREF_RAM_ALLOCATION = decision.allocationMb;
                Logger.appendToLog("[AntiCrash] Android closed the game because the phone ran out of memory "
                        + decision.next.lowMemorySteps + " time(s) in a row, so this start uses "
                        + decision.allocationMb + " MB instead of the " + requested + " MB in Settings. "
                        + "Changing the RAM setting, or turning \"Lower RAM after a low-memory close\" off and on, undoes this.");
            }
            if (decision.simpleJit) {
                Logger.appendToLog("[AntiCrash] The last crash happened inside Java's optimising compiler, so this start "
                        + "uses the simple compiler (C1 only). Turning \"Simple JIT after a compiler crash\" off and on undoes this.");
            }
        } catch (Throwable error) {
            // A safety net must never be the reason the game does not start.
            Log.w(TAG, "Anti Crash could not decide", error);
        }
    }

    /** Steps taken off the RAM setting so far (0 when the guard has not acted). */
    public static int lowMemorySteps(Context context) {
        return AntiCrashStore.read(context.getFilesDir()).lowMemorySteps;
    }

    /** True when the compiler-crash fallback is waiting to be used or in use. */
    public static boolean jitFallbackActive(Context context) {
        return AntiCrashStore.read(context.getFilesDir()).jitFallback;
    }

    /** Forget the steps taken off the RAM setting (that switch was turned off or on). The other guard keeps its memory. */
    public static void clearLowMemory(Context context) {
        File dir = context.getFilesDir();
        AntiCrashStore.write(dir, AntiCrashStore.read(dir).withoutLowMemorySteps());
    }

    /** Forget the simple-JIT fallback (that switch was turned off or on). The other guard keeps its memory. */
    public static void clearJitFallback(Context context) {
        File dir = context.getFilesDir();
        AntiCrashStore.write(dir, AntiCrashStore.read(dir).withoutJitFallback());
    }

    /**
     * Forget the steps and the fallback (Reset on the Advanced settings screen). Which exit was already counted
     * is kept, otherwise the same old close would be counted again at the next start.
     */
    public static void clear(Context context) {
        File dir = context.getFilesDir();
        AntiCrashStore.write(dir, AntiCrashStore.read(dir).withoutLowMemorySteps().withoutJitFallback());
    }
}
