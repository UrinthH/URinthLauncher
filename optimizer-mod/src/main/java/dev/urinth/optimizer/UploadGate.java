package dev.urinth.optimizer;

/** Reads the settings for the upload hook: the budget, or 0 when upload smoothing is off. */
public final class UploadGate {
    private UploadGate() {}

    private static volatile int targetFps = 60;

    /** Set once a second by the controller. */
    static void setTargetFps(int fps) {
        targetFps = fps;
    }

    /** The nanoseconds this frame's uploads may take; 0 = no limit. */
    public static long nanos(int pending) {
        if (!OptimizerConfig.current().uploads()) return 0L;
        return UploadBudget.nanos(targetFps, pending);
    }
}
