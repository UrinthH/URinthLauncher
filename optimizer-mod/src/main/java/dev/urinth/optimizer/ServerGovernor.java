package dev.urinth.optimizer;

/**
 * Lowers the simulation distance of a world while its server cannot keep up, and raises it again when it has room. Plain Java,
 * unit tested. It is fed the smoothed milliseconds a server tick takes (the game's limit is 50), about every five seconds.
 *
 * <p>The simulation distance is how far around the player the world is ticked: mobs move, crops grow, redstone runs. It is the
 * biggest cost of the server that runs inside the game on a phone, and the picture does not depend on it (the chunks beyond it are
 * still drawn, only frozen).
 */
final class ServerGovernor {
    /** Tick time (ms) above which the server is too slow. */
    static final float SLOW_MS = 40f;
    /** Tick time (ms) below which it has room. */
    static final float ROOM_MS = 22f;
    /** Slow samples in a row before the distance is lowered. */
    static final int SLOW_SAMPLES = 2;
    /** Samples with room in a row before it is raised again. */
    static final int ROOM_SAMPLES = 6;

    private int cap;
    private int slow;
    private int room;
    private int changes;

    /** The current limit, in chunks. */
    int cap() {
        return cap;
    }

    int changes() {
        return changes;
    }

    /** Starts (or restarts) from the limit the player configured. */
    void reset(int configured) {
        cap = Math.max(0, configured);
        slow = 0;
        room = 0;
    }

    /**
     * One sample.
     *
     * @param tickMs     the smoothed milliseconds one server tick takes
     * @param configured the limit from the settings (the most it will ever use); 0 means the mod does not limit anything
     * @param min        the least it will go down to, in chunks
     * @return the limit to use now; 0 = none
     */
    int sample(float tickMs, int configured, int min) {
        if (configured <= 0) {
            cap = 0;
            slow = 0;
            room = 0;
            return 0;
        }
        if (min < 2) min = 2;
        if (min > configured) min = configured;
        if (cap <= 0 || cap > configured) cap = configured;
        if (tickMs > SLOW_MS) {
            slow++;
            room = 0;
            if (slow >= SLOW_SAMPLES && cap > min) {
                cap--;
                slow = 0;
                changes++;
            }
        } else if (tickMs < ROOM_MS) {
            room++;
            slow = 0;
            if (room >= ROOM_SAMPLES && cap < configured) {
                cap++;
                room = 0;
                changes++;
            }
        } else {
            slow = 0;
            room = 0;
        }
        return cap;
    }
}
