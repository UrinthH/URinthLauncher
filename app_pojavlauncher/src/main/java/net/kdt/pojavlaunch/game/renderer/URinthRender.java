package net.kdt.pojavlaunch.game.renderer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.system.ErrnoException;
import android.system.Os;
import android.os.Build;
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

    /** Log a reproducible device/backend snapshot without changing player graphics settings. */
    private static void logDeviceSnapshot(Context context, RenderSpec renderer, boolean ultraEnabled) {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "unknown";
        long totalRamMb = -1;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memoryInfo);
                totalRamMb = memoryInfo.totalMem / (1024L * 1024L);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read device memory information", e);
        }

        Log.i(TAG, "Device snapshot: manufacturer=" + Build.MANUFACTURER
                + ", model=" + Build.MODEL
                + ", device=" + Build.DEVICE
                + ", Android=" + Build.VERSION.RELEASE
                + ", SDK=" + Build.VERSION.SDK_INT
                + ", ABI=" + abi
                + ", RAM_MB=" + totalRamMb
                + ", ultra=" + ultraEnabled
                + ", selectedRenderer=" + renderer.name()
                + ", rendererTag=" + renderer.tag()
                + ", customBackendIntegrated=false"
                + ", resolutionAndOptionsTxtModified=false");
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
        if (renderer == null) {
            Log.w(TAG, "Renderer profile skipped because no renderer was selected");
            return;
        }

        String tag = renderer.tag();
        boolean ultraEnabled = isUltraEnabled(context);
        logDeviceSnapshot(context, renderer, ultraEnabled);
        if (!ultraEnabled) {
            Log.i(TAG, "URinthUltra Mode OFF; selected backend remains "
                    + renderer.name() + " (" + tag + "); no Ultra overrides added");
            return;
        }

        env.put(ULTRA_KEY, "1");
        env.put(PROFILE_KEY, "ultra");
        env.put(BACKEND_KEY, tag);
        env.put(PROFILE_VERSION_KEY, "2");
        Log.i(TAG, "URinthUltra Mode ON; profile version=2; selected backend="
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
        } else {
            Log.i(TAG, "No backend-specific Ultra optimization is implemented for " + tag
                    + "; this profile does not replace the selected renderer");
        }
    }
}
