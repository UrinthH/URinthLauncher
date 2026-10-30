package net.kdt.pojavlaunch.game;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

import net.kdt.pojavlaunch.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * A line in the game log every 30 seconds that says what the phone was going through: how much
 * memory the game holds and how much is left for the system, whether the game had to wait for
 * memory to be read back from storage (major page faults, the cause of sudden freezes), how busy
 * its threads were, and how hot the phone is. When someone reports "it freezes", this tells
 * apart a memory squeeze, a heat throttle and a busy CPU, which need different fixes.
 *
 * It reads three tiny files and one system call every 30 seconds on a background thread, so it
 * costs the game nothing measurable.
 */
public final class PerfReport {
    private static final long INTERVAL_MS = 30_000L;

    private static Thread sThread;

    private PerfReport() {}

    public static synchronized void start(Context context) {
        stop();
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> loop(app), "PerfReport");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        sThread = thread;
        thread.start();
    }

    public static synchronized void stop() {
        if (sThread != null) sThread.interrupt();
        sThread = null;
    }

    private static void loop(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        long[] last = null;
        long lastAt = SystemClock.elapsedRealtime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(INTERVAL_MS);
                long now = SystemClock.elapsedRealtime();
                long[] stat = PerfLine.parseStat(read("/proc/self/stat"));
                String line = PerfLine.describe(stat, last, now - lastAt,
                        PerfLine.residentMegabytes(read("/proc/self/status")),
                        availableMegabytes(activityManager), isLowMemory(activityManager),
                        thermal(powerManager), read("/proc/loadavg"));
                Logger.appendToLog("[Perf] " + line);
                last = stat;
                lastAt = now;
            }
        } catch (InterruptedException stopped) {
            // The game closed.
        } catch (Throwable error) {
            // A diagnostic must never take the game down.
        }
    }

    private static long availableMegabytes(ActivityManager manager) {
        if (manager == null) return -1;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return info.availMem / (1024 * 1024);
    }

    private static boolean isLowMemory(ActivityManager manager) {
        if (manager == null) return false;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return info.lowMemory;
    }

    private static int thermal(PowerManager manager) {
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1;
        return manager.getCurrentThermalStatus();
    }

    private static String read(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            return text.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
