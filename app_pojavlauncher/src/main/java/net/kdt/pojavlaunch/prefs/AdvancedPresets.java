package net.kdt.pojavlaunch.prefs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "Quick setup" row of the Advanced settings screen: ready-made mixes of the 24 performance options, for a player who
 * does not want to go through them one by one. Plain Java with no Android code in it, so the table is tested on a plain JVM.
 *
 * A preset is not a setting of its own. Picking one writes the real options of {@link AdvancedPerformance} (the same
 * preferences the rows below it show, which the game reads at the next start), and the row shows which preset the options
 * add up to right now: any change of a single row below turns it into "your own mix". Only the 24 performance options
 * are part of a preset. The Anti Crash switches, the RAM slider, the MobileGlues options and the sustained performance and
 * big-core switches are left as they are.
 *
 * Every preset is a list of changes from the defaults, and "Balanced" has none: it is what Performance Mode did before
 * these options existed. What each change does is described on its own row; this class only says which ones go together.
 */
public final class AdvancedPresets {
    public static final String BALANCED = "balanced";
    public static final String MAX_FPS = "maxfps";
    public static final String SMOOTHEST = "smoothest";
    public static final String COOL = "cool";
    /** Not a preset: the options are a mix that no preset matches. */
    public static final String CUSTOM = "custom";

    /** The presets a player can pick, in the order the row lists them. {@link #CUSTOM} comes after them. */
    public static final String[] PRESETS = {BALANCED, MAX_FPS, SMOOTHEST, COOL};

    private static final Map<String, String[]> CHANGES = new LinkedHashMap<>();

    static {
        CHANGES.put(BALANCED, new String[0]);
        // The collector that runs no background threads and takes the least work per allocation, one big young generation
        // from the first second (the heap starts at its maximum) and four collector threads, so a pause does not wait for
        // a slow core. A pause is a little longer now and then; the average frame rate is what it is for.
        CHANGES.put(MAX_FPS, new String[] {
                AdvancedPerformance.KEY_GC, "parallel",
                AdvancedPerformance.KEY_INITIAL_HEAP, "max",
                AdvancedPerformance.KEY_GC_THREADS, "4",
        });
        // Short, frequent cleanups and more room to move objects in, which is what stops G1 from falling back to the one
        // long pause that shows up as a freeze.
        CHANGES.put(SMOOTHEST, new String[] {
                AdvancedPerformance.KEY_PAUSE_GOAL, "20",
                AdvancedPerformance.KEY_G1_RESERVE, "20",
        });
        // Less work in the background for a phone that gets hot in long games: the simple JIT, fewer compiler and
        // collector threads, and the screen at 60 Hz.
        CHANGES.put(COOL, new String[] {
                AdvancedPerformance.KEY_JIT, "c1",
                AdvancedPerformance.KEY_JIT_THREADS, "2",
                AdvancedPerformance.KEY_GC_THREADS, "2",
                AdvancedPerformance.KEY_REFRESH_RATE, "60",
        });
    }

    private AdvancedPresets() {}

    /** True for the four presets a player can pick ({@link #CUSTOM} and anything else are not). */
    public static boolean isPreset(String id) {
        return id != null && CHANGES.containsKey(id);
    }

