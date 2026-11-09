package dev.urinth.optimizer;

/** When the particle hook may swap "All" for "Decreased": the setting, and (in auto) only while the frame rate is low. */
public final class ParticleGate {
    private ParticleGate() {}

    private static volatile boolean low;

    /** Set once a second by the controller: the frame rate is below the target. */
    static void setLow(boolean value) {
        low = value;
    }

    /** True if "All" particles should become "Decreased" right now. */
    public static boolean reduce() {
        return decide(OptimizerConfig.current().particles(), low);
    }

    static boolean decide(int mode, boolean lowFps) {
        if (mode == OptimizerConfig.PARTICLES_ALWAYS) return true;
        if (mode == OptimizerConfig.PARTICLES_AUTO) return lowFps;
        return false;
    }
}
