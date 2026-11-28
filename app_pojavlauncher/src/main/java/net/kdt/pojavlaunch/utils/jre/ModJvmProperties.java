package net.kdt.pojavlaunch.utils.jre;

/**
 * Java properties the launcher gives the game for the URinth mods to read. Plain Java, unit tested.
 *
 * <p>The URinth Boost mod aims for the frame rate the screen can show. Inside the game the screen looks like a
 * 60 Hz one whatever it is, so the launcher, which knows the rate it asked the screen for, says it here.
 */
final class ModJvmProperties {
    /** The name the Boost mod reads (BoostConfig.SCREEN_HZ_PROPERTY in boost-mod). */
    static final String REFRESH_RATE = "urinth.refreshHz";
    /** Rates outside these are not passed on: they would be a wrong reading, not a screen. */
    static final int MIN_HZ = 30;
    static final int MAX_HZ = 480;

    private ModJvmProperties() {
    }

    /**
     * The argument that gives the game the screen's rate, e.g. {@code -Durinth.refreshHz=90}; null when the rate is
     * not known (0, NaN) or not believable.
     */
    static String refreshRate(float hz) {
        if (!(hz >= MIN_HZ)) return null; // also false for NaN
        if (hz > MAX_HZ) return null;
        return "-D" + REFRESH_RATE + "=" + Math.round(hz);
    }

    /** True when the arguments already name the property (a player's own Java arguments win). */
    static boolean names(Iterable<String> arguments) {
        for (String argument : arguments) {
            if (argument != null && argument.startsWith("-D" + REFRESH_RATE + "=")) return true;
        }
        return false;
    }
}
