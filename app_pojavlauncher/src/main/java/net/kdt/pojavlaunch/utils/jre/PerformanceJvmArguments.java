package net.kdt.pojavlaunch.utils.jre;

import net.kdt.pojavlaunch.prefs.DefaultJavaArgs;

import java.util.ArrayList;
import java.util.List;

/**
 * The Java arguments Performance Mode adds on top of the player's own. Plain Java with no Android
 * code in it, so it can be tested on a plain JVM.
 *
 * The rule for every flag is the same: it is added only when the player's arguments do not already
 * say something about it, so a flag typed in Settings always wins. The one exception is the
 * launcher's own default text, which Settings holds from the first start: that is not a choice the
 * player made, so {@link #dropLauncherDefaults} clears the parts of it that fight Performance Mode
 * first. None of these change what the game draws, and none of them reserve memory up front.
 *
 * What the numbers are based on: a player's frame-time log from this launcher showed that garbage
 * collection explains about three tenths of the time spent in hitches (a typical collection took
 * 20 ms, one in ten took 45 ms or more, the worst 459 ms), and that the heap never came near its
 * limit. So the flags aim at fewer and shorter pauses, not at a bigger heap.
 */
final class PerformanceJvmArguments {
    /**
     * The arguments of the launcher's own default text ({@link DefaultJavaArgs}) that work against
     * what Performance Mode is for, with the reason for each:
     * <ul>
     *   <li>MaxGCPauseMillis=100: tells G1 that a 100 ms freeze is fine (Performance Mode asks for 50).</li>
     *   <li>InitiatingHeapOccupancyPercent=25: starts a concurrent marking cycle once old memory passes
     *       a quarter of the maximum heap. A modded game keeps about 230 MB alive, which is right at
     *       that line on a 1 GB heap, so cycles ran back to back in a player's log (one lasted 1.4 s
     *       and had a 234 ms freeze in it). The JVM's own default is 45%, adjusted as it learns.</li>
     *   <li>G1ReservePercent=15: keeps more of the heap unused than the default 10%.</li>
     *   <li>UseStringDeduplication: extra background work on the weak cores, for a few percent of
     *       memory that Performance Mode does not need.</li>
     * </ul>
     */
    private static final String[] REPLACED_LAUNCHER_DEFAULTS = {
            "-XX:MaxGCPauseMillis=100", "-XX:G1ReservePercent=15",
            "-XX:InitiatingHeapOccupancyPercent=25", "-XX:+UseStringDeduplication"
    };

    private PerformanceJvmArguments() {}

    /**
     * Takes out of the arguments the ones above, but only when the whole default text of the
     * launcher is there: then the player never changed it, it is the launcher's own text and not
     * something they chose. A player who edited the text (even one flag) keeps every flag as it
     * is, and a flag typed on its own is never touched. Call before {@link #addTo}, which then adds
     * Performance Mode's own value for each.
     *
     * @return the arguments that were taken out, in order (empty when nothing was)
     */
    static List<String> dropLauncherDefaults(List<String> arguments) {
        List<String> removed = new ArrayList<>();
        for (String token : DefaultJavaArgs.tokens()) {
            if (!arguments.contains(token)) return removed;
        }
        for (String token : REPLACED_LAUNCHER_DEFAULTS) {
            if (arguments.remove(token)) removed.add(token);
        }
        return removed;
    }

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

    /**
     * The heap and collector settings among the arguments, written short ("Xmx1024M", "+UseG1GC",
     * "MaxGCPauseMillis=50") and cut into lines of at most {@code maxLineChars}, for the game log.
     * A log that shows what was really in effect settles any question about which flag won.
     */
    static List<String> describeMemorySettings(List<String> arguments, int maxLineChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String argument : arguments) {
            String shown;
            if (argument.startsWith("-XX:")) shown = argument.substring(4);
            else if (argument.startsWith("-Xms") || argument.startsWith("-Xmx") || argument.startsWith("-Xmn"))
                shown = argument.substring(1);
            else continue;
            if (line.length() > 0 && line.length() + 1 + shown.length() > maxLineChars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(shown);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
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
