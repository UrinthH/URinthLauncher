package net.kdt.pojavlaunch.game.renderer;

import static net.kdt.pojavlaunch.game.renderer.def.Renderers.FREEDRENO_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.GL4ES_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.KRYPTON_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.LEGACYZINK_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.LTW_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.MESA_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.MESA_RENDERER_EXT;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.MOBILEGLUES_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.ZINK_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.VIRGL_RENDERER;
import static net.kdt.pojavlaunch.game.renderer.def.Renderers.PANFROST_RENDERER;

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
    private Context rendererContext;
    private final Map<String, String> originalRendererEnvironment = new HashMap<>();

    public GameRenderer(String currentRenderer) {
        this.currentRenderer = getKnownRenderer(currentRenderer);
        if (this.currentRenderer == null) this.currentRenderer = getKnownRenderer(GL4ES_RENDERER);
        if (this.currentRenderer == null) throw new IllegalStateException("Failed to create the current renderer!");
    }

    /** Map renderer string to a known RenderSpec. */
    public static RenderSpec getKnownRenderer(String renderer) {
        if (renderer == null) {
            logRendererFailure("Unknown renderer null", null);
            return null;
        }
        switch (renderer) {
            case "opengles2_4":
            case "opengles2_5":
            case GL4ES_RENDERER: return new GLESRenderSpec.GL4ESRenderSpec();
            case LTW_RENDERER: return new GLESRenderSpec.LTWRenderSpec();
            case KRYPTON_RENDERER: return new GLESRenderSpec.KryptonRenderSpec();
            case ZINK_RENDERER: return new MesaRenderSpec.ZinkRenderSpec();
            case VIRGL_RENDERER: return new MesaRenderSpec.VirGLRenderSpec();
            case PANFROST_RENDERER: return new MesaRenderSpec.PanfrostRenderSpec();
            case FREEDRENO_RENDERER: return new MesaRenderSpec.FreedrenoRenderSpec();
            case MESA_RENDERER: return new MesaRenderSpec();
            case MESA_RENDERER_EXT: return new MesaRenderSpec.ExtMesaRenderSpec();
            case LEGACYZINK_RENDERER: return new MesaRenderSpec.LegacyZinkRenderSpec();
            case MOBILEGLUES_RENDERER: return new MobileGluesRenderSpec();
            default:
                logRendererFailure("Unknown renderer " + renderer, null);
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
     * Prepare the renderer selected in Settings. The boolean overload remains for callers
     * that still pass a version compatibility flag; selection itself is controlled by Settings.
     */
    public void setupEnvironment(Context context, boolean allowMobileGlues) throws ErrnoException {
        rendererContext = context;
        if (MOBILEGLUES_RENDERER.equals(currentRenderer.tag()) && !allowMobileGlues) {
            throw new IllegalStateException("MobileGlues requires Minecraft 1.17 or newer");
        }
        if (environment == null) {
            Log.w(TAG, "Tried to call setupEnvironment in already initialized environment");
            return;
        }

        // A GameRenderer can be reused when the user exits and launches another instance.
        // Restore only URinth-owned variables before configuring the newly selected backend,
        // so Ultra OFF cannot inherit overrides from a previous Ultra ON launch.
        URinthRender.restoreNormalEnvironment();

        // Ultra is a launch-time wrapper around the selected backend, not a fake native driver.
        // Keep the underlying tag/library intact so capability checks and fallback remain correct.
        if (URinthRender.isUltraEnabled(context)) {
            if (!(currentRenderer instanceof UrinthUltraWrapperRenderSpec)) {
                currentRenderer = new UrinthUltraWrapperRenderSpec(currentRenderer);
            }
        } else if (currentRenderer instanceof UrinthUltraWrapperRenderSpec) {
            // If this instance is reused after the toggle changes, OFF must remove the
            // wrapper identity as well as restoring the profile environment variables.
            currentRenderer = ((UrinthUltraWrapperRenderSpec) currentRenderer).getDelegate();
        }

        try {
            configureRendererEnvironment(context, environment);
        } catch (RuntimeException error) {
            configureGl4esEnvironmentAfterFailure(context, error);
        } catch (LinkageError error) {
            configureGl4esEnvironmentAfterFailure(context, error);
        }

        environment.clear();
        environment = null;
    }

    private void configureRendererEnvironment(Context context, Map<String, String> values)
            throws ErrnoException {
        // Respect the renderer selected in Settings; do not silently swap backends.
        currentRenderer.setupEnvironment(context, values);
        // Apply the opt-in profile to the exact environment map exported to the game process.
        URinthRender.applyProfile(context, currentRenderer, values);
        applyEnvironmentMap(values);
    }

    /**
     * If backend-specific environment setup fails before native initialization, switch to the
     * known GL4ES fallback and configure its own variables rather than aborting the launch.
     */
    private void configureGl4esEnvironmentAfterFailure(Context context, Throwable primaryError)
            throws ErrnoException {
        String failedName = currentRenderer == null ? "<null>" : currentRenderer.name();
        logRendererFailure("Renderer environment setup failed for " + failedName
                + "; attempting GL4ES fallback", primaryError);

        RenderSpec fallback = getKnownRenderer(FALLBACK_RENDERER);
        if (fallback == null) {
            throw new IllegalStateException("Renderer environment setup failed and GL4ES fallback is unavailable",
                    primaryError);
        }

        boolean keepUltraWrapper = currentRenderer instanceof UrinthUltraWrapperRenderSpec;
        restorePreviousRendererEnvironment();
        URinthRender.restoreNormalEnvironment();
        currentRenderer = keepUltraWrapper ? new UrinthUltraWrapperRenderSpec(fallback) : fallback;
        Map<String, String> fallbackEnvironment = new HashMap<>();
        try {
            configureRendererEnvironment(context, fallbackEnvironment);
        } catch (RuntimeException fallbackError) {
            fallbackError.addSuppressed(primaryError);
            throw new IllegalStateException("GL4ES fallback environment setup also failed", fallbackError);
        } catch (LinkageError fallbackError) {
            fallbackError.addSuppressed(primaryError);
            throw new IllegalStateException("GL4ES fallback environment setup also failed", fallbackError);
        }
    }

    /**
     * Export renderer/profile variables and retain the prior values so a failed backend can
     * be replaced without leaving its renderer-specific environment behind.
     */
    private void applyEnvironmentMap(Map<String, String> values) throws ErrnoException {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (!originalRendererEnvironment.containsKey(key)) {
                originalRendererEnvironment.put(key, Os.getenv(key));
            }
            Logger.appendToLog("Added renderer env: " + key + "=" + e.getValue());
            Os.setenv(key, e.getValue(), true);

            // This checks launcher-side application only; it cannot prove a native library
            // consumes the variable or that the setting improves frame rate.
            String actual = Os.getenv(key);
            boolean matches = e.getValue().equals(actual);
            logRendererInfo("Renderer env verification: key=" + key
                    + ", expected=" + e.getValue()
                    + ", actual=" + (actual == null ? "<unset>" : actual)
                    + ", matches=" + matches);
            if (!matches) {
                logRendererFailure("Renderer environment read-back mismatch for " + key, null);
            }
        }
    }

    private void restorePreviousRendererEnvironment() {
        for (Map.Entry<String, String> entry : originalRendererEnvironment.entrySet()) {
            try {
                if (entry.getValue() == null) {
                    Os.unsetenv(entry.getKey());
                } else {
                    Os.setenv(entry.getKey(), entry.getValue(), true);
                }
            } catch (ErrnoException error) {
                logRendererFailure("Could not restore renderer environment key " + entry.getKey(), error);
            }
        }
        originalRendererEnvironment.clear();
    }

    /** Get current selected renderer. */
    public RenderSpec getCurrentRenderer() {
        return currentRenderer;
    }

    /** Set renderer before setupEnvironment. */
    public void setCurrentRenderer(RenderSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Renderer spec must not be null");
        }
        logRendererInfo("Replacing current renderer with: " + spec.name());
        currentRenderer = spec;
    }

    /** Set renderer before setupEnvironment. */
    public void setCurrentRenderer(String renderer) throws IllegalArgumentException {
        RenderSpec spec = getKnownRenderer(renderer);
        if (spec == null) throw new IllegalArgumentException("Invalid renderer string" + renderer + "!");
        this.setCurrentRenderer(spec);
    }

    /**
     * Native renderer initialization can fail with either a Java exception or a
     * linkage error when an optional plugin/library is missing or incompatible.
     * Convert those failures into a normal setup failure so fallback can run.
     */
    /**
     * Android logging may be unavailable in plain JVM unit tests. Diagnostics
     * must never break renderer fallback when the platform logger is unavailable.
     */
    private static void logRendererFailure(String message, Throwable error) {
        try {
            if (error == null) Log.e(TAG, message);
            else Log.e(TAG, message, error);
        } catch (RuntimeException ignored) {
            // Android framework logging is not mocked in local JVM tests.
        }
    }

    private static void logRendererInfo(String message) {
        try {
            Log.i(TAG, message);
        } catch (RuntimeException ignored) {
            // Diagnostics must never interfere with backend selection.
        }
    }

    static boolean setupRendererSafely(RenderSpec renderer) {
        if (renderer == null) return false;
        try {
            return renderer.setupRenderer();
        } catch (RuntimeException error) {
            logRendererFailure("Renderer setup threw an exception: backend=" + renderer.name(), error);
            return false;
        } catch (LinkageError error) {
            logRendererFailure("Renderer native linkage failed: backend=" + renderer.name(), error);
            return false;
        }
    }

    /**
     * Resolve the selected backend library path and initialize it safely. Optional
     * plugin metadata can become stale between Settings and launch, so failures
     * here must also allow the normal fallback path to run.
     */
    static boolean prepareRendererSafely(RenderSpec renderer) {
        if (renderer == null) return false;
        try {
            setRendererLibraryPath(Tools.NATIVE_LIB_DIR, renderer.librarySearchPath());
            return setupRendererSafely(renderer);
        } catch (RuntimeException error) {
            logRendererFailure("Renderer preparation failed before native setup: backend="
                    + renderer.name(), error);
            return false;
        } catch (LinkageError error) {
            logRendererFailure("Renderer preparation hit a native linkage error: backend="
                    + renderer.name(), error);
            return false;
        }
    }

    /** Set up current renderer or fall back to GL4ES if setup fails. */
    public boolean maybeSetupRenderer() {
        final String requestedBackend = currentRenderer.name();
        final long setupStartedAt = SystemClock.elapsedRealtime();
        if (!prepareRendererSafely(currentRenderer)) {
            final long primarySetupMs = SystemClock.elapsedRealtime() - setupStartedAt;
            logRendererFailure("Renderer setup failed: backend=" + requestedBackend
                    + ", elapsedMs=" + primarySetupMs
                    + ", falling back to " + FALLBACK_RENDERER, null);

            RenderSpec fallback = getKnownRenderer(FALLBACK_RENDERER);
            if (fallback == null) {
                logRendererFailure("Renderer fallback is unavailable; primarySetupMs=" + primarySetupMs, null);
                return false;
            }

            if (currentRenderer instanceof UrinthUltraWrapperRenderSpec) {
                currentRenderer = new UrinthUltraWrapperRenderSpec(fallback);
            } else {
                currentRenderer = fallback;
            }
            final long fallbackStartedAt = SystemClock.elapsedRealtime();
            boolean fallbackReady = false;
            try {
                // Restore values injected by the failed renderer before preparing GL4ES.
                // Then construct and apply the fallback's own environment from scratch.
                restorePreviousRendererEnvironment();
                URinthRender.restoreNormalEnvironment();
                if (rendererContext != null) {
                    Map<String, String> fallbackEnvironment = new HashMap<>();
                    currentRenderer.setupEnvironment(rendererContext, fallbackEnvironment);
                    URinthRender.applyProfile(rendererContext, currentRenderer, fallbackEnvironment);
                    applyEnvironmentMap(fallbackEnvironment);
                }
                fallbackReady = prepareRendererSafely(currentRenderer);
            } catch (ErrnoException error) {
                logRendererFailure("Could not apply fallback renderer environment", error);
            } catch (RuntimeException error) {
                logRendererFailure("Fallback renderer environment setup failed", error);
            } catch (LinkageError error) {
                logRendererFailure("Fallback renderer environment hit a native linkage error", error);
            }
            final long fallbackSetupMs = SystemClock.elapsedRealtime() - fallbackStartedAt;
            logRendererInfo("Renderer fallback result: backend=" + currentRenderer.name()
                    + ", ready=" + fallbackReady
                    + ", primarySetupMs=" + primarySetupMs
                    + ", fallbackSetupMs=" + fallbackSetupMs
                    + ", totalSetupMs=" + (SystemClock.elapsedRealtime() - setupStartedAt));
            return fallbackReady;
        }

        logRendererInfo("Renderer setup succeeded: backend=" + currentRenderer.name()
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
