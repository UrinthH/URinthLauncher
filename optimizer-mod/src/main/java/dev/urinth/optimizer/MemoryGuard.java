package dev.urinth.optimizer;

/**
 * Judges from a few numbers whether the game is running out of memory. Plain Java, no game classes, unit tested.
 *
 * <p>On a phone with 2 to 3 GB the game, the system and the graphics driver share the same memory. When it runs out the game does
 * not slow down politely: the garbage collector starts to run all the time (the game freezes in half-second steps, then in whole
 * seconds), the system takes pages away from the game and reads them back (more freezing), and in the end the game is killed or
 * stops with "out of memory". This looks at the signs of that, early enough to act, and answers one of three levels:
 * <ul>
 *   <li>{@link Level#OK}: nothing to do.</li>
 *   <li>{@link Level#TIGHT}: memory is getting short. Cheap, invisible savings are enough (fewer particles, a shorter simulation
 *       distance).</li>
 *   <li>{@link Level#CRITICAL}: the game is about to be stopped or is already freezing from it. The caller may shorten the world.</li>
 * </ul>
 * A level rises at once but falls slowly (one level at a time, after a long calm), so it never flickers.
 *
 * <p>The signs are weighed so that a phone which is simply full, as phones are, is not taken for one that is in trouble: little
 * free memory alone only means TIGHT. CRITICAL needs proof that the game is really being hurt: the garbage collector failing to
 * keep up (repeated full collections, the old generation almost full), or the system taking the game's memory away (a flood of
 * page faults together with almost nothing free), and it needs that for two looks in a row.
 */
final class MemoryGuard {
    enum Level { OK, TIGHT, CRITICAL }

    /** The old generation after the last collection, as a share of the heap the game may use. */
    static final double TIGHT_OLD = 0.80;
    static final double CRITICAL_OLD = 0.92;
    /** The share of the last ten seconds the game stood still in garbage collections. Only ever TIGHT. */
    static final double TIGHT_GC = 0.12;
    /** What the system has left, in bytes (MemAvailable). */
    static final long TIGHT_AVAILABLE = 150L << 20;
    static final long CRITICAL_AVAILABLE = 100L << 20;
    /** Major page faults of the game a second: memory that was taken away from it and had to be read back. */
    static final double TIGHT_FAULTS = 150;
    static final double HEAVY_FAULTS = 200;
    static final double SEVERE_FAULTS = 1000;
    /** Full collections within the last two minutes that make it CRITICAL (one is TIGHT). */
    static final int FULL_GCS_CRITICAL = 2;
    /** Looks in a row that must say "critical" before it is CRITICAL. */
    static final int CRITICAL_LOOKS = 2;
    /** Calm looks (two seconds each) before the level falls by one: 30 seconds from TIGHT, 60 from CRITICAL. */
    static final int CALM_FROM_TIGHT = 15;
    static final int CALM_FROM_CRITICAL = 30;

    private Level level = Level.OK;
    private int criticalRun;
    private int calm;

    Level level() {
        return level;
    }

    void reset() {
        level = Level.OK;
        criticalRun = 0;
        calm = 0;
    }

    /**
     * One look at the memory.
     *
     * @param oldShare       the old generation after the last collection, 0 to 1 of the heap; 0 if unknown
     * @param gcShare        the share (0 to 1) of the last ten seconds spent in garbage collection pauses
     * @param recentFullGcs  full collections in the last two minutes
     * @param availableBytes what the system says it can still give, or -1 if it cannot be read
     * @param faultsPerSec   major page faults of the game a second, or -1 if unknown
     */
    Level update(double oldShare, double gcShare, int recentFullGcs, long availableBytes, double faultsPerSec) {
        boolean lowFree = availableBytes >= 0 && availableBytes < TIGHT_AVAILABLE;
        boolean veryLowFree = availableBytes >= 0 && availableBytes < CRITICAL_AVAILABLE;
        boolean critical = recentFullGcs >= FULL_GCS_CRITICAL
            || oldShare >= CRITICAL_OLD
            || (veryLowFree && faultsPerSec >= HEAVY_FAULTS)
            || faultsPerSec >= SEVERE_FAULTS;
        boolean tight = critical
            || recentFullGcs >= 1
            || oldShare >= TIGHT_OLD
            || gcShare >= TIGHT_GC
            || lowFree
            || faultsPerSec >= TIGHT_FAULTS;

        criticalRun = critical ? criticalRun + 1 : 0;
        Level wanted = criticalRun >= CRITICAL_LOOKS ? Level.CRITICAL : tight ? Level.TIGHT : Level.OK;

        if (wanted.ordinal() >= level.ordinal()) {
            level = wanted;
            calm = 0;
        } else {
            calm++;
            int need = level == Level.CRITICAL ? CALM_FROM_CRITICAL : CALM_FROM_TIGHT;
            if (calm >= need) {
                level = Level.values()[level.ordinal() - 1];
                calm = 0;
            }
        }
        return level;
    }
}
