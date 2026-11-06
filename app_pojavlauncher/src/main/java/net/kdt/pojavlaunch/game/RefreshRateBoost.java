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

import java.util.ArrayList;
import java.util.List;

/**
 * Asks Android to run the screen at a high refresh rate while the game is on screen, and in
 * Performance Mode keeps it there. A lot of phones sit at 60 Hz for apps that do not ask; with
 * the highest mode requested, Minecraft's vsync follows that rate.
 *
 * There are three parts:
 * <ul>
 * <li>The window asks for the best display mode (always).</li>
 * <li>Performance Mode also tells the system, through the game's surface, that the game wants
 * that frame rate (Android 11+). Phones with an adaptive screen pick their rate from what the
 * apps on screen say they need, and a game that draws straight to the screen says nothing, so
 * they fall back to 60 Hz. The surface says it.</li>
 * <li>Performance Mode also watches the screen: when the system lowers the rate anyway, the
 * request is repeated (at most once a second). If the system keeps refusing, which it does in
 * battery saver or on a hot phone, it backs off to one try every half minute instead of arguing.
 * See {@link RefreshRatePolicy.Guard}.</li>
 * </ul>
 *
 * Only the display mode's refresh rate is requested, and only among modes with the same physical
 * size as the current one, so nothing about the screen size, the game's render resolution or
 * options.txt is touched. A phone that has no mode above its current rate (or a 60 Hz screen)
 * simply stays as it is: the request can never raise the rate above what the hardware offers.
 */
public final class RefreshRateBoost {
    private static final String TAG = "RefreshRateBoost";

    private static volatile float sTarget;
    private static volatile boolean sCapChosen;
    /** Main thread only. */
    private static Session sSession;

    private RefreshRateBoost() {}

    /** The rate requested by the last {@link #start}, or 0 when none was made. */
    public static float targetRate() {
        return sTarget;
    }

    /**
     * The refresh rate to tell the game the screen has. The screen may still be switching to the
     * mode that was asked for, so the wanted rate counts, not only the current one. A rate the
     * player chose in Settings is reported as it is, also when it is below the current one.
     */
    public static float reportedRate(float displayRate) {
        float target = sTarget;
        if (target <= 0f) return displayRate;
        return sCapChosen ? target : Math.max(displayRate, target);
    }

    /**
     * Ask for the best refresh rate and, with {@code keepIt}, hold it for as long as the game
     * runs. Call from the main thread, once the window exists.
     *
     * @param capHz  0 for Auto (the highest rate up to 120 Hz), or the highest rate the player allows
     * @param keepIt vote on the game surface and ask again when the system lowers the rate
     */
    public static void start(Activity activity, int capHz, boolean keepIt) {
        stop();
        try {
            Session session = new Session(activity, capHz, keepIt);
            sSession = session;
            session.begin();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not request a refresh rate", error);
            sSession = null;
        }
    }

    /** Stop watching the screen. Call when the game screen closes. */
    public static void stop() {
        Session session = sSession;
        sSession = null;
        if (session != null) session.end();
        sTarget = 0f;
        sCapChosen = false;
    }

    /**
     * Same as {@link #stop()}, but only when the running request belongs to {@code owner}: a new
     * game screen can start before the old one is destroyed, and it must keep its own request.
     */
    public static void stop(Activity owner) {
        Session session = sSession;
        if (session != null && session.mActivity == owner) stop();
    }

    /** The game surface exists (again): tell the system what frame rate it is drawn at. */
    public static void voteOn(@Nullable Surface surface) {
        Session session = sSession;
        if (session != null) session.surfaceReady(surface);
    }

    /** The game surface is gone. */
    public static void surfaceGone() {
        Session session = sSession;
        if (session != null) session.surface = null;
    }

    /** The game screen is back in front: look at the screen right away. */
    public static void onForeground() {
        Session session = sSession;
        if (session != null) session.foreground();
    }

    private static final class Session implements DisplayManager.DisplayListener {
        private final Activity mActivity;
        private final int mCapHz;
        private final boolean mKeep;
        private final DisplayManager mDisplays;
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final RefreshRatePolicy.Guard mGuard = new RefreshRatePolicy.Guard();
        private RefreshRatePolicy.Choice mChoice;
        private boolean mListening;
        private int mReasks;
        /** Main thread only. */
        Surface surface;

        Session(Activity activity, int capHz, boolean keepIt) {
            mActivity = activity;
            mCapHz = capHz;
            mKeep = keepIt;
            mDisplays = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        }

