package dev.urinth.boost;

import java.util.Locale;

/**
 * Finds out where the big frame rate drops come from, instead of guessing. When the frame rate falls below half of the
 * rate the mod aims for ({@value #START_PERCENT} percent), this notes how long it takes to get back near the aim, and what
 * else was going on in that time: memory collections (how many, how long), how full the memory was, how many entities
 * and players were drawn per frame. One line is written when the frame rate is back (or, for a long one, every minute
 * while it lasts), so a game log shows at a glance whether a freeze was memory, a crowd, or neither.
 *
 * Plain Java, fed once a second by {@link BoostRuntime} and unit tested. It writes nothing itself and keeps nothing
 * but a few numbers; the number of lines per game start is limited so a log cannot fill up.
 */
public final class LagWatch {
    /** A reading below this share of the aim starts an episode. */
    static final double START_FACTOR = 0.5;
    static final int START_PERCENT = 50;
    /** An episode is over when the frame rate is back at this share of the aim. */
    static final double END_FACTOR = 0.9;
    /** The most lines one game start writes. */
    static final int MAX_LINES = 30;
    /** A long episode says how it is going after this long, and then once a minute. */
    static final long FIRST_PROGRESS_MS = 10_000;
    static final long PROGRESS_EVERY_MS = 60_000;
    /** Collections that took at least this much (and a fifth of the episode) are named as the likely cause. */
    static final long MIN_GC_MS = 250;
    static final double GC_SHARE = 0.2;
    /** Memory that is this full (percent of the maximum) is named when nothing else is. */
    static final int FULL_HEAP_PERCENT = 93;
    /** This many entities drawn per frame is a crowd. */
    static final int CROWD_ENTITIES = 150;
    /** The tag every line starts with. The launcher looks for it when it copies the game log. */
    public static final String TAG = "[URinth Lag] ";

    /** What the game side can tell right now. Numbers that are not known stay at -1 and are left out of the line. */
    public static final class Reading {
        /** Memory collections since the game started: how many, and how many milliseconds they took. */
        public long gcCount = -1;
        public long gcMillis = -1;
        /** Memory in use and the most it can grow to, in MB. */
        public long heapUsedMb = -1;
        public long heapMaxMb = -1;
        /** How often the entity and player hooks have been reached since the game started. */
        public long entityCalls = -1;
        public long playerCalls = -1;

        void clear() {
            gcCount = -1;
            gcMillis = -1;
            heapUsedMb = -1;
            heapMaxMb = -1;
            entityCalls = -1;
            playerCalls = -1;
        }
    }

    private boolean havePrevious;
    private long prevGcCount;
    private long prevGcMillis;
    private long prevEntityCalls;
    private long prevPlayerCalls;
    private long windowGcCount;
    private long windowGcMillis;

    private boolean active;
    private long startedAt;
    private long nextProgressAt;
    private int lowest;
    private boolean gcKnown;
    private long gcCount;
    private long gcMillis;
    private int heapPercent;
    private long heapMaxMb;
    private long entities;
    private long players;

    private int lines;
    private int episodes;
    private int worst;
    private String last = "";

