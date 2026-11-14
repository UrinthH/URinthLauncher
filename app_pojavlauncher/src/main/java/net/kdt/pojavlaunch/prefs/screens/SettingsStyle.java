package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;

/**
 * Colours, the accent choices and the grouping rule of the Settings screen. Everything that is
 * only arithmetic lives here, away from the views, so it can be unit-tested without a device.
 */
public final class SettingsStyle {
    private SettingsStyle() {}

    static final String KEY_ACCENT = "ui_accent";
    static final String KEY_REDUCE_MOTION = "ui_reduce_motion";
    static final String KEY_COMPACT = "ui_compact";

    static final String DEFAULT_ACCENT = "emerald";
    /** Same order as {@link #ACCENT_COLORS} and as the entries of the accent setting. */
    static final String[] ACCENT_IDS = {"emerald", "ocean", "violet", "coral", "amber", "graphite"};
    static final int[] ACCENT_COLORS = {
            0xFF12A35E, // emerald
            0xFF2563EB, // ocean
            0xFF7C3AED, // violet
            0xFFE11D48, // coral
            0xFFB45309, // amber
            0xFF344054, // graphite
    };

    /** The page behind the cards. */
    static final int PAGE = 0xFFF6F8FA;
    static final int CARD = 0xFFFFFFFF;
    static final int BORDER = 0xFFE4E8EE;
    static final int DIVIDER = 0xFFEEF1F5;
    static final int TEXT = 0xFF101828;
    static final int TEXT_DISABLED = 0xFF98A2B3;
    static final int MUTED = 0xFF667085;
    static final int HEADING = 0xFF475467;
    static final int TRACK_OFF = 0xFF9AA4B2;
    static final int TRACK_BACKGROUND = 0xFFE4E8EE;
    static final int DANGER = 0xFFD92D20;

    /** Where a row sits inside its group of rows; decides which corners are rounded and whether a divider is drawn. */
    enum RowPosition { ALONE, FIRST, MIDDLE, LAST }

    static RowPosition rowPosition(boolean sameGroupAbove, boolean sameGroupBelow) {
        if (sameGroupAbove) return sameGroupBelow ? RowPosition.MIDDLE : RowPosition.LAST;
        return sameGroupBelow ? RowPosition.FIRST : RowPosition.ALONE;
    }

    static boolean hasTopEdge(RowPosition position) {
        return position == RowPosition.ALONE || position == RowPosition.FIRST;
    }

    static boolean hasBottomEdge(RowPosition position) {
        return position == RowPosition.ALONE || position == RowPosition.LAST;
    }

    /**
     * The eight corner radii (top-left, top-right, bottom-right, bottom-left, x and y each) of a row's card.
     * Only the outer corners of a group are rounded.
     */
    static float[] corners(RowPosition position, float radius) {
        float top = hasTopEdge(position) ? radius : 0f;
        float bottom = hasBottomEdge(position) ? radius : 0f;
        return new float[]{top, top, top, top, bottom, bottom, bottom, bottom};
    }

    /** The accent of a stored value; anything unknown gives the default. */
    static String accentIdOrDefault(String id) {
        if (id != null) {
            for (String known : ACCENT_IDS) {
                if (known.equals(id)) return id;
            }
        }
        return DEFAULT_ACCENT;
    }

    static int accentColor(String id) {
        String clean = accentIdOrDefault(id);
        for (int i = 0; i < ACCENT_IDS.length; i++) {
            if (ACCENT_IDS[i].equals(clean)) return ACCENT_COLORS[i];
        }
        return ACCENT_COLORS[0];
    }

    /** The accent colour chosen under Settings > Appearance (the default one when nothing is stored). */
    public static int accentColor(SharedPreferences prefs) {
        return accentColor(prefs == null ? null : prefs.getString(KEY_ACCENT, DEFAULT_ACCENT));
    }

    static boolean reduceMotion(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(KEY_REDUCE_MOTION, false);
    }

    static boolean compact(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(KEY_COMPACT, false);
    }

    /**
     * Extra margin for each side of the settings list that keeps the cards within {@code maxContent}
     * pixels (a phone held sideways, a tablet); 0 when the list is narrower than that.
     */
    static int extraSide(int listWidth, int sideways, int maxContent) {
        int content = listWidth - 2 * sideways;
        return content > maxContent ? (content - maxContent) / 2 : 0;
    }

    /** The colour with a new alpha (0 to 255), keeping its red, green and blue. */
    static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** {@code base} moved {@code amount} (0 to 1) of the way to {@code tint}; the result is opaque. */
    static int mix(int base, int tint, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(channel(base, 16) * (1 - t) + channel(tint, 16) * t);
        int g = Math.round(channel(base, 8) * (1 - t) + channel(tint, 8) * t);
        int b = Math.round(channel(base, 0) * (1 - t) + channel(tint, 0) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int channel(int color, int shift) {
        return (color >> shift) & 0xFF;
    }

    /** WCAG contrast ratio of two opaque colours (1 to 21). */
    static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(channel(color, 16)) + 0.7152 * linear(channel(color, 8)) + 0.0722 * linear(channel(color, 0));
    }

    private static double linear(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