        void begin() {
            Display display = display();
            float current = display.getRefreshRate();
            mChoice = choose(display);
            sCapChosen = mCapHz > 0;
            askWindow();
            Logger.appendToLog("[RefreshRate] screen is at " + current + " Hz, requested " + mChoice.hz + " Hz"
                    + (mChoice.modeId == 0 ? " (current mode)" : " (mode " + mChoice.modeId + ")")
                    + (mCapHz > 0 ? ", limit chosen in Settings: " + mCapHz + " Hz" : ", Auto")
                    + (mKeep ? ", kept while playing" : ""));
            if (mKeep && mDisplays != null) {
                mDisplays.registerDisplayListener(this, mHandler);
                mListening = true;
            }
        }

        void end() {
            mListening = false;
            surface = null;
            if (mDisplays == null) return;
            try {
                mDisplays.unregisterDisplayListener(this);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop watching the screen", error);
            }
        }

        void surfaceReady(@Nullable Surface newSurface) {
            surface = newSurface;
            // Only the vote: at start the screen has not switched yet, and that is not a "drop".
            // The display listener looks after changes from here on.
            if (mKeep) vote(newSurface);
        }

        void foreground() {
            if (!mKeep) return;
            mGuard.reset();
            vote(surface);
            check();
        }

        // ------------------------------------------------------------ DisplayManager.DisplayListener

        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (!mListening) return;
            try {
                if (display().getDisplayId() == displayId) check();
            } catch (RuntimeException error) {
                Log.w(TAG, "Screen check failed", error);
            }
        }

        // ------------------------------------------------------------ what it does

        /** Ask again when the screen is below the wanted rate and the guard allows it. */
        private void check() {
            if (!mListening || mChoice == null) return;
            try {
                float current = display().getRefreshRate();
                if (!mGuard.shouldAsk(SystemClock.elapsedRealtime(), current, mChoice.hz)) return;
                mReasks++;
                // The first few, then one in a while: an adaptive screen can do this all session.
                if (mReasks <= 3 || mReasks % 25 == 0) {
                    Logger.appendToLog("[RefreshRate] screen dropped to " + current + " Hz, asking for "
                            + mChoice.hz + " Hz again (#" + mReasks + ", try " + mGuard.tries() + ")"
                            + (mGuard.tries() > RefreshRatePolicy.Guard.QUICK_TRIES
                                    ? ", the system keeps it down: trying only every 30 s" : ""));
                }
                askWindow();
                vote(surface);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not ask for the refresh rate again", error);
            }
        }

        @SuppressWarnings("deprecation")
        private Display display() {
            return mActivity.getWindowManager().getDefaultDisplay();
        }

        @SuppressWarnings("deprecation")
        private RefreshRatePolicy.Choice choose(Display display) {
            List<RefreshRatePolicy.Mode> modes = new ArrayList<>();
            RefreshRatePolicy.Mode current;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display.Mode now = display.getMode();
                current = new RefreshRatePolicy.Mode(now.getModeId(), now.getPhysicalWidth(),
                        now.getPhysicalHeight(), now.getRefreshRate());
                for (Display.Mode mode : display.getSupportedModes()) {
                    modes.add(new RefreshRatePolicy.Mode(mode.getModeId(), mode.getPhysicalWidth(),
                            mode.getPhysicalHeight(), mode.getRefreshRate()));
                }
            } else {
                // Before Android 6 there are only rates, no modes: the size is the same for all.
                current = new RefreshRatePolicy.Mode(0, 0, 0, display.getRefreshRate());
                float[] rates = display.getSupportedRefreshRates();
                if (rates != null) {
                    for (float rate : rates) modes.add(new RefreshRatePolicy.Mode(0, 0, 0, rate));
                }
            }
            return RefreshRatePolicy.choose(current, modes, mCapHz);
        }

        /** The window's own request: the display mode and its rate. */
        private void askWindow() {
            Window window = mActivity.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            if (mChoice.modeId != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                params.preferredDisplayModeId = mChoice.modeId;
            }
            params.preferredRefreshRate = mChoice.hz;
            window.setAttributes(params);
            sTarget = mChoice.hz;
        }

        /**
         * The surface's own request, which phones with an adaptive screen listen to. It always
         * gets sent again (it is a transaction, not a setting), so it also works for a rate the
         * window attributes already say.
         */
        private void vote(@Nullable Surface target) {
            if (target == null || mChoice == null || mChoice.hz <= 0f || !target.isValid()) return;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    target.setFrameRate(mChoice.hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ALWAYS);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    target.setFrameRate(mChoice.hz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                }
            } catch (RuntimeException error) {
                // The surface was released in the meantime, or the system refused: nothing to undo.
                Log.w(TAG, "Could not set the surface frame rate", error);
            }
        }
    }
}
