package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.util.HashMap;
import java.util.Map;

/**
 * Reversible opt-in renderer profile. It never edits options.txt, resolution,
 * or the user's selected renderer. Profile changes are applied at game-launch
 * environment setup time, so an already-running game must be restarted.
 */
public final class URinthRender {
    private static final String TAG = "URinthRender";
    private static final String PREFS = "urinth_ui";
    private static final String KEY_ULTRA = "ultra";
    private static final String PROFILE_KEY = "URINTH_RENDER_PROFILE";
    private static final String ULTRA_KEY = "URINTH_ULTRA_MODE";
    private static final String BACKEND_KEY = "URINTH_RENDER_BACKEND";
    private static final String PROFILE_VERSION_KEY = "URINTH_RENDER_PROFILE_VERSION";

    private static final String[] PROFILE_ENV_KEYS = {
            ULTRA_KEY,
            PROFILE_KEY,
            BACKEND_KEY,
            PROFILE_VERSION_KEY,
            "MESA_SHADER_CACHE_MAX_SIZE",
            "mesa_glthread",
            "MESA_GLSL_CACHE_DIR"
    };

    // Snapshot the launcher's original process environment before we change it.
    // OFF restores these values (or unsets variables that were originally absent).
    private static final Map<String, String> ORIGINAL_ENV = captureOriginalEnvironment();

    private URinthRender() {}

    private static Map<String, String> captureOriginalEnvironment() {
        Map<String, String> values = new HashMap<>();
        for (String key : PROFILE_ENV_KEYS) {
            values.put(key, System.getenv(key));
        }
        return values;
    }

    public static boolean isUltraEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ULTRA, true);
    }

    /**
     * Restore the launcher's original values before each renderer setup.
     * The selected RenderSpec then supplies its normal backend environment.
     */
    public static void restoreNormalEnvironment() {
        for (String key : PROFILE_ENV_KEYS) {
            try {
                String original = ORIGINAL_ENV.get(key);
                if (original == null) {
                    Os.unsetenv(key);
                } else {
                    Os.setenv(key, original, true);
                }
            } catch (ErrnoException e) {
                Log.w(TAG, "Could not restore normal environment variable " + key, e);
            }
        }
        Log.i(TAG, "Restored baseline renderer environment before profile selection");
    }

    /**
     * Apply only the opt-in environment overrides. If OFF, no Ultra overrides
     * are added after the normal renderer has configured its environment.
     */
    public static void applyProfile(Context context, RenderSpec renderer, Map<String, String> env) {
        if (!isUltraEnabled(context) || renderer == null) {
            Log.i(TAG, "URinthUltra Mode OFF; selected renderer remains unchanged and no Ultra overrides are added");
            return;
        }

        String tag = renderer.tag();
        env.put(ULTRA_KEY, "1");
        env.put(PROFILE_KEY, "ultra");
        env.put(BACKEND_KEY, tag);
        env.put(PROFILE_VERSION_KEY, "1");
        Log.i(TAG, "URinthUltra Mode ON; profile version=1; selected backend="
                + renderer.name() + " (" + tag + ")");

        // Mesa's shader cache and GL worker thread apply only to Mesa-backed
        // implementations (including Zink); leave GL4ES/LTW and other backends alone.
        if (Renderers.ZINK_RENDERER.equals(tag)
                || Renderers.MESA_RENDERER.equals(tag)
                || Renderers.MESA_RENDERER_EXT.equals(tag)
                || Renderers.LEGACYZINK_RENDERER.equals(tag)) {
            env.put("MESA_SHADER_CACHE_MAX_SIZE", "128M");
            env.put("mesa_glthread", "true");
            env.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
            Log.i(TAG, "Enabled Mesa shader cache sizing and GL worker-thread profile");
        }
    }
}
