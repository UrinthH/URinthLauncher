package dev.urinth.optimizer;

/**
 * How many background threads the game may use for chunk generation, chunk meshing and loading. The game takes one less than the
 * number of processors (7 on an 8 core phone) and runs them all next to the render thread and the server thread, which on a phone
 * takes the fast cores away from them. Fewer workers leave those cores to the frame rate; chunks load a little slower in return.
 * Plain Java, unit tested.
 */
final class ThreadPlan {
    private ThreadPlan() {}

    /** The system property the game reads, once, when it starts (an upper limit for the workers). */
    static final String PROPERTY = "max.bg.threads";

    /**
     * The value to set for {@link #PROPERTY}, or 0 to leave the game's own choice alone.
     *
     * @param cores      the processors the game sees
     * @param configured {@link OptimizerConfig#AUTO_THREADS}, {@link OptimizerConfig#VANILLA_THREADS} or a number of threads
     */
    static int workers(int cores, int configured) {
        if (configured == OptimizerConfig.VANILLA_THREADS) return 0;
        if (configured > 0) return Math.min(configured, 255);
        // automatic: half of the cores, at least 2 and at most 4. A 2 core device is left alone (the game already uses 1 worker).
        if (cores <= 2) return 0;
        return Math.max(2, Math.min(4, cores / 2));
    }
}
