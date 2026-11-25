package dev.urinth.sunflare;

/**
 * What the "URinth" page of the video settings offers: the names, the choices of every row and the text that explains it. Pure
 * logic (no Minecraft or Sodium classes) so it is unit tested; {@link UrinthPage} turns it into Sodium's rows. The choices are the
 * ones {@link FlareConfig} already has, in the same order, so a row can never show a value the mod does not understand.
 */
public final class PageChoices {
    /** The name of the page in the list on the left of the video settings. */
    public static final String PAGE_NAME = "URinth";

    public static final String FLARE_NAME = "Sun Flare";
    public static final String FLARE_TIP = "Bright glare around the sun when you look at it. Subtle is the cheapest, Strong is the most "
        + "visible. It costs a little GPU time.";

    public static final String BEAMS_NAME = "Sun Beams";
    public static final String BEAMS_TIP = "Light shafts that stream from the sun through the air. The higher levels are smoother but "
        + "cost more FPS. Perf Guard turns them down by itself when the frame rate gets too low.";

    public static final String STYLE_NAME = "Flare Style";
    public static final String STYLE_TIP = "Real is a soft, camera-like glare. Pack is the colourful look of the original shader pack.";

    public static final String LEAVES_NAME = "Fancy Leaves";
    public static final String LEAVES_TIP = "Does not draw the leaf faces that are buried inside a tree, so the game has less to draw. "
        + "Light skips the most, Safe skips the least and is the safest. The world is rebuilt when you change it.";

    public static final String CLOUDS_NAME = "Cloud Fix";
    public static final String CLOUDS_TIP = "Clouds vanish at a short render distance: the game cuts off everything beyond 4 times it, and "
        + "clouds are high up. This gives only the clouds a longer reach. It can cost a little FPS when you look up.";

    public static final String HELPERS_NAME = "Chunk Helpers";
    public static final String HELPERS_TIP = "How many background helpers build the blocks of the world. More helpers show new chunks "
        + "sooner but use more CPU while you move. Auto is Sodium's number, or 3 on phones with 6+ cores. Reloads the world.";

    public static final String UPDATES_NAME = "Block Updates";
    public static final String UPDATES_TIP = "How fast blocks you place or break change on screen. Instant shows them in the same frame "
        + "(that frame waits a few ms). Smooth is Sodium's way. Auto is instant while your FPS is good.";

    public static final String GUARD_NAME = "Perf Guard";
    public static final String GUARD_TIP = "Keeps the sun effects from pulling your FPS below the number you pick: when the frame rate "
        + "gets lower, they become cheaper or pause. Off lets them cost any amount.";

    public static final String LOG_NAME = "Stutter Log";
    public static final String LOG_TIP = "Writes slow frames to logs/urinth-perf.log, with the memory cleanup and CPU numbers next to "
        + "them, so the cause of a stutter can be found. It costs nothing while the game runs smoothly.";

    private PageChoices() {}

    /** A choice that has a name to show on its row. */
    public interface Labeled {
        String label();
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }

    /** Sun Flare: {@link FlareConfig#NAMES}. */
    public enum Flare implements Labeled {
        OFF, SUBTLE, FULL, STRONG;

        @Override
        public String label() {
            return FlareConfig.NAMES[ordinal()];
        }

        public static Flare of(int mode) {
            return values()[clamp(mode, values().length)];
        }
    }

    /** Sun Beams: {@link FlareConfig#BEAM_NAMES}. */
    public enum Beams implements Labeled {
        OFF, LOW, MEDIUM, HIGH, EXTREME;

        @Override
        public String label() {
            return FlareConfig.BEAM_NAMES[ordinal()];
        }

        public static Beams of(int level) {
            return values()[clamp(level, values().length)];
        }
    }

    /** Flare Style: Real or Pack. */
    public enum Style implements Labeled {
        REAL("Real"), PACK("Pack");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }

        public static Style of(boolean real) {
            return real ? REAL : PACK;
        }

        public boolean isReal() {
            return this == REAL;
        }
    }

    /** Fancy Leaves: {@link FlareConfig#LEAVES_NAMES}. */
    public enum Leaves implements Labeled {
        OFF, LIGHT, BALANCED, SAFE;

        @Override
        public String label() {
            return FlareConfig.LEAVES_NAMES[ordinal()];
        }

        public static Leaves of(int level) {
            return values()[clamp(level, values().length)];
        }
    }

    /** Chunk Helpers: the settings in {@link ChunkThreads#CHOICES}, in the same order (the name of each constant says which). */
    public enum Helpers implements Labeled {
        AUTO, TWO, THREE, FOUR, SIX;

        @Override
        public String label() {
            return ChunkThreads.label(count());
        }

        /** The setting this choice stands for ({@link ChunkThreads#AUTO} = 0 for Auto). */
        public int count() {
            return ChunkThreads.CHOICES[ordinal()];
        }

        /** The choice closest to a number of helpers (a setting file may hold any number). */
        public static Helpers nearest(int count) {
            int wanted = ChunkThreads.nearest(count);
            for (Helpers h : values()) {
                if (h.count() == wanted) return h;
            }
            return AUTO;
        }
    }

    /** Block Updates: {@link BlockUpdates#NAMES}. */
    public enum Updates implements Labeled {
        SMOOTH, AUTO, INSTANT;

        @Override
        public String label() {
            return BlockUpdates.NAMES[ordinal()];
        }

        public static Updates of(int mode) {
            return values()[clamp(mode, values().length)];
        }
    }

    /** Perf Guard: the frame rates in {@link FlareConfig#FPS_CHOICES}, in the same order (the name of each constant says which). */
    public enum Guard implements Labeled {
        OFF, FPS_60, FPS_72, FPS_80, FPS_90, FPS_120, FPS_144, FPS_200, FPS_250;

        @Override
        public String label() {
            return FlareConfig.fpsName(fps());
        }

        /** The frame rate this choice stands for (0 = Off). */
        public int fps() {
            return FlareConfig.FPS_CHOICES[ordinal()];
        }

        /** The choice closest to a frame rate, which may have been set to any number with /sunflare fps. 0 is Off. */
        public static Guard nearest(int fps) {
            if (fps <= 0) return OFF;
            Guard best = FPS_60;
            int bestGap = Integer.MAX_VALUE;
            for (Guard g : values()) {
                if (g == OFF) continue;
                int gap = Math.abs(g.fps() - fps);
                if (gap < bestGap) {
                    best = g;
                    bestGap = gap;
                }
            }
            return best;
        }
    }
}