    /**
     * The value of every one of the 24 performance options in a preset: key to a Boolean for a switch or a String for a
     * choice, ready to write into the preferences. Throws for an id that is not a preset (a mistake in the code).
     */
    public static Map<String, Object> valuesFor(String id) {
        String[] changes = CHANGES.get(id);
        if (changes == null) throw new IllegalArgumentException("Not a preset: " + id);
        Map<String, Object> values = new LinkedHashMap<>();
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
            values.put(spec.key, spec.isSwitch ? (Object) Boolean.valueOf(spec.defaultValue) : spec.defaultValue);
        }
        for (int i = 0; i < changes.length; i += 2) {
            AdvancedPerformance.Spec spec = AdvancedPerformance.specFor(changes[i]);
            values.put(spec.key, spec.isSwitch ? (Object) Boolean.valueOf(changes[i + 1]) : changes[i + 1]);
        }
        return Collections.unmodifiableMap(values);
    }

    /** The "key=value" changes from the defaults that a preset makes, for tests and the log. */
    public static String describe(String id) {
        String[] changes = CHANGES.get(id);
        if (changes == null) return "your own mix";
        if (changes.length == 0) return "the defaults";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < changes.length; i += 2) {
            if (text.length() > 0) text.append(", ");
            text.append(changes[i]).append('=').append(changes[i + 1]);
        }
        return text.toString();
    }

    /**
     * The stored key that remembers which quick setup the player picked, until a single option is changed by hand. Without
     * it the row could only say which preset the options happen to add up to, so a choice was gone the moment anything
     * rewrote the options (another process writing an older copy of the preferences file, for one).
     */
    public static final String KEY_CHOSEN = "advQuickSetup";

    /** What the Quick setup row shows, and whether the 24 options have to be put back to match it. */
    public static final class Shown {
        /** One of {@link #PRESETS}, or {@link #CUSTOM}. */
        public final String id;
        /** True when the player picked {@link #id} but the stored options no longer add up to it. */
        public final boolean restore;

        Shown(String id, boolean restore) {
            this.id = id;
            this.restore = restore;
        }
    }

    /**
     * Everything that picking a quick setup writes: its 24 options and the choice itself (key to a Boolean for a switch or
     * a String), to be put into one preferences edit so that the game and the screen never see a half-written mix.
     */
    public static Map<String, Object> writesFor(String id) {
        Map<String, Object> writes = new LinkedHashMap<>(valuesFor(id));
        writes.put(KEY_CHOSEN, id);
        return Collections.unmodifiableMap(writes);
    }

    /** The quick setup the player picked and has not left by changing a single option, or null. */
    public static String chosen(Map<String, ?> stored) {
        Object raw = stored == null ? null : stored.get(KEY_CHOSEN);
        return raw instanceof String && isPreset((String) raw) ? (String) raw : null;
    }

    /**
     * What the row shows for the stored options and the remembered choice. A remembered choice wins: when the options no
     * longer match it, something other than the player changed them, so the choice is shown and {@link Shown#restore} says
     * to write its options again. With no remembered choice the row shows what the options add up to, as before.
     */
    public static Shown shown(Map<String, ?> stored) {
        String chosen = chosen(stored);
        String detected = detect(stored);
        if (chosen != null) return new Shown(chosen, !chosen.equals(detected));
        return new Shown(detected, false);
    }

    /**
     * True when a choice is remembered but the options no longer match it because the player changed one of them. Call it
     * after a change made on the screen, and forget the choice (remove {@link #KEY_CHOSEN}) when it says true.
     */
    public static boolean choiceIsOutdated(Map<String, ?> stored) {
        String chosen = chosen(stored);
        return chosen != null && !chosen.equals(detect(stored));
    }

    /**
     * The preset that the stored options add up to, or {@link #CUSTOM} when none matches. A missing or unknown value counts
     * as the default, like everywhere else ({@link AdvancedPerformance#fromMap}), so a phone that never opened the screen is
     * on Balanced.
     */
    public static String detect(Map<String, ?> stored) {
        AdvancedPerformance current = AdvancedPerformance.fromMap(stored);
        for (String id : PRESETS) {
            if (matches(current, valuesFor(id))) return id;
        }
        return CUSTOM;
    }

    private static boolean matches(AdvancedPerformance current, Map<String, Object> wanted) {
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
            Object value = wanted.get(spec.key);
            if (spec.isSwitch) {
                if (current.on(spec.key) != ((Boolean) value).booleanValue()) return false;
            } else if (!current.choice(spec.key).equals(value)) {
                return false;
            }
        }
        return true;
    }
}
