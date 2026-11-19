package net.kdt.pojavlaunch.utils.jre;

import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.AUTO;

import net.kdt.pojavlaunch.prefs.AdvancedPerformance;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Advanced settings do to the JVM command line. Plain Java with no Android code in it, so it can
 * be tested on a plain JVM.
 *
 * Same rule as {@link PerformanceJvmArguments}: a flag the player typed in the Java arguments always wins,
 * so each flag below is added only when the arguments say nothing about it. Only choices that differ from
 * what Performance Mode does anyway are written out; "auto" and the default of a switch add nothing and
 * leave the decision to {@link PerformanceJvmArguments#addTo}, which runs afterwards and fills in what is
 * still missing. That keeps a player who never opens the screen on exactly the arguments of before.
 *
 * Flags that only exist for one collector (G1's young generation, region size, reserve, string
 * deduplication) are written only when G1 is the collector in effect, because the JVM refuses to start on
 * some of them with another one.
 */
final class AdvancedJvmArguments {
    private static final String COLLECTOR_G1 = "g1";
    private static final String COLLECTOR_PARALLEL = "parallel";
    private static final String COLLECTOR_SERIAL = "serial";
    /** A collector the player picked in the Java arguments that this screen does not tune (ZGC, Shenandoah...). */
    private static final String COLLECTOR_OTHER = "other";
    /** The code space the simple JIT gets when nothing else was said, the size the full JIT gets from the JVM. */
    static final int SIMPLE_JIT_CODE_CACHE_MB = 240;

    private AdvancedJvmArguments() {}

    /**
     * The heap the JVM starts with, in MB. Auto is what Performance Mode always did (half the maximum, at
     * least 256 MB; 256 MB outside it); "small" is 256 MB and "max" the maximum. Never more than the maximum.
     */
    static int initialHeapMb(AdvancedPerformance settings, boolean performanceMode, int maxHeapMb) {
        int initial;
        switch (settings.choice(AdvancedPerformance.KEY_INITIAL_HEAP)) {
            case "small":
                initial = 256;
                break;
            case "max":
                initial = maxHeapMb;
                break;
            default:
                initial = performanceMode ? Math.max(256, maxHeapMb / 2) : 256;
        }
        return Math.min(initial, maxHeapMb);
    }

    /**
     * How many CPU cores the JVM is told about. The JVM sizes its garbage collector and compiler threads
     * from this, and Minecraft sizes its background worker pool from it (cores minus one), so fewer cores
     * means fewer threads competing with the render thread. Never more than the phone has, never less than 1.
     */
    static int activeProcessors(AdvancedPerformance settings, int availableProcessors) {
        int available = Math.max(1, availableProcessors);
        int wanted = settings.number(AdvancedPerformance.KEY_CORES);
        if (wanted < 1) return available;
        return Math.min(wanted, available);
    }

    /** True when the simple (C1 only) JIT is in effect, by the player's choice or because Anti Crash forced it. */
    static boolean simpleJit(AdvancedPerformance settings, boolean forcedByAntiCrash) {
        return forcedByAntiCrash || "c1".equals(settings.choice(AdvancedPerformance.KEY_JIT));
    }

    /**
     * Writes the flags for the choices that differ from Performance Mode's own, into {@code arguments}.
     * Call after {@link PerformanceJvmArguments#dropLauncherDefaults} and before {@link PerformanceJvmArguments#addTo}.
     *
     * @param forceSimpleJit Anti Crash found that the last crash happened in Java's optimising compiler
     * @return the flags that were added, in order, for the game log
     */
    static List<String> addTo(List<String> arguments, AdvancedPerformance settings, boolean forceSimpleJit) {
        List<String> added = new ArrayList<>();

        // Has to be the very first flag: the JVM only skips a flag it does not know once it has read this one.
        if (settings.on(AdvancedPerformance.KEY_IGNORE_UNKNOWN_FLAGS)
                && !has(arguments, "-XX:+IgnoreUnrecognizedVMOptions")
                && !has(arguments, "-XX:-IgnoreUnrecognizedVMOptions")) {
            arguments.add(0, "-XX:+IgnoreUnrecognizedVMOptions");
            added.add("-XX:+IgnoreUnrecognizedVMOptions");
        }

        String collector = collectorInEffect(arguments, settings, added);
        boolean g1 = COLLECTOR_G1.equals(collector);
        boolean tunable = g1 || COLLECTOR_PARALLEL.equals(collector);

        // ---- garbage collection
        if (tunable) {
            int pause = settings.number(AdvancedPerformance.KEY_PAUSE_GOAL);
            if (pause > 0) put(arguments, added, "-XX:MaxGCPauseMillis", "-XX:MaxGCPauseMillis=" + pause);
            int threads = settings.number(AdvancedPerformance.KEY_GC_THREADS);
            if (threads > 0) put(arguments, added, "-XX:ParallelGCThreads", "-XX:ParallelGCThreads=" + threads);
        }
        if (g1) {
            String young = settings.choice(AdvancedPerformance.KEY_YOUNG_GEN);
            if (!AUTO.equals(young)) {
                // The two young generation flags are experimental ones: the unlock has to come before them.
                if (!has(arguments, "-XX:+UnlockExperimentalVMOptions")) {
                    arguments.add("-XX:+UnlockExperimentalVMOptions");
                    added.add("-XX:+UnlockExperimentalVMOptions");
                }
                boolean small = "small".equals(young);
                put(arguments, added, "-XX:G1NewSizePercent", "-XX:G1NewSizePercent=" + (small ? 20 : 40));
                put(arguments, added, "-XX:G1MaxNewSizePercent", "-XX:G1MaxNewSizePercent=" + (small ? 35 : 60));
            }
            int region = settings.number(AdvancedPerformance.KEY_REGION_SIZE);
            if (region > 0) put(arguments, added, "-XX:G1HeapRegionSize", "-XX:G1HeapRegionSize=" + region + "m");
            int reserve = settings.number(AdvancedPerformance.KEY_G1_RESERVE);
            if (reserve > 0) put(arguments, added, "-XX:G1ReservePercent", "-XX:G1ReservePercent=" + reserve);
            if (settings.on(AdvancedPerformance.KEY_STRING_DEDUP)) {
                putSwitch(arguments, added, "-XX:UseStringDeduplication", "-XX:+UseStringDeduplication");
            }
        }
        // Performance Mode ignores System.gc() and processes references in parallel; only turning them off is written.
        if (!settings.on(AdvancedPerformance.KEY_IGNORE_SYSTEM_GC)) {
            putSwitch(arguments, added, "-XX:DisableExplicitGC", "-XX:-DisableExplicitGC");
        }
        if (!settings.on(AdvancedPerformance.KEY_PARALLEL_REFS)) {
            putSwitch(arguments, added, "-XX:ParallelRefProcEnabled", "-XX:-ParallelRefProcEnabled");
        }

        // ---- memory
        if (settings.on(AdvancedPerformance.KEY_PRE_TOUCH)) {
            putSwitch(arguments, added, "-XX:AlwaysPreTouch", "-XX:+AlwaysPreTouch");
        }
        int metaspace = settings.number(AdvancedPerformance.KEY_METASPACE);
        if (metaspace > 0) put(arguments, added, "-XX:MetaspaceSize", "-XX:MetaspaceSize=" + metaspace + "m");

        // ---- JIT
        // A tiering flag typed in the Java arguments wins, and then the simple JIT is not in effect at all.
        boolean tiersChosenByPlayer = has(arguments, "-XX:TieredStopAtLevel") || has(arguments, "-XX:-TieredCompilation")
                || has(arguments, "-XX:+TieredCompilation");
        boolean simple = simpleJit(settings, forceSimpleJit) && !tiersChosenByPlayer;
        if (simple) {
            arguments.add("-XX:TieredStopAtLevel=1");
            added.add("-XX:TieredStopAtLevel=1");
        }
        int codeCache = settings.number(AdvancedPerformance.KEY_CODE_CACHE);
        if (codeCache > 0) {
            put(arguments, added, "-XX:ReservedCodeCacheSize", "-XX:ReservedCodeCacheSize=" + codeCache + "m");
        } else if (simple) {
            // With only the simple compiler the JVM may pick a much smaller code space (a modded game with
            // over a hundred mods fills it quickly, and a full one stops all compiling for good).
            put(arguments, added, "-XX:ReservedCodeCacheSize",
                    "-XX:ReservedCodeCacheSize=" + SIMPLE_JIT_CODE_CACHE_MB + "m");
        }
        int jitThreads = settings.number(AdvancedPerformance.KEY_JIT_THREADS);
        if (jitThreads > 0) put(arguments, added, "-XX:CICompilerCount", "-XX:CICompilerCount=" + jitThreads);

        // ---- network
        if (settings.on(AdvancedPerformance.KEY_IPV4)) {
            put(arguments, added, "-Djava.net.preferIPv4Stack", "-Djava.net.preferIPv4Stack=true");
        }
        return added;
    }

    /**
     * The collector the game will run with, writing the player's choice when the arguments name none. A
     * collector named in the Java arguments always wins over the screen.
     */
    private static String collectorInEffect(List<String> arguments, AdvancedPerformance settings, List<String> added) {
        if (PerformanceJvmArguments.hasCollectorChoice(arguments)) {
            if (has(arguments, "-XX:+UseG1GC")) return COLLECTOR_G1;
            if (has(arguments, "-XX:+UseParallelGC")) return COLLECTOR_PARALLEL;
            if (has(arguments, "-XX:+UseSerialGC")) return COLLECTOR_SERIAL;
            return COLLECTOR_OTHER;
        }
        String chosen = settings.choice(AdvancedPerformance.KEY_GC);
        switch (chosen) {
            case COLLECTOR_PARALLEL:
                arguments.add("-XX:+UseParallelGC");
                added.add("-XX:+UseParallelGC");
                return COLLECTOR_PARALLEL;
            case COLLECTOR_SERIAL:
                arguments.add("-XX:+UseSerialGC");
                added.add("-XX:+UseSerialGC");
                return COLLECTOR_SERIAL;
            case COLLECTOR_G1:
                arguments.add("-XX:+UseG1GC");
                added.add("-XX:+UseG1GC");
                return COLLECTOR_G1;
            default:
                // Auto: Performance Mode picks G1 on purpose (PerformanceJvmArguments.addTo).
                return COLLECTOR_G1;
        }
    }

    /** Adds {@code flag} unless the arguments already have one starting with {@code prefix}. */
    private static void put(List<String> arguments, List<String> added, String prefix, String flag) {
        if (has(arguments, prefix)) return;
        arguments.add(flag);
        added.add(flag);
    }

    /** Like {@link #put} for an on/off flag: the player may have typed either form, and either one counts. */
    private static void putSwitch(List<String> arguments, List<String> added, String name, String flag) {
        if (has(arguments, "-XX:+" + name.substring("-XX:".length()))
                || has(arguments, "-XX:-" + name.substring("-XX:".length()))) return;
        arguments.add(flag);
        added.add(flag);
    }

    private static boolean has(List<String> arguments, String prefix) {
        return PerformanceJvmArguments.hasArgument(arguments, prefix);
    }
}
