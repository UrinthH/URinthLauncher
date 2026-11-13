package net.kdt.pojavlaunch.utils;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.R;

/**
 * Works out why the previous game session ended when it did not end normally, and says so.
 *
 * A game that Android kills for running out of memory leaves no Java exception and no log: the
 * process is simply gone. Android does remember why (Android 11 and newer keep the exit reason of
 * recent processes), and {@link LaunchGuard} remembers how long the launch lived, so the launcher
 * can show both the next time it opens and save them in a file that is easy to send along.
 */
public final class GameExitReporter {
    private static final String TAG = "GameExitReporter";
    private static final String KEY_LAST_EXIT_TS = "lastGameExitReportedTs";
    private static final String KEY_LAST_MARKER_START = "lastGameMarkerReportedStart";
    /** Exits older than this are history, not "the last session". */
    private static final long MAX_AGE_MS = 12L * 60L * 60L * 1000L;
    private static final String REPORT_NAME = "last_game_exit.txt";
    /** Most of a native crash dump that is read into memory; the part that matters comes first. */
    private static final int MAX_DUMP_BYTES = 2 * 1024 * 1024;
    /** Lines of the native crash summary that fit in the dialog; the saved report has all of it. */
    private static final int DIALOG_DUMP_LINES = 4;

    private GameExitReporter() {}

    /** How Android says the previous game process ended. */
    private static final class ExitSummary {
        final long timestamp;
        final int reason;
        final String reasonName;
        final int status;
        final long pssKb;
        final long rssKb;
        final String description;
        /** Readable summary of the crash dump when the game crashed natively, else null. */
        @Nullable
        final String nativeCrash;

        ExitSummary(long timestamp, int reason, String reasonName, int status,
                    long pssKb, long rssKb, String description, @Nullable String nativeCrash) {
            this.timestamp = timestamp;
            this.reason = reason;
            this.reasonName = reasonName;
            this.status = status;
            this.pssKb = pssKb;
            this.rssKb = rssKb;
            this.description = description;
            this.nativeCrash = nativeCrash;
        }
    }

    /**
     * Build the report for the previous game session. Does file and system calls, so call it off
     * the main thread.
     *
     * @return {text for the dialog, full report text for copying}, or null when the previous
     *         session ended normally or was already reported
     */
    @Nullable
    public static String[] buildReport(Context context) {
        try {
            return buildReportUnchecked(context.getApplicationContext());
        } catch (Throwable error) {
            // Diagnostics must never be the thing that crashes the launcher.
            Log.w(TAG, "Could not build the game exit report", error);
            return null;
        }
    }

    @Nullable
    private static String[] buildReportUnchecked(Context context) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return null;
        long now = System.currentTimeMillis();
        long lastExitReported = prefs.getLong(KEY_LAST_EXIT_TS, 0L);
        long lastMarkerReported = prefs.getLong(KEY_LAST_MARKER_START, 0L);

        LaunchGuard.Record marker = LaunchGuard.peek(context);
        boolean endedDuringStartup = marker != null
                && marker.startMs > lastMarkerReported
                && now - marker.startMs < MAX_AGE_MS
                && marker.lifetimeMs() < LaunchGuard.STARTUP_WINDOW_MS
                // A marker that is still being refreshed belongs to a game that is still running.
                && now - marker.aliveMs > LaunchGuard.STALE_AFTER_MS;

