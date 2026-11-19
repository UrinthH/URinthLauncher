package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The small file behind the Anti Crash guard ({@link AntiCrash}), kept apart from Android so it can be
 * tested on a plain JVM. A file and not a preference because the game runs in its own process: a preference
 * written there can be overwritten by the launcher process, which keeps its own copy, while a file is read fresh.
 *
 * Everything here is a safety net: no method throws, and a file that cannot be read or written means
 * "nothing remembered".
 */
final class AntiCrashStore {
    static final String FILE_NAME = "anti_crash.txt";
    private static final int MAX_BYTES = 512;

    private AntiCrashStore() {}

    static AntiCrashPolicy.State read(File dir) {
        try {
            File file = new File(dir, FILE_NAME);
            if (!file.isFile() || file.length() > MAX_BYTES) return AntiCrashPolicy.State.empty();
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int total = 0;
                while (total < bytes.length) {
                    int read = in.read(bytes, total, bytes.length - total);
                    if (read < 0) break;
                    total += read;
                }
            }
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            return AntiCrashPolicy.State.empty();
        }
    }

    static AntiCrashPolicy.State parse(String text) {
        int steps = 0;
        boolean jit = false;
        long handled = 0L;
        int allocation = 0;
        if (text == null) return AntiCrashPolicy.State.empty();
        for (String line : text.split("\n")) {
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            try {
                switch (key) {
                    case "steps":
                        steps = Integer.parseInt(value);
                        break;
                    case "jit":
                        jit = "1".equals(value);
                        break;
                    case "handled":
                        handled = Long.parseLong(value);
                        break;
                    case "alloc":
                        allocation = Integer.parseInt(value);
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {
                // A damaged line counts as missing.
            }
        }
        return new AntiCrashPolicy.State(steps, jit, handled, allocation);
    }

    static String format(AntiCrashPolicy.State state) {
        return "steps=" + state.lowMemorySteps + "\n"
                + "jit=" + (state.jitFallback ? 1 : 0) + "\n"
                + "handled=" + state.handledAtMs + "\n"
                + "alloc=" + state.allocationMb + "\n";
    }

    static void write(File dir, AntiCrashPolicy.State state) {
        try {
            File file = new File(dir, FILE_NAME);
            File temp = new File(dir, FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(format(state).getBytes(StandardCharsets.UTF_8));
            }
            if (!temp.renameTo(file)) {
                // Some file systems refuse to rename over an existing file.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                if (!temp.renameTo(file)) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Only a safety net: never let it get in the way of starting the game.
        }
    }
}
