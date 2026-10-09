package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.game.renderer.GameRenderer;
import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.Map;

import git.artdeell.mojo.R;
import git.artdeell.mojoexec.MojoExec;

/**
 * Base GLES RenderSpec. Represents a desktop OpenGL wrapper running on-top of {@link GLESProvider}
 */
public abstract class GLESRenderSpec implements RenderSpec {
    private boolean nsBypass = false;
    protected abstract int glesVersion();
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        GLESProvider provider = GLESProvider.getGlesProvider(context, LauncherPreferences.PREF_USE_ANGLE);
        Log.i("GLESRenderSpec", "Using GLESProvider: " + provider.type());
        provider.setEnvironment(envMap);
        this.nsBypass = provider.requiresNamespace();
        if (LauncherPreferences.PREF_DUMP_SHADERS)
            envMap.put("LIBGL_VGPU_DUMP", "1");
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        // Prevent OptiFine (and other error-reporting stuff in Minecraft) from balooning the log
        envMap.put("LIBGL_NOERROR", "1");
    }
    public boolean setupRenderer() {
        return MojoExec.prepareEgl(library(), nsBypass, true, glesVersion());
    }

    public static class LTWRenderSpec extends GLESRenderSpec {
        public boolean compatibleDevice(Context context) {
            return JREUtils.getDetectedVersion() >= 3 && new File(Tools.NATIVE_LIB_DIR, this.library()).exists();
        }
        public String name() {
            return "OpenLTW";
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_ltw;
        }
        public String tag() {
            return Renderers.LTW_RENDERER;
        }
        public String library() {
            return "libltw.so";
        }
        protected int glesVersion() {
            return 3;
        }
    }

    public static class GL4ESRenderSpec extends GLESRenderSpec {
        public boolean compatibleDevice(Context context) {
            return true;
        }
        public String name() {
            return "GL4ES";
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_gles2_4;
        }
        public String tag() {
            return Renderers.GL4ES_RENDERER;
        }
        public String library() {
            return "libgl4es_114.so";
        }
        protected int glesVersion() {
            return 2;
        }
    }
    /**
     * Krypton/NG GL4ES wrapper. It is listed only when an installed plugin supplies
     * the required native library, avoiding a selectable renderer with a missing .so.
     */
    public static class KryptonRenderSpec extends GLESRenderSpec {
        private LibraryPlugin provider;

        private boolean discover(Context context) {
            if (provider != null && provider.checkLibraries(library())) return true;
            provider = null;
            String[] packages = { LibraryPlugin.ID_KRYPTON_PLUGIN, LibraryPlugin.ID_KRYPTON_ANGLELESS_PLUGIN };
            for (String packageName : packages) {
                LibraryPlugin candidate = LibraryPlugin.discoverPlugin(context, packageName);
                if (candidate != null && candidate.checkLibraries(library())) {
                    provider = candidate;
                    return true;
                }
            }
            return false;
        }

        @Override public boolean compatibleDevice(Context context) {
            return JREUtils.getDetectedVersion() >= 3 && discover(context);
        }
        @Override public String name() { return "Krypton Wrapper"; }
        @Override public int displayName() { return R.string.mcl_setting_renderer_krypton; }
        @Override public String tag() { return Renderers.KRYPTON_RENDERER; }
        @Override public String library() { return "libng_gl4es.so"; }
        @Override public String librarySearchPath() {
            return provider == null ? null : provider.getLibraryPath();
        }
        @Override protected int glesVersion() { return 3; }
        @Override public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.put("LIBGL_USE_MC_COLOR", "1");
            envMap.put("LIBGL_GL", "31");
            envMap.put("LIBGL_ES", "3");
            envMap.put("LIBGL_NORMALIZE", "1");
            envMap.put("LIBGL_NOERROR", "1");
        }
    }
}
