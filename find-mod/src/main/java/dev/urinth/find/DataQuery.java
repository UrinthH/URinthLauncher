package dev.urinth.find;

/**
 * One question put to a server with /data: where is this player, and in which dimension. The two answers arrive
 * as two separate chat lines; this collects them. Plain Java with the clock passed in, so it is unit tested
 * without waiting.
 */
public final class DataQuery {
    /** Give up when nothing useful has come back after this long. */
    public static final long TIMEOUT_MS = 3_000L;
    /** Once the position is known, wait this long for the dimension, then answer without it. */
    public static final long GRACE_MS = 1_500L;

    private final String name;
    private final long startedAtMs;
    private double[] position;
    private long positionAtMs;
    private String dimension;

    public DataQuery(String name, long nowMs) {
        this.name = name;
        this.startedAtMs = nowMs;
    }

    public String name() {
        return name;
    }

    /**
     * Takes a printed value if it is a position or a dimension. Returns true when it was one of the two, which
     * means the chat line belongs to this question and should not be shown as it is.
     */
    public boolean accept(String value, long nowMs) {
        double[] spot = DataReply.position(value);
        if (spot != null) {
            if (position == null) {
                position = spot;
                positionAtMs = nowMs;
            }
            return true;
        }
        String id = DataReply.dimension(value);
        if (id != null) {
            if (dimension == null) dimension = id;
            return true;
        }
        return false;
    }

    public boolean hasPosition() {
        return position != null;
    }

    /** True when there is something to show now: everything arrived, or the wait is over. */
    public boolean isDue(long nowMs) {
        if (position != null && dimension != null) return true;
        if (position != null && nowMs - positionAtMs >= GRACE_MS) return true;
        return nowMs - startedAtMs >= TIMEOUT_MS;
    }

    /** What to show: the player's position, or the honest "the server did not tell me". */
    public FindReply reply() {
        if (position == null) return FindReply.noAnswer(name);
        return FindReply.found(new Located(name, position[0], position[1], position[2], dimension));
    }
}
