package dev.urinth.sunflare;

import java.util.Locale;

/**
 * How many helper threads Sodium builds chunks with. Sodium picks {@code max(cores / 3, cores - 6)}, which is 2 on an 8-core phone,
 * so new chunks (a new area, a teleport, a world that has just loaded) appear a little at a time. This offers more helpers: a fixed
 * number, or Auto, which is Sodium's own number but at least {@link #AUTO_MINIMUM} on a phone with {@link #AUTO_MIN_CORES} cores or
 * more. The helpers run at a low priority, so they give way to the game's own threads when the phone is busy.
 *
 * <p>Plain logic, unit tested. The mixin asks it for the number when Sodium creates its chunk builder, which is also why changing it
 * reloads the world renderer.
 */
public final class ChunkThreads {
    private ChunkThreads() {}

    /** The setting that leaves the choice to Sodium (or to Auto's minimum). */
    public static final int AUTO = 0;
    /** The settings the page offers, in the order of its choices. */
    public static final int[] CHOICES = {AUTO, 2, 3, 4, 6};
    /** What Auto uses on a phone with many cores, if Sodium's own number is smaller. */
    public static final int AUTO_MINIMUM = 3;
    /** Phones with fewer cores than this keep Sodium's number. */
    public static final int AUTO_MIN_CORES = 6;

    private static volatile int setting = AUTO;

    public static int setting() {
        return setting;
    }

    public static void setSetting(int value) {
        setting = nearest(value);
    }

    /** The choice closest to a number: 0 or less is {@link #AUTO}; between two choices the smaller one wins. */
    public static int nearest(int value) {
        if (value <= 0) return AUTO;
        int best = CHOICES[1];
        for (int i = 1; i < CHOICES.length; i++) {
            if (Math.abs(CHOICES[i] - value) < Math.abs(best - value)) best = CHOICES[i];
        }
        return best;
    }

    /** What Auto means: Sodium's number, raised to {@link #AUTO_MINIMUM} (but at most half the cores) on a phone with many cores. */
    public static int autoCount(int sodiumsNumber, int cores) {
        if (cores < AUTO_MIN_CORES) return sodiumsNumber;
        return Math.max(sodiumsNumber, Math.min(AUTO_MINIMUM, cores / 2));
    }

    /** The number Sodium should use when the player chose a fixed one (never more than there are cores), or 0 for no say. */
    public static int fixedCount(int setting, int cores) {
        if (setting == AUTO || setting < 0) return 0;
        return Math.max(1, Math.min(setting, cores));
    }

    /** "Auto", "3": the text of a choice. */
    public static String label(int value) {
        return value == AUTO ? "Auto" : Integer.toString(value);
    }

    /** "auto" or the number, as the settings file keeps it. */
    public static String toName(int value) {
        return value == AUTO ? "auto" : Integer.toString(value);
    }

    /** The setting for a text of the settings file: "auto", empty or anything else that is not a number is {@link #AUTO}. */
    public static int fromName(String name) {
        if (name == null) return AUTO;
        String text = name.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("auto")) return AUTO;
        try {
            return nearest(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return AUTO;
        }
    }
}
