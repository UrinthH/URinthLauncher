package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.util.Map;

/**
 * URinthRender is an opt-in performance profile layered over the launcher's
 * existing renderer backends. It intentionally does not edit options.txt,
 * resolution, or the user's saved renderer selection.
 *
 * This is the first integration layer, not a replacement native GPU driver.
 */
public final class URinthRender {
    private static final String TAG = "URinthRender";
    private static final String PREFS = "urinth_ui";
    private static final String KEY_ULTRA = "ultra";
    private static final String PROFILE_KEY = "URINTH_RENDER_PROFILE";

    private URinthRender() {}

    public static boolean isUltraEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ULTRA, true);
    }

    /**
     * Add only renderer-level environment settings while Ultra Mode is enabled.
     * Renderer selection remains owned by the user's existing instance settings.
     */
    public static void applyProfile(Context context, RenderSpec renderer, Map<String, String> env) {
        if (!isUltraEnabled(context) || renderer == null) return;

        String tag = renderer.tag();
        env.put("URINTH_ULTRA_MODE", "1");
        env.put(PROFILE_KEY, "ultra");
        Log.i(TAG, "Ultra profile enabled for backend: " + renderer.name());

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
