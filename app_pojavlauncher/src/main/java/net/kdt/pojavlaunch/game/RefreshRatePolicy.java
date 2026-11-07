package net.kdt.pojavlaunch.game;

/**
 * The decision part of the "keep the refresh rate" watch in {@link RefreshRateBoost}, with no
 * Android code in it so it can be tested on a plain JVM.
 *
 * Many phones lower the screen rate again while a game runs (smart or adaptive refresh, a power
 * saving rule, the screen being touched less). Minecraft's vsync follows the screen, so the frame
 * rate then drops by a third or more with nothing wrong in the game itself. The idea, as other
 * launchers do it, is to ask again when the system has lowered the rate. Two things keep that from
 * doing harm: it waits longer after every request that did not help, so a phone that insists on a
 * lower rate is not nagged every second, and it never asks while the phone is hot, because then the
 * lower rate is the system protecting the phone.
 */
final class RefreshRatePolicy {
    /** Wait this long after the first request that did not help. It doubles each time. */
    static final long BASE_COOLDOWN_MS = 1_000L;
    /** The wait stops doubling after this many steps: 1 s, 2 s, 4 s, 8 s, 16 s, 32 s. */
    static final int MAX_BACKOFF_STEPS = 5;
    /** Rates closer than this count as the same rate (refresh rates are reported as 89.9 and 90.0). */
    static final float TOLERANCE_HZ = 0.5f;

    private RefreshRatePolicy() {}

    /** True when the screen is running slower than the rate that was asked for. */
    static boolean isBelowTarget(float requestedHz, float currentHz) {
        return requestedHz > 0f && currentHz < requestedHz - TOLERANCE_HZ;
    }

    /** How long to wait before asking again, given how many requests in a row did not help. */
    static long cooldownMs(int failedRequests) {
        int steps = Math.max(0, Math.min(failedRequests, MAX_BACKOFF_STEPS));
        return BASE_COOLDOWN_MS << steps;
    }

    /**
     * Whether to ask the system for the rate again now.
     *
     * @param lastRequestMs when the last request was made, or a negative number if none has been
     * @param failedRequests requests in a row after which the screen was still below the rate
     * @param phoneIsHot true while the heat level is high: the lower rate is then deliberate
     */
    static boolean shouldRequestAgain(float requestedHz, float currentHz, long nowMs, long lastRequestMs,
                                      int failedRequests, boolean phoneIsHot) {
        if (phoneIsHot || !isBelowTarget(requestedHz, currentHz)) return false;
        if (lastRequestMs < 0) return true;
        return nowMs - lastRequestMs >= cooldownMs(failedRequests);
    }
}
