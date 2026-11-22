package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LaunchFingerprintTest {
    private static Map<String, Object> stored() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("allocation", 2048);
        prefs.put("javaArgs", "-XX:+UseG1GC");
        prefs.put("renderer", "opengles3_ltw");
        prefs.put("performanceMode", true);
        return prefs;
    }

    private static Object anotherValue(AdvancedPerformance.Spec spec) {
        if (spec.isSwitch) return Boolean.valueOf(!"true".equals(spec.defaultValue));
        for (String value : spec.values) if (!value.equals(spec.defaultValue)) return value;
        throw new AssertionError(spec.key + " offers one value only");
    }

    @Test
    public void theSameSettingsGiveTheSameFingerprint() {
        assertTrue(LaunchFingerprint.of(stored()).differences(LaunchFingerprint.of(stored())).isEmpty());
    }

    @Test
    public void settingsThatDoNotDecideHowTheGameStartsAreNotPartOfIt() {
        Map<String, Object> other = stored();
        other.put("buttonscale", 150);
        other.put("mousespeed", 40);
        other.put("selectedInstance", "PVP");
        other.put("tuningDefaultsUndoneV1", true);
        assertTrue(LaunchFingerprint.of(stored()).differences(LaunchFingerprint.of(other)).isEmpty());
    }

    /** For every tracked setting outside the Advanced and MobileGlues tables, a value that differs from stored(). */
    private static final Object[][] OTHER_CHANGES = {
            {"allocation", 4096}, {"javaArgs", "-Xmn128m"}, {"renderer", "vulkan_zink"}, {"performanceMode", false},
            {"perfMobileGlues", false}, {"sustainedPerformance", true}, {"bigCoreAffinity", true},
            {"alternate_surface", true}, {"force_vsync", true}, {"use_angle", true}, {"resolutionRatio", 60},
            {"java_sandbox", false}, {"zinkPreferSystemDriver", true}, {"freedrenoSysmem", true},
            {"ubwcWorkaround", true}, {"alsoftForceOpenSL", true},
    };

    @Test
    public void everyTrackedSettingOutsideTheTablesChangesIt() {
        LaunchFingerprint before = LaunchFingerprint.of(stored());
        List<String> covered = new java.util.ArrayList<>();
        for (Object[] change : OTHER_CHANGES) {
            String key = (String) change[0];
            covered.add(key);
            Map<String, Object> changed = stored();
            changed.put(key, change[1]);
            assertEquals(key, Collections.singletonList(key), before.differences(LaunchFingerprint.of(changed)));
        }
        // A setting added to the class without a row here would go untested.
        List<String> outsideTheTables = new java.util.ArrayList<>(LaunchFingerprint.trackedKeys());
        outsideTheTables.removeAll(AdvancedPerformance.allKeys());
        outsideTheTables.remove(LaunchFingerprint.KEY_MOBILEGLUES);
        assertEquals(new java.util.TreeSet<>(outsideTheTables), new java.util.TreeSet<>(covered));
    }

    @Test
    public void everyAdvancedOptionChangesIt() {
        LaunchFingerprint before = LaunchFingerprint.of(stored());
        for (AdvancedPerformance.Spec[] table : new AdvancedPerformance.Spec[][] {
                AdvancedPerformance.PERFORMANCE_SPECS, AdvancedPerformance.ANTI_CRASH_SPECS}) {
            for (AdvancedPerformance.Spec spec : table) {
                Map<String, Object> changed = stored();
                changed.put(spec.key, anotherValue(spec));
                assertEquals(spec.key, Collections.singletonList(spec.key),
                        before.differences(LaunchFingerprint.of(changed)));
            }
        }
    }

    @Test
    public void aMobileGluesOptionChangesIt() {
        Map<String, Object> changed = stored();
        changed.put("mgFsr1", "3");
        List<String> differences = LaunchFingerprint.of(stored()).differences(LaunchFingerprint.of(changed));
        assertEquals(Collections.singletonList(LaunchFingerprint.KEY_MOBILEGLUES), differences);
    }

    @Test
    public void anOptionThatWasNeverTouchedAndOneSetToItsDefaultAreTheSame() {
        Map<String, Object> explicit = stored();
        for (Map.Entry<String, Object> entry : AdvancedPerformance.defaultValues().entrySet()) {
            explicit.put(entry.getKey(), entry.getValue());
        }
        explicit.put("mgFsr1", "0");
        assertTrue(LaunchFingerprint.of(stored()).differences(LaunchFingerprint.of(explicit)).isEmpty());
    }

    @Test
    public void aValueTheScreenNeverOffersCountsAsTheDefaultBecauseThatIsWhatTheGameGets() {
        Map<String, Object> damaged = stored();
        damaged.put(AdvancedPerformance.KEY_GC, "no-such-collector");
        damaged.put(AdvancedPerformance.KEY_IPV4, 42);
        assertTrue(LaunchFingerprint.of(stored()).differences(LaunchFingerprint.of(damaged)).isEmpty());
    }

    @Test
    public void aNullMapIsTheSameAsAnEmptyOne() {
        assertTrue(LaunchFingerprint.of(null).differences(LaunchFingerprint.of(new HashMap<String, Object>())).isEmpty());
    }

    @Test
    public void textRoundTripKeepsEverySettingEvenWhenItHoldsLinesAndBackslashes() {
        Map<String, Object> prefs = stored();
        prefs.put("javaArgs", "-Dfoo=bar\n-Dpath=C:\\games\\mc\r\n-Xshare:auto");
        LaunchFingerprint before = LaunchFingerprint.of(prefs);
        LaunchFingerprint after = LaunchFingerprint.decode(before.encode());
        assertNotNull(after);
        assertTrue(before.differences(after).isEmpty());
        assertEquals(before.encode(), after.encode());
    }

    @Test
    public void textThatHoldsNoSettingIsNotAFingerprint() {
        assertNull(LaunchFingerprint.decode(null));
        assertNull(LaunchFingerprint.decode(""));
        assertNull(LaunchFingerprint.decode("\n\n"));
        assertNull(LaunchFingerprint.decode("no equals sign here"));
        assertNull(LaunchFingerprint.decode("=value without a key"));
    }

    @Test
    public void aSettingMissingFromOneSideIsADifference() {
        LaunchFingerprint full = LaunchFingerprint.of(stored());
        LaunchFingerprint partial = LaunchFingerprint.decode("allocation=2048\n");
        assertNotNull(partial);
        assertFalse(full.differences(partial).isEmpty());
        assertTrue(full.differences(null).size() > 20);
    }

    @Test
    public void changedKeysAreNamedByGroupInAFixedOrder() {
        assertEquals(LaunchFingerprint.Group.MEMORY, LaunchFingerprint.groupOf("allocation"));
        assertEquals(LaunchFingerprint.Group.JAVA_ARGUMENTS, LaunchFingerprint.groupOf("javaArgs"));
        assertEquals(LaunchFingerprint.Group.RENDERER, LaunchFingerprint.groupOf("renderer"));
        assertEquals(LaunchFingerprint.Group.PERFORMANCE_MODE, LaunchFingerprint.groupOf("performanceMode"));
        assertEquals(LaunchFingerprint.Group.ADVANCED, LaunchFingerprint.groupOf(AdvancedPerformance.KEY_GC));
        assertEquals(LaunchFingerprint.Group.ADVANCED, LaunchFingerprint.groupOf("bigCoreAffinity"));
        assertEquals(LaunchFingerprint.Group.MOBILEGLUES, LaunchFingerprint.groupOf(LaunchFingerprint.KEY_MOBILEGLUES));
        assertEquals(LaunchFingerprint.Group.OTHER, LaunchFingerprint.groupOf("force_vsync"));
        assertEquals(LaunchFingerprint.Group.OTHER, LaunchFingerprint.groupOf("somethingNew"));
        assertEquals(LaunchFingerprint.Group.OTHER, LaunchFingerprint.groupOf(null));
        assertEquals(Arrays.asList(LaunchFingerprint.Group.MEMORY, LaunchFingerprint.Group.ADVANCED,
                        LaunchFingerprint.Group.MOBILEGLUES),
                LaunchFingerprint.groups(Arrays.asList(LaunchFingerprint.KEY_MOBILEGLUES, AdvancedPerformance.KEY_JIT,
                        "allocation", AdvancedPerformance.KEY_GC)));
        assertTrue(LaunchFingerprint.groups(null).isEmpty());
    }

    @Test
    public void everyTrackedKeyHasAGroupAndIsInTheFingerprint() {
        String text = LaunchFingerprint.of(stored()).encode();
        for (String key : LaunchFingerprint.trackedKeys()) {
            assertTrue(key, text.contains("\n" + key + "=") || text.startsWith(key + "="));
            assertNotNull(LaunchFingerprint.groupOf(key));
        }
    }
}
