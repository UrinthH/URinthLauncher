package dev.urinth.sunflare;

import java.util.Locale;

/**
 * Which video-settings screens get the Sun Flare button, and where the button goes. Pure logic (no Minecraft classes) so it
 * is unit tested. Sodium replaces the vanilla Video Settings screen with its own, so both are handled.
 */
final class SettingsScreens {
    enum Kind { NONE, VANILLA, SODIUM }

    static final String VANILLA_CLASS = "net.minecraft.client.gui.screens.options.VideoSettingsScreen";

    private SettingsScreens() {}

    /** Classifies a screen by its class name, so there is no compile-time dependency on Sodium. */
    static Kind classify(String className) {
        if (className == null) return Kind.NONE;
        if (className.equals(VANILLA_CLASS)) return Kind.VANILLA;
        String n = className.toLowerCase(Locale.ROOT);
        // Sodium's own screen, and Sodium-family screens such as Reese's Sodium Options.
        if (n.endsWith(".sodiumoptionsgui")) return Kind.SODIUM;
        if (!n.contains("sodium")) return Kind.NONE;
        for (String bad : new String[] {"corrupt", "prompt", "widget", "button", "page", "control", "tooltip"}) {
            if (n.contains(bad)) return Kind.NONE;
        }
        return n.contains("option") || n.contains("video") || n.contains("setting") ? Kind.SODIUM : Kind.NONE;
    }

    /** {x, y, w, h} of the button. */
    static int[] bounds(Kind kind, int screenWidth) {
        boolean wide = screenWidth >= 320;
        int w = wide ? 118 : 90;
        if (kind == Kind.SODIUM) {
            // Sodium: tabs on the top row, the donate button at (width - 128, 6), options in a 240px column on the left and its
            // Undo/Apply/Done row along the bottom. The right-hand column just under the donate row is free.
            w = screenWidth >= 380 ? 112 : 86;
            return new int[] {screenWidth - w - 6, 30, w, 20};
        }
        return new int[] {screenWidth - w - 4, 6, w, 20};
    }

    static String label(int mode, int width) {
        return (width < 100 ? "Flare: " : "Sun Flare: ") + FlareConfig.NAMES[mode];
    }
}
