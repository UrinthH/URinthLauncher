package net.kdt.pojavlaunch.utils.jre;

import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_CODE_CACHE;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_CORES;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_GC;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_GC_THREADS;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_IGNORE_SYSTEM_GC;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_IGNORE_UNKNOWN_FLAGS;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_INITIAL_HEAP;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_IPV4;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_JIT;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_JIT_THREADS;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_G1_RESERVE;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_METASPACE;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_PARALLEL_REFS;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_PAUSE_GOAL;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_PRE_TOUCH;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_REGION_SIZE;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_STRING_DEDUP;
import static net.kdt.pojavlaunch.prefs.AdvancedPerformance.KEY_YOUNG_GEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.prefs.AdvancedPerformance;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdvancedJvmArgumentsTest {
    /** What a new install's Java arguments hold before Performance Mode adds anything. */
    private static List<String> launcherDefaults() {
        return args("-Xmx1024M", "-Xms512M", "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:+DisableExplicitGC");
    }

    private static List<String> args(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static AdvancedPerformance with(Object... keysAndValues) {
        Map<String, Object> stored = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) stored.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return AdvancedPerformance.fromMap(stored);
    }

    /** What the game gets: the player's arguments, then Advanced settings, then Performance Mode's own defaults. */
    private static List<String> full(List<String> arguments, AdvancedPerformance settings, boolean forceSimpleJit) {
        AdvancedJvmArguments.addTo(arguments, settings, forceSimpleJit);
        PerformanceJvmArguments.addTo(arguments);
        return arguments;
    }

    private static int count(List<String> arguments, String prefix) {
        int n = 0;
        for (String a : arguments) if (a.startsWith(prefix)) n++;
        return n;
    }

    /** The name a flag sets: "-XX:+Foo" and "-XX:Foo=3" are both "Foo", "-Dx=y" is "-Dx". */
    private static String name(String flag) {
        String body = flag;
        if (flag.startsWith("-XX:")) {
            body = flag.substring(4);
            if (body.startsWith("+") || body.startsWith("-")) body = body.substring(1);
        }
        int equals = body.indexOf('=');
        return equals > 0 ? body.substring(0, equals) : body;
    }

    private static void assertNoFlagTwice(List<String> arguments) {
        Set<String> seen = new HashSet<>();
        for (String argument : arguments) {
            assertTrue("flag twice: " + argument + " in " + arguments, seen.add(name(argument)));
        }
    }

    @Test
    public void theDefaultsOnlyAddTheUnknownFlagGuardAndItComesFirst() {
        List<String> list = launcherDefaults();
        List<String> added = AdvancedJvmArguments.addTo(list, AdvancedPerformance.defaults(), false);
        assertEquals(Arrays.asList("-XX:+IgnoreUnrecognizedVMOptions"), added);
        assertEquals("-XX:+IgnoreUnrecognizedVMOptions", list.get(0));
        assertEquals(launcherDefaults().size() + 1, list.size());
    }

    @Test
    public void withTheDefaultsPerformanceModeRunsExactlyAsBeforePlusTheGuard() {
        List<String> before = launcherDefaults();
        PerformanceJvmArguments.addTo(before);
        List<String> after = full(launcherDefaults(), AdvancedPerformance.defaults(), false);
        List<String> expected = new ArrayList<>(before);
        expected.add(0, "-XX:+IgnoreUnrecognizedVMOptions");
        assertEquals(expected, after);
    }

    @Test
    public void theUnknownFlagGuardCanBeTurnedOffAndIsNotRepeated() {
        List<String> off = launcherDefaults();
        assertTrue(AdvancedJvmArguments.addTo(off, with(KEY_IGNORE_UNKNOWN_FLAGS, Boolean.FALSE), false).isEmpty());
        assertEquals(0, count(off, "-XX:+IgnoreUnrecognized"));

        List<String> typed = args("-XX:+IgnoreUnrecognizedVMOptions", "-Xmx1024M");
        AdvancedJvmArguments.addTo(typed, AdvancedPerformance.defaults(), false);
        assertEquals(1, count(typed, "-XX:+IgnoreUnrecognized"));
    }

    @Test
    public void parallelCollectorReplacesG1AndDropsG1OnlyFlags() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_GC, "parallel", KEY_PAUSE_GOAL, "35", KEY_GC_THREADS, "2",
                KEY_YOUNG_GEN, "small", KEY_REGION_SIZE, "4", KEY_G1_RESERVE, "20", KEY_STRING_DEDUP, Boolean.TRUE), false);
        assertTrue(list.contains("-XX:+UseParallelGC"));
        assertFalse(list.contains("-XX:+UseG1GC"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=35"));
        assertTrue(list.contains("-XX:ParallelGCThreads=2"));
        for (String g1Only : new String[] {"-XX:G1NewSizePercent", "-XX:G1MaxNewSizePercent", "-XX:G1HeapRegionSize",
                "-XX:G1ReservePercent", "-XX:+UseStringDeduplication", "-XX:+UnlockExperimentalVMOptions"}) {
            assertEquals(g1Only, 0, count(list, g1Only));
        }
        assertNoFlagTwice(list);
    }

    @Test
    public void serialCollectorGetsNoPauseGoalOrGcThreads() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_GC, "serial", KEY_PAUSE_GOAL, "35", KEY_GC_THREADS, "2"), false);
        assertTrue(list.contains("-XX:+UseSerialGC"));
        assertEquals(0, count(list, "-XX:MaxGCPauseMillis"));
        assertEquals(0, count(list, "-XX:ParallelGCThreads"));
        assertNoFlagTwice(list);
    }

    @Test
    public void g1AndItsTuningFlagsAreWritten() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_GC, "g1", KEY_PAUSE_GOAL, "20", KEY_REGION_SIZE, "4",
                KEY_G1_RESERVE, "25", KEY_STRING_DEDUP, Boolean.TRUE, KEY_GC_THREADS, "3"), false);
        assertEquals(1, count(list, "-XX:+UseG1GC"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=20"));
        assertTrue(list.contains("-XX:G1HeapRegionSize=4m"));
        assertTrue(list.contains("-XX:G1ReservePercent=25"));
        assertTrue(list.contains("-XX:+UseStringDeduplication"));
        assertTrue(list.contains("-XX:ParallelGCThreads=3"));
        assertNoFlagTwice(list);
    }

    @Test
    public void youngGenerationSizesAreWrittenAfterTheUnlockTheyNeed() {
        for (String size : new String[] {"small", "large"}) {
            List<String> list = full(args("-Xmx1024M"), with(KEY_YOUNG_GEN, size), false);
            int unlock = list.indexOf("-XX:+UnlockExperimentalVMOptions");
            assertTrue(size, unlock >= 0);
            String start = "small".equals(size) ? "20" : "40";
            String max = "small".equals(size) ? "35" : "60";
            assertEquals(size, unlock + 1, list.indexOf("-XX:G1NewSizePercent=" + start));
            assertEquals(size, unlock + 2, list.indexOf("-XX:G1MaxNewSizePercent=" + max));
            assertNoFlagTwice(list);
        }
    }

    @Test
    public void anUnlockTheArgumentsAlreadyHaveIsNotRepeated() {
        List<String> list = full(args("-XX:+UnlockExperimentalVMOptions", "-Xmx1024M"), with(KEY_YOUNG_GEN, "large"), false);
        assertEquals(1, count(list, "-XX:+UnlockExperimentalVMOptions"));
        assertTrue(list.contains("-XX:G1NewSizePercent=40"));
    }

    @Test
    public void aCollectorTypedByThePlayerWinsAndSoDoTheFlagsTheyTyped() {
        List<String> list = full(args("-XX:+UseSerialGC", "-XX:MaxGCPauseMillis=120", "-XX:MetaspaceSize=64m",
                        "-XX:ReservedCodeCacheSize=100m", "-XX:CICompilerCount=2", "-Djava.net.preferIPv4Stack=false"),
                with(KEY_GC, "g1", KEY_REGION_SIZE, "4", KEY_PAUSE_GOAL, "20", KEY_METASPACE, "512", KEY_CODE_CACHE, "512",
                        KEY_JIT_THREADS, "4", KEY_IPV4, Boolean.TRUE), false);
        assertFalse(list.contains("-XX:+UseG1GC"));
        assertEquals(0, count(list, "-XX:G1HeapRegionSize"));
        assertEquals(0, count(list, "-XX:MaxGCPauseMillis=20"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=120"));
        assertTrue(list.contains("-XX:MetaspaceSize=64m"));
        assertTrue(list.contains("-XX:ReservedCodeCacheSize=100m"));
        assertTrue(list.contains("-XX:CICompilerCount=2"));
        assertTrue(list.contains("-Djava.net.preferIPv4Stack=false"));
        assertNoFlagTwice(list);
    }

    @Test
    public void switchesTurnedOffAreWrittenWithAMinusAndPerformanceModeDoesNotAddThemBack() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_IGNORE_SYSTEM_GC, Boolean.FALSE, KEY_PARALLEL_REFS, Boolean.FALSE), false);
        assertTrue(list.contains("-XX:-DisableExplicitGC"));
        assertFalse(list.contains("-XX:+DisableExplicitGC"));
        assertTrue(list.contains("-XX:-ParallelRefProcEnabled"));
        assertFalse(list.contains("-XX:+ParallelRefProcEnabled"));
        assertNoFlagTwice(list);
    }

    @Test
    public void theyStayOnWhenLeftAtTheirDefault() {
        List<String> list = full(args("-Xmx1024M"), AdvancedPerformance.defaults(), false);
        assertTrue(list.contains("-XX:+DisableExplicitGC"));
        assertTrue(list.contains("-XX:+ParallelRefProcEnabled"));
    }

    @Test
    public void preTouchIsWrittenForAnyCollector() {
        for (String gc : new String[] {"g1", "parallel", "serial"}) {
            List<String> list = full(args("-Xmx1024M"), with(KEY_PRE_TOUCH, Boolean.TRUE, KEY_GC, gc), false);
            assertTrue(gc, list.contains("-XX:+AlwaysPreTouch"));
        }
    }

    @Test
    public void stringDeduplicationOnlyGoesWithG1() {
        assertTrue(full(args("-Xmx1024M"), with(KEY_STRING_DEDUP, Boolean.TRUE), false).contains("-XX:+UseStringDeduplication"));
        assertFalse(full(args("-Xmx1024M"), with(KEY_STRING_DEDUP, Boolean.TRUE, KEY_GC, "serial"), false)
                .contains("-XX:+UseStringDeduplication"));
    }

    @Test
    public void theSimpleJitComesWithEnoughCodeSpace() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_JIT, "c1"), false);
        assertTrue(list.contains("-XX:TieredStopAtLevel=1"));
        assertTrue(list.contains("-XX:ReservedCodeCacheSize=240m"));
        assertNoFlagTwice(list);
        // The player's own code space choice replaces the companion.
        List<String> sized = full(args("-Xmx1024M"), with(KEY_JIT, "c1", KEY_CODE_CACHE, "512"), false);
        assertTrue(sized.contains("-XX:ReservedCodeCacheSize=512m"));
        assertEquals(0, count(sized, "-XX:ReservedCodeCacheSize=240m"));
    }

    @Test
    public void theFullJitWithoutACodeSpaceChoiceWritesNothingAboutIt() {
        List<String> list = full(args("-Xmx1024M"), AdvancedPerformance.defaults(), false);
        assertEquals(0, count(list, "-XX:TieredStopAtLevel"));
        assertEquals(0, count(list, "-XX:ReservedCodeCacheSize"));
        List<String> sized = full(args("-Xmx1024M"), with(KEY_CODE_CACHE, "128"), false);
        assertTrue(sized.contains("-XX:ReservedCodeCacheSize=128m"));
        assertEquals(0, count(sized, "-XX:TieredStopAtLevel"));
    }

    @Test
    public void antiCrashCanForceTheSimpleJitEvenWhenTheChoiceIsAuto() {
        List<String> list = full(args("-Xmx1024M"), AdvancedPerformance.defaults(), true);
        assertTrue(list.contains("-XX:TieredStopAtLevel=1"));
        assertTrue(AdvancedJvmArguments.simpleJit(AdvancedPerformance.defaults(), true));
        assertFalse(AdvancedJvmArguments.simpleJit(AdvancedPerformance.defaults(), false));
        assertTrue(AdvancedJvmArguments.simpleJit(with(KEY_JIT, "c1"), false));
    }

    @Test
    public void aTieringFlagTheyTypedMeansTheSimpleJitIsNotInEffectAndAddsNoCodeSpaceCompanion() {
        List<String> list = full(args("-XX:TieredStopAtLevel=3"), with(KEY_JIT, "c1"), true);
        assertEquals(1, count(list, "-XX:TieredStopAtLevel"));
        assertTrue(list.contains("-XX:TieredStopAtLevel=3"));
        assertEquals(0, count(list, "-XX:ReservedCodeCacheSize"));
    }

    @Test
    public void jitThreadsAndIpv4AreWritten() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_JIT_THREADS, "3", KEY_IPV4, Boolean.TRUE), false);
        assertTrue(list.contains("-XX:CICompilerCount=3"));
        assertTrue(list.contains("-Djava.net.preferIPv4Stack=true"));
    }

    @Test
    public void metaspaceChoiceReplacesPerformanceModesOwnTriggerPoint() {
        List<String> list = full(args("-Xmx1024M"), with(KEY_METASPACE, "256"), false);
        assertTrue(list.contains("-XX:MetaspaceSize=256m"));
        assertEquals(1, count(list, "-XX:MetaspaceSize"));
        assertTrue(full(args("-Xmx1024M"), AdvancedPerformance.defaults(), false).contains("-XX:MetaspaceSize=128m"));
    }

    @Test
    public void theStartingHeapFollowsTheChoiceAndNeverExceedsTheMaximum() {
        AdvancedPerformance auto = AdvancedPerformance.defaults();
        assertEquals(512, AdvancedJvmArguments.initialHeapMb(auto, true, 1024));
        assertEquals(256, AdvancedJvmArguments.initialHeapMb(auto, true, 400));
        assertEquals(256, AdvancedJvmArguments.initialHeapMb(auto, false, 1024));
        assertEquals(256, AdvancedJvmArguments.initialHeapMb(with(KEY_INITIAL_HEAP, "small"), true, 1024));
        assertEquals(1024, AdvancedJvmArguments.initialHeapMb(with(KEY_INITIAL_HEAP, "max"), true, 1024));
        // A maximum below the usual floor still wins.
        assertEquals(200, AdvancedJvmArguments.initialHeapMb(with(KEY_INITIAL_HEAP, "small"), true, 200));
        assertEquals(200, AdvancedJvmArguments.initialHeapMb(auto, true, 200));
    }

    @Test
    public void theCoresTheGameSeesFollowTheChoiceAndStayBetweenOneAndWhatThePhoneHas() {
        assertEquals(8, AdvancedJvmArguments.activeProcessors(AdvancedPerformance.defaults(), 8));
        assertEquals(4, AdvancedJvmArguments.activeProcessors(with(KEY_CORES, "4"), 8));
        assertEquals(2, AdvancedJvmArguments.activeProcessors(with(KEY_CORES, "2"), 8));
        assertEquals(4, AdvancedJvmArguments.activeProcessors(with(KEY_CORES, "6"), 4));
        assertEquals(1, AdvancedJvmArguments.activeProcessors(with(KEY_CORES, "3"), 0));
        assertEquals(1, AdvancedJvmArguments.activeProcessors(AdvancedPerformance.defaults(), 0));
    }

    @Test
    public void everySingleChoiceGivesACommandLineWithNoFlagTwiceAndTheUnlockBeforeTheExperimentalOnes() {
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
            for (String value : spec.values) {
                Object stored = spec.isSwitch ? (Object) Boolean.valueOf(value) : value;
                List<String> list = full(launcherDefaults(), with(spec.key, stored), false);
                assertNoFlagTwice(list);
                int unlock = list.indexOf("-XX:+UnlockExperimentalVMOptions");
                for (int i = 0; i < list.size(); i++) {
                    String flag = list.get(i);
                    if (flag.startsWith("-XX:G1NewSizePercent") || flag.startsWith("-XX:G1MaxNewSizePercent")) {
                        assertTrue(spec.key + "=" + value + ": " + list, unlock >= 0 && unlock < i);
                    }
                    assertTrue("not a JVM option: " + flag, flag.startsWith("-X") || flag.startsWith("-D"));
                }
            }
        }
    }

    @Test
    public void everythingChangedAtOnceStillMakesOneCoherentCommandLine() {
        List<String> list = full(launcherDefaults(), with(KEY_GC, "g1", KEY_PAUSE_GOAL, "20", KEY_YOUNG_GEN, "large",
                KEY_REGION_SIZE, "8", KEY_G1_RESERVE, "15", KEY_GC_THREADS, "2", KEY_IGNORE_SYSTEM_GC, Boolean.FALSE,
                KEY_PARALLEL_REFS, Boolean.FALSE, KEY_STRING_DEDUP, Boolean.TRUE, KEY_PRE_TOUCH, Boolean.TRUE,
                KEY_METASPACE, "512", KEY_CODE_CACHE, "512", KEY_JIT, "c1", KEY_JIT_THREADS, "2", KEY_IPV4, Boolean.TRUE), false);
        assertNoFlagTwice(list);
        assertEquals("-XX:+IgnoreUnrecognizedVMOptions", list.get(0));
        assertTrue(list.contains("-Xmx1024M"));
        assertTrue(list.contains("-XX:ReservedCodeCacheSize=512m"));
        assertTrue(list.contains("-XX:TieredStopAtLevel=1"));
    }
}
