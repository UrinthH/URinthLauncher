package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.util.Map;

import git.artdeell.mojo.R;
import git.artdeell.mojoexec.MojoExec;

/**
 * Optional external MobileGlues backend. The renderer is supplied by the official
 * com.fcl.plugin.mobileglues app; URinthLauncher does not bundle or replace its native library.
 */
public final class MobileGluesRenderSpec implements RenderSpec {
    private static final String TAG = "MobileGluesRenderSpec";
    private static final String LIBRARY = "libmobileglues.so";
    private LibraryPlugin provider;

    private LibraryPlugin discover(Context context) {
        if (provider == null) {
            provider = LibraryPlugin.discoverPlugin(
                    context, LibraryPlugin.ID_MOBILEGLUES_PLUGIN);
        }
        return provider;
    }

    @Override
    public boolean compatibleDevice(Context context) {
        LibraryPlugin plugin = discover(context);
        boolean available = plugin != null && plugin.checkLibraries(LIBRARY);
        if (!available) {
            Log.i(TAG, "Official MobileGlues plugin or native library is unavailable");
        }
        return available;
    }

    @Override
    public String name() {
        return "MobileGlues (external)";
    }

    @Override
    public int displayName() {
        return R.string.mcl_setting_renderer_ltw;
    }

    @Override
    public String tag() {
        return Renderers.MOBILEGLUES_RENDERER;
    }

    @Override
    public String library() {
        return LIBRARY;
    }

    @Override
    public String librarySearchPath() {
        LibraryPlugin plugin = provider;
        return plugin == null ? null : plugin.getLibraryPath();
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        GLESProvider glesProvider = GLESProvider.getGlesProvider(
                context, LauncherPreferences.PREF_USE_ANGLE);
        glesProvider.setEnvironment(envMap);
        envMap.put("LIBGL_ES", "3");
        envMap.put("POJAV_RENDERER", "opengles3");
        envMap.put("POJAVEXEC_EGL", LIBRARY);
        envMap.put("LIBGL_EGL", LIBRARY);
        envMap.put("MG_COUNT_LAUNCH", "1");
        Log.i(TAG, "Configured MobileGlues EGL library from external plugin path: "
                + librarySearchPath());
    }

    @Override
    public boolean setupRenderer() {
        LibraryPlugin plugin = provider;
        if (plugin == null || !plugin.checkLibraries(LIBRARY)) return false;
        String absoluteLibrary = plugin.resolveAbsolutePath(LIBRARY);
        boolean ready = MojoExec.prepareEgl(absoluteLibrary, true, false, 0);
        Log.i(TAG, "MobileGlues EGL setup result=" + ready + ", library=" + absoluteLibrary);
        return ready;
    }
}
