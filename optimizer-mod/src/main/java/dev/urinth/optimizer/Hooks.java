package dev.urinth.optimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which of the changes to the game's code are really running. A change that cannot be applied (the game's code looks different)
 * is skipped without an error, so this is how the status screen and /urinthopt can say what is working instead of guessing. Each
 * hook reports itself the first time the game calls it.
 */
public final class Hooks {
    private Hooks() {}

    private static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    public static volatile boolean fog;
    public static volatile boolean cull;
    public static volatile boolean particles;
    public static volatile boolean uploads;
    public static volatile boolean simulation;
    private static volatile String failure = "";

    public static void fogSeen() {
        if (!fog) {
            fog = true;
            LOG.info("[Optimizer] fog hook is running");
        }
    }

    public static void cullSeen() {
        if (!cull) {
            cull = true;
            LOG.info("[Optimizer] chunk culling hook is running");
        }
    }

    public static void particlesSeen() {
        if (!particles) {
            particles = true;
            LOG.info("[Optimizer] particle hook is running");
        }
    }

    public static void uploadsSeen() {
        if (!uploads) {
            uploads = true;
            LOG.info("[Optimizer] upload smoothing hook is running");
        }
    }

    public static void simulationSeen() {
        if (!simulation) {
            simulation = true;
            LOG.info("[Optimizer] simulation distance hook is running");
        }
    }

    /** A hook threw: the game goes on with its own behaviour, this is only remembered for the status. */
    public static void failed(String hook, Throwable t) {
        failure = hook + ": " + t;
        LOG.warn("[Optimizer] the {} hook failed and was skipped", hook, t);
    }

    public static String failure() {
        return failure;
    }

    /** "running" or "waiting" (the game has not used it yet, for example no world is open). */
    public static String state(boolean seen) {
        return seen ? "running" : "waiting";
    }
}
