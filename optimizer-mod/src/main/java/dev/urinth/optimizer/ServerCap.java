package dev.urinth.optimizer;

/**
 * The simulation distance limit of the world the server runs, shared with the mixin that applies it. The game asks for a distance
 * (the client options, or server.properties); this keeps the last value it asked for, so the limit can be moved up and down later
 * and the game's own request stays what it was.
 */
public final class ServerCap {
    private ServerCap() {}

    private static volatile int cap;
    private static volatile int requested;

    /** The distance the game asked for, or 0 if it has not asked yet. */
    static int requested() {
        return requested;
    }

    static int cap() {
        return cap;
    }

    static void setCap(int chunks) {
        cap = Math.max(0, chunks);
    }

    static void reset() {
        cap = 0;
        requested = 0;
    }

    /** What the server should really use for a requested simulation distance. */
    public static int apply(int asked) {
        requested = asked;
        int c = cap;
        if (c > 0 && asked > c) return c;
        return asked;
    }
}
