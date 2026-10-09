package net.kdt.pojavlaunch.game.renderer;

import static net.kdt.pojavlaunch.game.renderer.def.Renderers.FREEDRENO_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.GL4ES_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.LEGACYZINK_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.LTW_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.MESA_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.MESA_RENDERER_EXT;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.MOBILEGLUES_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.ZINK_RENDERER;

import android.content.Context;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.impl.GLESRenderSpec;
import net.kdt.pojavlaunch.game.renderer.impl.MesaRenderSpec;
import net.kdt.pojavlaunch.game.renderer.impl.MobileGluesRenderSpec;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.HashMap;
import java.util.Map;

import git.artdeell.mojoexec.MojoExec;

/*
 How to add an extra renderer (guide 2026 mediafire works):

 1. Create RenderSpec for that renderer
 2. Add all requires locale strings and wire them up inside your freshly cooked RenderSpec
 3. Add the renderer tag onto the getKnownRenderer() mapping
 4. (Optional): Add the renderer tag into constant list in Renderers class
 5. Add the renderer tag onto the list of renderers to check for the compatibility (see RendererCache)
 6. ???
 7. PROFIT
*/

/** Class for managing game renderers (OpenGL ES & Vulkan). */
public class GameRenderer {
    private final static String TAG = "Renderer";
    private final static String FALLBACK_RENDERER = GL4ES_RENDERER;
    private RenderSpec currentRenderer;
    private Map<String, String> environment = new HashMap<>();

    public GameRenderer(String currentRenderer) {
        this.currentRenderer = getKnownRenderer(currentRenderer);
        if (this.currentRenderer == null) this.currentRenderer = getKnownRenderer(GL4ES_RENDERER);
        if (this.currentRenderer == null) throw new IllegalStateException("Failed to create the current renderer!");
    }

    /** Map renderer string to a known RenderSpec. */
    public static RenderSpec getKnownRenderer(String renderer) {
        switch (renderer) {
            case "opengles2_4":
            case "opengles2_5":
            case GL4ES_RENDERER: return new GLESRenderSpec.GL4ESRenderSpec();
            case LTW_RENDERER: return new GLESRenderSpec.LTWRenderSpec();
            case ZINK_RENDERER: return new MesaRenderSpec.ZinkRenderSpec();
            case FREEDRENO_RENDERER: return new MesaRenderSpec.FreedrenoRenderSpec();
            case MESA_RENDERER: return new MesaRenderSpec();
            case MESA_RENDERER_EXT: return new MesaRenderSpec.ExtMesaRenderSpec();
            case LEGACYZINK_RENDERER: return new MesaRenderSpec.LegacyZinkRenderSpec();
            case MOBILEGLUES_RENDERER: return new MobileGluesRenderSpec();
            default:
                Log.e(TAG, "Unknown renderer " + renderer);
                return null;
        }
    }

    /** Set renderer library path. */
    public static void setRendererLibraryPath(String mainPath, String additionalPath) {
        if (additionalPath != null) mainPath = additionalPath + ":" + mainPath;
        MojoExec.setNativeLibraryDir(mainPath);
    }

    /**
     * Setup current selected renderer environment. Call before maybeSetupRenderer().
     * @throws ErrnoException if an underlying Os environment call fails.
     */
    public void setupEnvironment(Context context) throws ErrnoException {
        setupEnvironment(context, true);
    }

