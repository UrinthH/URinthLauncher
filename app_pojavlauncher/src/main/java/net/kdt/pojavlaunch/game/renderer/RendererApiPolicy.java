package net.kdt.pojavlaunch.game.renderer;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rules behind the OpenGL / Vulkan switcher in Settings. Plain Java, so every rule can be tested
 * without a device.
 *
 * <ul>
 *   <li>Which API a renderer belongs to: Zink and Legacy Zink (and a renderer plugin that calls itself
 *       Vulkan or Zink) are Vulkan, everything else is OpenGL.</li>
 *   <li>What happens when the player taps the other API: pick the renderer used last with that API, or
 *       the first one that can run; say why when Vulkan cannot be used at all, instead of switching to
 *       something that will not start.</li>
 *   <li>Performance Mode works with both APIs: it is not turned off for Vulkan. With the mode on, the
 *       OpenGL side is its renderer LTW (see {@link PerformanceRendererPolicy}) and the Vulkan
 *       renderers stay available.</li>
 * </ul>
 * Works for every Minecraft version the renderers themselves support, which includes 1.21.4 and newer
 * (Zink: every version; LTW and the plugins: 1.17 and newer; GL4ES stops at 1.21.4 and the launcher
 * moves such a launch to LTW by itself, see GameRunner).
 */
public final class RendererApiPolicy {
    private RendererApiPolicy() {}

    /** The Vulkan renderers built into the launcher; they run on Mesa, which comes from a plugin app. */
    public static final String[] BUILT_IN_VULKAN = {Renderers.ZINK_RENDERER, Renderers.LEGACYZINK_RENDERER};

    /** Preference keys that remember the renderer used last with each API. */
    public static final String KEY_LAST_OPENGL = "renderer_last_opengl";
    public static final String KEY_LAST_VULKAN = "renderer_last_vulkan";

    /** One renderer that Settings can offer. */
    public static final class Choice {
        public final String id;
        public final String name;
        public final GraphicsApi api;
        /** False when the renderer is known but cannot start now (for example its plugin app is not installed). */
        public final boolean usable;

        public Choice(String id, String name, GraphicsApi api, boolean usable) {
            this.id = id;
            this.name = name;
            this.api = api;
            this.usable = usable;
        }
    }

    public enum Outcome {
        /** Nothing to do: that API is already the one in use. */
        ALREADY,
        /** Select {@link Result#rendererId}. */
        SELECT,
        /** The phone does not report Vulkan support. */
        NO_VULKAN_HARDWARE,
        /** The phone has Vulkan, but no Vulkan renderer can start (the Mesa plugin app is missing). */
        NO_VULKAN_RENDERER,
        /** No OpenGL renderer can start (should not happen: GL4ES is built in). */
        NO_OPENGL_RENDERER
    }

    public static final class Result {
        public final Outcome outcome;
        /** The renderer to select; null unless the outcome is SELECT. */
        public final String rendererId;

        Result(Outcome outcome, String rendererId) {
            this.outcome = outcome;
            this.rendererId = rendererId;
        }
    }

    /** Which API a renderer draws with. The label is only used for renderer plugin apps. */
    public static GraphicsApi apiOf(String rendererId, String label) {
        if (rendererId != null) {
            if (rendererId.equals(Renderers.ZINK_RENDERER) || rendererId.equals(Renderers.LEGACYZINK_RENDERER)) {
                return GraphicsApi.VULKAN;
            }
            if (rendererId.startsWith("vulkan")) return GraphicsApi.VULKAN;
        }
        if (mentionsVulkan(rendererId) || mentionsVulkan(label)) return GraphicsApi.VULKAN;
        return GraphicsApi.OPENGL;
    }

    /** Is this a Vulkan renderer (Zink, Legacy Zink, or a plugin whose id says Vulkan or Zink)? */
    public static boolean isVulkan(String rendererId) {
        return apiOf(rendererId, null) == GraphicsApi.VULKAN;
    }

