package dev.urinth.optimizer;

/**
 * Lowers the simulation distance of a world while its server cannot keep up, and raises it again when it has room. Plain Java,
 * unit tested. It is fed the smoothed milliseconds a server tick takes (the game's limit is 50), about every five seconds.
 *
 * <p>The simulation distance is how far around the player the world is ticked: mobs move, crops grow, redstone runs. It is the
 * biggest cost of the server that runs inside the game on a phone, and the picture does not depend on it (the chunks beyond it are
 * still drawn, only frozen).
 *
 * <p>Raising it is not free: the chunks that come back into the simulation wake up all at once, and for a moment the server does
 * more work than before. On a server that is only just able to keep up that moment tips it over, and the distance would go down
 * again, up again, down again, a small stall every half minute. So every raise that does not hold (the distance has to be lowered
 * again within a minute) doubles the time it waits before the next one, up to ten minutes, and a raise that holds halves it again.
 */
final class ServerGovernor {
    /** The least the simulation distance is ever lowered to (by the speed of the server, or by a shortage of memory). */
    static final int MIN_SIMULATION = 4;
    /** Tick time (ms) above which the server is too slow. */
    static final float SLOW_MS = 40f;
    /** Tick time (ms) below which it has room. */
    static final float ROOM_MS = 22f;
    /** Slow samples in a row before the distance is lowered. */
    static final int SLOW_SAMPLES = 2;
    /** Samples with room in a row before it is raised again, at first (30 seconds). */
    static final int ROOM_SAMPLES = 6;
    /** The longest wait, in samples (ten minutes). */
    static final int ROOM_SAMPLES_MAX = 120;
    /** Samples a raise must survive to count as holding (one minute). */
    static final int TRIAL_SAMPLES = 12;

    private int cap;
    private int slow;
    private int room;
    private int changes;
    private int hold = ROOM_SAMPLES;
    /** Samples since the last raise; -1 when no raise is being tried. */
    private int sinceRaise = -1;

    /** The current limit, in chunks. */
    int cap() {
        return cap;
    }

    int changes() {
        return changes;
    }

    /** Samples with room it needs now before it raises the distance. */
    int hold() {
        return hold;
    }

    /** Starts (or restarts) from the limit the player configured. */
    void reset(int configured) {
        cap = Math.max(0, configured);
        slow = 0;
        room = 0;
        hold = ROOM_SAMPLES;
        sinceRaise = -1;
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

        if (sinceRaise >= 0 && ++sinceRaise > TRIAL_SAMPLES) {
            // the raise held: the next one may come sooner
            sinceRaise = -1;
            hold = Math.max(ROOM_SAMPLES, hold / 2);
        }
        if (tickMs > SLOW_MS) {
            slow++;
            room = 0;
            if (slow >= SLOW_SAMPLES && cap > min) {
                // the raise did not hold: wait twice as long before trying again
                if (sinceRaise >= 0) hold = Math.min(hold * 2, ROOM_SAMPLES_MAX);
                sinceRaise = -1;
                cap--;
                slow = 0;
                changes++;
            }
        } else if (tickMs < ROOM_MS) {
            room++;
            slow = 0;
            if (room >= hold && cap < configured) {
                cap++;
                room = 0;
                changes++;
                sinceRaise = 0;
            }
        } else {
            slow = 0;
            room = 0;
        }
        return cap;
    }
}
