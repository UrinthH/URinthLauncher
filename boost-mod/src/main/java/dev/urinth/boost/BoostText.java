package dev.urinth.boost;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words /uboost shows, as plain lines of text, so what the mod tells the player is covered by unit tests too.
 * The game side only turns each line into a chat message.
 */
public final class BoostText {
    private BoostText() {}

    public static final String COMMANDS =
            "/uboost auto | off | light | strong | max   /uboost target <fps>   /uboost bench";

    /** What /uboost says about the state of the mod. */
    public static List<String> status(BoostRuntime runtime) {
        List<String> lines = new ArrayList<>();
        String problem = runtime.problem();
        if (problem != null) {
            lines.add("URinth Boost switched itself off until the game is restarted, so nothing is taken away.");
            lines.add("Reason: " + problem);
            return lines;
        }
        BoostMode mode = runtime.mode();
        int level = runtime.level();
        lines.add("URinth Boost: " + (mode.isAuto()
                ? "automatic, level now " + BoostLevel.name(level)
                : "held at " + mode.word()));
        String fps = runtime.lastFps() > 0 ? runtime.lastFps() + " fps" : "no frame rate reading yet";
        if (mode.isAuto()) {
            lines.add(fps + ", aim " + runtime.target() + ". It only takes things away while the frame rate is below that.");
        } else {
            lines.add(fps);
        }
        lines.add(describe(runtime.limits()));
        lines.add(counts(runtime.stats()));
        if (mode.isAuto() && !runtime.governorNote().isEmpty()) {
            lines.add("Last decision: " + runtime.governorNote());
        }
        lines.add(COMMANDS);
        return lines;
    }

    /** What a level takes away, in words. Distances are in blocks. */
    public static String describe(Limits limits) {
        if (limits.level == BoostLevel.OFF) return "Nothing is taken away right now. Everything is drawn as the game normally does.";
        List<String> parts = new ArrayList<>();
        if (limits.particlesPerTick != Limits.UNLIMITED) {
            parts.add("at most " + limits.particlesPerTick + " new particles per tick");
        }
        add(parts, "new particles beyond ", limits.particleDistSq);
        add(parts, "dropped items beyond ", limits.itemDistSq);
        add(parts, "armor stands beyond ", limits.standDistSq);
        add(parts, "small mobs beyond ", limits.mobDistSq);
        add(parts, "name tags beyond ", limits.nameTagDistSq);
        add(parts, "armor and held items of far players and mobs beyond ", limits.layerDistSq);
        return "Taking away: " + String.join("; ", parts) + ". Players themselves are always drawn.";
    }

    private static void add(List<String> parts, String what, double distSq) {
        double blocks = Limits.blocks(distSq);
        if (!Double.isInfinite(blocks)) parts.add(what + Math.round(blocks) + " blocks");
    }

    /** How often each hook was reached and how often it took something away. */
    public static String counts(BoostStats stats) {
        return "Checked / left out since the game started: particles " + pair(stats, BoostStats.PARTICLE)
                + ", entities " + pair(stats, BoostStats.ENTITY)
                + ", name tags " + pair(stats, BoostStats.NAME_TAG)
                + ", armor " + pair(stats, BoostStats.LAYERS) + ".";
    }

    private static String pair(BoostStats stats, int hook) {
        return String.format(Locale.ROOT, "%,d / %,d", stats.calls(hook), stats.acted(hook));
    }

    /** The lines shown when the /uboost bench test is over. */
    public static List<String> benchResult(Bench.Result result) {
        List<String> lines = new ArrayList<>();
        if (result.offAverage <= 0 || result.maxAverage <= 0) {
            lines.add("Boost test: no frame rate readings were taken, so there is no result. Try again.");
            return lines;
        }
        lines.add("Boost test finished.");
        lines.add("Boost off: " + whole(result.offAverage) + " fps (between " + result.offLowest + " and " + result.offHighest
                + ").  Boost max: " + whole(result.maxAverage) + " fps (between " + result.maxLowest + " and "
                + result.maxHighest + ").");
        if (result.unstable()) {
            lines.add("The picture changed while the test ran (people moving, a fight, loading), so this comparison is not "
                    + "reliable. Stand still in the same place and run it again.");
        } else if (result.noGain()) {
            lines.add("No measurable difference here: most likely what limits your frame rate in this spot is not "
                    + "particles or far entities, so Boost has nothing to take away that would help. Try it where it is "
                    + "crowded.");
        } else {
            lines.add("Boost max was " + String.format(Locale.ROOT, "%.0f%%", result.gain * 100)
                    + " faster here. This is one spot at one moment, not a promise for everywhere.");
        }
        return lines;
    }

    /** The line shown above the hotbar while the bench test runs. */
    public static String benchProgress(int secondsLeft, int level) {
        return "Boost test: " + secondsLeft + " s left, stand still (now " + BoostLevel.name(level) + ")";
    }

    private static String whole(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
