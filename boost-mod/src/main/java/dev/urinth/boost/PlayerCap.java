package dev.urinth.boost;

import java.util.Arrays;

/**
 * Decides which players are left undrawn when only the nearest few are to be drawn. The crowd around you is what
 * costs the most to draw (every player is a body, armor, held items and a name), so in a crowd drawing only the
 * nearest ones gives the biggest gain, and in a world with nobody else it changes nothing.
 *
 * A player who was drawn a moment ago stays drawn while he is only a few places beyond the limit, so two players at
 * about the same distance do not flicker in and out as they move. Ties at the limit are all drawn.
 */
public final class PlayerCap {
    /** A player drawn at the last update stays drawn while he is at most this many places beyond the limit. */
    static final int SLACK = 3;
    private static final int[] NONE = new int[0];

    private int[] visible = NONE; // sorted ids drawn at the last update
    private volatile int[] hidden = NONE; // sorted ids left out

    /** Same as {@link #update(int, int[], double[], int)} with every entry of the two arrays. */
    public void update(int cap, int[] ids, double[] distSq) {
        update(cap, ids, distSq, Math.min(ids.length, distSq.length));
    }

    /**
     * @param cap how many players to draw; zero or less means all of them
     * @param ids the entity ids of the other players
     * @param distSq the squared distance of each of them, in the same order
     * @param count how many entries of the two arrays are to be used
     */
    public void update(int cap, int[] ids, double[] distSq, int count) {
        int n = Math.max(0, Math.min(count, Math.min(ids.length, distSq.length)));
        if (cap <= 0) {
            visible = NONE;
            hidden = NONE;
            return;
        }
        if (n <= cap) {
            visible = sortedCopy(ids, n);
            hidden = NONE;
            return;
        }
        // A distance that is not a number counts as very far, so it can never push a near player out.
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = Double.isNaN(distSq[i]) ? Double.POSITIVE_INFINITY : distSq[i];
        double[] sorted = d.clone();
        Arrays.sort(sorted);
        double limit = sorted[cap - 1];
        double slackLimit = sorted[Math.min(n, cap + SLACK) - 1];
        int[] draw = new int[n];
        int[] leave = new int[n];
        int drawn = 0;
        int left = 0;
        for (int i = 0; i < n; i++) {
            boolean keep = d[i] <= limit || (d[i] <= slackLimit && contains(visible, ids[i]));
            if (keep) {
                draw[drawn++] = ids[i];
            } else {
                leave[left++] = ids[i];
            }
        }
        visible = sortedCopy(draw, drawn);
        hidden = sortedCopy(leave, left);
    }

    /** Back to drawing everybody. */
    public void clear() {
        visible = NONE;
        hidden = NONE;
    }

    /** True when this player is one of those left out. */
    public boolean isHidden(int id) {
        int[] h = hidden;
        return h.length != 0 && Arrays.binarySearch(h, id) >= 0;
    }

    public int hiddenCount() {
        return hidden.length;
    }

    private static boolean contains(int[] sortedIds, int id) {
        return sortedIds.length != 0 && Arrays.binarySearch(sortedIds, id) >= 0;
    }

    private static int[] sortedCopy(int[] ids, int count) {
        int[] copy = Arrays.copyOf(ids, count);
        Arrays.sort(copy);
        return copy;
    }
}
