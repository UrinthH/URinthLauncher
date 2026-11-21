package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdvancedPresetsTest {
    private static Set<String> performanceKeys() {
        Set<String> keys = new HashSet<>();
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) keys.add(spec.key);
        return keys;
    }

    /** A value for a key that is different from the one given and that the screen offers. */
    private static Object anotherValue(AdvancedPerformance.Spec spec, Object current) {
        if (spec.isSwitch) return Boolean.valueOf(!((Boolean) current).booleanValue());
        for (String value : spec.values) if (!value.equals(current)) return value;
        throw new AssertionError(spec.key + " offers one value only");
    }

    @Test
    public void everyPresetSetsAllTwentyFourPerformanceOptionsAndNothingElse() {
        assertEquals(4, AdvancedPresets.PRESETS.length);
        for (String id : AdvancedPresets.PRESETS) {
            Map<String, Object> values = AdvancedPresets.valuesFor(id);
            assertEquals(id, performanceKeys(), values.keySet());
            for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
                Object value = values.get(spec.key);
                if (spec.isSwitch) {
                    assertTrue(id + " " + spec.key, value instanceof Boolean);
                } else {
                    assertTrue(id + " " + spec.key + " = " + value, value instanceof String && spec.accepts((String) value));
                }
            }
        }
    }

    @Test
    public void balancedIsTheDefaultsAndNothingIsChanged() {
        Map<String, Object> defaults = new HashMap<>(AdvancedPerformance.defaultValues());
        defaults.keySet().retainAll(performanceKeys());
        assertEquals(defaults, AdvancedPresets.valuesFor(AdvancedPresets.BALANCED));
        assertEquals("the defaults", AdvancedPresets.describe(AdvancedPresets.BALANCED));
        assertEquals(AdvancedPresets.BALANCED, AdvancedPresets.detect(Collections.<String, Object>emptyMap()));
        assertEquals(AdvancedPresets.BALANCED, AdvancedPresets.detect(null));
    }

    @Test
    public void everyOtherPresetReallyChangesSomething() {
        Map<String, Object> defaults = AdvancedPresets.valuesFor(AdvancedPresets.BALANCED);
        for (String id : AdvancedPresets.PRESETS) {
            if (AdvancedPresets.BALANCED.equals(id)) continue;
            assertFalse(id + " is the same as Balanced", defaults.equals(AdvancedPresets.valuesFor(id)));
            assertFalse(id, AdvancedPresets.describe(id).isEmpty());
        }
    }

    @Test
    public void presetsDifferFromEachOtherInAtLeastTwoOptions() {
        // With one option of difference, one click on one row would turn a preset into another one.
        for (String first : AdvancedPresets.PRESETS) {
            for (String second : AdvancedPresets.PRESETS) {
                if (first.equals(second)) continue;
                int differences = 0;
                Map<String, Object> a = AdvancedPresets.valuesFor(first);
                Map<String, Object> b = AdvancedPresets.valuesFor(second);
                for (String key : a.keySet()) if (!a.get(key).equals(b.get(key))) differences++;
                assertTrue(first + " and " + second + " differ in " + differences, differences >= 2);
            }
        }
    }

    @Test
    public void aPresetIsRecognisedAgainFromTheStoredValues() {
        for (String id : AdvancedPresets.PRESETS) {
            assertEquals(id, AdvancedPresets.detect(AdvancedPresets.valuesFor(id)));
        }
    }

    @Test
    public void changingOneRowOfAPresetMakesItYourOwnMix() {
        for (String id : AdvancedPresets.PRESETS) {
            for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
                Map<String, Object> stored = new HashMap<>(AdvancedPresets.valuesFor(id));
                stored.put(spec.key, anotherValue(spec, stored.get(spec.key)));
                assertEquals(id + " with " + spec.key + " changed", AdvancedPresets.CUSTOM, AdvancedPresets.detect(stored));
            }
        }
    }

    @Test
    public void theAntiCrashSwitchesAreNotPartOfAPreset() {
        for (String id : AdvancedPresets.PRESETS) {
            Map<String, Object> stored = new HashMap<>(AdvancedPresets.valuesFor(id));
            for (AdvancedPerformance.Spec spec : AdvancedPerformance.ANTI_CRASH_SPECS) {
                stored.put(spec.key, Boolean.valueOf(!Boolean.parseBoolean(spec.defaultValue)));
            }
            assertEquals(id, AdvancedPresets.detect(stored));
        }
    }

    @Test
    public void aValueTheScreenNeverOffersCountsAsTheDefault() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(AdvancedPerformance.KEY_GC, "zgc");
        stored.put(AdvancedPerformance.KEY_PRE_TOUCH, 7);
        stored.put(AdvancedPerformance.KEY_JIT, Boolean.TRUE);
        assertEquals(AdvancedPresets.BALANCED, AdvancedPresets.detect(stored));
    }

    @Test
    public void onlyTheFourPresetsArePresets() {
        for (String id : AdvancedPresets.PRESETS) assertTrue(id, AdvancedPresets.isPreset(id));
        assertFalse(AdvancedPresets.isPreset(AdvancedPresets.CUSTOM));
        assertFalse(AdvancedPresets.isPreset(null));
        assertFalse(AdvancedPresets.isPreset("turbo"));
        assertEquals("your own mix", AdvancedPresets.describe(AdvancedPresets.CUSTOM));
    }

    @Test
    public void thereAreNoValuesForYourOwnMixOrForNothing() {
        for (String id : new String[] {AdvancedPresets.CUSTOM, null, "turbo"}) {
            try {
                AdvancedPresets.valuesFor(id);
                throw new AssertionError("values for " + id);
            } catch (IllegalArgumentException expected) {
                // a mix that is not a preset has no values to write
            }
        }
    }
}
