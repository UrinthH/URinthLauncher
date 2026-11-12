package dev.urinth.boost;

import java.util.Locale;

/**
 * What the player chose with /uboost. AUTO lets the {@link Governor} pick the level from the frame rate; the others
 * hold one level all the time.
 */
public enum BoostMode {
    AUTO("auto", -1),
    OFF("off", BoostLevel.OFF),
    LIGHT("light", BoostLevel.LIGHT),
    STRONG("strong", BoostLevel.STRONG),
    MAX("max", BoostLevel.MAX);

    private final String word;
    private final int fixedLevel;

    BoostMode(String word, int fixedLevel) {
        this.word = word;
        this.fixedLevel = fixedLevel;
    }

    /** The word the player types and the settings file holds. */
    public String word() {
        return word;
    }

    public boolean isAuto() {
        return fixedLevel < 0;
    }

    /** The level held in this mode; only meaningful when {@link #isAuto()} is false. */
    public int fixedLevel() {
        return fixedLevel;
    }

    /** The mode a word names (any case, spaces around it ignored), or null when it names none. */
    public static BoostMode parse(String text) {
        if (text == null) return null;
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        for (BoostMode mode : values()) {
            if (mode.word.equals(trimmed)) return mode;
        }
        return null;
    }

    public static BoostMode parseOr(String text, BoostMode fallback) {
        BoostMode mode = parse(text);
        return mode == null ? fallback : mode;
    }
}
