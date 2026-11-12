package dev.urinth.boost;

/**
 * Counts what the hooks have done, so /uboost can show it and the log can say it. For every hook two numbers:
 * how many times the game reached it ("calls"; a hook that was never reached did not apply, and this is how the
 * player and the developer can see that) and how many times it took something away ("acted").
 *
 * Plain counters with no locking: the hooks and the readers all run on the game's main thread, and a lost count
 * would not matter anyway.
 */
public final class BoostStats {
    public static final int PARTICLE = 0;
    public static final int ENTITY = 1;
    public static final int NAME_TAG = 2;
    public static final int LAYERS = 3;
    public static final int PLAYER = 4;
    public static final int HOOKS = 5;

    private final long[] calls = new long[HOOKS];
    private final long[] acted = new long[HOOKS];

    public void call(int hook) {
        calls[hook]++;
    }

    public void act(int hook) {
        acted[hook]++;
    }

    public long calls(int hook) {
        return calls[hook];
    }

    public long acted(int hook) {
        return acted[hook];
    }

    public BoostStats copy() {
        BoostStats copy = new BoostStats();
        System.arraycopy(calls, 0, copy.calls, 0, HOOKS);
        System.arraycopy(acted, 0, copy.acted, 0, HOOKS);
        return copy;
    }

    /** What happened since {@code earlier} was copied. */
    public BoostStats since(BoostStats earlier) {
        BoostStats diff = new BoostStats();
        for (int i = 0; i < HOOKS; i++) {
            diff.calls[i] = calls[i] - earlier.calls[i];
            diff.acted[i] = acted[i] - earlier.acted[i];
        }
        return diff;
    }

    /** True when any hook acted at all. */
    public boolean anyActed() {
        for (long n : acted) {
            if (n > 0) return true;
        }
        return false;
    }
}
