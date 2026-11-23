package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The remembered choice of the Quick setup row: the choice outlives the options it wrote, so that options rewritten by
 * something other than the player (another process saving an older copy of the settings, for one) are put back instead of
 * the choice being lost.
 */
public class AdvancedPresetChoiceTest {
    private static Set<String> performanceKeys() {
        Set<String> keys = new HashSet<>();
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) keys.add(spec.key);
        return keys;
    }

    private static Object anotherValue(AdvancedPerformance.Spec spec, Object current) {
        if (spec.isSwitch) return Boolean.valueOf(!((Boolean) current).booleanValue());
        for (String value : spec.values) if (!value.equals(current)) return value;
        throw new AssertionError(spec.key + " offers one value only");
    }

    /** What the preferences hold after a preset was picked: the options and the choice. */
    private static Map<String, Object> picked(String id) {
        return new HashMap<>(AdvancedPresets.writesFor(id));
    }

    @Test
    public void pickingAPresetWritesItsTwentyFourOptionsAndTheChoice() {
        for (String id : AdvancedPresets.PRESETS) {
            Map<String, Object> writes = AdvancedPresets.writesFor(id);
            Set<String> expected = performanceKeys();
            expected.add(AdvancedPresets.KEY_CHOSEN);
            assertEquals(id, expected, writes.keySet());
            assertEquals(id, writes.get(AdvancedPresets.KEY_CHOSEN));
            for (String key : performanceKeys()) {
                assertEquals(id + " " + key, AdvancedPresets.valuesFor(id).get(key), writes.get(key));
            }
        }
    }

    @Test
    public void theChoiceIsNotOneOfTheOptionsSoItNeverRestartsTheGame() {
        assertFalse(AdvancedPerformance.allKeys().contains(AdvancedPresets.KEY_CHOSEN));
        assertFalse(performanceKeys().contains(AdvancedPresets.KEY_CHOSEN));
        assertEquals("advQuickSetup", AdvancedPresets.KEY_CHOSEN);
    }

    @Test
    public void withoutAChoiceTheRowShowsWhatTheOptionsAddUpTo() {
        for (String id : AdvancedPresets.PRESETS) {
            AdvancedPresets.Shown shown = AdvancedPresets.shown(AdvancedPresets.valuesFor(id));
            assertEquals(id, shown.id);
            assertFalse(id, shown.restore);
        }
        AdvancedPresets.Shown nothing = AdvancedPresets.shown(Collections.<String, Object>emptyMap());
        assertEquals(AdvancedPresets.BALANCED, nothing.id);
        assertFalse(nothing.restore);
        assertEquals(AdvancedPresets.BALANCED, AdvancedPresets.shown(null).id);

        Map<String, Object> mix = new HashMap<>(AdvancedPresets.valuesFor(AdvancedPresets.MAX_FPS));
        mix.put(AdvancedPerformance.KEY_JIT, "c1");
        AdvancedPresets.Shown shown = AdvancedPresets.shown(mix);
        assertEquals(AdvancedPresets.CUSTOM, shown.id);
        assertFalse(shown.restore);
    }

    @Test
    public void aChoiceWhoseOptionsAreStillThereNeedsNothing() {
        for (String id : AdvancedPresets.PRESETS) {
            AdvancedPresets.Shown shown = AdvancedPresets.shown(picked(id));
            assertEquals(id, shown.id);
            assertFalse(id, shown.restore);
            assertFalse(id, AdvancedPresets.choiceIsOutdated(picked(id)));
        }
    }

    @Test
    public void aChoiceIsShownAndItsOptionsAreRestoredWhenTheOptionsWereRewritten() {
        // The reported bug: Max FPS picked, the three options it changes are back to the defaults a moment later.
        for (String chosen : AdvancedPresets.PRESETS) {
            for (String rewrittenTo : AdvancedPresets.PRESETS) {
                if (chosen.equals(rewrittenTo)) continue;
                Map<String, Object> stored = picked(chosen);
                stored.putAll(AdvancedPresets.valuesFor(rewrittenTo));
                AdvancedPresets.Shown shown = AdvancedPresets.shown(stored);
                assertEquals(chosen + " over " + rewrittenTo, chosen, shown.id);
                assertTrue(chosen + " over " + rewrittenTo, shown.restore);
            }
        }
    }

    @Test
    public void restoringPutsTheChosenPresetBackWholeAndOnlyOnce() {
        Map<String, Object> stored = picked(AdvancedPresets.MAX_FPS);
        stored.putAll(AdvancedPresets.valuesFor(AdvancedPresets.BALANCED));
        assertEquals(AdvancedPresets.BALANCED, AdvancedPresets.detect(stored));

        AdvancedPresets.Shown shown = AdvancedPresets.shown(stored);
        assertTrue(shown.restore);
        stored.putAll(AdvancedPresets.writesFor(shown.id)); // what AdvancedPresetStore.heal writes

        assertEquals(AdvancedPresets.MAX_FPS, AdvancedPresets.detect(stored));
        assertFalse(AdvancedPresets.shown(stored).restore);
        assertEquals(AdvancedPresets.MAX_FPS, AdvancedPresets.shown(stored).id);
        assertEquals("parallel", stored.get(AdvancedPerformance.KEY_GC));
        assertEquals("max", stored.get(AdvancedPerformance.KEY_INITIAL_HEAP));
        assertEquals("4", stored.get(AdvancedPerformance.KEY_GC_THREADS));
    }

    @Test
    public void balancedCanBeChosenToo() {
        Map<String, Object> stored = picked(AdvancedPresets.BALANCED);
        assertFalse(AdvancedPresets.shown(stored).restore);
        stored.putAll(AdvancedPresets.valuesFor(AdvancedPresets.MAX_FPS));
        AdvancedPresets.Shown shown = AdvancedPresets.shown(stored);
        assertEquals(AdvancedPresets.BALANCED, shown.id);
        assertTrue(shown.restore);
    }

    @Test
    public void changingOneOptionByHandEndsTheChoice() {
        for (String id : AdvancedPresets.PRESETS) {
            for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
                Map<String, Object> stored = picked(id);
                stored.put(spec.key, anotherValue(spec, stored.get(spec.key)));
                assertTrue(id + " with " + spec.key + " changed", AdvancedPresets.choiceIsOutdated(stored));
            }
        }
    }

    @Test
    public void withNoChoiceNothingIsOutdated() {
        for (String id : AdvancedPresets.PRESETS) {
            assertFalse(id, AdvancedPresets.choiceIsOutdated(AdvancedPresets.valuesFor(id)));
        }
        assertFalse(AdvancedPresets.choiceIsOutdated(null));
        assertFalse(AdvancedPresets.choiceIsOutdated(Collections.<String, Object>emptyMap()));
    }

    @Test
    public void theAntiCrashSwitchesNeitherRestoreNorEndAChoice() {
        for (String id : AdvancedPresets.PRESETS) {
            Map<String, Object> stored = picked(id);
            for (AdvancedPerformance.Spec spec : AdvancedPerformance.ANTI_CRASH_SPECS) {
                stored.put(spec.key, Boolean.valueOf(!Boolean.parseBoolean(spec.defaultValue)));
            }
            assertFalse(id, AdvancedPresets.shown(stored).restore);
            assertFalse(id, AdvancedPresets.choiceIsOutdated(stored));
            assertEquals(id, AdvancedPresets.shown(stored).id);
        }
    }

    @Test
    public void aChoiceThatIsNotAPresetIsIgnored() {
        Object[] broken = {AdvancedPresets.CUSTOM, "turbo", "", Integer.valueOf(3), Boolean.TRUE};
        for (Object choice : broken) {
            Map<String, Object> stored = new HashMap<>(AdvancedPresets.valuesFor(AdvancedPresets.COOL));
            stored.put(AdvancedPresets.KEY_CHOSEN, choice);
            assertTrue(String.valueOf(choice), AdvancedPresets.chosen(stored) == null);
            AdvancedPresets.Shown shown = AdvancedPresets.shown(stored);
            assertEquals(String.valueOf(choice), AdvancedPresets.COOL, shown.id);
            assertFalse(String.valueOf(choice), shown.restore);
            assertFalse(String.valueOf(choice), AdvancedPresets.choiceIsOutdated(stored));
        }
        assertNull(AdvancedPresets.chosen(null));
        assertNull(AdvancedPresets.chosen(Collections.<String, Object>emptyMap()));
    }

    @Test
    public void aPhoneFromBeforeTheChoiceExistedKeepsWorkingAsItDid() {
        // Options that were stored by an older build, with no choice: the row shows what they add up to, nothing is restored.
        Map<String, Object> stored = new HashMap<>(AdvancedPresets.valuesFor(AdvancedPresets.SMOOTHEST));
        assertNull(AdvancedPresets.chosen(stored));
        assertEquals(AdvancedPresets.SMOOTHEST, AdvancedPresets.shown(stored).id);
        assertFalse(AdvancedPresets.shown(stored).restore);
    }

    @Test
    public void everyPresetCanBePickedAfterEveryOtherOne() {
        // Picking is one write of options and choice together, so the last pick always wins whole.
        Map<String, Object> stored = new HashMap<>();
        for (String first : AdvancedPresets.PRESETS) {
            for (String second : AdvancedPresets.PRESETS) {
                stored.putAll(AdvancedPresets.writesFor(first));
                stored.putAll(AdvancedPresets.writesFor(second));
                assertEquals(first + " then " + second, second, AdvancedPresets.shown(stored).id);
                assertEquals(first + " then " + second, second, AdvancedPresets.detect(stored));
                assertFalse(first + " then " + second, AdvancedPresets.shown(stored).restore);
            }
        }
    }
}
