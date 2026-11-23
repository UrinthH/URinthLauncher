package net.kdt.pojavlaunch.prefs;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

/**
 * Writes a quick setup ({@link AdvancedPresets}) into the preferences, all 24 options and the remembered choice in one
 * edit, so that there is one write and one notification instead of 25, and no moment in between where the options are a
 * half-written mix. What to write and when it is wrong is decided in {@link AdvancedPresets}, which is tested; this class
 * only hands it to the preferences.
 *
 * Only the launcher calls this. The game process only reads these options; writing them from there, with its own older
 * copy of the file, is what used to undo changes made in the launcher.
 */
public final class AdvancedPresetStore {
    private static final String TAG = "AdvancedPresetStore";

    private AdvancedPresetStore() {}

    /** Puts a preset's 24 options and the choice into the editor. Does not write them yet. */
    public static SharedPreferences.Editor put(SharedPreferences.Editor editor, String id) {
        return putAll(editor, AdvancedPresets.writesFor(id));
    }

    /**
     * Puts the default of every Advanced option into the editor and forgets the remembered choice, for "Reset all". Does
     * not write them yet.
     */
    public static SharedPreferences.Editor putDefaults(SharedPreferences.Editor editor) {
        return putAll(editor, AdvancedPerformance.defaultValues()).remove(AdvancedPresets.KEY_CHOSEN);
    }

    private static SharedPreferences.Editor putAll(SharedPreferences.Editor editor, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else editor.putString(entry.getKey(), (String) value);
        }
        return editor;
    }

    /**
     * If the player picked a quick setup and the options no longer match it (something other than the player changed
     * them), writes the quick setup's options again and waits until they are on disk, because the game process reads the
     * file as soon as it starts.
     *
     * @return true when options were put back, so that the caller loads the preferences again
     */
    public static boolean heal(SharedPreferences prefs) {
        if (prefs == null) return false;
        AdvancedPresets.Shown shown = AdvancedPresets.shown(prefs.getAll());
        if (!shown.restore) return false;
        Log.w(TAG, "The options no longer matched the quick setup that was picked (" + shown.id
                + "), writing it again");
        if (!put(prefs.edit(), shown.id).commit()) {
            Log.e(TAG, "The quick setup " + shown.id + " could not be written to disk");
        }
        return true;
    }

    /** Forgets the remembered choice, after the player changed a single option. */
    public static void forget(SharedPreferences prefs) {
        if (prefs != null && prefs.contains(AdvancedPresets.KEY_CHOSEN)) {
            prefs.edit().remove(AdvancedPresets.KEY_CHOSEN).apply();
        }
    }
}
