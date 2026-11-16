package net.kdt.pojavlaunch.game.renderer;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/**
 * Whether Performance Mode starts the game with MobileGlues instead of LTW.
 *
 * <p>LTW is the renderer Performance Mode stands for ({@link PerformanceRendererPolicy}). Some
 * players get a smoother game from MobileGlues, which the launcher does not carry itself: it comes
 * from a separate plugin app. So the swap is decided for each launch, from what is true at that
 * moment, and it only ever replaces LTW:
 *
 * <ul>
 *   <li>Performance Mode is on and the renderer about to start is LTW (a Vulkan or any other
 *       renderer the player chose is never swapped);</li>
 *   <li>the player has not switched the swap off in Settings;</li>
 *   <li>the last launch did not fail while starting (then only the built-in renderer is used);</li>
 *   <li>the plugin app is installed and this phone can run it;</li>
 *   <li>the game version is one MobileGlues supports (Minecraft 1.17 and newer).</li>
 * </ul>
 * When any of these is not true the game starts with LTW exactly as before, and the game log says
 * which one it was. Plain Java, so it can be tested without a device.
 */
public final class PerformanceMobileGlues {
    /** What to do for this launch, and a line for the game log that says why (null: not involved). */
    public static final class Decision {
        public final boolean useMobileGlues;
        public final String note;

        Decision(boolean useMobileGlues, String note) {
            this.useMobileGlues = useMobileGlues;
            this.note = note;
        }
    }

    private static final Decision NOT_INVOLVED = new Decision(false, null);

    private PerformanceMobileGlues() {}

    /**
     * Cheap check that comes first, so the plugin is only looked for when the answer can matter:
     * is this a launch the setting is about, and nothing already rules the swap out?
     */
    public static boolean isCandidate(boolean performanceMode, boolean wanted, boolean safeLaunch, String rendererTag) {
        return performanceMode && wanted && !safeLaunch && Renderers.LTW_RENDERER.equals(rendererTag);
    }

    public static Decision decide(boolean performanceMode, boolean wanted, boolean safeLaunch, String rendererTag,
                                  boolean pluginUsable, boolean versionSupported) {
        if (!performanceMode || !Renderers.LTW_RENDERER.equals(rendererTag)) return NOT_INVOLVED;
        if (!wanted) {
            return new Decision(false, "renderer: LTW (MobileGlues is switched off for Performance Mode in Settings)");
        }
        if (safeLaunch) {
            return new Decision(false, "renderer: LTW (the last launch did not start, so only the built-in renderer is used this time)");
        }
        if (!pluginUsable) {
            return new Decision(false, "renderer: LTW (the MobileGlues plugin app is not installed, or this phone cannot run it)");
        }
        if (!versionSupported) {
            return new Decision(false, "renderer: LTW (MobileGlues needs Minecraft 1.17 or newer)");
        }
        return new Decision(true, "renderer: MobileGlues (plugin found), instead of LTW");
    }
}
