package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.GameRestartPolicy;
import net.kdt.pojavlaunch.prefs.LaunchFingerprint;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The game that keeps running in the background, and the settings it was started with.
 *
 * The game lives in its own ":game" process, held up by a foreground service, so it survives the player going back
 * to the launcher. Pressing Play then brings the same game back: the same Java machine, with the Java flags, memory
 * and renderer it was started with. A setting changed in the meantime reaches the game only after it is closed and
 * started again, which used to happen only when the player swiped the app away.
 *
 * The game process calls {@link #publish} when it starts the game. The launcher calls {@link #check} when Play is
 * pressed, to find out whether the running game is out of date, and {@link #stop} to close it when the player
 * agrees to restart it. The decisions are made by {@link GameRestartPolicy}, which is tested on its own.
 */
public final class RunningGame {
    private static final String TAG = "RunningGame";
    private static final String RECORD_NAME = "running_game.txt";
    private static final String GAME_PROCESS_SUFFIX = ":game";
    /** The system takes a moment to notice that a process was killed. */
    private static final long STOP_TIMEOUT_MS = 4_000L;
    private static final long STOP_POLL_MS = 100L;

    /** Game process only: set when this process was told to start the game. */
    private static volatile boolean sJvmRequested;

    private RunningGame() {}

    /** What {@link #check} found. */
    public static final class Check {
        public final GameRestartPolicy.Verdict verdict;
        /** What differs, by group, when the verdict is CHANGED; empty otherwise. */
        public final List<LaunchFingerprint.Group> changed;

        Check(GameRestartPolicy.Verdict verdict, List<LaunchFingerprint.Group> changed) {
            this.verdict = verdict;
            this.changed = changed;
        }

        /** True when the running game was started with other settings than the ones chosen now. */
        public boolean shouldAsk() {
            return verdict == GameRestartPolicy.Verdict.CHANGED;
        }
    }

    // ---- game process ----

    /** Game process: whether this process has been told to start the game already. */
    public static boolean jvmRequested() {
        return sJvmRequested;
    }

    /**
     * Brings this process' copy of the preferences up to date with the file, if another process changed it. Android
     * keeps one copy per process and only looks at the file again when asked in this (deprecated, still working) way.
     * A process that is only warm, started early with no game in it yet, may otherwise start the game with settings
     * from before the player changed them, and a later write from an old copy would undo the change in the file.
     */
    @SuppressWarnings("deprecation")
    public static void refreshSettings(Context context) {
        try {
            Context app = context.getApplicationContext();
            // The same file as PreferenceManager.getDefaultSharedPreferences, which LauncherPreferences uses.
            app.getSharedPreferences(app.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS);
        } catch (Throwable error) {
            Log.w(TAG, "Could not refresh the settings", error);
        }
    }

    /**
     * Game process: write down the settings the game is about to be started with, and which process it runs in.
     * Call it when the game is started, not when an already running game is shown again.
     */
    public static void publish(Context context) {
        sJvmRequested = true;
        try {
            LaunchFingerprint fingerprint = LaunchFingerprint.of(LauncherPreferences.DEFAULT_PREF.getAll());
            String text = GameRestartPolicy.encode(new GameRestartPolicy.Record(Process.myPid(), fingerprint));
            write(recordFile(context), text);
        } catch (Throwable error) {
            // Without the record the launcher simply never asks; the game itself is not affected.
            Log.w(TAG, "Could not record the settings the game started with", error);
        }
    }

    // ---- launcher process ----

    /** Launcher process: whether a game is running that was started with other settings than the ones chosen now. */
    public static Check check(Context context) {
        try {
            Context app = context.getApplicationContext();
            List<Integer> live = liveGamePids(app);
            File file = recordFile(app);
            if (live.isEmpty()) {
                if (file.exists()) file.delete();
                return new Check(GameRestartPolicy.Verdict.NO_GAME, Collections.<LaunchFingerprint.Group>emptyList());
            }
            // The game may have written to the same file since this process read it.
            refreshSettings(app);
            LaunchFingerprint now = LaunchFingerprint.of(LauncherPreferences.DEFAULT_PREF.getAll());
            GameRestartPolicy.Record record = GameRestartPolicy.decode(read(file));
            GameRestartPolicy.Verdict verdict = GameRestartPolicy.decide(live, record, now);
            List<LaunchFingerprint.Group> changed = verdict == GameRestartPolicy.Verdict.CHANGED
                    ? LaunchFingerprint.groups(record.fingerprint.differences(now))
                    : Collections.<LaunchFingerprint.Group>emptyList();
            Log.i(TAG, "Game processes " + live + ", verdict " + verdict + ", changed " + changed);
            return new Check(verdict, changed);
        } catch (Throwable error) {
            // Never stand in the way of Play.
            Log.w(TAG, "Could not check the running game", error);
            return new Check(GameRestartPolicy.Verdict.UNKNOWN, Collections.<LaunchFingerprint.Group>emptyList());
        }
    }

    /**
     * Launcher process, off the main thread: close the game and wait until the system has let go of it.
     *
     * @return true when no game process is left
     */
    public static boolean stop(Context context) {
        Context app = context.getApplicationContext();
        try {
            for (int pid : liveGamePids(app)) Process.killProcess(pid);
            long deadline = SystemClock.elapsedRealtime() + STOP_TIMEOUT_MS;
            while (!liveGamePids(app).isEmpty() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(STOP_POLL_MS);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not close the game", error);
        }
        boolean gone = liveGamePids(app).isEmpty();
        if (gone) {
            // Closed on purpose, so it is not a launch that died: no safe launch, no crash report. The service's
            // notification goes with its process; this only makes sure it does not stay behind.
            LaunchGuard.forget(app);
            recordFile(app).delete();
            try {
                NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_SERVICE);
            } catch (RuntimeException ignored) {
                // Nothing to remove, or not allowed to: the system cleans it up.
            }
        }
        return gone;
    }

    // ---- both ----

    private static List<Integer> liveGamePids(Context app) {
        List<Integer> pids = new ArrayList<>();
        ActivityManager manager = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager == null ? null : manager.getRunningAppProcesses();
        if (processes == null) return pids;
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (info.processName != null && info.processName.endsWith(GAME_PROCESS_SUFFIX)) pids.add(info.pid);
        }
        return pids;
    }

    private static File recordFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), RECORD_NAME);
    }

    private static void write(File file, String text) throws IOException {
        // Next to the file, then renamed over it: the launcher never reads half a record.
        File temp = new File(file.getParentFile(), RECORD_NAME + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temp)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!temp.renameTo(file)) {
            file.delete();
            if (!temp.renameTo(file)) throw new IOException("Could not replace " + file);
        }
    }

    private static String read(File file) {
        if (!file.isFile()) return null;
        try (FileInputStream stream = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) > 0 && bytes.size() < 64 * 1024) bytes.write(chunk, 0, read);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }
}
