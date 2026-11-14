package net.kdt.pojavlaunch.game.renderer;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/**
 * How the Performance Mode switch drives the renderer.
 *
 * <p>Most of Performance Mode is about the phone, not about one renderer: the thermal guard, the
 * render-thread scheduling, the refresh-rate watch, the SurfaceView and the calmer memory collector
 * work the same with OpenGL and with Vulkan. Only the OpenGL side has a renderer of its own:
 *
 * <ul>
 *   <li>OpenGL, mode on: the list shows only the Performance Mode renderer (LTW), and it is the
 *       selected one.</li>
 *   <li>OpenGL, mode off: LTW is left out and every other OpenGL renderer is back.</li>
 *   <li>Vulkan (Zink, Legacy Zink): never touched by the mode. It is listed with the mode on or off,
 *       and turning the mode on keeps it selected.</li>
 * </ul>
 * The OpenGL renderer the player had chosen before turning the mode on is remembered and put back
 * when it is turned off. Plain Java, so it can be tested without a device.
 */
public final class PerformanceRendererPolicy {
    /** The built-in OpenGL renderer that Performance Mode stands for. */
    public static final String PERFORMANCE_RENDERER = Renderers.LTW_RENDERER;
    /** Used when nothing better is known to go back to. */
    public static final String FALLBACK_RENDERER = Renderers.GL4ES_RENDERER;

    private PerformanceRendererPolicy() {}

    /** A Vulkan renderer: Performance Mode leaves it alone. */
    public static boolean isVulkanRenderer(String rendererId) {
        return RendererApiPolicy.isVulkan(rendererId);
    }

    /** Should this renderer appear in the Settings list? */
    public static boolean isListed(String rendererId, boolean performanceMode) {
        if (isVulkanRenderer(rendererId)) return true;
        boolean isPerformance = PERFORMANCE_RENDERER.equals(rendererId);
        return performanceMode == isPerformance;
    }

    private static boolean isOrdinary(String rendererId) {
        return rendererId != null && !rendererId.isEmpty() && !PERFORMANCE_RENDERER.equals(rendererId);
    }

    /**
     * Does the stored renderer disagree with the mode, so that it has to be corrected? With the mode
     * on, anything except LTW and a Vulkan renderer disagrees; with the mode off, LTW does.
     */
    public static boolean needsSync(boolean performanceMode, String rendererId) {
        boolean isPerformance = PERFORMANCE_RENDERER.equals(rendererId);
        if (performanceMode) return !isPerformance && !isVulkanRenderer(rendererId);
        return isPerformance;
    }

    /** The renderer to select when the mode is turned on: a Vulkan renderer stays, anything else becomes LTW. */
    public static String selectionOnEnable(String current) {
        return isVulkanRenderer(current) ? current : PERFORMANCE_RENDERER;
    }

    /**
     * The renderer to remember when the mode is turned on: the OpenGL renderer in use now, unless
     * that already is Performance Mode or a Vulkan renderer (the mode keeps those), in which case
     * the previously remembered one stays.
     */
    public static String rememberedOnEnable(String current, String alreadyRemembered) {
        return isOrdinary(current) && !isVulkanRenderer(current) ? current : alreadyRemembered;
    }

    /** The renderer to select when the mode is turned off. A Vulkan renderer in use stays selected. */
    public static String selectionOnDisable(String current, String remembered) {
        if (isOrdinary(current)) return current;
        if (isOrdinary(remembered)) return remembered;
        return FALLBACK_RENDERER;
    }
}
