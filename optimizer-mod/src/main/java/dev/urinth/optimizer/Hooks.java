package dev.urinth.optimizer;

import java.util.concurrent.atomic.AtomicIntegerArray;
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

    /** After this many failures a hook is switched off for the rest of the session and the game's own behaviour is left. */
    static final int MAX_FAILURES = 20;
    /** The failures that are written to the log in full; the rest are only counted (a hook can run every frame). */
    static final int LOGGED_FAILURES = 3;

    private static final String[] NAMES = {"fog", "culling", "particles", "uploads", "simulation distance", "controller", "server governor"};
    private static final AtomicIntegerArray FAILURES = new AtomicIntegerArray(NAMES.length + 1);

    private static int slot(String hook) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(hook)) return i;
        }
        return NAMES.length;
    }

    /** False once the hook has failed {@link #MAX_FAILURES} times: it then does nothing and the game runs its own code. */
    public static boolean usable(String hook) {
        return FAILURES.get(slot(hook)) < MAX_FAILURES;
    }

    /** A hook threw: the game goes on with its own behaviour, this is only remembered for the status. */
    public static void failed(String hook, Throwable t) {
        int count = FAILURES.incrementAndGet(slot(hook));
        failure = hook + ": " + t;
        if (count <= LOGGED_FAILURES) {
            LOG.warn("[Optimizer] the {} hook failed and was skipped", hook, t);
        }
        if (count == MAX_FAILURES) {
            LOG.warn("[Optimizer] the {} hook failed {} times: it is switched off for this session", hook, count);
        }
    }

    /** For tests: forget every failure. */
    static void forgetFailures() {
        for (int i = 0; i < FAILURES.length(); i++) FAILURES.set(i, 0);
        failure = "";
    }

    public static String failure() {
        return failure;
    }

    /** "running" or "waiting" (the game has not used it yet, for example no world is open). */
    public static String state(boolean seen) {
        return seen ? "running" : "waiting";
    }
}
