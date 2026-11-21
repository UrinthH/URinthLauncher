package net.kdt.pojavlaunch.game;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

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
 * waiting. This reads that counter for the render thread every second. When the wait grows,
 * Minecraft's background helpers (world and chunk workers, disk workers, Sodium's chunk builders)
 * and the JVM's JIT compiler threads are moved a little further back in the queue, and moved
 * forward again once the wait is gone. It never touches the render thread's own work, the
 * integrated server, the network threads or the garbage collector, and it does nothing at all
 * while the render thread gets its core without waiting. It adds no work for the game: a small
 * file read and a sum per sample.
 *
 * When the phone is hot ({@link ThermalGuard}) the helpers and compiler threads are lowered ahead
 * of time, since a throttled CPU is where contention starts. Some phones (a Unisoc T606 one did)
 * keep no wait counters at all; there the waits cannot be watched, so the helpers and compiler
 * threads simply stay slightly lowered for the whole game, which costs nothing while cores are
 * free. This class is the only place that lowers thread priorities, so two parts never fight over
 * the same thread.
 *
 * A summary line goes into the game log once a minute, so a lag report can be matched to what
 * the render thread was actually going through. A second line, "[RenderThread]", says how busy the
 * render thread was (see {@link RenderThreadStats}): always busy means the CPU is what limits the
 * frame rate, a thread that spends part of every frame waiting means the graphics chip or the
 * screen is, which is the first thing to know before trying to raise the FPS.
 */
