package dev.urinth.optimizer;

/** The frame rate the controller really aims for: the one the player asked for, but never more than the game can show. */
final class TargetFps {
    private TargetFps() {}

    /** The game's "Unlimited" frame limit is 260. */
    static final int UNLIMITED = 260;

    /**
     * @param configured the frame rate from the settings
     * @param frameLimit the game's frame limit (260 = unlimited)
     * @param vsync      whether v-sync is on
     * @param refresh    the refresh rate of the screen as the game sees it (0 = unknown)
     */
    static int effective(int configured, int frameLimit, boolean vsync, int refresh) {
        int target = configured;
        if (frameLimit > 0 && frameLimit < UNLIMITED) target = Math.min(target, frameLimit);
        if (vsync && refresh >= 20) target = Math.min(target, refresh);
        return Math.max(20, target);
    }
}
