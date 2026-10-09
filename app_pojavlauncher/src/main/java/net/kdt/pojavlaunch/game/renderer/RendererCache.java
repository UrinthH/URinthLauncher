package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.content.res.Resources;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.util.ArrayList;
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
    public static RendererCache getCompatibleRenderers(Context context) {
        if (sCompatibleRenderers != null) return sCompatibleRenderers;
        Resources resources = context.getResources();
        String[] renderers = Renderers.allRendererIds();
        ArrayList<String> rendererIds = new ArrayList<>(renderers.length);
        ArrayList<String> rendererNames = new ArrayList<>(renderers.length);
        for (String renderer : renderers) {
            RenderSpec spec = GameRenderer.getKnownRenderer(renderer);
            if (spec == null) {
                // A broken registry entry must not crash the Settings screen in release builds.
                android.util.Log.e("Renderer", "No RenderSpec registered for renderer ID: " + renderer);
                continue;
            }
            if (!spec.compatibleDevice(context)) continue;
            rendererIds.add(renderer);
            rendererNames.add(resources.getString(spec.displayName()));
        }
        rendererIds.trimToSize();
        rendererNames.trimToSize();
        return (sCompatibleRenderers = new RendererCache(
                rendererIds, rendererNames.toArray(new String[0])));
    }

    /** Destroy compatible renderers cache. Safe to call repeatedly. */
    public static void releaseRendererCache() {
        if (sCompatibleRenderers != null) {
            sCompatibleRenderers.rendererIds.clear();
            sCompatibleRenderers = null;
        }
    }
}
