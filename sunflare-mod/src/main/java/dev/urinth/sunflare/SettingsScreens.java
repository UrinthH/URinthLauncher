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

    /** Slot 0 is the lens flare button, slot 1 the sun beams button. */
    static final int FLARE = 0;
    static final int BEAMS = 1;
    /** Slot 2 is the flare style (Real / Pack). */
    static final int STYLE = 2;
    /** Slot 3 is the cheaper Fancy leaves level. */
    static final int LEAVES = 3;

    /** {x, y, w, h} of a button. */
    static int[] bounds(Kind kind, int screenWidth, int slot) {
        if (kind == Kind.SODIUM) {
            // Sodium: tabs on the top row, the donate button at (width - 128, 6), options in a 240px column on the left and its
            // Undo/Apply/Done row along the bottom. The right-hand column just under the donate row is free.
            int w = screenWidth >= 380 ? 112 : 86;
            return new int[] {screenWidth - w - 6, 30 + 22 * slot, w, 20};
        }
        // Vanilla: the flare button top right, the beams button top left, both in the header row
        int w = screenWidth >= 320 ? 118 : 90;
        if (slot == FLARE) return new int[] {screenWidth - w - 4, 6, w, 20};
        // top left: beams at the corner; the style button next to it when there is room, else under it
        if (slot == LEAVES) return new int[] {screenWidth - w - 4, 28, w, 20};
        return slot == BEAMS ? new int[] {4, 6, w, 20} : new int[] {4, 28, w, 20};
    }

    static String label(int slot, int mode, int width) {
        boolean narrow = width < 100;
        if (slot == STYLE) return (narrow ? "Style: " : "Flare Style: ") + (mode == 0 ? "Real" : "Pack");
        if (slot == LEAVES) return (narrow ? "Leaves: " : "Fancy Leaves: ") + FlareConfig.LEAVES_NAMES[mode];
        if (slot == FLARE) return (narrow ? "Flare: " : "Sun Flare: ") + FlareConfig.NAMES[mode];
        return (narrow ? "Beams: " : "Sun Beams: ") + FlareConfig.BEAM_NAMES[mode];
    }
}
