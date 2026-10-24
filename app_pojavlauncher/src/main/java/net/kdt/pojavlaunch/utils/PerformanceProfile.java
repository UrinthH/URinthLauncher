package net.kdt.pojavlaunch.utils;

import android.content.SharedPreferences;

import java.io.File;
import java.io.ByteArrayOutputStream;
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
 * The game-settings half of Performance Mode.
 *
 * The settings that cost the most frame time in Minecraft are the ones that scale with how much
 * is drawn and simulated: render distance, simulation distance, entity distance, particles, fancy
 * graphics, smooth lighting, entity shadows, biome blending, clouds and mipmaps. When Performance
 * Mode is on, this lowers those in the instance's options.txt before the game starts.
 *
 * Rules, so it stays safe:
 * <ul>
 *   <li>it only lowers a value, never raises one, so a player who already runs lighter settings
 *       keeps them;</li>
 *   <li>it only edits keys that are already in the file, so an unknown or renamed key in another
 *       Minecraft version is never invented;</li>
 *   <li>it never touches resolution, fullscreen, the frame limit, VSync or anything else;</li>
 *   <li>every change is remembered, and turning Performance Mode off puts each value back, unless
 *       the player has changed that value in game since, in which case their choice stays.</li>
 * </ul>
 */
public final class PerformanceProfile {
    /** One value this profile changed: what it was and what it was set to. */
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

    /** Lower the heavy settings in {@code lines} (the lines of options.txt) and report what changed. */
    public static List<Change> lighten(List<String> lines) {
        List<Change> changes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon);
            String value = line.substring(colon + 1);
            String lighter = lighterValue(key, value);
            if (lighter == null || lighter.equals(value)) continue;
            lines.set(i, key + ":" + lighter);
            changes.add(new Change(key, value, lighter));
        }
        return changes;
    }

    /** The lighter replacement for one option, or null to leave it as it is. */
    static String lighterValue(String key, String value) {
        switch (key) {
            case "renderDistance": return capInt(value, 8);
            case "simulationDistance": return capInt(value, 6);
            case "biomeBlendRadius": return capInt(value, 1);
            case "mipmapLevels": return capInt(value, 2);
            case "entityDistanceScaling": return capFloat(value, 0.75f);
            case "particles": return "0".equals(value) ? "1" : null; // all -> decreased
            case "graphicsMode": return isInt(value) ? "0" : null; // fast
            case "fancyGraphics": return "true".equals(value) ? "false" : null;
            case "entityShadows": return "true".equals(value) ? "false" : null;
            case "ao":
                if ("true".equals(value)) return "false";
                if ("1".equals(value) || "2".equals(value)) return "0";
                return null;
            case "renderClouds":
                if ("\"true\"".equals(value) || "\"fast\"".equals(value)) return "\"false\"";
                if ("true".equals(value) || "fast".equals(value)) return "false";
                return null;
            default: return null;
        }
    }

    private static boolean isInt(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String capInt(String value, int max) {
        try {
            int number = Integer.parseInt(value.trim());
            return number > max ? Integer.toString(max) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String capFloat(String value, float max) {
        try {
            float number = Float.parseFloat(value.trim());
            return number > max ? Float.toString(max) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Put back what {@link #lighten} changed. A value is only restored while it is still what this
     * profile set; if the player changed it in game since, their choice stays.
     *
     * @return how many values were restored
     */
    public static int restore(List<String> lines, List<Change> changes) {
        int restored = 0;
        for (Change change : changes) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.equals(change.key + ":" + change.applied)) {
                    lines.set(i, change.key + ":" + change.original);
                    restored++;
                    break;
                }
            }
        }
        return restored;
    }

    /** Turn a list of changes into one string for SharedPreferences (tab and newline separated). */
    public static String encode(List<Change> changes) {
        StringBuilder out = new StringBuilder();
        for (Change change : changes) {
            if (out.length() > 0) out.append('\n');
            out.append(change.key).append('\t').append(change.original).append('\t').append(change.applied);
        }
        return out.toString();
    }

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
     * Bring one instance's options.txt in line with the Performance Mode switch: lighten it when
     * on (once, remembering what it changed), put the values back when off.
     *
     * @return a short description of what happened, or null when nothing changed
     */
    public static String sync(File gameDirectory, boolean performanceMode, SharedPreferences prefs) {
        File options = new File(gameDirectory, "options.txt");
        if (!options.isFile()) return null;
        String prefKey = PREF_PREFIX + options.getAbsolutePath();
        String remembered = prefs.getString(prefKey, "");
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(
                    readAll(options).split("\r?\n", -1)));
            if (performanceMode) {
                List<Change> changes = lighten(lines);
                if (changes.isEmpty()) return null;
                // Keep the first remembered original for a key, so repeated launches never
                // overwrite "what the player had" with "what we set".
                List<Change> all = decode(remembered);
                for (Change change : changes) {
                    boolean known = false;
                    for (Change old : all) {
                        if (old.key.equals(change.key)) {
                            all.set(all.indexOf(old), new Change(old.key, old.original, change.applied));
                            known = true;
                            break;
                        }
                    }
                    if (!known) all.add(change);
                }
                write(options, lines);
                prefs.edit().putString(prefKey, encode(all)).apply();
                return "lowered " + changes.size() + " heavy game settings";
            }
            List<Change> old = decode(remembered);
            if (old.isEmpty()) return null;
            int restored = restore(lines, old);
            if (restored > 0) write(options, lines);
            prefs.edit().remove(prefKey).apply();
            return restored > 0 ? "restored " + restored + " game settings" : null;
        } catch (IOException | RuntimeException error) {
            return null;
        }
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
