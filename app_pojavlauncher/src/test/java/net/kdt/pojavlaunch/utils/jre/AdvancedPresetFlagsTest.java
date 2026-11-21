package net.kdt.pojavlaunch.utils.jre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.prefs.AdvancedPerformance;
import net.kdt.pojavlaunch.prefs.AdvancedPresets;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Quick setup presets are not labels: each one has to end up as real Java flags or as a setting the game reads. */
public class AdvancedPresetFlagsTest {
    private static AdvancedPerformance settings(String preset) {
        return AdvancedPerformance.fromMap(AdvancedPresets.valuesFor(preset));
    }

    private static List<String> flags(String preset) {
        List<String> arguments = new ArrayList<>(Arrays.asList("-Xmx1024M", "-Xms512M"));
        AdvancedJvmArguments.addTo(arguments, settings(preset), false);
        PerformanceJvmArguments.addTo(arguments);
        return arguments;
    }

    @Test
    public void balancedAddsNothingOfItsOwn() {
        List<String> balanced = flags(AdvancedPresets.BALANCED);
        assertFalse(balanced.contains("-XX:+UseParallelGC"));
        assertFalse(balanced.contains("-XX:TieredStopAtLevel=1"));
        assertEquals(512, AdvancedJvmArguments.initialHeapMb(settings(AdvancedPresets.BALANCED), true, 1024));
    }

    @Test
    public void maxFpsUsesTheParallelCollectorAFullStartingHeapAndFourCollectorThreads() {
        List<String> list = flags(AdvancedPresets.MAX_FPS);
        assertTrue(list.toString(), list.contains("-XX:+UseParallelGC"));
        assertTrue(list.toString(), list.contains("-XX:ParallelGCThreads=4"));
        assertFalse(list.toString(), list.contains("-XX:+UseG1GC"));
        assertEquals(1024, AdvancedJvmArguments.initialHeapMb(settings(AdvancedPresets.MAX_FPS), true, 1024));
    }

    @Test
    public void smoothestShortensThePauseGoalAndKeepsMoreRoomFree() {
        List<String> list = flags(AdvancedPresets.SMOOTHEST);
        assertTrue(list.toString(), list.contains("-XX:MaxGCPauseMillis=20"));
        assertTrue(list.toString(), list.contains("-XX:G1ReservePercent=20"));
        assertFalse(list.toString(), list.contains("-XX:+UseParallelGC"));
    }

    @Test
    public void coolUsesTheSimpleJitFewerThreadsAndSixtyHertz() {
        List<String> list = flags(AdvancedPresets.COOL);
        assertTrue(list.toString(), list.contains("-XX:TieredStopAtLevel=1"));
        assertTrue(list.toString(), list.contains("-XX:CICompilerCount=2"));
        assertTrue(list.toString(), list.contains("-XX:ParallelGCThreads=2"));
        assertEquals(60, settings(AdvancedPresets.COOL).number(AdvancedPerformance.KEY_REFRESH_RATE));
        assertTrue(AdvancedJvmArguments.simpleJit(settings(AdvancedPresets.COOL), false));
    }
}