    /**
     * One observation, about once a second.
     *
     * @param nowMs a clock in milliseconds that only goes forward
     * @param fps the frame rate the game reports; zero or less (nothing measured yet) is ignored
     * @param target the frame rate Boost aims for
     * @param levelName the name of the Boost level in use now
     * @param reading the numbers from the game; cumulative counters, as they stand now
     * @return the line to write to the log, or null when there is nothing to say
     */
    public String sample(long nowMs, int fps, int target, String levelName, Reading reading) {
        if (fps <= 0 || target <= 0) return null;

        long gcN = delta(reading.gcCount, prevGcCount);
        long gcMs = delta(reading.gcMillis, prevGcMillis);
        long entityCalls = delta(reading.entityCalls, prevEntityCalls);
        long playerCalls = delta(reading.playerCalls, prevPlayerCalls);
        boolean knowsGc = reading.gcCount >= 0 && reading.gcMillis >= 0;
        long beforeGcN = windowGcCount;
        long beforeGcMs = windowGcMillis;
        windowGcCount = gcN;
        windowGcMillis = gcMs;
        prevGcCount = reading.gcCount;
        prevGcMillis = reading.gcMillis;
        prevEntityCalls = reading.entityCalls;
        prevPlayerCalls = reading.playerCalls;
        boolean hadPrevious = havePrevious;
        havePrevious = true;
        if (!hadPrevious) {
            // The first reading has nothing to be compared with: its counters are everything since the game started.
            gcN = 0;
            gcMs = 0;
            entityCalls = 0;
            playerCalls = 0;
            windowGcCount = 0;
            windowGcMillis = 0;
        }
        long entitiesPerFrame = Math.round((double) entityCalls / fps);
        long playersPerFrame = Math.round((double) playerCalls / fps);
        int heap = reading.heapMaxMb > 0 && reading.heapUsedMb >= 0
                ? (int) Math.min(100, reading.heapUsedMb * 100 / reading.heapMaxMb) : -1;

        boolean low = fps < target * START_FACTOR;
        if (!active) {
            if (!low) return null;
            active = true;
            startedAt = nowMs;
            nextProgressAt = nowMs + FIRST_PROGRESS_MS;
            lowest = fps;
            episodes++;
            worst = episodes == 1 ? fps : Math.min(worst, fps);
            gcKnown = knowsGc;
            // The frame rate the game reports lags a little behind, so the second before counts as part of it.
            gcCount = beforeGcN + gcN;
            gcMillis = beforeGcMs + gcMs;
            heapPercent = Math.max(0, heap);
            heapMaxMb = Math.max(0, reading.heapMaxMb);
            entities = entitiesPerFrame;
            players = playersPerFrame;
            return null;
        }

        lowest = Math.min(lowest, fps);
        worst = Math.min(worst, fps);
        gcKnown |= knowsGc;
        gcCount += gcN;
        gcMillis += gcMs;
        heapPercent = Math.max(heapPercent, heap);
        heapMaxMb = Math.max(heapMaxMb, reading.heapMaxMb);
        entities = Math.max(entities, entitiesPerFrame);
        players = Math.max(players, playersPerFrame);

        if (fps >= target * END_FACTOR) {
            String line = describe(nowMs, fps, target, levelName, true);
            finish();
            return line;
        }
        if (nowMs >= nextProgressAt) {
            nextProgressAt = nowMs + PROGRESS_EVERY_MS;
            return describe(nowMs, fps, target, levelName, false);
        }
        return null;
    }

    /** The player left the world: an episode that was going on is dropped, with no line. */
    public void leaveWorld() {
        active = false;
        havePrevious = false;
        windowGcCount = 0;
        windowGcMillis = 0;
    }

    /** How many times the frame rate fell below half of the aim (an episode that is still going counts). */
    public int episodes() {
        return episodes;
    }

    /** The lowest frame rate of all the episodes, or 0 when there was none. */
    public int worstFps() {
        return episodes == 0 ? 0 : worst;
    }

    /** The last line that was written; empty until there was one. */
    public String last() {
        return last;
    }

    private void finish() {
        active = false;
    }

    private String describe(long nowMs, int fps, int target, String levelName, boolean over) {
        if (lines >= MAX_LINES) return null;
        long durationMs = Math.max(1000, nowMs - startedAt);
        long seconds = Math.round(durationMs / 1000.0);
        StringBuilder text = new StringBuilder(TAG);
        if (over) {
            text.append("lasted ").append(seconds).append(" s, lowest ").append(lowest).append(" fps, back to ")
                    .append(fps).append(" fps (aim ").append(target).append(')');
        } else {
            text.append("still going after ").append(seconds).append(" s, lowest ").append(lowest).append(" fps, now ")
                    .append(fps).append(" fps (aim ").append(target).append(')');
        }
        if (gcKnown) {
            text.append("; memory collections: ").append(gcCount).append(" (").append(gcMillis).append(" ms)");
        }
        if (heapPercent > 0 && heapMaxMb > 0) {
            text.append("; memory up to ").append(heapPercent).append("% of ").append(heapMaxMb).append(" MB");
        }
        text.append("; drawn per frame up to ").append(entities).append(" entities, ").append(players).append(" players");
        if (levelName != null && !levelName.isEmpty()) {
            text.append("; Boost ").append(levelName.toLowerCase(Locale.ROOT));
        }
        text.append("; likely: ").append(cause(durationMs));
        lines++;
        if (lines == MAX_LINES) text.append(" (the last lag line of this game start)");
        last = text.toString();
        return last;
    }

    private String cause(long durationMs) {
        if (gcKnown && gcMillis >= MIN_GC_MS && gcMillis >= durationMs * GC_SHARE) return "memory collections";
        if (heapPercent >= FULL_HEAP_PERCENT) return "memory nearly full";
        if (entities >= CROWD_ENTITIES) return "a crowd of entities";
        if (!gcKnown && heapPercent <= 0) return "cannot tell, no memory numbers";
        return "not memory and not a crowd (chunk loading, shaders or heat)";
    }

    private static long delta(long current, long previous) {
        if (current < 0 || previous < 0) return 0;
        return Math.max(0, current - previous);
    }
}
