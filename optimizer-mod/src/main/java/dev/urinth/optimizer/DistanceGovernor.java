package dev.urinth.optimizer;

/**
 * Decides how many chunks the game draws, from the frame rate. Plain Java, no game classes, unit tested.
 *
 * <p>It is called once a second with the frame rate of that second and answers with the distance (in chunks) to draw now. It never
 * answers more than the render distance the player chose and never less than the minimum. The rules:
 * <ul>
 *   <li>Two low seconds in a row (below 90% of the target) step the distance down. The further below the target, the bigger the step
 *       (up to 3 chunks), so a phone far from the target gets there in a few seconds. A single bad second (a hitch) is ignored.</li>
 *   <li>A long run of good seconds (97% of the target or more) steps it up by one chunk. If that step makes the frame rate drop again,
 *       it goes back and the next attempt waits twice as long (up to five minutes), so it settles instead of flickering. When the
 *       frame rate is far above the target there is obviously room and the wait is short.</li>
 *   <li>If shrinking the distance does not raise the frame rate (the bottleneck is something else: shaders, resolution, a hot phone),
 *       it gives the chunks back and stops trying for a while, instead of making the picture worse for nothing.</li>
 *   <li>While the game is not really running (a menu, the window in the background, joining a world) nothing changes.</li>
 * </ul>
 */
final class DistanceGovernor {
    /** Below this share of the target the frame rate is low. */
    static final double LOW = 0.90;
    /** At or above this share it is good. In between nothing is done. */
    static final double GOOD = 0.97;
    /** At or above this share there is plenty of room. */
    static final double ROOM = 1.30;
    /** Low seconds in a row before the distance shrinks. */
    static final int LOW_SECONDS = 2;
    /** Seconds after a change in which nothing is judged: the fog is still moving and the new chunk graph is being built. */
    static final int SETTLE_SECONDS = 3;
    /** Good seconds before a step up, at first and at most. It doubles after every step up that did not hold. */
    static final int HOLD_MIN = 20;
    static final int HOLD_MAX = 300;
    /** Good seconds before a step up when the frame rate is far above the target. */
    static final int ROOM_HOLD = 6;
    /** Seconds a step up must survive to count as holding. */
    static final int TRIAL_SECONDS = 25;
    /** Steps down in a row that did not raise the frame rate before the mod gives up. */
    static final int FUTILE_AFTER = 2;
    static final int FUTILE_HOLD_MIN = 90;
    static final int FUTILE_HOLD_MAX = 600;

    private int soft;
    private int lastReal;
    private int lowStreak;
    private int goodStreak;
    private int cooldown;
    private int hold = HOLD_MIN;
    private boolean trial;
    private int trialSeconds;
    private boolean gainCheck;
    private int fpsBeforeDrop;
    private int noGain;
    private int droppedInRun;
    private long clock;
    private long futileUntil;
    private int futileHold = FUTILE_HOLD_MIN;
    private boolean struggling;
    private int lastFps;
    private int lastTarget;
    private int changes;
    private String reason = "starting";

    /** The distance to draw now, in chunks; 0 before the first call. */
    int soft() {
        return soft;
    }

    /** True while the last second was low: the game is short of frame rate right now. */
    boolean struggling() {
        return struggling;
    }

    /** True while the mod has given up shrinking because it did not help. */
    boolean giveUp() {
        return clock < futileUntil;
    }

    int lastFps() {
        return lastFps;
    }

    int lastTarget() {
        return lastTarget;
    }

    /** How many times the distance has been changed. */
    int changes() {
        return changes;
    }

    /** One line on the last decision, for the status. */
    String reason() {
        return reason;
    }

    /** Forget everything learned (the player asked for it, or a new world was opened). The distance restarts at the full one. */
    void reset() {
        soft = 0;
        lastReal = 0;
        lowStreak = 0;
        goodStreak = 0;
        cooldown = 0;
        hold = HOLD_MIN;
        trial = false;
        trialSeconds = 0;
        gainCheck = false;
        noGain = 0;
        droppedInRun = 0;
        futileUntil = 0;
        futileHold = FUTILE_HOLD_MIN;
        struggling = false;
        reason = "reset";
    }

