package net.kdt.pojavlaunch.game;

import java.util.List;

/**
 * The decision part of {@link RefreshRateBoost}, with no Android code in it so it can be tested on
 * a plain JVM: which screen mode to ask for, and when to ask for it again.
 *
 * Only modes with the same physical size as the current one are ever considered, so the request
 * can change how often the screen refreshes and nothing else. It never touches the game's render
 * resolution or options.txt.
 */
public final class RefreshRatePolicy {
    /** "Auto" asks for the highest rate up to this one. */
    static final float AUTO_MAX_HZ = 120f;
    /** Rates are reported with fractions (59.99, 90.0001): closer than this means the same rate. */
    static final float SAME_RATE = 0.5f;

    private RefreshRatePolicy() {}

    /**
     * The limit as the Settings list stores it ("0" is Auto) in Hz. Anything missing, unreadable
     * or out of a sane range means Auto, so a bad value can never lock the screen to a rate.
     */
    public static int parseCap(String stored) {
        if (stored == null) return 0;
        try {
            int hz = Integer.parseInt(stored.trim());
            return hz >= 30 && hz <= 240 ? hz : 0;
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** One mode of the screen, as {@code Display.Mode} reports it. Width and height are physical. */
    static final class Mode {
        final int id;
        final int width;
        final int height;
        final float hz;

        Mode(int id, int width, int height, float hz) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.hz = hz;
        }
    }

    /** What to hold: the rate, and the id of the mode that has it (0 when the current mode does). */
    static final class Choice {
        final float hz;
        final int modeId;

        Choice(float hz, int modeId) {
            this.hz = hz;
            this.modeId = modeId;
        }
    }

    /**
     * Pick the rate to hold while the game is on screen.
     *
     * @param current   the mode the screen is in now
     * @param supported every mode the screen offers
     * @param capHz     0 for Auto, otherwise the highest rate the player allows (60, 90, 120...)
     */
    static Choice choose(Mode current, List<Mode> supported, int capHz) {
        // Auto never lowers a screen that already runs above 120 Hz; a rate the player picked is kept.
        float cap = capHz > 0 ? capHz : Math.max(AUTO_MAX_HZ, current.hz);
        Mode best = null;
        Mode lowest = null;
        for (Mode mode : supported) {
            if (mode.width != current.width || mode.height != current.height) continue;
            if (lowest == null || mode.hz < lowest.hz - SAME_RATE) lowest = mode;
            if (mode.hz <= cap + SAME_RATE && (best == null || mode.hz > best.hz + SAME_RATE)) best = mode;
        }
        // Every mode is above the cap: the lowest one the screen has is the closest it can get.
        if (best == null) best = lowest;
        if (best == null || sameRate(best.hz, current.hz)) return new Choice(current.hz, 0);
        return new Choice(best.hz, best.id);
    }

    static boolean sameRate(float a, float b) {
        return Math.abs(a - b) < SAME_RATE;
    }

    /**
     * Decides, each time the screen reports a change, whether to ask for the rate again.
     *
     * Phones with an adaptive screen drop to 60 Hz on their own whenever they think the content
     * does not need more, and a game that draws straight to the screen is easy to mistake for such
     * content. So the request is repeated when the screen falls below it, at most once a second.
     * If asking does not bring the rate back several times in a row, the system is holding it down
     * on purpose (battery saver, a hot phone): from then on it is tried once every half minute
     * instead of arguing with the system.
     */
    static final class Guard {
        static final long COOLDOWN_MS = 1_000L;
        static final int QUICK_TRIES = 3;
        static final long PATIENT_COOLDOWN_MS = 30_000L;

        private boolean asked;
        private long lastAskMs;
        private int tries;

        /** @return true when the screen is below the wanted rate and it is time to ask again */
        boolean shouldAsk(long nowMs, float currentHz, float wantedHz) {
            if (wantedHz <= 0f) return false;
            if (currentHz >= wantedHz - SAME_RATE) {
                tries = 0; // it got there: the next drop starts from a clean slate
                return false;
            }
            long wait = tries >= QUICK_TRIES ? PATIENT_COOLDOWN_MS : COOLDOWN_MS;
            if (asked && nowMs - lastAskMs < wait) return false;
            asked = true;
            lastAskMs = nowMs;
            tries++;
            return true;
        }

        /** The game is back in front: forget the waits, the system may have changed its mind. */
        void reset() {
            asked = false;
            tries = 0;
        }

        /** Asks made since the screen was last at the wanted rate. */
        int tries() {
            return tries;
        }
    }
}