    private static boolean mentionsVulkan(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("vulkan") || lower.contains("zink");
    }

    /** The choices of one API, in the order they are given. */
    public static List<Choice> of(GraphicsApi api, List<Choice> all) {
        ArrayList<Choice> out = new ArrayList<>();
        if (all == null) return out;
        for (Choice choice : all) {
            if (choice != null && choice.api == api) out.add(choice);
        }
        return out;
    }

    /** How many renderers of this API can start right now. */
    public static int usableCount(GraphicsApi api, List<Choice> all) {
        int count = 0;
        for (Choice choice : of(api, all)) {
            if (choice.usable) count++;
        }
        return count;
    }

    /** The renderer to use when switching to {@code api}: the last one used with it, else the first that can start. */
    public static String pick(GraphicsApi api, List<Choice> all, String lastUsedWithApi) {
        List<Choice> candidates = of(api, all);
        if (lastUsedWithApi != null) {
            for (Choice choice : candidates) {
                if (choice.usable && choice.id.equals(lastUsedWithApi)) return choice.id;
            }
        }
        for (Choice choice : candidates) {
            if (choice.usable) return choice.id;
        }
        return null;
    }

    /**
     * What to do when the player taps {@code target}.
     *
     * @param current the API of the renderer in use now
     * @param all every renderer Settings knows, usable or not
     * @param lastUsedWithTarget the renderer used last with {@code target}, or null
     * @param performanceMode whether Performance Mode is on: its OpenGL renderer is LTW, so that is the
     *                        only OpenGL one that can be picked; Vulkan renderers are not affected
     * @param vulkanHardware whether the phone reports Vulkan support
     */
    public static Result decideSwitch(GraphicsApi target, GraphicsApi current, List<Choice> all,
                                      String lastUsedWithTarget, boolean performanceMode, boolean vulkanHardware) {
        if (target == current) return new Result(Outcome.ALREADY, null);
        List<Choice> offered = performanceMode ? listedWithPerformanceMode(all) : all;
        if (target == GraphicsApi.VULKAN) {
            if (!vulkanHardware) return new Result(Outcome.NO_VULKAN_HARDWARE, null);
            String id = pick(GraphicsApi.VULKAN, offered, lastUsedWithTarget);
            if (id == null) return new Result(Outcome.NO_VULKAN_RENDERER, null);
            return new Result(Outcome.SELECT, id);
        }
        String id = pick(GraphicsApi.OPENGL, offered, lastUsedWithTarget);
        if (id == null) return new Result(Outcome.NO_OPENGL_RENDERER, null);
        return new Result(Outcome.SELECT, id);
    }

    /** The choices Performance Mode lets through: its own renderer (LTW) and the Vulkan renderers. */
    private static List<Choice> listedWithPerformanceMode(List<Choice> all) {
        ArrayList<Choice> out = new ArrayList<>();
        if (all == null) return out;
        for (Choice choice : all) {
            if (choice != null && PerformanceRendererPolicy.isListed(choice.id, true)) out.add(choice);
        }
        return out;
    }

    /** "1.1" for the encoded Vulkan version a phone reports (major in the top bits), or null when unknown. */
    public static String formatVulkanVersion(int encoded) {
        if (encoded <= 0) return null;
        int major = encoded >>> 22;
        int minor = (encoded >>> 12) & 0x3FF;
        if (major == 0) return null;
        return major + "." + minor;
    }
    /** A renderer's name without the notes in its display name: "Zink (Vulkan) - all versions" gives "Zink". */
    public static String shortName(String name) {
        if (name == null) return "";
        int cut = name.length();
        int paren = name.indexOf(" (");
        if (paren > 0) cut = Math.min(cut, paren);
        int dash = name.indexOf(" - ");
        if (dash > 0) cut = Math.min(cut, dash);
        return name.substring(0, cut).trim();
    }
}
