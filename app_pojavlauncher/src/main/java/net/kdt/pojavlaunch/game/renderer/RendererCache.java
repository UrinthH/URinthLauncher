package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.content.res.Resources;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.game.renderer.impl.ApkRenderSpec;
import net.kdt.pojavlaunch.game.renderer.impl.ApkRendererScanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compatible renderers cache. Used for the UI renderer list.
 */
public class RendererCache {
    private static RendererCache sCompatibleRenderers;

    public final List<String> rendererIds;
    public final String[] rendererDisplayNames;

    public RendererCache(List<String> rendererIds, String[] rendererDisplayNames) {
        this.rendererIds = rendererIds;
        this.rendererDisplayNames = rendererDisplayNames;
    }

    /**
     * Return renderers compatible with the current device.
     * Don't forget to clean the cache when the list isn't needed anymore.
     *
     * @param context application context
     * @return cache containing all compatible renderers
     */
    public static synchronized RendererCache getCompatibleRenderers(Context context) {
        Resources resources = context.getResources();
        // Renderer plugin apps installed now are listed too; a plugin uninstalled later is dropped.
        ApkRendererScanner.scan(context);
        String[] renderers = Renderers.settingsRendererIds();
        ArrayList<String> rendererIds = new ArrayList<>(renderers.length);
        ArrayList<String> rendererNames = new ArrayList<>(renderers.length);
        for (String renderer : renderers) {
            RenderSpec spec = GameRenderer.getKnownRenderer(renderer);
            if (spec == null) {
                // Skip only invalid registry entries; never silently hide a registered backend.
                logProbeFailure("No RenderSpec registered for renderer ID: " + renderer, null);
                continue;
            }

            // Keep every registered renderer visible in Settings with its plain name. Some
            // backends need optional native libraries/plugins that may be absent; if one is
            // selected without its library, the launcher logs it and falls back to GL4ES.
            if (!isCompatibleSafely(spec, context)) {
                logProbeNote("Renderer listed but not currently usable on this device: "
                        + spec.name() + " (" + renderer + ")");
            }
            rendererIds.add(renderer);
            rendererNames.add(resources.getString(spec.displayName()));
        }
        for (ApkRenderSpec plugin : ApkRendererScanner.installed()) {
            rendererIds.add(plugin.tag());
            rendererNames.add(plugin.label());
        }
        rendererIds.trimToSize();
        rendererNames.trimToSize();
        String[] displayNames = rendererNames.toArray(new String[0]);
        // Rebuild only when installed plugins or their labels changed. This lets a plugin
        // installed while the launcher is open appear without requiring a process restart.
        if (sCompatibleRenderers != null
                && sCompatibleRenderers.rendererIds.equals(rendererIds)
                && Arrays.equals(sCompatibleRenderers.rendererDisplayNames, displayNames)) {
            return sCompatibleRenderers;
        }
        if (sCompatibleRenderers != null) {
            sCompatibleRenderers.rendererIds.clear();
        }
        return (sCompatibleRenderers = new RendererCache(rendererIds, displayNames));
    }

    /**
     * A broken optional renderer/plugin must not crash the Settings screen or hide
     * every other backend. Treat probe exceptions and native linkage failures as
     * an incompatible renderer and leave a diagnostic in the log.
     */
    private static void logProbeFailure(String message, Throwable error) {
        try {
            android.util.Log.e("Renderer", message, error);
        } catch (RuntimeException ignored) {
            // Platform logging is unavailable in plain JVM unit tests.
        }
    }

    private static void logProbeNote(String message) {
        try {
            android.util.Log.i("Renderer", message);
        } catch (RuntimeException ignored) {
            // Platform logging is unavailable in plain JVM unit tests.
        }
    }

    static boolean isCompatibleSafely(RenderSpec spec, Context context) {
        if (spec == null) return false;
        try {
            return spec.compatibleDevice(context);
        } catch (RuntimeException error) {
            logProbeFailure("Renderer compatibility probe failed: " + spec.name(), error);
            return false;
        } catch (LinkageError error) {
            logProbeFailure("Renderer compatibility probe hit a native linkage error: "
                    + spec.name(), error);
            return false;
        }
    }

    /** Destroy compatible renderers cache. Safe to call repeatedly. */
    public static void releaseRendererCache() {
        if (sCompatibleRenderers != null) {
            sCompatibleRenderers.rendererIds.clear();
            sCompatibleRenderers = null;
        }
    }
}