        ExitSummary exit = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            exit = ExitReader.newestGameExit(context);
            if (exit != null && (exit.timestamp <= lastExitReported || now - exit.timestamp > MAX_AGE_MS)) {
                exit = null;
            }
        }
        boolean abnormalExit = exit != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && ExitReader.isAbnormal(exit.reason);
        if (!endedDuringStartup && !abnormalExit) return null;

        int hint;
        if (exit != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ExitReader.isMemoryKill(exit.reason)) {
            hint = R.string.game_exit_hint_memory;
        } else if (exit != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ExitReader.isNativeCrash(exit.reason)) {
            hint = R.string.game_exit_hint_native;
        } else if (endedDuringStartup) {
            hint = R.string.game_exit_hint_generic;
        } else {
            hint = R.string.game_exit_hint_other;
        }

        String report = describe(context, now, marker, endedDuringStartup, exit);
        String savedTo = save(context, report);

        StringBuilder dialog = new StringBuilder(context.getString(hint));
        if (endedDuringStartup) {
            dialog.append("\n\n").append(context.getString(R.string.game_exit_ran_for,
                    Math.max(1L, marker.lifetimeMs() / 1000L)));
        }
        if (exit != null) {
            dialog.append("\n").append(context.getString(R.string.game_exit_android_says, exit.reasonName));
        }
        if (exit != null && exit.nativeCrash != null) {
            dialog.append("\n\n").append(firstLines(exit.nativeCrash, DIALOG_DUMP_LINES));
        }
        if (savedTo != null) {
            dialog.append("\n\n").append(context.getString(R.string.game_exit_saved_to, savedTo));
        }

        prefs.edit()
                .putLong(KEY_LAST_EXIT_TS, exit != null ? exit.timestamp : lastExitReported)
                .putLong(KEY_LAST_MARKER_START, marker != null ? marker.startMs : lastMarkerReported)
                .apply();
        return new String[] { dialog.toString(), report };
    }

    private static String describe(Context context, long now, @Nullable LaunchGuard.Record marker,
                                   boolean endedDuringStartup, @Nullable ExitSummary exit) {
        StringBuilder out = new StringBuilder();
        out.append("Game exit report\n");
        out.append("Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(new Date(now))).append('\n');
        out.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("RAM: ").append(Tools.getTotalDeviceMemory(context)).append(" MB total, ")
                .append(Tools.getFreeDeviceMemory(context)).append(" MB free now\n");
        out.append("RAM allocation setting: ").append(LauncherPreferences.PREF_RAM_ALLOCATION).append(" MB\n");
        out.append("Custom Java arguments: \"").append(LauncherPreferences.PREF_CUSTOM_JAVA_ARGS).append("\"\n");
        if (marker != null) {
            out.append("Launch marker: process lived about ").append(marker.lifetimeMs() / 1000L).append(" s")
                    .append(endedDuringStartup ? " and never finished starting\n" : "\n");
        }
        if (exit != null) {
            out.append("Android exit reason: ").append(exit.reasonName)
                    .append(" (status ").append(exit.status).append(")\n");
            if (exit.rssKb > 0) out.append("Memory held when it ended: ").append(exit.rssKb / 1024L).append(" MB resident");
            if (exit.pssKb > 0) out.append(", ").append(exit.pssKb / 1024L).append(" MB proportional");
            if (exit.rssKb > 0 || exit.pssKb > 0) out.append('\n');
            if (exit.description != null && !exit.description.isEmpty()) {
                out.append("Android description: ").append(exit.description).append('\n');
            }
            if (exit.nativeCrash != null) {
                out.append("Native crash summary:\n").append(exit.nativeCrash);
                if (!exit.nativeCrash.endsWith("\n")) out.append('\n');
            }
        } else {
            out.append("Android exit reason: not available on this Android version\n");
        }
        // What the most recent launch was set up with. After a quick retry this describes the
        // retry, which is still the configuration to compare against.
        String details = LaunchGuard.loadDetails(context);
        if (details != null && !details.trim().isEmpty()) {
            out.append("Most recent launch configuration:\n").append(details);
            if (!details.endsWith("\n")) out.append('\n');
        }
        return out.toString();
    }

    /** The first {@code count} lines of {@code text}, for the dialog. */
    private static String firstLines(String text, int count) {
        StringBuilder out = new StringBuilder();
        int taken = 0;
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (taken > 0) out.append('\n');
            out.append(line);
            if (++taken >= count) break;
        }
        return out.toString();
    }

    /** @return the path the report was saved to, or null if it could not be saved */
    @Nullable
    private static String save(Context context, String report) {
        try {
            File dir = new File(Tools.checkStorageRoot(context) ? Tools.DIR_GAME_HOME : Tools.DIR_DATA);
            File file = new File(dir, REPORT_NAME);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(report);
            }
            return file.getAbsolutePath();
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Could not save the game exit report", error);
            return null;
        }
    }

    /** Everything that needs Android 11's exit-reason API lives here, away from older devices. */
    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("InlinedApi")
    private static final class ExitReader {
        private ExitReader() {}

        @Nullable
        static ExitSummary newestGameExit(Context context) {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return null;
            List<ApplicationExitInfo> infos =
                    manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 12);
            ApplicationExitInfo newest = null;
            for (ApplicationExitInfo info : infos) {
                String processName = info.getProcessName();
                // The game runs in its own ":game" process, separate from the launcher UI.
                if (processName == null || !processName.endsWith(":game")) continue;
                if (newest != null && info.getTimestamp() <= newest.getTimestamp()) continue;
                newest = info;
            }
            if (newest == null) return null;
            int reason = newest.getReason();
            String crashDump = reason == ApplicationExitInfo.REASON_CRASH_NATIVE ? readCrashDump(newest) : null;
            return new ExitSummary(newest.getTimestamp(), reason, reasonName(reason), newest.getStatus(),
                    newest.getPss(), newest.getRss(), newest.getDescription(), crashDump);
        }

        /**
         * For a native crash Android keeps the crash dump (a "tombstone"). It names the signal and
         * the library the crash happened in, which is exactly what is missing when the game dies
         * without a log. Reading it is best effort: any problem just means no summary.
         */
        @Nullable
        private static String readCrashDump(ApplicationExitInfo info) {
            try (InputStream stream = info.getTraceInputStream()) {
                if (stream == null) return null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[16 * 1024];
                int read;
                while (bytes.size() < MAX_DUMP_BYTES && (read = stream.read(chunk)) > 0) {
                    bytes.write(chunk, 0, read);
                }
                return NativeCrashSummary.summarize(bytes.toByteArray());
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Could not read the native crash dump", error);
                return null;
            }
        }

        static boolean isMemoryKill(int reason) {
            return reason == ApplicationExitInfo.REASON_LOW_MEMORY;
        }

        static boolean isNativeCrash(int reason) {
            return reason == ApplicationExitInfo.REASON_CRASH_NATIVE;
        }

        /**
         * Exits that mean something went wrong. A plain SIGKILL is not listed: the launcher ends
         * the game process that way on a normal quit, so it says nothing by itself.
         */
        static boolean isAbnormal(int reason) {
            switch (reason) {
                case ApplicationExitInfo.REASON_LOW_MEMORY:
                case ApplicationExitInfo.REASON_CRASH:
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                case ApplicationExitInfo.REASON_ANR:
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                    return true;
                default:
                    return false;
            }
        }

        static String reasonName(int reason) {
            switch (reason) {
                case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
                case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
                case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
                case ApplicationExitInfo.REASON_CRASH: return "CRASH";
                case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
                case ApplicationExitInfo.REASON_ANR: return "ANR";
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
                case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
                case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
                case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
                case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
                case ApplicationExitInfo.REASON_OTHER: return "OTHER";
                default: return "UNKNOWN(" + reason + ")";
            }
        }
    }
}