    /**
     * Prepare the selected backend and optional Ultra profile. MobileGlues is selected only
     * when Ultra is ON, its plugin library is installed, and the caller confirms MC >= 1.17.
     */
    public void setupEnvironment(Context context, boolean allowMobileGlues) throws ErrnoException {
        if (environment == null) {
            Log.w(TAG, "Tried to call setupEnvironment in already initialized environment");
            return;
        }
        URinthRender.restoreNormalEnvironment();

        boolean ultraEnabled = URinthRender.isUltraEnabled(context);
        if (ultraEnabled) {
            RenderSpec delegate = currentRenderer instanceof UrinthUltraWrapperRenderSpec
                    ? ((UrinthUltraWrapperRenderSpec) currentRenderer).getDelegate()
                    : currentRenderer;

            if (allowMobileGlues) {
                RenderSpec mobileGlues = new MobileGluesRenderSpec();
                if (mobileGlues.compatibleDevice(context)) {
                    delegate = mobileGlues;
                    Log.i(TAG, "URinthUltra selected external MobileGlues backend; library availability and GLES compatibility checks passed");
                } else {
                    Log.w(TAG, "MobileGlues plugin/library unavailable or GLES 3.x requirement not met; preserving the selected renderer");
                }
            } else {
                Log.i(TAG, "MobileGlues requires Minecraft 1.17+; preserving the selected renderer for this version");
            }

            currentRenderer = new UrinthUltraWrapperRenderSpec(delegate);
            Log.i(TAG, "URinthUltra Wrapper active; delegated backend=" + currentRenderer.tag());
        } else if (currentRenderer instanceof UrinthUltraWrapperRenderSpec) {
            currentRenderer = ((UrinthUltraWrapperRenderSpec) currentRenderer).getDelegate();
            Log.i(TAG, "URinthUltra disabled; restored selected backend=" + currentRenderer.tag());
        }

        currentRenderer.setupEnvironment(context, environment);
        URinthRender.applyProfile(context, currentRenderer, environment);
        for (Map.Entry<String, String> e : environment.entrySet()) {
            Logger.appendToLog("Added renderer env: " + e.getKey() + "=" + e.getValue());
            Os.setenv(e.getKey(), e.getValue(), true);

            // Read back the process environment immediately after setenv. This verifies
            // launcher-side application only; it cannot prove a native library consumes
            // the variable or that it improves frame rate.
            String actual = Os.getenv(e.getKey());
            boolean matches = e.getValue().equals(actual);
            Log.i(TAG, "Renderer env verification: key=" + e.getKey()
                    + ", expected=" + e.getValue()
                    + ", actual=" + (actual == null ? "<unset>" : actual)
                    + ", matches=" + matches);
            if (!matches) {
                Log.w(TAG, "Renderer environment read-back mismatch for " + e.getKey());
            }
        }

        environment.clear();
        environment = null;
    }

    /** Get current selected renderer. */
    public RenderSpec getCurrentRenderer() {
        return currentRenderer;
    }

    /** Set renderer before setupEnvironment. */
    public void setCurrentRenderer(RenderSpec spec) {
        Log.i(TAG, "Replacing default renderer with the new: " + spec.name());
        currentRenderer = spec;
    }

    /** Set renderer before setupEnvironment. */
    public void setCurrentRenderer(String renderer) throws IllegalArgumentException {
        RenderSpec spec = getKnownRenderer(renderer);
        if (spec == null) throw new IllegalArgumentException("Invalid renderer string" + renderer + "!");
        this.setCurrentRenderer(spec);
    }

    /** Set up current renderer or fall back to GL4ES if setup fails. */
    public boolean maybeSetupRenderer() {
        final String requestedBackend = currentRenderer.name();
        final long setupStartedAt = SystemClock.elapsedRealtime();
        setRendererLibraryPath(Tools.NATIVE_LIB_DIR, currentRenderer.librarySearchPath());
        if (!currentRenderer.setupRenderer()) {
            final long primarySetupMs = SystemClock.elapsedRealtime() - setupStartedAt;
            Log.e(TAG, "Renderer setup failed: backend=" + requestedBackend
                    + ", elapsedMs=" + primarySetupMs
                    + ", falling back to " + FALLBACK_RENDERER);

            RenderSpec fallback = getKnownRenderer(FALLBACK_RENDERER);
            if (fallback == null) {
                Log.e(TAG, "Renderer fallback is unavailable; primarySetupMs=" + primarySetupMs);
                return false;
            }

            if (currentRenderer instanceof UrinthUltraWrapperRenderSpec) {
                currentRenderer = new UrinthUltraWrapperRenderSpec(fallback);
            } else {
                currentRenderer = fallback;
            }
            setRendererLibraryPath(Tools.NATIVE_LIB_DIR, currentRenderer.librarySearchPath());
            final long fallbackStartedAt = SystemClock.elapsedRealtime();
            boolean fallbackReady = currentRenderer.setupRenderer();
            final long fallbackSetupMs = SystemClock.elapsedRealtime() - fallbackStartedAt;
            Log.i(TAG, "Renderer fallback result: backend=" + currentRenderer.name()
                    + ", ready=" + fallbackReady
                    + ", primarySetupMs=" + primarySetupMs
                    + ", fallbackSetupMs=" + fallbackSetupMs
                    + ", totalSetupMs=" + (SystemClock.elapsedRealtime() - setupStartedAt));
            return fallbackReady;
        }

        Log.i(TAG, "Renderer setup succeeded: backend=" + currentRenderer.name()
                + ", setupMs=" + (SystemClock.elapsedRealtime() - setupStartedAt));
        return true;
    }

    /** Enable custom Vulkan driver (Turnip) usage. */
    public void overrideVulkanDriver() {
        if (LauncherPreferences.PREF_FREEDRENO_SYSMEM) environment.put("TU_DEBUG", "sysmem");
        if (LauncherPreferences.PREF_UBWC_WORKAROUND) environment.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
        MojoExec.setUseTurnip(true);
    }
}
