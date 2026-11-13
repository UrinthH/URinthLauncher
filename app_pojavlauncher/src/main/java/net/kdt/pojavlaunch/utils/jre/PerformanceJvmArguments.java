package net.kdt.pojavlaunch.utils.jre;

import java.util.Iterator;
import java.util.List;

/**
 * The Java arguments Performance Mode adds on top of the player's own. Plain Java with no Android
 * code in it, so it can be tested on a plain JVM.
 *
 * The rule for every flag is the same: it is added only when the player's arguments do not already
 * say something about it, so a flag typed in Settings always wins. None of these change what the
 * game draws, and none of them reserve memory up front.
 *
 * One exception, on purpose: the launcher's own default Java arguments (see
 * {@link #LAUNCHER_DEFAULTS}) are not "something the player said". They are written into Settings
 * once, on a new install, and a player who never opened that field still has them. Three of their
 * values are bad for a phone's 1 GB game heap, so when the field still holds the launcher's block
 * those three values are dropped and the better ones are used instead. A player who changed the
 * block (or typed their own flags) keeps everything they wrote.
 *
 * What the numbers are based on, from a real log of this launcher on a 2.7 GB phone with 118 mods,
 * 1 GB heap, Performance Mode on:
 * <ul>
 * <li>The collections were 33-82 ms long and came every 2-3 seconds, 15 in the first minute. Each
 *     one is 3 to 7 frames at 90 Hz. The old setup forced a young generation of at least 30% of the
 *     heap, so each collection had a large pile to copy, and G1 was allowed up to 100 ms
 *     because the launcher's default block said so (Performance Mode's own 50 ms never applied:
 *     the default block already contained a goal, and existing flags are never overridden).</li>
 * <li>That forced young generation, the 15% reserve and a marking cycle started at 25% occupancy
 *     together left a 1 GB heap with little room for the old generation of a modded game. That is
 *     how a heap runs into back-to-back marking cycles, mixed collections and, in the end, the
 *     multi-second full collection that feels like a freeze.</li>
 * </ul>
 * So the G1 profile asks for short pauses (a smaller goal, which lets G1 size the young generation
 * itself) and puts the reserve and the marking trigger back to the JVM's own defaults. The optional
 * low-pause collector (Shenandoah) does most of its work beside the game instead of stopping it.
 */
final class PerformanceJvmArguments {
    /** The pause goal Performance Mode gives G1. G1's own default is 200 ms. */
    static final int PAUSE_GOAL_MS = 30;

    /**
     * The default Java arguments the launcher writes into Settings on a new install. Kept here as
     * separate tokens so a block that still holds them can be recognised.
     */
    private static final String[] LAUNCHER_DEFAULTS = {
            "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+ParallelRefProcEnabled",
            "-XX:+DisableExplicitGC", "-XX:G1ReservePercent=15",
            "-XX:InitiatingHeapOccupancyPercent=25", "-XX:+UseStringDeduplication"
    };

    /** Of {@link #LAUNCHER_DEFAULTS}, the values Performance Mode replaces when the block is the launcher's. */
    private static final String[] REPLACED_WHEN_LAUNCHER_DEFAULT = {
            "-XX:MaxGCPauseMillis=100", "-XX:G1ReservePercent=15", "-XX:InitiatingHeapOccupancyPercent=25"
    };

    /** A block missing at most this many of the launcher's tokens still counts as the launcher's. */
    private static final int LAUNCHER_DEFAULTS_TOLERANCE = 1;

    private PerformanceJvmArguments() {}

