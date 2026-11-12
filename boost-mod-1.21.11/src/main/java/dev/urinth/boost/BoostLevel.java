package dev.urinth.boost;

/**
 * The four steps of shedding. Level 0 is the game exactly as it is: nothing is taken away. Each step above it
 * takes away a little more of what costs the most frames in a crowd and is the least missed (particles, far items,
 * far shadows). See {@link Limits} for the numbers.
 */
public final class BoostLevel {
    public static final int OFF = 0;
    public static final int LIGHT = 1;
    public static final int STRONG = 2;
    public static final int MAX = 3;

    private BoostLevel() {}

    public static int clamp(int level) {
        return Math.max(OFF, Math.min(MAX, level));
    }

    public static String name(int level) {
        switch (clamp(level)) {
            case OFF:
                return "off";
            case LIGHT:
                return "light";
            case STRONG:
                return "strong";
            default:
                return "max";
        }
    }
}
