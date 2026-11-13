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
 *       the first one that can run; ask before turning Performance Mode off (it pins the game to the
 *       OpenGL renderer LTW); say why when Vulkan cannot be used at all, instead of switching to
 *       something that will not start.</li>
 * </ul>
 * Works for every Minecraft version the renderers themselves support, which includes 1.21.4 and newer
 * (Zink: every version; LTW and the plugins: 1.17 and newer; GL4ES stops at 1.21.4 and the launcher
 * moves such a launch to LTW by itself, see GameRunner).
 */
public final class RendererApiPolicy {
    private RendererApiPolicy() {}

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
        /** Select {@link Result#rendererId}, but only after Performance Mode has been turned off. */
        TURN_OFF_PERFORMANCE_MODE,
        /** The phone does not report Vulkan support. */
        NO_VULKAN_HARDWARE,
        /** The phone has Vulkan, but no Vulkan renderer can start (the Mesa plugin app is missing). */
        NO_VULKAN_RENDERER,
        /** No OpenGL renderer can start (should not happen: GL4ES is built in). */
        NO_OPENGL_RENDERER
    }

    public static final class Result {
        public final Outcome outcome;
        /** The renderer to select; null unless the outcome is SELECT or TURN_OFF_PERFORMANCE_MODE. */
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
     * @param performanceMode whether Performance Mode (which pins the game to LTW) is on
     * @param vulkanHardware whether the phone reports Vulkan support
     */
    public static Result decideSwitch(GraphicsApi target, GraphicsApi current, List<Choice> all,
                                      String lastUsedWithTarget, boolean performanceMode, boolean vulkanHardware) {
        if (target == current) return new Result(Outcome.ALREADY, null);
        if (target == GraphicsApi.VULKAN) {
            if (!vulkanHardware) return new Result(Outcome.NO_VULKAN_HARDWARE, null);
            String id = pick(GraphicsApi.VULKAN, all, lastUsedWithTarget);
            if (id == null) return new Result(Outcome.NO_VULKAN_RENDERER, null);
            return new Result(performanceMode ? Outcome.TURN_OFF_PERFORMANCE_MODE : Outcome.SELECT, id);
        }
        String id = pick(GraphicsApi.OPENGL, all, lastUsedWithTarget);
        if (id == null) return new Result(Outcome.NO_OPENGL_RENDERER, null);
        return new Result(Outcome.SELECT, id);
    }

    /** "1.1" for the encoded Vulkan version a phone reports (major in the top bits), or null when unknown. */
    public static String formatVulkanVersion(int encoded) {
        if (encoded <= 0) return null;
        int major = encoded >>> 22;
        int minor = (encoded >>> 12) & 0x3FF;
        if (major == 0) return null;
        return major + "." + minor;
    }
}
