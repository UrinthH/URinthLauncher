package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;

import java.io.File;

import git.artdeell.mojo.BuildConfig;

/**
 * The safety net around the Performance Mode swap to MobileGlues.
 *
 * <p>The launcher's own start-up guard only notices a game that died within its first 30 seconds, and a phone
 * like yours needs about a minute to reach the point where a renderer problem shows. So a swapped launch is
 * followed separately: each launch notes whether it used the swap, and when the game then stops by itself with
 * an error code soon after ({@link PerformanceMobileGlues#swapFailed}), the swap is not used again until the
 * player turns the MobileGlues switch in Settings off and on. The record only counts for the launcher build
 * that made it, so installing a newer build tries MobileGlues again. The files live in {@link SwapFailureStore}.
 */
public final class PerformanceMobileGluesGuard {
    private static final String TAG = "MobileGluesGuard";

    private PerformanceMobileGluesGuard() {}

    private static File dir(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return (app == null ? context : app).getFilesDir();
    }

    /** Once per launch, after its renderer is settled: whether this launch started with the swap. */
    public static void markLaunch(Context context, boolean swapUsed) {
        File dir = dir(context);
        if (dir != null) SwapFailureStore.markLaunch(dir, swapUsed, System.currentTimeMillis());
    }

    /** When the game stops by itself with an exit code (called from the exit screen's hook). */
    public static void onGameExit(Context context, int exitCode, boolean isSignal) {
        File dir = dir(context);
        if (dir == null) return;
        if (SwapFailureStore.onGameExit(dir, BuildConfig.VERSION_NAME, System.currentTimeMillis(), exitCode, isSignal)) {
            String text = "the game ended with error code " + exitCode + " soon after it started with MobileGlues, "
                    + "so Performance Mode uses LTW until the MobileGlues switch in Settings is turned off and on";
            Log.w(TAG, text);
            try {
                Logger.appendToLog("[PerformanceMode] " + text);
            } catch (Throwable ignored) {
                // The game is going away; the line is only a bonus.
            }
        }
    }

    /** Whether this launcher build saw a swapped launch fail, so the swap is held back. */
    public static boolean lastSwapFailed(Context context) {
        File dir = dir(context);
        return dir != null && SwapFailureStore.lastSwapFailed(dir, BuildConfig.VERSION_NAME);
    }

    /** The player changed the MobileGlues switch: forget the failure. */
    public static void clearFailure(Context context) {
        File dir = dir(context);
        if (dir != null) SwapFailureStore.clearFailure(dir);
    }
}
