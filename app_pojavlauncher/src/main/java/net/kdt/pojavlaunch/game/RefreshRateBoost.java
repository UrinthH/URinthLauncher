package net.kdt.pojavlaunch.game;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;

import java.lang.ref.WeakReference;

/**
 * Asks Android to run the screen at the highest refresh rate it supports, up to 120 Hz, while the
 * game is on screen. A lot of phones sit at 60 Hz for apps that do not ask; with the highest
 * mode requested, Minecraft's vsync follows that rate.
 *
 * Only the display mode's refresh rate is requested, and only among modes with the same physical
 * size as the current one, so nothing about the screen size, the game's render resolution or
 * options.txt is touched. A phone that has no mode above its current rate (or a 60 Hz screen)
 * simply stays as it is: the request can never raise the rate above what the hardware offers.
 *
 * In Performance Mode {@link #start} also keeps the request in place. Phones with smart or adaptive
 * refresh often lower the rate again after a while, and the frame rate then drops with it. Three
 * things are done about that, all ideas other launchers use: the game surface itself votes for the
 * rate (Android 12 and later, which also works on screens that cannot switch modes without a
 * flicker), the request is repeated as soon as Android reports the system lowered the rate, and it
 * is also checked on a plain timer. The timer is the one that matters most: once the screen settles
 * at the lower rate, Android has nothing further to report and stops sending that event, so without
 * the timer nothing would ever ask again and the game would stay at the lower rate, and the lower
 * frame rate that comes with it, until it is closed and reopened. {@link RefreshRatePolicy} decides
 * whether to actually ask on any given check: never while the phone is hot, and with a longer wait
 * after each request that did not help, so a phone that insists on a lower rate is not asked over
 * and over.
 */
public final class RefreshRateBoost {
    private static final String TAG = "RefreshRateBoost";
    private static final float WANTED_MAX_HZ = 120f;
    /** How often the rate is checked even without an Android event saying it changed. */
    private static final long POLL_MS = 2_000L;

    private static volatile float sTarget;
    /** The display mode asked for by {@link #apply}, or 0 when no particular mode was. */
    private static int sModeId;

    // The watch. All of it is touched from the main thread only (start, stop and the listener).
    private static boolean sWatching;
    @Nullable
    private static DisplayManager sDisplayManager;
    @Nullable
    private static DisplayManager.DisplayListener sListener;
    @Nullable
    private static WeakReference<Activity> sActivity;
    @Nullable
    private static WeakReference<GameView> sGameView;
    private static long sLastRequestAt = -1;
    private static int sFailedRequests;
    @Nullable
    private static Handler sPollHandler;
    @Nullable
    private static Runnable sPollTask;

    private RefreshRateBoost() {}

    /** The rate requested by the last {@link #apply}, or 0 when none was made. */
    public static float targetRate() {
        return sTarget;
    }

