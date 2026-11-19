package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdvancedPerformanceTest {
    @Test
    public void thereAreTwentyFourPerformanceOptionsAndThreeAntiCrashOnes() {
        assertEquals(24, AdvancedPerformance.PERFORMANCE_SPECS.length);
        assertEquals(3, AdvancedPerformance.ANTI_CRASH_SPECS.length);
        assertEquals(27, AdvancedPerformance.allKeys().size());
    }

    @Test
    public void everyKeyIsUniqueAndEveryChoiceDefaultsToAutoOrItsFirstValue() {
        Set<String> seen = new HashSet<>();
        for (String key : AdvancedPerformance.allKeys()) {
            assertTrue("key twice: " + key, seen.add(key));
            AdvancedPerformance.Spec spec = AdvancedPerformance.specFor(key);
            assertTrue("no entry for " + key, spec != null);
            assertTrue(key + " must offer at least two values", spec.values.length >= 2);
            assertTrue(key + " default must be one of its values", spec.accepts(spec.defaultValue));
            if (spec.isSwitch) {
                assertEquals(key, 2, spec.values.length);
            } else {
                assertEquals(key + " default is the first value", spec.values[0], spec.defaultValue);
                assertEquals(key + " default is auto", AdvancedPerformance.AUTO, spec.defaultValue);
            }
        }
    }

    @Test
    public void everyValueOfANumericChoiceIsANumber() {
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
            if (spec.isSwitch) continue;
            boolean numeric = false;
            for (String value : spec.values) {
                if (AdvancedPerformance.AUTO.equals(value)) continue;
                try {
                    Integer.parseInt(value);
                    numeric = true;
                } catch (NumberFormatException word) {
                    assertFalse(spec.key + " mixes words and numbers: " + value, numeric);
                }
            }
        }
    }

    @Test
    public void defaultsChangeNothingAboutHowPerformanceModeRuns() {
        AdvancedPerformance defaults = AdvancedPerformance.defaults();
        assertTrue(defaults.changed().isEmpty());
        // Performance Mode already ignored System.gc(), processed references in parallel and ran the heat watch,
        // the helper-thread scheduler, the refresh rate watch and the performance report.
        for (String key : new String[] {AdvancedPerformance.KEY_IGNORE_SYSTEM_GC, AdvancedPerformance.KEY_PARALLEL_REFS,
                AdvancedPerformance.KEY_THERMAL_GUARD, AdvancedPerformance.KEY_HELPER_THREADS,
                AdvancedPerformance.KEY_REFRESH_WATCH, AdvancedPerformance.KEY_PERF_REPORT, AdvancedPerformance.KEY_GC_LOG}) {
            assertTrue(key, defaults.on(key));
        }
        // And it never touched the heap, deduplicated strings or preferred IPv4.
        for (String key : new String[] {AdvancedPerformance.KEY_PRE_TOUCH, AdvancedPerformance.KEY_STRING_DEDUP,
                AdvancedPerformance.KEY_IPV4}) {
            assertFalse(key, defaults.on(key));
        }
        assertEquals(AdvancedPerformance.AUTO, defaults.choice(AdvancedPerformance.KEY_GC));
        assertEquals(-1, defaults.number(AdvancedPerformance.KEY_CORES));
    }

    @Test
    public void storedValuesAreReadBack() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(AdvancedPerformance.KEY_GC, "parallel");
        stored.put(AdvancedPerformance.KEY_CORES, "4");
        stored.put(AdvancedPerformance.KEY_PRE_TOUCH, Boolean.TRUE);
        stored.put(AdvancedPerformance.KEY_THERMAL_GUARD, "false");
        AdvancedPerformance settings = AdvancedPerformance.fromMap(stored);
        assertEquals("parallel", settings.choice(AdvancedPerformance.KEY_GC));
        assertEquals(4, settings.number(AdvancedPerformance.KEY_CORES));
        assertTrue(settings.on(AdvancedPerformance.KEY_PRE_TOUCH));
        assertFalse(settings.on(AdvancedPerformance.KEY_THERMAL_GUARD));
        assertEquals(4, settings.changed().size());
    }

    @Test
    public void aValueTheScreenNeverOffersFallsBackToTheDefault() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(AdvancedPerformance.KEY_GC, "zgc");                  // not offered
        stored.put(AdvancedPerformance.KEY_CORES, 4);                   // a number instead of text
        stored.put(AdvancedPerformance.KEY_PAUSE_GOAL, "999");          // not offered
        stored.put(AdvancedPerformance.KEY_PRE_TOUCH, "yes");           // not a boolean
        stored.put(AdvancedPerformance.KEY_IPV4, Integer.valueOf(1));   // not a boolean
        stored.put(AdvancedPerformance.KEY_JIT, Boolean.TRUE);          // a switch value in a choice
        AdvancedPerformance settings = AdvancedPerformance.fromMap(stored);
        assertTrue(settings.changed().isEmpty());
    }

    @Test
    public void missingOrNullInputGivesTheDefaults() {
        assertTrue(AdvancedPerformance.fromMap(null).changed().isEmpty());
        assertTrue(AdvancedPerformance.fromMap(new HashMap<String, Object>()).changed().isEmpty());
    }

    @Test
    public void changedListsOnlyWhatDiffersAndInTableOrder() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(AdvancedPerformance.KEY_JIT, "c1");
        stored.put(AdvancedPerformance.KEY_INITIAL_HEAP, "max");
        stored.put(AdvancedPerformance.KEY_IGNORE_SYSTEM_GC, Boolean.TRUE); // the default, so not listed
        List<String> changed = AdvancedPerformance.fromMap(stored).changed();
        assertEquals(2, changed.size());
        assertEquals(AdvancedPerformance.KEY_INITIAL_HEAP + "=max", changed.get(0));
        assertEquals(AdvancedPerformance.KEY_JIT + "=c1", changed.get(1));
    }

    @Test
    public void defaultValuesMatchTheTypesThePreferencesStore() {
        Map<String, Object> defaults = AdvancedPerformance.defaultValues();
        assertEquals(27, defaults.size());
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
            Object value = defaults.get(spec.key);
            if (spec.isSwitch) assertTrue(spec.key, value instanceof Boolean);
            else assertEquals(spec.key, spec.defaultValue, value);
        }
        // Reading them back must give the defaults again.
        assertTrue(AdvancedPerformance.fromMap(defaults).changed().isEmpty());
    }

    @Test
    public void aWrongKindOfAccessIsACodingErrorAndSaysSo() {
        AdvancedPerformance settings = AdvancedPerformance.defaults();
        assertNull(AdvancedPerformance.specFor("nope"));
        for (Runnable wrong : new Runnable[] {
                () -> settings.choice(AdvancedPerformance.KEY_PRE_TOUCH),
                () -> settings.on(AdvancedPerformance.KEY_GC),
                () -> settings.choice("nope"),
                () -> settings.on("nope")}) {
            try {
                wrong.run();
                throw new AssertionError("expected an IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }
}
