package net.kdt.pojavlaunch.game;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import git.artdeell.mojo.R;

/**
 * Repeating thermal watchdog that runs for as long as the game screen is open.
 *
 * Every few seconds it reads how hot the device is, from three independent sources so that it
 * still works on phones that implement only one of them: the OS thermal status (Android 10+),
 * the OS thermal headroom forecast (Android 11+) and the battery temperature (every version).
 * The result is a heat level from cool to critical, which rises immediately and falls only after
 * several calmer readings in a row, so it cannot flap around a threshold.
 *
 * What it does with that level is limited to what an app can do without root, and none of it
 * touches the render resolution, options.txt or the chosen renderer:
 * <ul>
 *   <li>warm and above: ask the display to run at 60 Hz instead of 90/120 Hz, which lowers how
 *       often a vsync-paced game renders, and the power the panel itself draws;</li>
 *   <li>hot and above: turn Android's Sustained Performance Mode on if the user has not already
 *       (the OS then holds CPU and GPU clocks at a level the phone can keep up indefinitely), and
 *       lower the priority of the JVM's JIT compiler threads so they stop competing with the
 *       render thread while the cores are being throttled. Garbage collector threads are left
 *       alone on purpose: starving them on a small heap can end in a long full collection.</li>
 * </ul>
 * Everything is undone as the phone cools. It cannot lower an unlimited frame rate by itself: a
 * game that renders without waiting for vsync is limited only by its own frame limiter.
 */
public final class ThermalGuard {
    private static final String TAG = "ThermalGuard";

    private static final int LEVEL_COOL = 0;
    private static final int LEVEL_WARM = 1;
    private static final int LEVEL_HOT = 2;
    private static final int LEVEL_CRITICAL = 3;

    private static final long FIRST_POLL_MS = 2_000L;
    private static final long POLL_COOL_MS = 6_000L;
    private static final long POLL_HOT_MS = 3_000L;
    /** Consecutive calmer readings needed before easing back down one level. */
    private static final int CALMER_READINGS_TO_EASE = 3;
    private static final float CAPPED_REFRESH_RATE = 60f;
    /** Unix nice value given to background JVM helper threads while the phone is hot. */
    private static final int BACKGROUND_NICE = 10;
    private static final long TOAST_COOLDOWN_MS = 3L * 60L * 1000L;

    private static ThermalGuard sInstance;

    private final Activity mActivity;
    private final GameView mGameView;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private final PowerManager mPowerManager;
    private final boolean mCanCapRefreshRate;
    private final boolean mSustainedModeSupported;
    private final Runnable mPollTask = new Runnable() {
        @Override
        public void run() {
            poll();
        }
    };

    @Nullable
    private PowerManager.OnThermalStatusChangedListener mStatusListener;
    private boolean mRunning;
    private int mLevel = LEVEL_COOL;
    private int mCalmerReadings;
    private boolean mRefreshRateCapped;
    @Nullable
    private Surface mCappedSurface;
    private boolean mSustainedModeForced;
    private long mLastToastAt = -TOAST_COOLDOWN_MS;
    /** Kernel thread id to the priority it had before this guard lowered it. */
    private final Map<Integer, Integer> mLoweredThreads = new HashMap<>();
    /** Kernel thread id to thread name; read once per thread, since names rarely change. */
    private final Map<Integer, String> mThreadNames = new HashMap<>();

    private ThermalGuard(Activity activity, GameView gameView) {
        mActivity = activity;
        mGameView = gameView;
        mPowerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        mCanCapRefreshRate = highestRefreshRate(activity) > CAPPED_REFRESH_RATE + 1f;
        mSustainedModeSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && mPowerManager != null && mPowerManager.isSustainedPerformanceModeSupported();
    }

    /** Start watching. Call from the main thread; a guard that is already running is replaced. */
    public static synchronized void start(Activity activity, GameView gameView) {
        stop();
        try {
            ThermalGuard guard = new ThermalGuard(activity, gameView);
            sInstance = guard;
            guard.begin();
        } catch (Throwable error) {
            // The guard is an extra. If it cannot run, the game must still start.
            Log.w(TAG, "Thermal guard unavailable", error);
            sInstance = null;
        }
    }

    /** Stop watching and give back any thread priorities that were lowered. */
    public static synchronized void stop() {
        if (sInstance == null) return;
        sInstance.end();
        sInstance = null;
    }

