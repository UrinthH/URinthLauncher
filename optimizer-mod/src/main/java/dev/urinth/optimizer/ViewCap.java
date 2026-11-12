package dev.urinth.optimizer;

/**
 * The view distance limit of the game's own server, shared with the mixin that applies it (see {@link ViewValve}). 0 = no limit.
 * The option the player chose is never touched: only what the server inside the game is told.
 */
public final class ViewCap {
    private ViewCap() {}

    private static volatile int cap;

    static void set(int chunks) {
        cap = Math.max(0, chunks);
    }

    static int cap() {
        return cap;
    }

    /** The view distance the game's server should use for the one the option holds. */
    public static int apply(int option) {
        int c = cap;
        return c > 0 && option > c ? c : option;
    }
}
