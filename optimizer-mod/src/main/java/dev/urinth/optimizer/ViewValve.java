package dev.urinth.optimizer;

/**
 * The last resort when the game is about to run out of memory: it shortens the world the game around the player keeps loaded by two
 * chunks, which frees the loaded chunks, their lighting, the meshes and the graphics memory behind them (about a third of it for
 * two chunks at a distance of 10). Plain Java, no game classes, unit tested.
 *
 * <p>The render distance the player chose is not changed (options.txt is not touched and the option keeps its value): the game's own
 * server inside the game is simply told a shorter distance than the option holds, the same way the game itself does when a server
 * limits the view distance. Rules:
 * <ul>
 *   <li>It only acts on {@link MemoryGuard.Level#CRITICAL} and never goes below {@link #FLOOR} chunks.</li>
 *   <li>Between two steps down at least {@link #DWELL} seconds pass, so the world has time to unload and the memory to show it.</li>
 *   <li>It gives the chunks back one at a time after {@link #RESTORE} seconds without any trouble, but never beyond one chunk less
 *       than the distance that ran the game out of memory. If that distance is not enough either, the limit goes lower and so does
 *       the ceiling, so it settles instead of going up and down.</li>
 * </ul>
 */
final class ViewValve {
    /** The world is never shortened below this many chunks. */
    static final int FLOOR = 6;
    /** Chunks taken away per step. */
    static final int STEP = 2;
    /** Seconds between two steps down. */
    static final long DWELL = 20;
    /** Seconds without trouble before one chunk is given back. */
    static final long RESTORE = 180;

    private int cap;
    private int ceiling;
    private long lastChange = Long.MIN_VALUE / 4;
    private long calmSince;
    private int steps;

    /** The limit in chunks; 0 = none. */
    int cap() {
        return cap;
    }

    /** The most the distance may be given back to after a step down; 0 = no ceiling yet. */
    int ceiling() {
        return ceiling;
    }

    /** How many times it has taken chunks away. */
    int steps() {
        return steps;
    }

    void reset() {
        cap = 0;
        ceiling = 0;
        lastChange = Long.MIN_VALUE / 4;
        calmSince = 0;
        steps = 0;
    }

    /**
     * One look, about once a second.
     *
     * @param level   how short of memory the game is
     * @param option  the render distance the player chose, in chunks
     * @param enabled false when the player switched this off
     * @param now     seconds, only ever increasing
     * @return the limit in chunks; 0 = none
     */
    int update(MemoryGuard.Level level, int option, boolean enabled, long now) {
        if (!enabled || option <= FLOOR) {
            cap = 0;
            return 0;
        }
        // the player chose a distance that is already shorter than the limit: there is nothing to limit
        if (cap > 0 && cap >= option) cap = 0;
        int effective = cap > 0 ? cap : option;
        if (level != MemoryGuard.Level.OK) calmSince = now;

        if (level == MemoryGuard.Level.CRITICAL && effective > FLOOR && now - lastChange >= DWELL) {
            ceiling = effective - 1;
            cap = Math.max(FLOOR, effective - STEP);
            lastChange = now;
            calmSince = now;
            steps++;
        } else if (cap > 0 && level == MemoryGuard.Level.OK && now - calmSince >= RESTORE && now - lastChange >= RESTORE) {
            int limit = ceiling > 0 ? Math.min(ceiling, option) : option;
            if (cap < limit) {
                int next = cap + 1;
                cap = next >= option ? 0 : next;
                lastChange = now;
                calmSince = now;
            }
        }
        return cap;
    }
}
