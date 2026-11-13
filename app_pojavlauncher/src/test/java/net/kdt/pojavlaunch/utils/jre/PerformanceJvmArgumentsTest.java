package net.kdt.pojavlaunch.utils.jre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PerformanceJvmArgumentsTest {
    /** What a new install has in its Java arguments field: LauncherPreferences.DEFAULT_JAVA_ARGS. */
    private static final String[] LAUNCHER_BLOCK = {
            "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+ParallelRefProcEnabled",
            "-XX:+DisableExplicitGC", "-XX:G1ReservePercent=15",
            "-XX:InitiatingHeapOccupancyPercent=25", "-XX:+UseStringDeduplication"
    };

    private static List<String> args(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static int count(List<String> arguments, String prefix) {
        int n = 0;
        for (String a : arguments) if (a.startsWith(prefix)) n++;
        return n;
    }

    private static int collectors(List<String> arguments) {
        int n = 0;
        for (String a : arguments) if (a.startsWith("-XX:+Use") && a.endsWith("GC")) n++;
        return n;
    }

    @Test
    public void theLaunchersOwnDefaultBlockStaysInSyncWithLauncherPreferences() {
        // The block above is a copy of the real default (a compile-time constant, so reading it
        // here does not load the Android-dependent class). If someone changes the real one, the
        // recognition in PerformanceJvmArguments must be looked at too.
        assertEquals(Arrays.asList(LauncherPreferences.DEFAULT_JAVA_ARGS.split(" ")), Arrays.asList(LAUNCHER_BLOCK));
        assertTrue(PerformanceJvmArguments.isLauncherDefaultBlock(args(LAUNCHER_BLOCK)));
    }

    @Test
    public void theLaunchersDefaultBlockGetsTheShortPauseProfile() {
        List<String> list = args(LAUNCHER_BLOCK);
        String described = PerformanceJvmArguments.addTo(list, false);
        // The pause goal of 100 ms was the launcher's, so it is replaced by Performance Mode's.
        assertEquals(1, count(list, "-XX:MaxGCPauseMillis"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=30"));
        // The reserve and the marking trigger go back to the JVM's own values.
        assertEquals(0, count(list, "-XX:G1ReservePercent"));
        assertEquals(0, count(list, "-XX:InitiatingHeapOccupancyPercent"));
        // No forced size for the young generation, and nothing that needs the experimental unlock.
        assertEquals(0, count(list, "-XX:G1NewSizePercent"));
        assertEquals(0, count(list, "-XX:G1MaxNewSizePercent"));
        assertEquals(0, count(list, "-XX:+UnlockExperimentalVMOptions"));
        // What was already fine is kept, once.
        assertEquals(1, count(list, "-XX:+UseG1GC"));
        assertEquals(1, count(list, "-XX:+ParallelRefProcEnabled"));
        assertEquals(1, count(list, "-XX:+DisableExplicitGC"));
        assertEquals(1, count(list, "-XX:+UseStringDeduplication"));
        assertTrue(list.contains("-XX:-UsePerfData"));
        assertTrue(list.contains("-XX:MetaspaceSize=128m"));
        assertEquals("G1, pause goal 30 ms", described);
    }

    @Test
    public void aBlockMissingOneOfTheLaunchersFlagsIsStillTheLaunchers() {
        for (int skipped = 0; skipped < LAUNCHER_BLOCK.length; skipped++) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < LAUNCHER_BLOCK.length; i++) if (i != skipped) list.add(LAUNCHER_BLOCK[i]);
            assertTrue("without " + LAUNCHER_BLOCK[skipped], PerformanceJvmArguments.isLauncherDefaultBlock(list));
        }
    }

    @Test
    public void aBlockMissingTwoOfTheLaunchersFlagsBelongsToThePlayer() {
        List<String> list = args("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:G1ReservePercent=15");
        assertFalse(PerformanceJvmArguments.isLauncherDefaultBlock(list));
        PerformanceJvmArguments.addTo(list, false);
        // The player wrote these; they are kept as they are.
        assertTrue(list.contains("-XX:MaxGCPauseMillis=100"));
        assertTrue(list.contains("-XX:G1ReservePercent=15"));
        assertEquals(1, count(list, "-XX:MaxGCPauseMillis"));
    }

    @Test
    public void aPauseGoalThePlayerChangedInsideTheLaunchersBlockIsKept() {
        List<String> list = args(LAUNCHER_BLOCK);
        list.set(list.indexOf("-XX:MaxGCPauseMillis=100"), "-XX:MaxGCPauseMillis=60");
        PerformanceJvmArguments.addTo(list, false);
        assertTrue(list.contains("-XX:MaxGCPauseMillis=60"));
        assertEquals(1, count(list, "-XX:MaxGCPauseMillis"));
        // The other two values are still the launcher's, so they are still replaced.
        assertEquals(0, count(list, "-XX:G1ReservePercent"));
        assertEquals(0, count(list, "-XX:InitiatingHeapOccupancyPercent"));
        assertEquals("G1, pause goal 60 ms", PerformanceJvmArguments.describeCollector(list));
    }

    @Test
    public void picksG1WhenNoCollectorIsNamedAtAll() {
        List<String> list = args("-Xmx1024M");
        PerformanceJvmArguments.addTo(list, false);
        assertEquals(1, count(list, "-XX:+UseG1GC"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=30"));
        assertTrue(list.contains("-XX:+DisableExplicitGC"));
        assertEquals(0, count(list, "-XX:G1NewSizePercent"));
    }

    @Test
    public void emptyArgumentsGetTheFullSet() {
        List<String> list = args();
        PerformanceJvmArguments.addTo(list, false);
        assertTrue(list.contains("-XX:+UseG1GC"));
        assertTrue(list.contains("-XX:+DisableExplicitGC"));
        assertTrue(list.contains("-XX:MetaspaceSize=128m"));
        assertTrue(list.contains("-XX:+ParallelRefProcEnabled"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=30"));
    }

    @Test
    public void aCollectorTheUserChoseIsLeftAloneAndGetsNoG1Flags() {
        for (String collector : new String[] {"-XX:+UseSerialGC", "-XX:+UseParallelGC", "-XX:+UseZGC", "-XX:+UseShenandoahGC"}) {
            for (boolean lowPause : new boolean[] {false, true}) {
                List<String> list = args(collector);
                PerformanceJvmArguments.addTo(list, lowPause);
                assertEquals(collector, 0, count(list, "-XX:+UseG1GC"));
                assertEquals(collector, 0, count(list, "-XX:G1"));
                assertEquals(collector, 0, count(list, "-XX:MaxGCPauseMillis"));
                assertEquals(collector, 1, collectors(list));
                assertTrue(collector, list.contains(collector));
                // The collector-independent flags still apply.
                assertTrue(collector, list.contains("-XX:+DisableExplicitGC"));
                assertTrue(collector, list.contains("-XX:MetaspaceSize=128m"));
            }
        }
    }

    @Test
    public void aCollectorTheUserAddedBesideTheLaunchersBlockReplacesTheLaunchersG1() {
        // A player who appends their own collector to the launcher's block must not end up with two
        // collectors, which would stop the JVM from starting.
        for (String collector : new String[] {"-XX:+UseZGC", "-XX:+UseParallelGC", "-XX:+UseShenandoahGC"}) {
            List<String> list = args(LAUNCHER_BLOCK);
            list.add(collector);
            PerformanceJvmArguments.addTo(list, false);
            assertEquals(collector, 1, collectors(list));
            assertTrue(collector, list.contains(collector));
            assertEquals(collector, 0, count(list, "-XX:MaxGCPauseMillis"));
        }
    }

    @Test
    public void theLowPauseCollectorReplacesG1WithoutLeavingTwoCollectors() {
        List<String> list = args(LAUNCHER_BLOCK);
        String described = PerformanceJvmArguments.addTo(list, true);
        assertEquals(1, collectors(list));
        assertEquals(1, count(list, "-XX:+UseShenandoahGC"));
        assertEquals(0, count(list, "-XX:+UseG1GC"));
        // Nothing of G1's is left behind.
        assertEquals(0, count(list, "-XX:G1"));
        assertEquals(0, count(list, "-XX:InitiatingHeapOccupancyPercent"));
        assertEquals(0, count(list, "-XX:MaxGCPauseMillis"));
        // The collector-independent flags are there, once each.
        assertEquals(1, count(list, "-XX:+DisableExplicitGC"));
        assertEquals(1, count(list, "-XX:+ParallelRefProcEnabled"));
        assertEquals(1, count(list, "-XX:+UseStringDeduplication"));
        assertEquals(1, count(list, "-XX:MetaspaceSize"));
        assertEquals("Shenandoah (low pause)", described);
    }

    @Test
    public void theLowPauseCollectorWorksFromEmptyArgumentsToo() {
        List<String> list = args("-Xmx1024M");
        PerformanceJvmArguments.addTo(list, true);
        assertEquals(1, collectors(list));
        assertTrue(list.contains("-XX:+UseShenandoahGC"));
    }

    @Test
    public void flagsTheUserSetAreNeverChanged() {
        List<String> list = args("-XX:+UseG1GC", "-XX:-DisableExplicitGC", "-XX:MetaspaceSize=256m",
                "-XX:G1NewSizePercent=20", "-XX:G1MaxNewSizePercent=60", "-XX:+UsePerfData", "-XX:-ParallelRefProcEnabled");
        PerformanceJvmArguments.addTo(list, false);
        assertTrue(list.contains("-XX:-DisableExplicitGC"));
        assertFalse(list.contains("-XX:+DisableExplicitGC"));
        assertEquals(1, count(list, "-XX:MetaspaceSize"));
        assertTrue(list.contains("-XX:MetaspaceSize=256m"));
        assertEquals(1, count(list, "-XX:G1NewSizePercent"));
        assertTrue(list.contains("-XX:G1NewSizePercent=20"));
        assertTrue(list.contains("-XX:G1MaxNewSizePercent=60"));
        assertTrue(list.contains("-XX:+UsePerfData"));
        assertFalse(list.contains("-XX:-UsePerfData"));
        assertFalse(list.contains("-XX:+ParallelRefProcEnabled"));
    }

    @Test
    public void aMaxMetaspaceSizeDoesNotCountAsTheMetaspaceTriggerPoint() {
        List<String> list = args("-XX:MaxMetaspaceSize=512m");
        PerformanceJvmArguments.addTo(list, false);
        assertTrue(list.contains("-XX:MetaspaceSize=128m"));
        assertTrue(list.contains("-XX:MaxMetaspaceSize=512m"));
    }

    @Test
    public void runningTwiceDoesNotRepeatAnything() {
        for (boolean lowPause : new boolean[] {false, true}) {
            List<String> list = args(LAUNCHER_BLOCK);
            PerformanceJvmArguments.addTo(list, lowPause);
            List<String> once = new ArrayList<>(list);
            PerformanceJvmArguments.addTo(list, lowPause);
            assertEquals(once.size(), list.size());
            assertEquals(once, list);
        }
    }

    @Test
    public void collectorChoiceIsRecognisedOnlyForRealCollectorFlags() {
        assertTrue(PerformanceJvmArguments.hasCollectorChoice(args("-XX:+UseG1GC")));
        assertTrue(PerformanceJvmArguments.hasCollectorChoice(args("-Xmx1G", "-XX:+UseSerialGC")));
        assertFalse(PerformanceJvmArguments.hasCollectorChoice(args("-XX:+UseCompressedOops")));
        assertFalse(PerformanceJvmArguments.hasCollectorChoice(args("-XX:+UseStringDeduplication")));
        assertFalse(PerformanceJvmArguments.hasCollectorChoice(args("-XX:+UseGCOverheadLimit")));
        assertFalse(PerformanceJvmArguments.hasCollectorChoice(args()));
    }

    @Test
    public void theDescriptionSaysWhichCollectorWasChosen() {
        assertEquals("Shenandoah (low pause)", PerformanceJvmArguments.describeCollector(args("-XX:+UseShenandoahGC")));
        assertEquals("G1, pause goal 200 (JVM default) ms", PerformanceJvmArguments.describeCollector(args("-XX:+UseG1GC")));
        assertEquals("the collector named in Java arguments", PerformanceJvmArguments.describeCollector(args("-XX:+UseZGC")));
        assertEquals("the JVM's own choice", PerformanceJvmArguments.describeCollector(args()));
    }

    // ---- Does a real JVM accept what we produce? ----------------------------------------------
    // The launcher's JVM once refused to start because of a flag set that nobody had run ("Bad
    // arguments"). These start a real JVM with the exact flags and require it to come up. The
    // JVM running the tests is not the phone's JVM, but flag names, values and conflicts are
    // checked the same way by every HotSpot of the same generation.

    /** Starts the JVM that runs these tests with the flags and a plain "-version". */
    private static int startJvm(List<String> flags) throws IOException, InterruptedException {
        String java = new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-Xmx256M");
        command.add("-Xms128M");
        for (String flag : flags) if (flag.startsWith("-XX:")) command.add(flag);
        command.add("-version");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        // The output is not needed, but it has to be read or the JVM could block on a full pipe.
        byte[] sink = new byte[4096];
        try (InputStream output = process.getInputStream()) {
            while (output.read(sink) >= 0) { /* discard */ }
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("the JVM did not finish: " + command);
        }
        return process.exitValue();
    }

    @Test
    public void aRealJvmStartsWithTheG1Profile() throws Exception {
        List<String> list = args(LAUNCHER_BLOCK);
        PerformanceJvmArguments.addTo(list, false);
        assertEquals("flags: " + list, 0, startJvm(list));
        List<String> empty = args();
        PerformanceJvmArguments.addTo(empty, false);
        assertEquals("flags: " + empty, 0, startJvm(empty));
    }

    @Test
    public void aRealJvmStartsWhenAPlayerAppendedTheirOwnCollectorToTheLaunchersBlock() throws Exception {
        // String deduplication with this collector needs a newer JVM than some build machines have;
        // skip when this one cannot do the combination at all (the point here is the collector conflict).
        Assume.assumeTrue(startJvm(args("-XX:+UseParallelGC", "-XX:+UseStringDeduplication")) == 0);
        List<String> list = args(LAUNCHER_BLOCK);
        list.add("-XX:+UseParallelGC");
        PerformanceJvmArguments.addTo(list, false);
        assertEquals("flags: " + list, 0, startJvm(list));
    }

    @Test
    public void aRealJvmStartsWithTheLowPauseProfile() throws Exception {
        // Not every JVM build includes Shenandoah; skip when this one does not.
        Assume.assumeTrue(startJvm(args("-XX:+UseShenandoahGC", "-XX:+UseStringDeduplication",
                "-XX:+ParallelRefProcEnabled")) == 0);
        List<String> list = args(LAUNCHER_BLOCK);
        PerformanceJvmArguments.addTo(list, true);
        assertEquals("flags: " + list, 0, startJvm(list));
    }
}
