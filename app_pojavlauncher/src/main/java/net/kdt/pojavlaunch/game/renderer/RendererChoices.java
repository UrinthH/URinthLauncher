package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;

import net.kdt.pojavlaunch.game.renderer.RendererApiPolicy.Choice;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every renderer the Settings screen can talk about, each with the graphics API it belongs to and
 * whether it can start on this phone. The list is the one the renderer setting has always used
 * (GL4ES, LTW and the installed renderer plugin apps) plus the Vulkan renderers, which have never
 * been listed there: they are needed to tell the player why Vulkan can or cannot be used.
 *
 * The rules that decide what a tap on OpenGL or Vulkan does are in {@link RendererApiPolicy}.
 */
public final class RendererChoices {
    /** Every renderer, usable or not, in display order. */
    public final List<Choice> all;

    private RendererChoices(List<Choice> all) {
        this.all = all;
    }

    /**
     * @param performanceMode whether Performance Mode is on: its OpenGL renderer is LTW, so LTW can only
     *                        be selected while it is on (selecting it with the mode off would be
     *                        undone by {@code LauncherPreferences.syncRendererWithPerformanceMode}).
     *                        Vulkan renderers do not depend on the mode.
     */
    public static RendererChoices load(Context context, boolean performanceMode) {
        RendererCache cache = RendererCache.getCompatibleRenderers(context);
        List<String> registered = Arrays.asList(Renderers.allRendererIds());
        ArrayList<Choice> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cache.rendererIds.size() && i < cache.rendererDisplayNames.length; i++) {
            String id = cache.rendererIds.get(i);
            String name = cache.rendererDisplayNames[i];
            if (id == null || !seen.add(id)) continue;
            // A renderer plugin app is listed only while it is installed, so it counts as usable here.
            boolean usable = !registered.contains(id)
                    || RendererCache.isCompatibleSafely(GameRenderer.getKnownRenderer(id), context);
            if (PerformanceRendererPolicy.PERFORMANCE_RENDERER.equals(id) && !performanceMode) usable = false;
            out.add(new Choice(id, name, RendererApiPolicy.apiOf(id, name), usable));
        }
        for (String id : RendererApiPolicy.BUILT_IN_VULKAN) {
            if (!seen.add(id)) continue;
            RenderSpec spec = GameRenderer.getKnownRenderer(id);
            if (spec == null) continue;
            out.add(new Choice(id, context.getResources().getString(spec.displayName()),
                    GraphicsApi.VULKAN, RendererCache.isCompatibleSafely(spec, context)));
        }
        return new RendererChoices(out);
    }

    public Choice find(String id) {
        if (id == null) return null;
        for (Choice choice : all) {
            if (choice.id.equals(id)) return choice;
        }
        return null;
    }

    /** The name shown for a renderer; the id itself when it is not known (for example a plugin that was removed). */
    public String nameOf(String id) {
        Choice choice = find(id);
        return choice == null ? String.valueOf(id) : choice.name;
    }

    /** The API of the renderer in use; an unknown renderer counts as OpenGL, like the launcher's own fallback (GL4ES). */
    public GraphicsApi apiOf(String id) {
        Choice choice = find(id);
        return choice == null ? RendererApiPolicy.apiOf(id, null) : choice.api;
    }

    /**
     * What the renderer setting lists while {@code api} is selected. For OpenGL the Performance Mode
     * rule is the one the renderer setting has always followed: with the mode on only LTW is offered,
     * with it off LTW is left out. The Vulkan renderers are listed with the mode on or off.
     */
    public List<Choice> listed(GraphicsApi api, boolean performanceMode) {
        ArrayList<Choice> out = new ArrayList<>();
        for (Choice choice : RendererApiPolicy.of(api, all)) {
            if (PerformanceRendererPolicy.isListed(choice.id, performanceMode)) out.add(choice);
        }
        return out;
    }
}
