package net.kdt.pojavlaunch.utils.jre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.prefs.DefaultJavaArgs;

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

    // ---- the launcher's own default text must not fight Performance Mode ---------------------

    private static List<String> launcherDefaultText() {
        return new ArrayList<>(Arrays.asList(DefaultJavaArgs.tokens()));
    }

    @Test
    public void theLauncherDefaultTextGivesWayToPerformanceModesOwnMemorySettings() {
        List<String> list = launcherDefaultText();
        list.add(0, "-Xmx1024M");
        List<String> removed = PerformanceJvmArguments.dropLauncherDefaults(list);
        assertEquals(Arrays.asList("-XX:MaxGCPauseMillis=100", "-XX:G1ReservePercent=15",
                "-XX:InitiatingHeapOccupancyPercent=25", "-XX:+UseStringDeduplication"), removed);
        PerformanceJvmArguments.addTo(list);
        // The 50 ms pause goal replaces the 100 ms one; the other three go back to the JVM's own choice.
        assertEquals(1, count(list, "-XX:MaxGCPauseMillis"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=50"));
        assertEquals(0, count(list, "-XX:InitiatingHeapOccupancyPercent"));
        assertEquals(0, count(list, "-XX:G1ReservePercent"));
        assertEquals(0, count(list, "-XX:+UseStringDeduplication"));
        // What the launcher text had that is still wanted stays, once.
        assertEquals(1, count(list, "-XX:+UseG1GC"));
        assertEquals(1, count(list, "-XX:+ParallelRefProcEnabled"));
        assertEquals(1, count(list, "-XX:+DisableExplicitGC"));
        assertTrue(list.contains("-Xmx1024M"));
    }

    @Test
    public void textThePlayerEditedKeepsEveryFlag() {
        // One flag changed (80 instead of 100): the whole text is the player's now.
        List<String> list = launcherDefaultText();
        list.set(list.indexOf("-XX:MaxGCPauseMillis=100"), "-XX:MaxGCPauseMillis=80");
        List<String> before = new ArrayList<>(list);
        assertTrue(PerformanceJvmArguments.dropLauncherDefaults(list).isEmpty());
        assertEquals(before, list);
        PerformanceJvmArguments.addTo(list);
        assertTrue(list.contains("-XX:MaxGCPauseMillis=80"));
        assertTrue(list.contains("-XX:InitiatingHeapOccupancyPercent=25"));

        // A flag removed from the text counts as an edit too.
        List<String> shorter = launcherDefaultText();
        shorter.remove("-XX:+UseStringDeduplication");
        assertTrue(PerformanceJvmArguments.dropLauncherDefaults(shorter).isEmpty());
        assertTrue(shorter.contains("-XX:G1ReservePercent=15"));
    }

    @Test
    public void flagsAddedOnTopOfTheLauncherTextAreNeverTouched() {
        List<String> list = launcherDefaultText();
        list.add("-XX:+UseLargePages");
        list.add("-XX:MaxGCPauseMillis=30"); // the player's own, next to the launcher's 100
        PerformanceJvmArguments.dropLauncherDefaults(list);
        PerformanceJvmArguments.addTo(list);
        assertTrue(list.contains("-XX:+UseLargePages"));
        assertEquals(1, count(list, "-XX:MaxGCPauseMillis"));
        assertTrue(list.contains("-XX:MaxGCPauseMillis=30"));
    }

    @Test
    public void emptyOrUnrelatedArgumentsAreLeftAlone() {
        List<String> empty = args();
        assertTrue(PerformanceJvmArguments.dropLauncherDefaults(empty).isEmpty());
        assertTrue(empty.isEmpty());
        List<String> other = args("-XX:+UseG1GC", "-XX:InitiatingHeapOccupancyPercent=25");
        assertTrue(PerformanceJvmArguments.dropLauncherDefaults(other).isEmpty());
        assertEquals(args("-XX:+UseG1GC", "-XX:InitiatingHeapOccupancyPercent=25"), other);
    }

    @Test
    public void theLauncherDefaultTextIsWhatTheSettingsStartWith() {
        // LauncherPreferences.DEFAULT_JAVA_ARGS is this text; the tokens must be the same flags.
        assertEquals(7, DefaultJavaArgs.tokens().length);
        assertTrue(Arrays.asList(DefaultJavaArgs.tokens()).contains("-XX:+UseG1GC"));
        assertEquals(String.join(" ", DefaultJavaArgs.tokens()), DefaultJavaArgs.TEXT);
    }

    @Test
    public void theMemorySettingsInEffectAreListedShortAndInOrder() {
        List<String> list = args("-Xmx1024M", "-Xms512M", "-Djava.home=/x", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50",
                "-XX:G1NewSizePercent=30", "-verbose:gc", "-Xmn200M");
        assertEquals(Arrays.asList("Xmx1024M Xms512M +UseG1GC MaxGCPauseMillis=50 G1NewSizePercent=30 Xmn200M"),
                PerformanceJvmArguments.describeMemorySettings(list, 150));
        // Cut into lines of at most the given length, never in the middle of a flag.
        List<String> cut = PerformanceJvmArguments.describeMemorySettings(list, 30);
        assertEquals(Arrays.asList("Xmx1024M Xms512M +UseG1GC", "MaxGCPauseMillis=50", "G1NewSizePercent=30 Xmn200M"), cut);
        assertTrue(PerformanceJvmArguments.describeMemorySettings(args("-Dx=1"), 150).isEmpty());
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
