package net.kdt.pojavlaunch.game.renderer;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/**
 * How the Performance Mode switch on the home screen drives the renderer list in Settings.
 *
 * <ul>
 *   <li>Mode on: the list shows only the Performance Mode renderer, and it is the selected one.</li>
 *   <li>Mode off: the Performance Mode renderer is left out and every other renderer is back.</li>
 * </ul>
 * The renderer the player had chosen before turning the mode on is remembered and put back when
 * it is turned off. Plain Java, so it can be tested without a device.
 */
public final class PerformanceRendererPolicy {
    /** The built-in renderer that Performance Mode stands for. */
    public static final String PERFORMANCE_RENDERER = Renderers.LTW_RENDERER;
    /** Used when nothing better is known to go back to. */
    public static final String FALLBACK_RENDERER = Renderers.GL4ES_RENDERER;

    private PerformanceRendererPolicy() {}

    /** Should this renderer appear in the Settings list? */
    public static boolean isListed(String rendererId, boolean performanceMode) {
        boolean isPerformance = PERFORMANCE_RENDERER.equals(rendererId);
        return performanceMode == isPerformance;
    }

    private static boolean isOrdinary(String rendererId) {
        return rendererId != null && !rendererId.isEmpty() && !PERFORMANCE_RENDERER.equals(rendererId);
    }

    /**
     * The renderer to remember when the mode is turned on: the one in use now, unless that already
     * is Performance Mode, in which case the previously remembered one stays.
     */
    public static String rememberedOnEnable(String current, String alreadyRemembered) {
        return isOrdinary(current) ? current : alreadyRemembered;
    }

    /** The renderer to select when the mode is turned off. */
    public static String selectionOnDisable(String current, String remembered) {
        if (isOrdinary(current)) return current;
        if (isOrdinary(remembered)) return remembered;
        return FALLBACK_RENDERER;
    }
}
