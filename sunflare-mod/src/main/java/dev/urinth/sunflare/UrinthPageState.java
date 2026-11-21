package dev.urinth.sunflare;

/**
 * Whether the URinth page was added to the Sodium video settings the last time that screen was made. While it was, the separate
 * Sun Flare buttons stay away (they are the rows of the page); when it was not (a Sodium that changed, an error) the buttons come
 * back, so the settings are never out of reach. Pure logic, unit tested.
 */
final class UrinthPageState {
    private static volatile boolean added;

    private UrinthPageState() {}

    static void setAdded(boolean value) {
        added = value;
    }

    static boolean isAdded() {
        return added;
    }

    /** True when a screen of this kind should get the separate buttons. */
    static boolean needsButtons(SettingsScreens.Kind kind) {
        if (kind == SettingsScreens.Kind.NONE) return false;
        return !(kind == SettingsScreens.Kind.SODIUM && added);
    }
}