    private void begin() {
        mRunning = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mPowerManager != null) {
            PowerManager.OnThermalStatusChangedListener listener = status -> {
                // The OS says something changed: look right away instead of waiting for the timer.
                if (!mRunning) return;
                mHandler.removeCallbacks(mPollTask);
                poll();
            };
            try {
                mPowerManager.addThermalStatusListener(ContextCompat.getMainExecutor(mActivity), listener);
                mStatusListener = listener;
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not listen for thermal status changes", error);
            }
        }
        log("Started. Display cap " + (mCanCapRefreshRate ? "available" : "not needed")
                + ", sustained performance mode "
                + (mSustainedModeSupported ? "supported" : "not supported on this device"));
        mHandler.postDelayed(mPollTask, FIRST_POLL_MS);
    }

    private void end() {
        mRunning = false;
        mHandler.removeCallbacks(mPollTask);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mPowerManager != null && mStatusListener != null) {
            try {
                mPowerManager.removeThermalStatusListener(mStatusListener);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop listening for thermal status changes", error);
            }
        }
        mStatusListener = null;
        restoreLoweredThreads();
        // The window and surface are about to be destroyed with the activity: nothing to reset.
    }

    private void poll() {
        if (!mRunning) return;
        try {
            int reading = readHeatLevel();
            if (reading > mLevel) {
                mCalmerReadings = 0;
                changeLevel(reading);
            } else if (reading < mLevel) {
                if (++mCalmerReadings >= CALMER_READINGS_TO_EASE) {
                    mCalmerReadings = 0;
                    changeLevel(mLevel - 1);
                }
            } else {
                mCalmerReadings = 0;
            }
            // Re-apply on every pass, not only when the level changes: the game surface gets
            // re-created when the app is backgrounded, and the JVM starts new helper threads.
            applyRefreshRateCap(mLevel >= LEVEL_WARM);
            applySustainedMode(mLevel >= LEVEL_HOT);
            if (mLevel >= LEVEL_HOT) lowerBackgroundThreads();
            else restoreLoweredThreads();
        } catch (Throwable error) {
            Log.w(TAG, "Thermal guard pass failed", error);
        } finally {
            if (mRunning) {
                mHandler.removeCallbacks(mPollTask);
                mHandler.postDelayed(mPollTask, mLevel >= LEVEL_HOT ? POLL_HOT_MS : POLL_COOL_MS);
            }
        }
    }

    // ---------------------------------------------------------------- reading the device

    private int readHeatLevel() {
        int level = LEVEL_COOL;
        if (mPowerManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                level = Math.max(level, levelFromStatus(mPowerManager.getCurrentThermalStatus()));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                float headroom = mPowerManager.getThermalHeadroom(10);
                // NaN means this device does not report headroom.
                if (!Float.isNaN(headroom)) level = Math.max(level, levelFromHeadroom(headroom));
            }
        }
        return Math.max(level, levelFromBatteryTemperature());
    }

    private static int levelFromStatus(int status) {
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) return LEVEL_CRITICAL;
        if (status >= PowerManager.THERMAL_STATUS_MODERATE) return LEVEL_HOT;
        if (status >= PowerManager.THERMAL_STATUS_LIGHT) return LEVEL_WARM;
        return LEVEL_COOL;
    }

    /** Headroom is a forecast where 1.0 is the point the OS starts severe throttling. */
    private static int levelFromHeadroom(float headroom) {
        if (headroom >= 1.0f) return LEVEL_CRITICAL;
        if (headroom >= 0.92f) return LEVEL_HOT;
        if (headroom >= 0.80f) return LEVEL_WARM;
        return LEVEL_COOL;
    }

    /** The one temperature every Android version exposes, so it covers phones with no thermal API. */
    private int levelFromBatteryTemperature() {
        Intent battery = mActivity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return LEVEL_COOL;
        int tenthsOfDegree = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (tenthsOfDegree == Integer.MIN_VALUE) return LEVEL_COOL;
        if (tenthsOfDegree >= 450) return LEVEL_CRITICAL;
        if (tenthsOfDegree >= 425) return LEVEL_HOT;
        if (tenthsOfDegree >= 400) return LEVEL_WARM;
        return LEVEL_COOL;
    }

    private void changeLevel(int newLevel) {
        int oldLevel = mLevel;
        mLevel = newLevel;
        log("Heat level " + levelName(oldLevel) + " -> " + levelName(newLevel));
        long now = SystemClock.elapsedRealtime();
        if (newLevel >= LEVEL_HOT && oldLevel < LEVEL_HOT && now - mLastToastAt >= TOAST_COOLDOWN_MS) {
            mLastToastAt = now;
            Toast.makeText(mActivity, R.string.thermal_guard_hot_toast, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- what it does about it

    /**
     * Cap the display's refresh rate while warm. On Android 11+ with a SurfaceView this is a hint
     * on the surface itself, which only changes the refresh rate when the switch is seamless.
     * Otherwise (older Android, or a TextureView) it is a preferred refresh rate on the window.
     */
    private void applyRefreshRateCap(boolean cap) {
        if (!mCanCapRefreshRate) return;
        Surface surface = mGameView.getCurrentSurface();
        boolean surfaceChanged = cap && surface != mCappedSurface;
        if (cap == mRefreshRateCapped && !surfaceChanged) return;

        float rate = cap ? CAPPED_REFRESH_RATE : 0f;
        boolean usedSurface = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && LauncherPreferences.PREF_USE_ALTERNATE_SURFACE
                && surface != null && surface.isValid()) {
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            usedSurface = true;
        }
        Window window = mActivity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        float windowRate = (cap && !usedSurface) ? rate : 0f;
        if (params.preferredRefreshRate != windowRate) {
            params.preferredRefreshRate = windowRate;
            window.setAttributes(params);
        }
        boolean wasCapped = mRefreshRateCapped;
        mRefreshRateCapped = cap;
        mCappedSurface = cap ? surface : null;
        // Only report a real change, not the quiet re-apply after the surface was re-created.
        if (cap != wasCapped) {
            log(cap ? "Display refresh rate capped at " + (int) CAPPED_REFRESH_RATE + " Hz"
                    : "Display refresh rate cap removed");
        }
    }

    /** Hold CPU/GPU clocks at a sustainable level while hot, unless the user already runs that way. */
    private void applySustainedMode(boolean wanted) {
        if (!mSustainedModeSupported || LauncherPreferences.PREF_SUSTAINED_PERFORMANCE) return;
        if (wanted == mSustainedModeForced) return;
        mActivity.getWindow().setSustainedPerformanceMode(wanted);
        mSustainedModeForced = wanted;
        log(wanted ? "Sustained performance mode on" : "Sustained performance mode off");
    }

    /**
     * Let the render thread win when the cores are being throttled: the JVM's JIT compiler threads
     * are not latency sensitive, so they go to the back of the queue. This does not lower the
     * total work done, it only changes who waits for whom.
     */
    private void lowerBackgroundThreads() {
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return;

        Set<Integer> alive = new HashSet<>();
        for (File task : tasks) {
            int tid;
            try {
                tid = Integer.parseInt(task.getName());
            } catch (NumberFormatException notAThread) {
                continue;
            }
            alive.add(tid);
            if (!mThreadNames.containsKey(tid)) mThreadNames.put(tid, readThreadName(task));
        }
        // Forget threads that ended so a recycled thread id is looked at fresh.
        Iterator<Integer> known = mThreadNames.keySet().iterator();
        while (known.hasNext()) {
            if (!alive.contains(known.next())) known.remove();
        }

        for (Map.Entry<Integer, String> thread : mThreadNames.entrySet()) {
            int tid = thread.getKey();
            if (mLoweredThreads.containsKey(tid) || !isBackgroundHelper(thread.getValue())) continue;
            try {
                int original = Process.getThreadPriority(tid);
                if (original >= BACKGROUND_NICE) continue;
                Process.setThreadPriority(tid, BACKGROUND_NICE);
                mLoweredThreads.put(tid, original);
            } catch (RuntimeException gone) {
                // The thread ended in the meantime, or the system refused: leave it alone.
            }
        }
    }

    private void restoreLoweredThreads() {
        if (mLoweredThreads.isEmpty()) return;
        for (Map.Entry<Integer, Integer> lowered : mLoweredThreads.entrySet()) {
            try {
                Process.setThreadPriority(lowered.getKey(), lowered.getValue());
            } catch (RuntimeException gone) {
                // Already ended.
            }
        }
        mLoweredThreads.clear();
        log("Background thread priorities restored");
    }

    /** The kernel cuts thread names to 15 characters, hence the short prefixes. */
    private static boolean isBackgroundHelper(@Nullable String name) {
        if (name == null) return false;
        return name.startsWith("C1 Compiler") || name.startsWith("C2 Compiler");
    }

    @Nullable
    private static String readThreadName(File task) {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(task, "comm")))) {
            String name = reader.readLine();
            return name == null ? null : name.trim();
        } catch (IOException error) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("deprecation")
    private static float highestRefreshRate(Activity activity) {
        try {
            Display display = activity.getWindowManager().getDefaultDisplay();
            float best = display.getRefreshRate();
            float[] rates = display.getSupportedRefreshRates();
            if (rates != null) {
                for (float rate : rates) best = Math.max(best, rate);
            }
            return best;
        } catch (RuntimeException error) {
            return 0f;
        }
    }

    private static String levelName(int level) {
        switch (level) {
            case LEVEL_WARM: return "warm";
            case LEVEL_HOT: return "hot";
            case LEVEL_CRITICAL: return "critical";
            default: return "cool";
        }
    }

    private static void log(String message) {
        Log.i(TAG, message);
        try {
            Logger.appendToLog("[ThermalGuard] " + message);
        } catch (Throwable ignored) {
            // The native logger is not available yet, or already gone. The Android log has it.
        }
    }
}