    /**
     * One second has passed.
     *
     * @param fps    the frame rate of the last second
     * @param target the frame rate to keep (already limited by the frame limit and v-sync)
     * @param real   the render distance the player chose, in chunks
     * @param min    the shortest distance allowed, in chunks
     * @param active false while the game is not really running (menu, window in the background, just joined a world)
     * @return the distance to draw now, in chunks
     */
    int second(int fps, int target, int real, int min, boolean active) {
        clock++;
        if (real < 1) real = 1;
        if (min < 1) min = 1;
        if (min > real) min = real;
        if (target < 10) target = 10;
        lastFps = fps;
        lastTarget = target;

        if (soft == 0) {
            soft = real;
        } else if (soft > real) {
            soft = real;
        } else if (real > lastReal && lastReal > 0 && soft >= lastReal) {
            // the player raised the render distance while everything was drawn: draw all of it, and see how it goes
            soft = real;
            trial = true;
            trialSeconds = 0;
            cooldown = SETTLE_SECONDS;
            reason = "render distance raised to " + real;
        }
        if (soft < min) soft = min;
        lastReal = real;

        if (!active) {
            lowStreak = 0;
            goodStreak = 0;
            struggling = false;
            return soft;
        }

        boolean low = fps < target * LOW;
        boolean good = fps >= target * GOOD;
        struggling = low;

        if (cooldown > 0) {
            cooldown--;
            return soft;
        }

        if (gainCheck) {
            gainCheck = false;
            int needed = Math.max(2, (int) Math.ceil(fpsBeforeDrop * 0.03));
            if (fps >= fpsBeforeDrop + needed) {
                noGain = 0;
                droppedInRun = 0;
            } else {
                noGain++;
                if (noGain >= FUTILE_AFTER && low) {
                    int back = Math.min(real, soft + droppedInRun);
                    reason = "a shorter distance did not raise the frame rate, back to " + back;
                    soft = back;
                    droppedInRun = 0;
                    noGain = 0;
                    futileUntil = clock + futileHold;
                    futileHold = Math.min(futileHold * 2, FUTILE_HOLD_MAX);
                    lowStreak = 0;
                    goodStreak = 0;
                    cooldown = SETTLE_SECONDS;
                    changes++;
                    return soft;
                }
            }
        }

        if (low) {
            lowStreak++;
            goodStreak = 0;
        } else if (good) {
            goodStreak++;
            lowStreak = 0;
        } else {
            lowStreak = 0;
            goodStreak = 0;
        }

        if (trial && !low) {
            trialSeconds++;
            if (trialSeconds >= TRIAL_SECONDS) {
                trial = false;
                hold = Math.max(HOLD_MIN, hold / 2);
                futileHold = FUTILE_HOLD_MIN;
            }
        }

        if (lowStreak >= LOW_SECONDS && soft > min && clock >= futileUntil) {
            int steps = fps < target * 0.6 ? 3 : fps < target * 0.8 ? 2 : 1;
            int next = Math.max(min, soft - steps);
            fpsBeforeDrop = fps;
            gainCheck = true;
            droppedInRun += soft - next;
            reason = "fps " + fps + " is below " + (int) Math.ceil(target * LOW) + " (target " + target + "): " + soft + " -> " + next + " chunks";
            if (trial) {
                hold = Math.min(hold * 2, HOLD_MAX);
                trial = false;
            }
            soft = next;
            lowStreak = 0;
            goodStreak = 0;
            cooldown = SETTLE_SECONDS;
            changes++;
        } else if (soft < real && goodStreak >= (fps >= target * ROOM ? ROOM_HOLD : hold)) {
            reason = "fps " + fps + " holds the target " + target + ": " + soft + " -> " + (soft + 1) + " chunks";
            soft++;
            trial = true;
            trialSeconds = 0;
            goodStreak = 0;
            lowStreak = 0;
            gainCheck = false;
            cooldown = SETTLE_SECONDS;
            changes++;
        }
        return soft;
    }
}
