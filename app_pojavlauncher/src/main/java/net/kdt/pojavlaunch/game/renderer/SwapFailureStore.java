package net.kdt.pojavlaunch.game.renderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The two tiny files behind {@link PerformanceMobileGluesGuard}, kept apart so they can be tested without a
 * device. They are files and not preferences because the game runs in its own process: a preference written
 * there can be overwritten by the launcher process, which keeps its own copy, while a file is read fresh.
 *
 * <ul>
 *   <li>{@code mg_swap_started.txt}: when the launch in progress started with the Performance Mode swap;</li>
 *   <li>{@code mg_swap_failed.txt}: the launcher build that saw a swapped launch end the way
 *       {@link PerformanceMobileGlues#swapFailed} describes. It only counts for that same build, so an updated
 *       launcher tries MobileGlues again.</li>
 * </ul>
 * Everything here is a safety net: no method throws, and a file that cannot be read or written means "nothing
 * recorded".
 */
final class SwapFailureStore {
    static final String STARTED_NAME = "mg_swap_started.txt";
    static final String FAILED_NAME = "mg_swap_failed.txt";
    private static final int MAX_BYTES = 256;

    private SwapFailureStore() {}

    /** A launch begins: remember when it used the swap, or forget an older one when it did not. */
    static void markLaunch(File dir, boolean swapUsed, long nowMs) {
        try {
            File started = new File(dir, STARTED_NAME);
            if (swapUsed) write(started, Long.toString(nowMs));
            else delete(started);
        } catch (IOException | RuntimeException ignored) {
            // Only a safety net: never let it get in the way of starting the game.
        }
    }

    /**
     * The game stopped by itself with this exit code. Reads and removes the swap's start time and, when the way
     * it ended points at MobileGlues, records that for this launcher build.
     *
     * @return true when a failure was recorded
     */
    static boolean onGameExit(File dir, String buildId, long nowMs, int exitCode, boolean isSignal) {
        try {
            File started = new File(dir, STARTED_NAME);
            long startedAt = parseLong(read(started));
            delete(started);
            if (buildId == null || !PerformanceMobileGlues.swapFailed(startedAt, nowMs, exitCode, isSignal)) {
                return false;
            }
            write(new File(dir, FAILED_NAME), buildId);
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    /** Whether this same launcher build recorded a failure (a newer build tries MobileGlues again). */
    static boolean lastSwapFailed(File dir, String buildId) {
        try {
            String recorded = read(new File(dir, FAILED_NAME));
            return recorded != null && buildId != null && recorded.equals(buildId);
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    /** The player turned the MobileGlues switch off or on: give it a fresh start. */
    static void clearFailure(File dir) {
        try {
            delete(new File(dir, FAILED_NAME));
        } catch (IOException | RuntimeException ignored) {
            // Nothing to clear, or it cannot be cleared: the next build ignores it anyway.
        }
    }

    private static long parseLong(String text) {
        if (text == null) return 0L;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /** The trimmed text of a small file, or null when there is no such file. */
    private static String read(File file) throws IOException {
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[MAX_BYTES];
            int length = 0;
            int count;
            while (length < buffer.length && (count = in.read(buffer, length, buffer.length - length)) > 0) {
                length += count;
            }
            return new String(buffer, 0, length, StandardCharsets.UTF_8).trim();
        }
    }

    private static void delete(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("could not delete " + file);
    }
}
