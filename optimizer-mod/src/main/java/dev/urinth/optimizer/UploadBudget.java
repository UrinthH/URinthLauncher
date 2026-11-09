package dev.urinth.optimizer;

/**
 * How long one frame may spend copying finished chunk meshes to the graphics card. The game copies every pending mesh in the frame
 * they are finished in, which after a big batch (flying, teleporting, a chunk storm) is one long frame. With a budget the rest waits
 * for the next frames. Plain Java, unit tested.
 */
final class UploadBudget {
    private UploadBudget() {}

    static final double SHARE_OF_FRAME = 0.18;
    static final double MIN_MS = 1.2;
    static final double MAX_MS = 4.0;
    /** With this many meshes waiting, the budget doubles (the world is still loading). */
    static final int BACKLOG = 48;
    /** With this many waiting, there is no budget: finish the loading. */
    static final int FLOOD = 400;

    /**
     * The nanoseconds the uploads of this frame may take; 0 = no limit (the game's own behaviour).
     *
     * @param targetFps the frame rate being kept
     * @param pending   how many meshes are waiting
     */
    static long nanos(int targetFps, int pending) {
        if (pending <= 1 || pending >= FLOOD) return 0L;
        double frameMs = 1000.0 / Math.max(20, targetFps);
        double ms = Math.max(MIN_MS, Math.min(MAX_MS, frameMs * SHARE_OF_FRAME));
        if (pending > BACKLOG) ms *= 2;
        return (long) (ms * 1.0e6);
    }
}