    /**
     * Add the Performance Mode flags to the arguments.
     *
     * @param arguments    the arguments so far; changed in place
     * @param lowPauseWanted true when the player chose the low-pause collector (Shenandoah). The
     *                     caller has already checked that the Java runtime can do it.
     * @return a short description of the collector setup, for the game log
     */
    static String addTo(List<String> arguments, boolean lowPauseWanted) {
        boolean launcherBlock = isLauncherDefaultBlock(arguments);
        if (launcherBlock) removeAll(arguments, REPLACED_WHEN_LAUNCHER_DEFAULT);

        if (hasOtherCollector(arguments, "-XX:+UseG1GC")) {
            // The player named a collector of their own. If the only G1 flag is the one the launcher
            // wrote into its default block, it is the launcher's, not the player's, and it would make
            // the JVM refuse to start ("multiple garbage collectors"). The player's choice wins.
            if (launcherBlock) removeAll(arguments, new String[] {"-XX:+UseG1GC"});
        } else if (lowPauseWanted) {
            // The player asked for the low-pause collector in Settings, and nothing in their
            // arguments names a different one. G1 flags and the G1 collector flag would conflict
            // with it (the JVM refuses two collectors), so they go.
            removeStartingWith(arguments, "-XX:+UseG1GC");
            removeStartingWith(arguments, "-XX:G1");
            removeStartingWith(arguments, "-XX:InitiatingHeapOccupancyPercent");
            removeStartingWith(arguments, "-XX:MaxGCPauseMillis");
            arguments.add("-XX:+UseShenandoahGC");
        } else if (!hasCollectorChoice(arguments)) {
            // With no collector named at all, choose G1 on purpose, so the G1 settings below apply and
            // the result does not depend on how the JVM sizes up the phone ("is this a server?") at start.
            arguments.add("-XX:+UseG1GC");
        }

        // A call to System.gc(), from the game or any of a hundred mods, makes the collector do a full
        // stop-the-world collection, which on a phone's slow cores is a freeze of a quarter second or
        // more. The JVM collects by itself when it needs to; ignoring the explicit request costs nothing.
        if (!hasArgument(arguments, "-XX:+DisableExplicitGC") && !hasArgument(arguments, "-XX:-DisableExplicitGC"))
            arguments.add("-XX:+DisableExplicitGC");

        // Loading a modded game's classes (Fabric, the game and thirty mods are tens of thousands) pushes
        // class metadata past the JVM's small default trigger point again and again, and each crossing
        // starts a collection while the game is still loading. This only moves the trigger point; it
        // does not set aside any memory.
        if (!hasArgument(arguments, "-XX:MetaspaceSize"))
            arguments.add("-XX:MetaspaceSize=128m");

        // The JVM keeps a small statistics file in shared memory and updates it from a timer.
        // Nothing here reads it.
        if (!hasArgument(arguments, "-XX:-UsePerfData") && !hasArgument(arguments, "-XX:+UsePerfData"))
            arguments.add("-XX:-UsePerfData");

        // The weak and soft references a modded game creates are processed on several threads
        // instead of one. Works with every collector Performance Mode uses.
        if (!hasArgument(arguments, "-XX:+ParallelRefProcEnabled") && !hasArgument(arguments, "-XX:-ParallelRefProcEnabled"))
            arguments.add("-XX:+ParallelRefProcEnabled");

        // G1 only: a short pause goal. Without a floor under the young generation, G1 picks its size
        // so that one collection fits the goal. (G1's default goal is 200 ms, which shows up as a freeze.)
        if (hasArgument(arguments, "-XX:+UseG1GC") && !hasArgument(arguments, "-XX:MaxGCPauseMillis"))
            arguments.add("-XX:MaxGCPauseMillis=" + PAUSE_GOAL_MS);

        return describeCollector(arguments);
    }

    /** One short phrase for the game log: which collector the final arguments select. */
    static String describeCollector(List<String> arguments) {
        if (hasArgument(arguments, "-XX:+UseShenandoahGC")) return "Shenandoah (low pause)";
        if (hasArgument(arguments, "-XX:+UseG1GC")) {
            String goal = valueOf(arguments, "-XX:MaxGCPauseMillis=");
            return "G1, pause goal " + (goal == null ? "200 (JVM default)" : goal) + " ms";
        }
        return hasCollectorChoice(arguments) ? "the collector named in Java arguments" : "the JVM's own choice";
    }

    private static String valueOf(List<String> arguments, String prefix) {
        String found = null;
        for (String argument : arguments) {
            if (argument.startsWith(prefix)) found = argument.substring(prefix.length());
        }
        return found;
    }

    /**
     * True when the arguments are still the launcher's own default block: all of its flags, or all
     * but one (a player may have taken one out).
     */
    static boolean isLauncherDefaultBlock(List<String> arguments) {
        int present = 0;
        for (String token : LAUNCHER_DEFAULTS) {
            if (arguments.contains(token)) present++;
        }
        return present >= LAUNCHER_DEFAULTS.length - LAUNCHER_DEFAULTS_TOLERANCE;
    }

    /** True when any argument starts with this text. */
    static boolean hasArgument(List<String> arguments, String prefix) {
        for (String argument : arguments) if (argument.startsWith(prefix)) return true;
        return false;
    }

    /** True when the arguments pick a garbage collector: -XX:+UseG1GC, +UseSerialGC, +UseParallelGC, +UseZGC and so on. */
    static boolean hasCollectorChoice(List<String> arguments) {
        for (String argument : arguments) {
            if (isCollectorFlag(argument)) return true;
        }
        return false;
    }

    /** True when the arguments pick a collector other than the one named. */
    private static boolean hasOtherCollector(List<String> arguments, String named) {
        for (String argument : arguments) {
            if (isCollectorFlag(argument) && !argument.equals(named)) return true;
        }
        return false;
    }

    private static boolean isCollectorFlag(String argument) {
        return argument.startsWith("-XX:+Use") && argument.endsWith("GC");
    }

    private static void removeAll(List<String> arguments, String[] exact) {
        Iterator<String> iterator = arguments.iterator();
        while (iterator.hasNext()) {
            String argument = iterator.next();
            for (String candidate : exact) {
                if (candidate.equals(argument)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    private static void removeStartingWith(List<String> arguments, String prefix) {
        Iterator<String> iterator = arguments.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) iterator.remove();
        }
    }
}
