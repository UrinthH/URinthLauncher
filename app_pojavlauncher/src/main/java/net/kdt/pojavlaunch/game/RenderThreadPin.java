package net.kdt.pojavlaunch.game;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * The "Keep the render thread on the fast cores" switch (Settings and Advanced settings). Phones with two kinds of cores, such as the
 * two fast and six slow ones of a Unisoc T606, let Android decide which kind runs a thread. The render thread waits for the graphics
 * chip between bursts of work, so Android can see it as a light thread and leave it on a slow core, which caps the frame rate. With the
 * switch on, this moves that one thread (found by its name, once Minecraft has started it) onto the fast cores and says so in the
 * game log, with the cores the kernel then allows it, so the result can be read there.
 *
 * Only the render thread is touched. Every other thread, the integrated server and the chunk builders included, stays wherever the
 * system puts it. A phone that does not tell its fast cores apart (see {@link FastCores}) is left alone, and so is one where the
 * kernel refuses the change. Whether the frame rate changes can only be told by playing, and a thread kept on two cores can also
 * wait for them while something else is using them, so the switch is off until the player turns it on.
 */
public final class RenderThreadPin {
    private static final String TAG = "RenderThreadPin";
    /** How often to look for the render thread until it exists. */
    private static final long POLL_MS = 2_000L;
    /** Minecraft creates the render thread within a minute or two; after this long it is not going to. */
    private static final long GIVE_UP_MS = 15 * 60_000L;

    private static RenderThreadPin sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final long mStartedAt = SystemClock.elapsedRealtime();
    private long mFastCores;
    private boolean mRunning;
    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            poll();
        }
    };

    private RenderThreadPin() {}

    /** Start looking for the render thread. Call from the main thread; one already running is replaced. */
    public static synchronized void start() {
        stop();
        try {
            RenderThreadPin pin = new RenderThreadPin();
            sInstance = pin;
            pin.begin();
        } catch (Throwable error) {
            // An extra: if it cannot run, the game still starts.
            Log.w(TAG, "Render thread pinning unavailable", error);
            sInstance = null;
        }
    }

    /** Stop looking. A thread that was moved stays where it is until the game ends. */
    public static synchronized void stop() {
        RenderThreadPin pin = sInstance;
        sInstance = null;
        if (pin != null) pin.end();
    }

    private void begin() {
        mFastCores = RenderScheduler.readFastCores();
        if (mFastCores == 0) {
            log("The switch is on, but this phone does not report which of its cores are fast, so nothing was moved");
            return;
        }
        mRunning = true;
        log("Waiting for the render thread, to keep it on " + FastCores.describe(mFastCores));
        mHandler.postDelayed(mPoll, POLL_MS);
    }

    private void end() {
        mRunning = false;
        mHandler.removeCallbacks(mPoll);
    }

    private void poll() {
        if (!mRunning) return;
        try {
            int tid = findRenderThread();
            if (tid > 0) {
                pin(tid);
                mRunning = false;
                return;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Render thread check failed", error);
        }
        if (SystemClock.elapsedRealtime() - mStartedAt > GIVE_UP_MS) {
            mRunning = false;
            log("No render thread appeared, nothing was moved");
            return;
        }
        mHandler.postDelayed(mPoll, POLL_MS);
    }

    private void pin(int tid) {
        String before = allowedCores(tid);
        int result = CorePinner.pin(tid, mFastCores);
        String after = allowedCores(tid);
        if (result == 0) {
            log("Render thread " + tid + " now runs only on " + FastCores.describe(mFastCores)
                    + " (the kernel allows it: " + after + ", before: " + before + ")");
        } else if (result < 0) {
            log("Could not move the render thread: the native call is not available in this build");
        } else {
            log("The system refused to move the render thread (error " + result + "), it stays where it was: " + before);
        }
    }

    /** The kernel thread id of the thread Minecraft draws on, or -1 while it does not exist yet. */
    private static int findRenderThread() {
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return -1;
        for (File task : tasks) {
            int tid;
            try {
                tid = Integer.parseInt(task.getName());
            } catch (NumberFormatException notAThread) {
                continue;
            }
            String name = RenderScheduler.readThreadName(task);
            if (name != null && SchedulerPolicy.isRenderThread(name)) return tid;
        }
        return -1;
    }

    /** The "Cpus_allowed_list" line of a thread's status ("0-7", or "6-7" once it was moved), for the log. */
    private static String allowedCores(int tid) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/task/" + tid + "/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Cpus_allowed_list:")) return line.substring("Cpus_allowed_list:".length()).trim();
            }
        } catch (IOException error) {
            // The thread ended, or the phone hides it.
        }
        return "unknown";
    }

    private static void log(String message) {
        Log.i(TAG, message);
        try {
            Logger.appendToLog("[BigCore] " + message);
        } catch (Throwable ignored) {
            // The native logger is not available yet, or already gone. The Android log has it.
        }
    }
}
