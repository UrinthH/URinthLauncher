package net.kdt.pojavlaunch.utils;

import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Performance Mode does not change options.txt. An earlier build did lower some game settings
 * when it was switched on, and remembered the old values. This class only puts those remembered
 * values back, once, and only where the value is still what that build set (so anything the
 * player changed in game since is kept). It never lowers or changes any other setting. On an
 * install that never used that build there is nothing remembered and the file is not opened.
 */
public final class PerformanceProfile {
    /** One value an earlier build changed: what it was and what it was set to. */
    public static final class Change {
        public final String key;
        public final String original;
        public final String applied;

        public Change(String key, String original, String applied) {
            this.key = key;
            this.original = original;
            this.applied = applied;
        }
    }

    private static final String PREF_PREFIX = "performanceProfileChanges:";

    private PerformanceProfile() {}

    /** Put back the remembered values in {@code lines} while they are still what was set. */
    public static int restore(List<String> lines, List<Change> changes) {
        int restored = 0;
        for (Change change : changes) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals(change.key + ":" + change.applied)) {
                    lines.set(i, change.key + ":" + change.original);
                    restored++;
                    break;
                }
            }
        }
        return restored;
    }

    /** Remembered changes are stored as key, original and applied value, tab separated, one per line. */
    public static List<Change> decode(String encoded) {
        List<Change> changes = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return changes;
        for (String row : encoded.split("\n")) {
            String[] parts = row.split("\t", -1);
            if (parts.length == 3 && !parts[0].isEmpty()) changes.add(new Change(parts[0], parts[1], parts[2]));
        }
        return changes;
    }

    /**
     * Undo what the earlier build did to one instance's options.txt, if anything was remembered.
     *
     * @return how many values were put back
     */
    public static int undoEarlierChanges(File gameDirectory, SharedPreferences prefs) {
        File options = new File(gameDirectory, "options.txt");
        String prefKey = PREF_PREFIX + options.getAbsolutePath();
        List<Change> remembered = decode(prefs.getString(prefKey, ""));
        if (remembered.isEmpty()) return 0;
        int restored = 0;
        try {
            if (options.isFile()) {
                List<String> lines = new ArrayList<>(Arrays.asList(readAll(options).split("\r?\n", -1)));
                restored = restore(lines, remembered);
                if (restored > 0) write(options, lines);
            }
        } catch (IOException | RuntimeException error) {
            return 0;
        }
        prefs.edit().remove(prefKey).apply();
        return restored;
    }

    private static void write(File options, List<String> lines) throws IOException {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(lines.get(i));
        }
        try (OutputStream stream = new FileOutputStream(options)) {
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readAll(File file) throws IOException {
        try (InputStream stream = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
