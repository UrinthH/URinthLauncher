package dev.urinth.optimizer;

/** When the particle hook may swap "All" for "Decreased": the setting, and (in auto) only while the frame rate is low. */
public final class ParticleGate {
    private ParticleGate() {}

    private static volatile boolean low;
    private static volatile boolean shortOfMemory;

    /** Set once a second by the controller: the frame rate is below the target. */
    static void setLow(boolean value) {
        low = value;
    }

    /** Set once a second by the controller: the game is short of memory (every particle is an object the collector has to handle). */
    static void setShortOfMemory(boolean value) {
        shortOfMemory = value;
    }

    /** True if "All" particles should become "Decreased" right now. */
    public static boolean reduce() {
        return decide(OptimizerConfig.current().particles(), low || shortOfMemory);
    }

    static boolean decide(int mode, boolean lowFps) {
        if (mode == OptimizerConfig.PARTICLES_ALWAYS) return true;
        if (mode == OptimizerConfig.PARTICLES_AUTO) return lowFps;
        return false;
    }
}