    /** Requests the highest supported refresh rate (at most 120 Hz). Call from the main thread. */
    @SuppressWarnings("deprecation")
    public static void apply(Activity activity) {
        try {
            Window window = activity.getWindow();
            Display display = activity.getWindowManager().getDefaultDisplay();
            WindowManager.LayoutParams params = window.getAttributes();
            float current = display.getRefreshRate();
            float best = current;
            int bestModeId = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display.Mode now = display.getMode();
                for (Display.Mode mode : display.getSupportedModes()) {
                    if (mode.getPhysicalWidth() != now.getPhysicalWidth()
                            || mode.getPhysicalHeight() != now.getPhysicalHeight()) continue;
                    float rate = mode.getRefreshRate();
                    if (rate <= WANTED_MAX_HZ + 0.5f && rate > best + 0.5f) {
                        best = rate;
                        bestModeId = mode.getModeId();
                    }
                }
                if (bestModeId != 0) params.preferredDisplayModeId = bestModeId;
            }
            params.preferredRefreshRate = best;
            window.setAttributes(params);
            sTarget = best;
            sModeId = bestModeId;
            Logger.appendToLog("[RefreshRate] screen is at " + current + " Hz, requested " + best + " Hz"
                    + (bestModeId == 0 ? " (no higher mode available)" : " (mode " + bestModeId + ")"));
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not request a higher refresh rate", error);
        }
    }

    /**
     * Keep the request in place until {@link #stop}: vote for the rate on the game surface and ask
     * again when the system lowers the rate. Call from the main thread after {@link #apply}. Does
     * nothing when {@link #apply} found no rate above the current one, since then there is nothing
     * for the system to lower.
     */
    public static synchronized void start(Activity activity, @Nullable GameView gameView) {
        stop();
        try {
            if (sTarget <= 0f) return;
            DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) return;
            DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    onScreenChanged(displayId);
                }
            };
            sActivity = new WeakReference<>(activity);
            sGameView = new WeakReference<>(gameView);
            sLastRequestAt = -1;
            sFailedRequests = 0;
            manager.registerDisplayListener(listener, new Handler(Looper.getMainLooper()));
            sDisplayManager = manager;
            sListener = listener;
            sWatching = true;
            if (gameView != null) voteForSurface(gameView.getCurrentSurface());
            Handler pollHandler = new Handler(Looper.getMainLooper());
            Runnable pollTask = new Runnable() {
                @Override
                public void run() {
                    if (!sWatching) return;
                    // Catches the case the display listener cannot: the screen has already settled at
                    // the lower rate, so Android has nothing new to report and sends no further event.
                    onScreenChanged(display(activity).getDisplayId());
                    pollHandler.postDelayed(this, POLL_MS);
                }
            };
            sPollHandler = pollHandler;
            sPollTask = pollTask;
            pollHandler.postDelayed(pollTask, POLL_MS);
            log("keeping the " + sTarget + " Hz request in place; it is repeated if the system lowers the rate");
        } catch (RuntimeException error) {
            // An extra: if it cannot run, the one-time request from apply() still stands.
            Log.w(TAG, "Could not start watching the refresh rate", error);
            stop();
        }
    }

    /** Stop watching. Safe to call when nothing is running. */
    public static synchronized void stop() {
        sWatching = false;
        if (sPollHandler != null && sPollTask != null) sPollHandler.removeCallbacks(sPollTask);
        sPollHandler = null;
        sPollTask = null;
        DisplayManager manager = sDisplayManager;
        DisplayManager.DisplayListener listener = sListener;
        sDisplayManager = null;
        sListener = null;
        sActivity = null;
        sGameView = null;
        if (manager != null && listener != null) {
            try {
                manager.unregisterDisplayListener(listener);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop watching the refresh rate", error);
            }
        }
    }

    /**
     * The game surface votes for the rate. This is what makes the system pick the high mode on
     * screens that only switch modes when an app's content asks for it. Called when the watch starts
     * and again whenever the game gets a new surface (it is re-created when the app comes back from
     * the background). Needs Android 12; does nothing before that or when the watch is not running.
     */
    public static void voteForSurface(@Nullable Surface surface) {
        float target = sTarget;
        if (!sWatching || surface == null || target <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            if (!surface.isValid()) return;
            surface.setFrameRate(target, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ALWAYS);
        } catch (RuntimeException error) {
            Log.w(TAG, "The game surface could not vote for the refresh rate", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static Display display(Activity activity) {
        return activity.getWindowManager().getDefaultDisplay();
    }

    @SuppressWarnings("deprecation")
    private static void onScreenChanged(int displayId) {
        try {
            Activity activity = sActivity == null ? null : sActivity.get();
            if (!sWatching || activity == null) return;
            Display display = display(activity);
            if (display.getDisplayId() != displayId) return;
            float current = display.getRefreshRate();
            if (!RefreshRatePolicy.isBelowTarget(sTarget, current)) {
                sFailedRequests = 0; // the screen is at the rate: the next drop starts fresh
                return;
            }
            long now = SystemClock.elapsedRealtime();
            boolean hot = ThermalGuard.currentHeatLevel() >= ThermalGuard.HEAT_HOT;
            if (!RefreshRatePolicy.shouldRequestAgain(sTarget, current, now, sLastRequestAt, sFailedRequests, hot)) return;
            sLastRequestAt = now;
            sFailedRequests++;
            requestAgain(activity, current);
        } catch (RuntimeException error) {
            Log.w(TAG, "Refresh rate check failed", error);
        }
    }

    private static void requestAgain(Activity activity, float current) {
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (sModeId != 0) params.preferredDisplayModeId = sModeId;
        params.preferredRefreshRate = sTarget;
        window.setAttributes(params);
        GameView gameView = sGameView == null ? null : sGameView.get();
        if (gameView != null) voteForSurface(gameView.getCurrentSurface());
        log("the system lowered the screen to " + current + " Hz, asked for " + sTarget + " Hz again (attempt "
                + sFailedRequests + ")");
    }

    private static void log(String message) {
        Log.i(TAG, message);
        try {
            Logger.appendToLog("[RefreshRate] " + message);
        } catch (Throwable ignored) {
            // The native logger is not available yet, or already gone. The Android log has it.
        }
    }
}
