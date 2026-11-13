package net.kdt.pojavlaunch.utils.jre;

import java.util.List;

/**
 * The Java arguments Performance Mode adds on top of the player's own. Plain Java with no Android
 * code in it, so it can be tested on a plain JVM.
 *
 * The rule for every flag is the same: it is added only when the player's arguments do not already
 * say something about it, so a flag typed in Settings always wins. None of these change what the
 * game draws, and none of them reserve memory up front.
 *
 * What the numbers are based on: a player's frame-time log from this launcher showed that garbage
 * collection explains about three tenths of the time spent in hitches (a typical collection took
 * 20 ms, one in ten took 45 ms or more, the worst 459 ms), and that the heap never came near its
 * limit. So the flags aim at fewer and shorter pauses, not at a bigger heap.
 */
final class PerformanceJvmArguments {
    private PerformanceJvmArguments() {}

    static void addTo(List<String> arguments) {
        // With no collector named at all, choose G1 on purpose, so the G1 settings below apply and the
        // result does not depend on how the JVM sizes up the phone ("is this a server?") at start.
        if (!hasCollectorChoice(arguments)) arguments.add("-XX:+UseG1GC");

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

        // The rest only applies to G1.
        if (!hasArgument(arguments, "-XX:+UseG1GC")) return;
        if (!hasArgument(arguments, "-XX:+UnlockExperimentalVMOptions"))
            arguments.add("-XX:+UnlockExperimentalVMOptions");
        // A larger young generation, so short-lived garbage (the bulk of Minecraft's allocations) is
        // cleared in small quick collections instead of piling up into a long pause.
        if (!hasArgument(arguments, "-XX:G1NewSizePercent"))
            arguments.add("-XX:G1NewSizePercent=30");
        if (!hasArgument(arguments, "-XX:G1MaxNewSizePercent"))
            arguments.add("-XX:G1MaxNewSizePercent=45");
        if (!hasArgument(arguments, "-XX:-UsePerfData") && !hasArgument(arguments, "-XX:+UsePerfData"))
            arguments.add("-XX:-UsePerfData");
        // Ask G1 for short pauses (its default goal is 200 ms, which shows up as a freeze), and
        // let it process the many weak/soft references a modded game creates on several threads.
        if (!hasArgument(arguments, "-XX:MaxGCPauseMillis"))
            arguments.add("-XX:MaxGCPauseMillis=50");
        if (!hasArgument(arguments, "-XX:+ParallelRefProcEnabled") && !hasArgument(arguments, "-XX:-ParallelRefProcEnabled"))
            arguments.add("-XX:+ParallelRefProcEnabled");
    }

    /** True when any argument starts with this text. */
    static boolean hasArgument(List<String> arguments, String prefix) {
        for (String argument : arguments) if (argument.startsWith(prefix)) return true;
        return false;
    }

    /** True when the arguments pick a garbage collector: -XX:+UseG1GC, +UseSerialGC, +UseParallelGC, +UseZGC and so on. */
    static boolean hasCollectorChoice(List<String> arguments) {
        for (String argument : arguments) {
            if (argument.startsWith("-XX:+Use") && argument.endsWith("GC")) return true;
        }
        return false;
    }
}
