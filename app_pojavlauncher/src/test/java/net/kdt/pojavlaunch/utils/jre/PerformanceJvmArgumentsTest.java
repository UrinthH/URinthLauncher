package net.kdt.pojavlaunch.utils.jre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PerformanceJvmArgumentsTest {
    private static List<String> args(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static int count(List<String> arguments, String prefix) {
        int n = 0;
        for (String a : arguments) if (a.startsWith(prefix)) n++;
        return n;
    }

    @Test
    public void addsTheGarbageCollectionFlagsToTheLauncherDefaults() {
        // The arguments a new install starts with.
        List<String> list = args("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+ParallelRefProcEnabled",
                "-XX:+DisableExplicitGC", "-XX:G1ReservePercent=15", "-XX:InitiatingHeapOccupancyPercent=25");
        PerformanceJvmArguments.addTo(list);
        assertTrue(list.contains("-XX:+UnlockExperimentalVMOptions"));
        assertTrue(list.contains("-XX:G1NewSizePercent=30"));
        assertTrue(list.contains("-XX:G1MaxNewSizePercent=45"));
        assertTrue(list.contains("-XX:-UsePerfData"));
        assertTrue(list.contains("-XX:MetaspaceSize=128m"));
        // Already there, so not repeated, and the player's own pause goal is kept (100, not 50).
        assertEquals(1, count(list, "-XX:MaxGCPauseMillis"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=100"));
        assertEquals(1, count(list, "-XX:+DisableExplicitGC"));
        assertEquals(1, count(list, "-XX:+ParallelRefProcEnabled"));
        assertEquals(1, count(list, "-XX:+UseG1GC"));
    }

    @Test
    public void picksG1WhenNoCollectorIsNamedAtAll() {
        List<String> list = args("-Xmx1024M");
        PerformanceJvmArguments.addTo(list);
        assertEquals(1, count(list, "-XX:+UseG1GC"));
        assertTrue(list.contains("-XX:G1NewSizePercent=30"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=50"));
        assertTrue(list.contains("-XX:+DisableExplicitGC"));
    }

    @Test
    public void emptyArgumentsGetTheFullSet() {
        List<String> list = args();
        PerformanceJvmArguments.addTo(list);
        assertTrue(list.contains("-XX:+UseG1GC"));
        assertTrue(list.contains("-XX:+DisableExplicitGC"));
        assertTrue(list.contains("-XX:MetaspaceSize=128m"));
        assertTrue(list.contains("-XX:+ParallelRefProcEnabled"));
    }

    @Test
    public void aCollectorTheUserChoseIsLeftAloneAndGetsNoG1Flags() {
        for (String collector : new String[] {"-XX:+UseSerialGC", "-XX:+UseParallelGC", "-XX:+UseZGC", "-XX:+UseShenandoahGC"}) {
            List<String> list = args(collector);
            PerformanceJvmArguments.addTo(list);
            assertEquals(collector, 0, count(list, "-XX:+UseG1GC"));
            assertEquals(collector, 0, count(list, "-XX:G1"));
            assertEquals(collector, 0, count(list, "-XX:MaxGCPauseMillis"));
            // The collector-independent flags still apply.
            assertTrue(collector, list.contains("-XX:+DisableExplicitGC"));
            assertTrue(collector, list.contains("-XX:MetaspaceSize=128m"));
        }
    }

    @Test
    public void flagsTheUserSetAreNeverChanged() {
        List<String> list = args("-XX:+UseG1GC", "-XX:-DisableExplicitGC", "-XX:MetaspaceSize=256m",
                "-XX:G1NewSizePercent=20", "-XX:G1MaxNewSizePercent=60", "-XX:+UsePerfData", "-XX:-ParallelRefProcEnabled");
        PerformanceJvmArguments.addTo(list);
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
        PerformanceJvmArguments.addTo(list);
        assertTrue(list.contains("-XX:MetaspaceSize=128m"));
        assertTrue(list.contains("-XX:MaxMetaspaceSize=512m"));
    }

    @Test
    public void runningTwiceDoesNotRepeatAnything() {
        List<String> list = args("-XX:+UseG1GC");
        PerformanceJvmArguments.addTo(list);
        int size = list.size();
        PerformanceJvmArguments.addTo(list);
        assertEquals(size, list.size());
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
}
