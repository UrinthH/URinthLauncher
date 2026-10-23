package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Notices a game launch that died while it was still starting up, so the next launch can run in
 * a plain "safe launch" configuration instead of repeating the same failure every time.
 *
 * It works from a tiny marker file in the app's private storage. The game process writes the
 * marker as soon as it starts and refreshes it every couple of seconds. A launch that lives past
 * {@link #STARTUP_WINDOW_MS} deletes the marker itself, and a clean exit deletes it too. If the
 * marker is still there at the next launch, and it shows a short life, the previous launch did not
 * make it through start-up. Android kills a process without any callback when it runs short on
 * memory, so a file written while the process is alive is the only trace such a kill leaves.
 */
public final class LaunchGuard {
    private static final String TAG = "LaunchGuard";
    private static final String MARKER_NAME = "launch_guard.txt";
    private static final String DETAILS_NAME = "launch_details.txt";
    private static final int MAX_DETAILS_BYTES = 8 * 1024;

    /** A launch that stays alive this long is considered to have started properly. */
    public static final long STARTUP_WINDOW_MS = 30_000L;
    /** How often a running game refreshes its marker. */
    private static final long HEARTBEAT_MS = 2_000L;
    /** A marker refreshed more recently than this belongs to a game that is probably still running. */
    public static final long STALE_AFTER_MS = 3 * HEARTBEAT_MS;
    /** A marker left this long ago is history, not "the launch before this one". */
    private static final long MARKER_MAX_AGE_MS = 12L * 60L * 60L * 1000L;

    private static final Object LOCK = new Object();
    private static File sMarker;
    private static boolean sSafeLaunch;
    private static boolean sStopHeartbeat;

    private LaunchGuard() {}

    /** What the marker file says about one launch. */
    public static final class Record {
        public final int pid;
        public final long startMs;
        public final long aliveMs;

        Record(int pid, long startMs, long aliveMs) {
            this.pid = pid;
            this.startMs = startMs;
            this.aliveMs = aliveMs;
        }

        /** How long the process was known to be alive. */
        public long lifetimeMs() {
            return aliveMs - startMs;
        }
    }

    /**
     * Call once, early in the game process. Reads what the previous launch left behind, starts
     * tracking this one, and says whether this launch should run in safe launch.
     *
     * @return true when the previous launch died within its start-up window
     */
    public static boolean begin(Context context) {
        synchronized (LOCK) {
            // The activity was re-created inside a process that is already tracked.
            if (sMarker != null) return sSafeLaunch;
            try {
                File marker = markerFile(context);
                Record previous = read(marker);
                int pid = Process.myPid();
                long now = System.currentTimeMillis();
                boolean previousFailed = previous != null
                        && previous.pid != pid
                        && previous.lifetimeMs() < STARTUP_WINDOW_MS
                        && now - previous.aliveMs < MARKER_MAX_AGE_MS;
                sMarker = marker;
                sSafeLaunch = previousFailed;
                sStopHeartbeat = false;
                write(marker, pid, now, now);
                startHeartbeat(marker, pid, now);
                if (previousFailed) {
                    Log.w(TAG, "The previous launch ended after " + previous.lifetimeMs()
                            + " ms, before it finished starting. Using safe launch this time.");
                }
            } catch (Throwable error) {
                // Never let the guard itself get in the way of starting the game.
                Log.w(TAG, "Launch guard unavailable", error);
                sSafeLaunch = false;
            }
            return sSafeLaunch;
        }
    }

    /** Whether this process was put into safe launch by {@link #begin(Context)}. */
    public static boolean isSafeLaunch() {
        synchronized (LOCK) {
            return sSafeLaunch;
        }
    }

    /** The game ended on purpose: forget this launch so it is not mistaken for a crash. */
    public static void markCleanExit() {
        synchronized (LOCK) {
            sStopHeartbeat = true;
            if (sMarker != null) sMarker.delete();
        }
    }

    /** What the last launch left behind, without changing anything. Used by the launcher UI. */
    @Nullable
    public static Record peek(Context context) {
        return read(markerFile(context));
    }

    /**
     * Remember what this launch is configured with (renderer, RAM, Java arguments), so a report
     * about a launch that left no log can still say what it was running. Unlike the marker this is
     * kept after the game ends; the next launch replaces it.
     */
    public static void saveDetails(Context context, String details) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(detailsFile(context)), StandardCharsets.UTF_8)) {
            writer.write(details);
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Could not save the launch details", error);
        }
    }

    /** What {@link #saveDetails} stored for the last launch, or null when there is nothing. */
    @Nullable
    public static String loadDetails(Context context) {
        File file = detailsFile(context);
        if (!file.isFile()) return null;
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[MAX_DETAILS_BYTES];
            int length = 0;
            int read;
            while (length < buffer.length
                    && (read = stream.read(buffer, length, buffer.length - length)) > 0) {
                length += read;
            }
            return length == 0 ? null : new String(buffer, 0, length, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static File markerFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), MARKER_NAME);
    }

    private static File detailsFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), DETAILS_NAME);
    }

    private static void startHeartbeat(File marker, int pid, long startMs) {
        Thread thread = new Thread(() -> {
            long begun = SystemClock.elapsedRealtime();
            while (true) {
                try {
                    Thread.sleep(HEARTBEAT_MS);
                } catch (InterruptedException interrupted) {
                    return;
                }
                synchronized (LOCK) {
                    if (sStopHeartbeat) return;
                    try {
                        if (SystemClock.elapsedRealtime() - begun >= STARTUP_WINDOW_MS) {
                            // Made it through start-up: nothing left to guard against.
                            sStopHeartbeat = true;
                            marker.delete();
                            return;
                        }
                        write(marker, pid, startMs, System.currentTimeMillis());
                    } catch (IOException error) {
                        Log.w(TAG, "Could not refresh the launch marker", error);
                        return;
                    }
                }
            }
        }, "LaunchGuard heartbeat");
        thread.setDaemon(true);
        thread.start();
    }

    @Nullable
    private static Record read(File marker) {
        if (!marker.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(marker), StandardCharsets.UTF_8))) {
            int pid = -1;
            long start = -1;
            long alive = -1;
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                switch (key) {
                    case "pid": pid = Integer.parseInt(value); break;
                    case "start": start = Long.parseLong(value); break;
                    case "alive": alive = Long.parseLong(value); break;
                    default: break;
                }
            }
            if (pid < 0 || start < 0 || alive < start) return null;
            return new Record(pid, start, alive);
        } catch (IOException | NumberFormatException error) {
            return null;
        }
    }

    private static void write(File marker, int pid, long startMs, long aliveMs) throws IOException {
        // Write next to the marker and rename over it, so a process killed halfway through a
        // write leaves either the old marker or the new one, never a torn file.
        File temp = new File(marker.getParentFile(), MARKER_NAME + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            writer.write("pid=" + pid + "\nstart=" + startMs + "\nalive=" + aliveMs + "\n");
        }
        if (!temp.renameTo(marker)) {
            marker.delete();
            if (!temp.renameTo(marker)) throw new IOException("Could not replace " + marker);
        }
    }
}
