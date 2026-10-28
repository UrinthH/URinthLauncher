package net.kdt.pojavlaunch.game;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Part of Performance Mode: keeps the render thread from waiting for a CPU core.
 *
 * A hitch on screen usually means the render thread was ready to draw but another thread was
 * using the core it needed. The kernel counts, for every thread, how long it spent ready and
 * waiting. This reads that counter for the render thread every second and a half. When the wait
 * grows, Minecraft's background helpers (world and chunk workers, disk workers, Sodium's chunk
 * builders) are moved a little further back in the queue, and moved forward again once the wait
 * is gone. It never touches the render thread's own work, the integrated server, the network
 * threads, the garbage collector or the JIT compiler, and it does nothing at all while the render
 * thread gets its core without waiting. It adds no work for the game: a small file read and a
 * sum per sample.
 *
 * When the phone is hot ({@link ThermalGuard}) the helpers are lowered ahead of time, since a
 * throttled CPU is where contention starts.
 *
 * A summary line goes into the game log once a minute, so a lag report can be matched to what
 * the render thread was actually going through.
 */
public final class RenderScheduler {
    private static final String TAG = "RenderScheduler";
    private static final long SAMPLE_MS = 1_500L;
    private static final long SUMMARY_MS = 60_000L;
    private static final int GIVE_UP_AFTER_FAILED_READS = 4;

    private static RenderScheduler sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SchedulerPolicy mPolicy = new SchedulerPolicy();
    private final Runnable mSampleTask = new Runnable() {
        @Override
        public void run() {
            sample();
        }
    };

    private boolean mRunning;
    private int mRenderTid = -1;
    private long mLastWaitNs = -1;
    private long mLastSampleAt;
    private int mFailedReads;
    private int mAppliedLevel = SchedulerPolicy.LEVEL_NONE;

    /** Kernel thread id to thread name, read once per thread. */
    private final Map<Integer, String> mThreadNames = new HashMap<>();
    /** Helper thread id to the priority it had before it was lowered. */
    private final Map<Integer, Integer> mOriginalPriorities = new HashMap<>();
    /** Helper thread id to the priority value currently set by this class. */
    private final Map<Integer, Integer> mAppliedPriorities = new HashMap<>();

    // Numbers for the once-a-minute summary.
    private long mSummaryStartedAt;
    private int mSamples;
    private int mSamplesLowered;
    private double mWaitSum;
    private double mWaitPeak;

    private RenderScheduler() {}

    /** Start sampling. Call from the main thread; a scheduler already running is replaced. */
    public static synchronized void start() {
        stop();
        try {
            RenderScheduler scheduler = new RenderScheduler();
            sInstance = scheduler;
            scheduler.begin();
        } catch (Throwable error) {
            // An extra: if it cannot run, the game still starts.
            Log.w(TAG, "Render scheduler unavailable", error);
            sInstance = null;
        }
    }

    /** Stop sampling and give every lowered thread its own priority back. */
    public static synchronized void stop() {
        RenderScheduler scheduler = sInstance;
        sInstance = null;
        if (scheduler != null) scheduler.end();
    }

    private void begin() {
        mRunning = true;
        mSummaryStartedAt = SystemClock.elapsedRealtime();
        mHandler.postDelayed(mSampleTask, SAMPLE_MS);
        log("Started: helper threads are lowered only while the render thread is waiting for a core");
    }

    private void end() {
        mRunning = false;
        mHandler.removeCallbacks(mSampleTask);
        restoreAll();
    }

    private void sample() {
        if (!mRunning) return;
        try {
            long now = SystemClock.elapsedRealtimeNanos();
            Set<Integer> helpers = scanThreads();
            if (mRenderTid > 0) {
                long wait = SchedulerPolicy.parseWaitNanos(readFile(new File("/proc/self/task/" + mRenderTid + "/schedstat")));
                if (wait < 0) {
                    if (++mFailedReads >= GIVE_UP_AFTER_FAILED_READS) {
                        log("This phone does not report thread wait times; the scheduler is switching itself off");
                        mRunning = false;
                        restoreAll();
                        return;
                    }
                } else {
                    mFailedReads = 0;
                    if (mLastWaitNs >= 0) {
                        double fraction = SchedulerPolicy.waitFraction(mLastWaitNs, wait, now - mLastSampleAt);
                        mPolicy.step(fraction);
                        record(fraction);
                    }
                    mLastWaitNs = wait;
                    mLastSampleAt = now;
                }
            }
            int level = Math.max(mPolicy.level, levelForHeat(ThermalGuard.currentHeatLevel()));
            apply(level, helpers);
            maybeSummarise();
        } catch (Throwable error) {
            Log.w(TAG, "Render scheduler pass failed", error);
        } finally {
            if (mRunning) {
                mHandler.removeCallbacks(mSampleTask);
                mHandler.postDelayed(mSampleTask, SAMPLE_MS);
            }
        }
    }

