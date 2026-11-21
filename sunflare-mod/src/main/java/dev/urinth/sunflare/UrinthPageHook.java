package dev.urinth.sunflare;

import java.util.List;

/**
 * Called by the mixin each time Sodium makes its video settings screen: puts the URinth page into the screen's list of pages, right
 * after the first one, so it is visible without scrolling. Anything that goes wrong is logged and swallowed (the separate buttons
 * then come back, see {@link UrinthPageState}); the settings screen must open whatever happens here.
 */
public final class UrinthPageHook {
    private UrinthPageHook() {}

    /** @param pages Sodium's list of option pages of the screen that is being made */
    public static void addTo(List<Object> pages) {
        try {
            FlareConfig config = SunFlareMod.sharedConfig();
            if (config == null || pages == null) {
                UrinthPageState.setAdded(false);
                return;
            }
            Object page = UrinthPage.build(config);
            pages.add(Math.min(1, pages.size()), page);
            UrinthPageState.setAdded(true);
        } catch (Throwable t) {
            UrinthPageState.setAdded(false);
            SunFlareMod.LOG.warn("[SunFlare] could not add the URinth page to the video settings, the separate buttons are used", t);
        }
    }
}
