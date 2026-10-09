package net.kdt.pojavlaunch.game.renderer.impl;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.GameRenderer;
import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GpuUtils;

import java.io.File;
import java.util.Map;

import git.artdeell.mojo.R;
import git.artdeell.mojoexec.MojoExec;

/**
 * Mesa3D RenderSpec. Provides desktop Mesa, zink & freedreno
 */
public class MesaRenderSpec implements RenderSpec {
    public String library() {
        return "libEGL_mesa.so";
    }
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
    }
    protected boolean hasMesa() {
        return SDK_INT >= 29 && new File(Tools.NATIVE_LIB_DIR, this.library()).exists();
    }
    public boolean compatibleDevice(Context context) {
        return hasMesa() && GpuUtils.checkChromebook(context.getPackageManager());
    }
    public String name() {
        return "Mesa";
    }
    public int displayName() {
        return R.string.mcl_setting_renderer_mesa_desktop;
    }
    public String tag() {
        return Renderers.MESA_RENDERER;
    }
    public boolean setupRenderer() {
        return MojoExec.prepareEgl(library(), true, false, 3);
    }

    public static class ZinkRenderSpec extends MesaRenderSpec {
        public String name() {
            return "ZINK";
        }
        public String tag() {
            return Renderers.ZINK_RENDERER;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_vulkan_zink;
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            // This is needed because mobile drivers often don't implement required features for zink
            // hence making it fall back to OpenGL 2.1 and break the modern game completely
            // We don't care much about passing CTS hence this is fine
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
            // These variables advertise a version to Mesa; they do not add missing
            // GPU/driver features. Keep that distinction visible in renderer diagnostics.
            Log.w("Renderer", "Zink requests GL 4.6 / GLSL 4.60 version overrides; actual feature support is device/driver-dependent and must be validated by the game");
            super.setupEnvironment(context, envMap);
        }
        public boolean setupRenderer() {
            MojoExec.preloadVulkan();
            return super.setupRenderer();
        }
        public boolean compatibleDevice(Context context) {
            return hasMesa() && GpuUtils.checkVulkanSupport(context.getPackageManager());
        }
    }

    public static class FreedrenoRenderSpec extends MesaRenderSpec {
        public String name() {
            return "Freedreno";
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_freedreno_kgsl;
        }
        public String tag() {
            return Renderers.FREEDRENO_RENDERER;
        }
        public boolean compatibleDevice(Context context) {
            GpuUtils.GLInfo info = GpuUtils.getGlInfo();
            return hasMesa() && info != null && info.isAdreno();
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            if (LauncherPreferences.PREF_FREEDRENO_SYSMEM) envMap.put("FD_MESA_DEBUG", "sysmem");
            if(LauncherPreferences.PREF_UBWC_WORKAROUND) envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
            // On Adreno 5XX and lower only Core 3.1 is exposed by default due to missing hardware extensions.
            // 3.3 is required for modern games so let's force 3.3 if running on such GPU - it's known to be working.
            GpuUtils.GLInfo info = GpuUtils.getGlInfo();
            if (info != null && info.isAdreno500Lower()) {
                envMap.put("MESA_GL_VERSION_OVERRIDE", "3.3");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "330");
            }
            super.setupEnvironment(context, envMap);
        }
    }
    public static class ExtMesaRenderSpec extends MesaRenderSpec {
        private LibraryPlugin provider;
        protected String plugin() {
            return LibraryPlugin.ID_MESA_PLUGIN;
        }
        public String name() {
            return "Mesa (external)";
        }
        public String librarySearchPath() {
            // A plugin can be removed after the Settings list was populated.
            // Keep launch setup null-safe so the normal GL4ES fallback can run.
            return provider == null ? null : provider.getLibraryPath();
        }
        public String tag() {
            return Renderers.MESA_RENDERER_EXT;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_mesa_desktop_ext;
        }
        private boolean discover(Context context) {
            if (provider != null && provider.checkLibraries(library())) return true;
            provider = LibraryPlugin.discoverPlugin(context, plugin());
            if (provider != null && provider.checkLibraries(library())) return true;
            if (!LibraryPlugin.ID_MESA_PLUGIN.equals(plugin())) return false;
            String[] alternatives = {
                    LibraryPlugin.ID_MESA_PLUGIN_FCL,
                    LibraryPlugin.ID_MESA_PLUGIN_MIO_2319,
                    LibraryPlugin.ID_MESA_PLUGIN_MIO_2427,
                    LibraryPlugin.ID_MESA_PLUGIN_MIO_2434,
                    LibraryPlugin.ID_MESA_PLUGIN_MIO_2500,
                    LibraryPlugin.ID_MESA_PLUGIN_MIO_2500_RC1
            };
            for (String packageName : alternatives) {
                LibraryPlugin candidate = LibraryPlugin.discoverPlugin(context, packageName);
                if (candidate != null && candidate.checkLibraries(library())) {
                    provider = candidate;
                    return true;
                }
            }
            provider = null;
            return false;
        }
        public boolean compatibleDevice(Context context) {
            return discover(context) && provider.checkLibraries(library());
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            discover(context);
            super.setupEnvironment(context, envMap);
        }
        public boolean setupRenderer() {
            if(provider == null) return false;
            return MojoExec.prepareEgl(provider.resolveAbsolutePath(library()), true, false, 0);
        }
    }
    public static class LegacyZinkRenderSpec extends ExtMesaRenderSpec {
        protected String plugin() {
            return LibraryPlugin.ID_ZINK_PLUGIN;
        }
        public String name() {
            return "ZINK (Legacy)";
        }
        public String tag() {
            return Renderers.LEGACYZINK_RENDERER;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_vulkan_lzink;
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        }
        public boolean setupRenderer() {
            MojoExec.preloadVulkan();
            return super.setupRenderer();
        }
        public boolean compatibleDevice(Context context) {
            return GpuUtils.checkVulkanSupport(context.getPackageManager()) && super.compatibleDevice(context);
        }
        public String library() {
            return "libEGL_legacy.so";
        }
    }
    /** VirGL backend; only exposed if an installed Mesa package provides its library. */
    public static class VirGLRenderSpec extends ExtMesaRenderSpec {
        @Override public String name() { return "VirGL"; }
        @Override public String tag() { return Renderers.VIRGL_RENDERER; }
        @Override public int displayName() { return R.string.mcl_setting_renderer_virgl; }
        @Override public String library() { return "libOSMesa_2121.so"; }
        @Override public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.put("GALLIUM_DRIVER", "virpipe");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "virpipe");
            envMap.put("VTEST_SOCKET_NAME", new File(Tools.DIR_CACHE, ".virgl_test").getAbsolutePath());
        }
    }

    /** Panfrost backend; restricted to ARM/Mali-class devices with the matching library. */
    public static class PanfrostRenderSpec extends ExtMesaRenderSpec {
        @Override public String name() { return "Panfrost (Mali)"; }
        @Override public String tag() { return Renderers.PANFROST_RENDERER; }
        @Override public int displayName() { return R.string.mcl_setting_renderer_panfrost; }
        @Override public String library() { return "libOSMesa_2300d.so"; }
        @Override public boolean compatibleDevice(Context context) {
            GpuUtils.GLInfo info = GpuUtils.getGlInfo();
            return info != null && info.isMali() && super.compatibleDevice(context);
        }
        @Override public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.put("GALLIUM_DRIVER", "panfrost");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "panfrost");
        }
    }
}