    /** A hot phone starts the helpers lowered, a critical one lowers them clearly. */
    private static int levelForHeat(int heatLevel) {
        if (heatLevel >= ThermalGuard.HEAT_CRITICAL) return SchedulerPolicy.LEVEL_STRONG;
        if (heatLevel >= ThermalGuard.HEAT_HOT) return SchedulerPolicy.LEVEL_MILD;
        return SchedulerPolicy.LEVEL_NONE;
    }

    /** Find the render thread and the helper threads. Names are read once per thread. */
    private Set<Integer> scanThreads() {
        Set<Integer> helpers = new HashSet<>();
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return helpers;
        Set<Integer> alive = new HashSet<>();
        for (File task : tasks) {
            int tid;
            try {
                tid = Integer.parseInt(task.getName());
            } catch (NumberFormatException notAThread) {
                continue;
            }
            alive.add(tid);
            String name = mThreadNames.get(tid);
            if (name == null) {
                name = readThreadName(task);
                mThreadNames.put(tid, name == null ? "" : name);
            }
            if (mRenderTid < 0 && SchedulerPolicy.isRenderThread(name)) {
                mRenderTid = tid;
                log("Watching the render thread");
            } else if (SchedulerPolicy.isHelperThread(name)) {
                helpers.add(tid);
            }
        }
        mThreadNames.keySet().retainAll(alive);
        mOriginalPriorities.keySet().retainAll(alive);
        mAppliedPriorities.keySet().retainAll(alive);
        return helpers;
    }

    private void apply(int level, Set<Integer> helpers) {
        if (level == SchedulerPolicy.LEVEL_NONE) {
            if (!mAppliedPriorities.isEmpty()) restoreAll();
            if (mAppliedLevel != level) log("Helper threads back to normal priority");
            mAppliedLevel = level;
            return;
        }
        int nice = SchedulerPolicy.niceFor(level);
        for (int tid : helpers) {
            Integer current = mAppliedPriorities.get(tid);
            if (current != null && current == nice) continue;
            try {
                if (!mOriginalPriorities.containsKey(tid)) {
                    mOriginalPriorities.put(tid, Process.getThreadPriority(tid));
                }
                Process.setThreadPriority(tid, nice);
                mAppliedPriorities.put(tid, nice);
            } catch (RuntimeException gone) {
                // The thread ended in the meantime, or the system refused: leave it alone.
            }
        }
        if (mAppliedLevel != level) {
            log("Helper threads lowered (" + (level >= SchedulerPolicy.LEVEL_STRONG ? "strongly" : "slightly")
                    + "): " + helpers.size() + " threads");
        }
        mAppliedLevel = level;
    }

    private void restoreAll() {
        for (Map.Entry<Integer, Integer> original : mOriginalPriorities.entrySet()) {
            try {
                Process.setThreadPriority(original.getKey(), original.getValue());
            } catch (RuntimeException gone) {
                // Already ended.
            }
        }
        mOriginalPriorities.clear();
        mAppliedPriorities.clear();
    }

    private void record(double waitFraction) {
        mSamples++;
        mWaitSum += waitFraction;
        if (waitFraction > mWaitPeak) mWaitPeak = waitFraction;
        if (mAppliedLevel != SchedulerPolicy.LEVEL_NONE) mSamplesLowered++;
    }

    private void maybeSummarise() {
        long now = SystemClock.elapsedRealtime();
        if (now - mSummaryStartedAt < SUMMARY_MS || mSamples == 0) return;
        log(String.format(java.util.Locale.US,
                "Last minute: the render thread waited for a CPU core %.1f%% of the time on average, %.0f%% at worst; "
                        + "helper threads were lowered for %d%% of it",
                100 * mWaitSum / mSamples, 100 * mWaitPeak, Math.round(100f * mSamplesLowered / mSamples)));
        mSummaryStartedAt = now;
        mSamples = 0;
        mSamplesLowered = 0;
        mWaitSum = 0;
        mWaitPeak = 0;
    }

    @Nullable
    private static String readThreadName(File task) {
        String name = readFile(new File(task, "comm"));
        return name == null ? null : name.trim();
    }

    @Nullable
    private static String readFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException error) {
            return null;
        }
    }

    private static void log(String message) {
        Log.i(TAG, message);
        try {
            Logger.appendToLog("[PerformanceMode] " + message);
        } catch (Throwable ignored) {
            // The native logger is not available yet, or already gone. The Android log has it.
        }
    }
}
