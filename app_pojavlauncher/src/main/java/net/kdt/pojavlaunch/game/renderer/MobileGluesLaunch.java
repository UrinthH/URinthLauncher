package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.util.Map;

/**
 * Hands the player's MobileGlues settings to MobileGlues at launch: writes {@code config.json} into the
 * launcher's own folder ({@link MobileGluesFolder}) and sets {@code MG_DIR_PATH} to it. A launcher that sets
 * that variable is one MobileGlues treats as known, so it reads the file instead of forcing its built-in
 * defaults (see {@link MobileGluesConfig}).
 */
public final class MobileGluesLaunch {
    private static final String TAG = "MobileGluesLaunch";

    private MobileGluesLaunch() {}

    /** The launcher's own MobileGlues folder, or null when no storage of this app can be written to. */
    public static File folder(Context context) {
        File external = context.getExternalFilesDir(null);
        File internal = context.getFilesDir();
        return MobileGluesFolder.pick(
                external == null ? null : new File(external, MobileGluesFolder.FOLDER_NAME),
                internal == null ? null : new File(internal, MobileGluesFolder.FOLDER_NAME));
    }

    /**
     * Writes the settings and adds {@code MG_DIR_PATH} to the renderer environment. Never throws: when the
     * folder or the file cannot be written, MobileGlues is left to use its own folder and defaults, and the
     * game log says so.
     */
    public static void apply(Context context, Map<String, String> env) {
        try {
            MobileGluesConfig config = MobileGluesConfig.fromMap(LauncherPreferences.DEFAULT_PREF.getAll());
            File dir = folder(context);
            if (dir == null) {
                note("could not make a folder for MobileGlues settings, so MobileGlues uses its own defaults");
                return;
            }
            MobileGluesFolder.writeConfig(dir, config.toJson());
            env.put("MG_DIR_PATH", dir.getAbsolutePath());
            note("settings written to " + dir.getAbsolutePath() + ": " + config.describe());
        } catch (Throwable error) {
            Log.w(TAG, "Could not hand the MobileGlues settings over", error);
            note("could not write the MobileGlues settings (" + error + "), so MobileGlues uses its own defaults");
        }
    }

    private static void note(String text) {
        Log.i(TAG, text);
        try {
            Logger.appendToLog("[MGConfig] " + text);
        } catch (Throwable ignored) {
            // The game log is only an extra: never let it get in the way of starting the game.
        }
    }
}
