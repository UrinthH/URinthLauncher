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
    /** Voice chat thread id to the priority it had before it was raised. */
    private final Map<Integer, Integer> mVoiceOriginal = new HashMap<>();
    private final Set<Integer> mVoiceThreads = new HashSet<>();
    private boolean mVoiceBoostLogged;
    private final Map<Integer, String> mVoiceNames = new HashMap<>();
    /** CPU ticks per thread at the start of the current summary window, for the "who used the CPU" line. */
    private Map<Integer, Long> mWindowTicks = new HashMap<>();
    private long mWindowStartedAt;

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
        snapshotWindow();
        mHandler.postDelayed(mSampleTask, SAMPLE_MS);
        log("Started: helper threads are lowered only while the render thread is waiting for a core");
    }

    private void end() {
        mRunning = false;
        mHandler.removeCallbacks(mSampleTask);
        restoreAll();
        restoreVoiceThreads();
    }

    private void sample() {
        if (!mRunning) return;
        try {
            long now = SystemClock.elapsedRealtimeNanos();
            Set<Integer> helpers = scanThreads();
            boostVoiceThreads();
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
            } else if (VoiceChatAudio.isModPresent() && SchedulerPolicy.isVoiceThread(name)) {
                if (mVoiceThreads.add(tid)) mVoiceNames.put(tid, name);
            }
        }
        mVoiceThreads.retainAll(alive);
        mVoiceNames.keySet().retainAll(alive);
        mVoiceOriginal.keySet().retainAll(alive);
        mThreadNames.keySet().retainAll(alive);
        mOriginalPriorities.keySet().retainAll(alive);
        mAppliedPriorities.keySet().retainAll(alive);
        return helpers;
    }

    /**
     * With a voice chat mod installed, give its recording / playback threads a more urgent
     * priority than normal, so a busy game cannot make the voice cut out. The render thread is
     * never touched. Undone when the scheduler stops.
     */
    private void boostVoiceThreads() {
        for (int tid : mVoiceThreads) {
            if (mVoiceOriginal.containsKey(tid)) continue;
            try {
                mVoiceOriginal.put(tid, Process.getThreadPriority(tid));
                Process.setThreadPriority(tid, SchedulerPolicy.VOICE_NICE);
            } catch (RuntimeException gone) {
                mVoiceOriginal.remove(tid); // refused or ended: leave it alone
            }
        }
        if (!mVoiceBoostLogged && !mVoiceOriginal.isEmpty()) {
            mVoiceBoostLogged = true;
            StringBuilder names = new StringBuilder();
            for (int tid : mVoiceOriginal.keySet()) {
                if (names.length() > 0) names.append(", ");
                names.append(mVoiceNames.get(tid));
            }
            log("Voice chat threads raised in priority: " + names);
        }
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

    private void restoreVoiceThreads() {
        for (Map.Entry<Integer, Integer> original : mVoiceOriginal.entrySet()) {
            try {
                Process.setThreadPriority(original.getKey(), original.getValue());
            } catch (RuntimeException gone) {
                // Already ended.
            }
        }
        mVoiceOriginal.clear();
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
        logLoadReport(now);
        snapshotWindow();
        mSummaryStartedAt = now;
        mSamples = 0;
        mSamplesLowered = 0;
        mWaitSum = 0;
        mWaitPeak = 0;
    }

    /** Remember every thread's CPU time now; one pass over /proc, once a minute. */
    private void snapshotWindow() {
        mWindowStartedAt = SystemClock.elapsedRealtime();
        mWindowTicks = readAllThreadTicks(null);
    }

    /** Reads each thread's CPU ticks; when {@code names} is given, also its name. */
    private static Map<Integer, Long> readAllThreadTicks(@Nullable Map<Integer, String> names) {
        Map<Integer, Long> ticks = new HashMap<>();
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return ticks;
        for (File task : tasks) {
            try {
                int tid = Integer.parseInt(task.getName());
                long used = SchedulerPolicy.parseCpuTicks(readFile(new File(task, "stat")));
                if (used < 0) continue;
                ticks.put(tid, used);
                if (names != null) {
                    String name = readThreadName(task);
                    names.put(tid, name == null ? "?" : name);
                }
            } catch (NumberFormatException notAThread) {
                // not a thread folder
            }
        }
        return ticks;
    }

    /**
     * One line a minute that says where the phone's CPU went and how much memory was left, so a
     * report of "it lags" can be matched to a cause: the render thread starved, the server thread
     * busy, the compiler or the voice chat eating cores, or Android short of memory.
     */
    private void logLoadReport(long now) {
        try {
            Map<Integer, String> names = new HashMap<>();
            Map<Integer, Long> nowTicks = readAllThreadTicks(names);
            Map<String, Long> byName = new HashMap<>();
            for (Map.Entry<Integer, Long> entry : nowTicks.entrySet()) {
                Long before = mWindowTicks.get(entry.getKey());
                long delta = entry.getValue() - (before == null ? 0 : before);
                if (delta <= 0) continue;
                String name = names.get(entry.getKey());
                // Names with a number on the end belong to one pool (Worker-Main-3, IO-Worker-1).
                String group = name == null ? "?" : name.replaceAll("[-_ ]?\\d+$", "");
                Long sum = byName.get(group);
                byName.put(group, (sum == null ? 0 : sum) + delta);
            }
            String busiest = SchedulerPolicy.busiestThreads(byName, now - mWindowStartedAt, 100, 6);
            String meminfo = readWhole(new File("/proc/meminfo"));
            long available = SchedulerPolicy.parseMeminfoKb(meminfo, "MemAvailable");
            long swapTotal = SchedulerPolicy.parseMeminfoKb(meminfo, "SwapTotal");
            long swapFree = SchedulerPolicy.parseMeminfoKb(meminfo, "SwapFree");
            log("CPU use (percent of one core): " + (busiest.isEmpty() ? "idle" : busiest)
                    + " | memory available " + (available < 0 ? "?" : (available >> 10) + " MB")
                    + (swapTotal > 0 && swapFree >= 0 ? ", swap in use " + ((swapTotal - swapFree) >> 10) + " MB" : "")
                    + " | heat level " + ThermalGuard.currentHeatLevel());
        } catch (Throwable ignored) {
            // A report must never get in the way of the game.
        }
    }

    @Nullable
    private static String readWhole(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            return text.toString();
        } catch (IOException error) {
            return null;
        }
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
