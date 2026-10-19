package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GpuUtils;

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
        if (provider != null && provider.checkLibraries(LIBRARY)) return provider;
        provider = null;
        String[] packages = {
                LibraryPlugin.ID_MOBILEGLUES_PLUGIN,
                LibraryPlugin.ID_MOBILEGLUES_PLUGIN_FCL,
                LibraryPlugin.ID_MOBILEGLUES_PLUGIN_MIO
        };
        for (String packageName : packages) {
            LibraryPlugin candidate = LibraryPlugin.discoverPlugin(context, packageName);
            if (candidate != null && candidate.checkLibraries(LIBRARY)) {
                provider = candidate;
                break;
            }
        }
        return provider;
    }

    @Override
    public boolean compatibleDevice(Context context) {
        LibraryPlugin plugin = discover(context);
        boolean available = plugin != null && plugin.checkLibraries(LIBRARY);
        if (!available) {
            Log.i(TAG, "Official MobileGlues plugin or native library is unavailable");
            return false;
        }

        // MobileGlues requires a device EGL/GLES stack capable of creating GLES 3.
        // Do not auto-select it when the launcher's capability probe only confirms GLES 2.
        try {
            GpuUtils.GLInfo info = GpuUtils.getGlInfo();
            boolean supportsGles3 = info != null && info.glesMajorVersion >= 3;
            if (!supportsGles3) {
                Log.w(TAG, "MobileGlues not selected: device GLES major version is "
                        + (info == null ? "unknown" : info.glesMajorVersion)
                        + "; GLES 3 or newer is required");
                return false;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "MobileGlues not selected because GLES capability could not be verified", error);
            return false;
        }

        // MobileGlues translates OpenGL onto Vulkan (it bundles its own Zink/Mesa-style
        // Gallium loader). Without real Vulkan hardware support it still reports GLES 3
        // through the native GLES stack, but its own internal Vulkan/Gallium device probe
        // then finds no usable GPU bus and GLFW fails to create a window ("EGL: Failed to
        // find support for OpenGL" / "Could not find any graphics adapters!"), crashing the
        // game outright instead of just not being offered. Gate on Vulkan the same way the
        // launcher's own Zink backend already does.
        boolean supportsVulkan = GpuUtils.checkVulkanSupport(context.getPackageManager());
        if (!supportsVulkan) {
            Log.w(TAG, "MobileGlues not selected: this device has no Vulkan hardware support, "
                    + "which MobileGlues' internal Gallium/Zink backend requires");
        }
        return supportsVulkan;
    }

    @Override
    public String name() {
        return "MobileGlues (external)";
    }

    @Override
    public int displayName() {
        return R.string.mcl_setting_renderer_mobileglues;
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
    public String[] pluginPackages() {
        return new String[] {
                LibraryPlugin.ID_MOBILEGLUES_PLUGIN,
                LibraryPlugin.ID_MOBILEGLUES_PLUGIN_FCL,
                LibraryPlugin.ID_MOBILEGLUES_PLUGIN_MIO
        };
    }

    @Override
    public String librarySearchPath() {
        LibraryPlugin plugin = provider;
        return plugin == null ? null : plugin.getLibraryPath();
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        // Resolve the plugin on the launch instance; RendererCache probes a separate instance.
        discover(context);
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