public final class RenderScheduler {
    private static final String TAG = "RenderScheduler";
    /** One second: a 130 ms stall is 13% of it, enough to register, where 1.5 s diluted it to 8.7%. */
    private static final long SAMPLE_MS = 1_000L;
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
    /** True once the phone has shown it reports no usable wait times. */
    private boolean mWaitTimesUnavailable;
    private int mAppliedLevel = SchedulerPolicy.LEVEL_NONE;
    /** How busy the render thread is and on which cores it runs, for the once-a-minute "[RenderThread]" line. */
    @Nullable
    private RenderThreadStats mRenderStats;

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
        long fastCores = readFastCores();
        mRenderStats = new RenderThreadStats(fastCores, readClockTicks());
        logTagged("RenderThread", fastCores != 0
                ? "Fast cores of this phone: " + FastCores.describe(fastCores)
                : "This phone does not report which of its cores are fast");
        mHandler.postDelayed(mSampleTask, SAMPLE_MS);
        log("Started: helper and JIT compiler threads are lowered only while the render thread is waiting for a core");
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
            Set<Integer> helpers = new HashSet<>();
            Set<Integer> compilers = new HashSet<>();
            scanThreads(helpers, compilers);
            if (mRenderTid > 0 && mRenderStats != null) {
                mRenderStats.add(RenderThreadStats.parseStat(
                        readFile(new File("/proc/self/task/" + mRenderTid + "/stat"))), now);
            }
            if (mRenderTid > 0 && !mWaitTimesUnavailable) {
                String schedstat = readFile(new File("/proc/self/task/" + mRenderTid + "/schedstat"));
                long wait = SchedulerPolicy.hasRunTime(schedstat) ? SchedulerPolicy.parseWaitNanos(schedstat) : -1;
                if (wait < 0) {
                    if (++mFailedReads >= GIVE_UP_AFTER_FAILED_READS) {
                        // No wait times means no way to tell when the render thread is being kept
                        // waiting, so the reaction cannot be timed. The helpers stay slightly
                        // lowered instead (see SchedulerPolicy.effectiveLevel).
                        mWaitTimesUnavailable = true;
                        log("This phone does not report thread wait times, so helper and JIT compiler threads stay "
                                + "slightly lowered for the whole game instead of reacting to waits");
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
            int level = SchedulerPolicy.effectiveLevel(mPolicy.level,
                    levelForHeat(ThermalGuard.currentHeatLevel()), mWaitTimesUnavailable);
            apply(level, helpers, compilers);
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

    /** A hot phone starts the helpers and compiler threads lowered, a critical one lowers them clearly. */
    private static int levelForHeat(int heatLevel) {
        if (heatLevel >= ThermalGuard.HEAT_CRITICAL) return SchedulerPolicy.LEVEL_STRONG;
        if (heatLevel >= ThermalGuard.HEAT_HOT) return SchedulerPolicy.LEVEL_MILD;
        return SchedulerPolicy.LEVEL_NONE;
    }

    /**
     * Find the render thread, the helper threads and the JIT compiler threads, filling the two
     * sets. Names are read once per thread.
     */
    private void scanThreads(Set<Integer> helpers, Set<Integer> compilers) {
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
            } else if (SchedulerPolicy.isJitThread(name)) {
                compilers.add(tid);
            }
        }
        mThreadNames.keySet().retainAll(alive);
        mOriginalPriorities.keySet().retainAll(alive);
        mAppliedPriorities.keySet().retainAll(alive);
    }

    private void apply(int level, Set<Integer> helpers, Set<Integer> compilers) {
        if (level == SchedulerPolicy.LEVEL_NONE) {
            if (!mAppliedPriorities.isEmpty()) restoreAll();
            if (mAppliedLevel != level) log("Helper and JIT compiler threads back to normal priority");
            mAppliedLevel = level;
            return;
        }
        lower(helpers, SchedulerPolicy.niceFor(level));
        lower(compilers, SchedulerPolicy.niceForJit(level));
        if (mAppliedLevel != level) {
            log("Background threads lowered (" + (level >= SchedulerPolicy.LEVEL_STRONG ? "strongly" : "slightly")
                    + "): " + helpers.size() + " helpers, " + compilers.size() + " JIT compiler threads");
        }
        mAppliedLevel = level;
    }

    /** Give every thread in the set this nice value. A thread already further back is left as it is. */
    private void lower(Set<Integer> tids, int nice) {
        for (int tid : tids) {
            Integer current = mAppliedPriorities.get(tid);
            if (current != null && current == nice) continue;
            try {
                if (!mOriginalPriorities.containsKey(tid)) {
                    int original = Process.getThreadPriority(tid);
                    // Never move a thread forward: one that is already at or behind this value stays put.
                    if (original >= nice) continue;
                    mOriginalPriorities.put(tid, original);
                }
                Process.setThreadPriority(tid, nice);
                mAppliedPriorities.put(tid, nice);
            } catch (RuntimeException gone) {
                // The thread ended in the meantime, or the system refused: leave it alone.
            }
        }
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
        if (now - mSummaryStartedAt < SUMMARY_MS) return;
        // A phone with no wait counters (a Unisoc one did not have them) has no wait line, but still has the busy line.
        if (mSamples > 0) {
            log(String.format(java.util.Locale.US,
                    "Last minute: the render thread waited for a CPU core %.1f%% of the time on average, %.0f%% at worst; "
                            + "helper threads were lowered for %d%% of it",
                    100 * mWaitSum / mSamples, 100 * mWaitPeak, Math.round(100f * mSamplesLowered / mSamples)));
        }
        if (mRenderStats != null && mRenderStats.hasData()) {
            logTagged("RenderThread", mRenderStats.summarise(LauncherPreferences.PREF_BIG_CORE_AFFINITY
                    ? "the big-core setting is on (see the [BigCore] line)"
                    : "the big-core setting is off"));
        }
        mSummaryStartedAt = now;
        mSamples = 0;
        mSamplesLowered = 0;
        mWaitSum = 0;
        mWaitPeak = 0;
    }

    /** The fast cores of this phone as a mask, 0 when it reports nothing that tells them apart (see {@link FastCores}). */
    static long readFastCores() {
        int count = Math.min(Runtime.getRuntime().availableProcessors(), FastCores.MAX_CPUS);
        long[] capacity = new long[count];
        long[] maxFreq = new long[count];
        for (int cpu = 0; cpu < count; cpu++) {
            capacity[cpu] = readNumber("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity");
            maxFreq[cpu] = readNumber("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
        }
        return FastCores.maskFor(capacity, maxFreq);
    }

    /** The number in a one-line file, or -1 when it cannot be read. */
    private static long readNumber(String path) {
        String text = readFile(new File(path));
        if (text == null) return -1;
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /** The kernel's clock tick rate (CPU times in /proc are counted in ticks), 100 when Android will not say. */
    private static long readClockTicks() {
        try {
            long ticks = Os.sysconf(OsConstants._SC_CLK_TCK);
            return ticks > 0 ? ticks : 100;
        } catch (Throwable error) {
            return 100;
        }
    }

    @Nullable
    static String readThreadName(File task) {
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
        logTagged("PerformanceMode", message);
    }

    private static void logTagged(String tag, String message) {
        Log.i(TAG, message);
        try {
            Logger.appendToLog("[" + tag + "] " + message);
        } catch (Throwable ignored) {
            // The native logger is not available yet, or already gone. The Android log has it.
        }
    }
}
