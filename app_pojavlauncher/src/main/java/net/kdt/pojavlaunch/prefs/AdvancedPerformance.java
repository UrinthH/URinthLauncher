package net.kdt.pojavlaunch.prefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The options of the "Advanced settings" screen, which is only shown while Performance Mode is on.
 * Plain Java with no Android code in it, so the table and the rules around it are tested on a plain JVM.
 *
 * Every option here has one job in the launcher and nothing else: the JVM options become Java flags in
 * {@code AdvancedJvmArguments}, the rest are read by the game screen (heat watch, refresh rate, helper
 * threads, Game State hint, log lines) or by the Anti Crash guard. A unit test lists the keys the screen
 * uses and the values it offers and compares them with this table, so a row cannot exist without a
 * matching entry here, and an entry cannot be offered without a way to change it.
 *
 * "auto" always means what Performance Mode did before these options existed, so a player who never opens
 * the screen gets the same game as before.
 */
public final class AdvancedPerformance {
    /** The value of a choice that leaves the decision to Performance Mode. */
    public static final String AUTO = "auto";

    // ---- Memory
    public static final String KEY_INITIAL_HEAP = "advInitialHeap";
    public static final String KEY_METASPACE = "advMetaspace";
    public static final String KEY_CODE_CACHE = "advCodeCache";
    public static final String KEY_PRE_TOUCH = "advPreTouch";
    public static final String KEY_STRING_DEDUP = "advStringDedup";
    // ---- Garbage collection
    public static final String KEY_GC = "advGc";
    public static final String KEY_PAUSE_GOAL = "advPauseGoal";
    public static final String KEY_YOUNG_GEN = "advYoungGen";
    public static final String KEY_REGION_SIZE = "advRegionSize";
    public static final String KEY_G1_RESERVE = "advG1Reserve";
    public static final String KEY_GC_THREADS = "advGcThreads";
    public static final String KEY_IGNORE_SYSTEM_GC = "advIgnoreSystemGc";
    public static final String KEY_PARALLEL_REFS = "advParallelRefs";
    // ---- CPU and JIT
    public static final String KEY_JIT = "advJit";
    public static final String KEY_JIT_THREADS = "advJitThreads";
    public static final String KEY_CORES = "advCores";
    public static final String KEY_HELPER_THREADS = "advHelperThreads";
    // ---- Screen and heat
    public static final String KEY_REFRESH_RATE = "advRefreshRate";
    public static final String KEY_REFRESH_WATCH = "advRefreshWatch";
    public static final String KEY_THERMAL_GUARD = "advThermalGuard";
    public static final String KEY_GAME_STATE = "advGameState";
    // ---- Network and logs
    public static final String KEY_IPV4 = "advIpv4";
    public static final String KEY_GC_LOG = "advGcLog";
    public static final String KEY_PERF_REPORT = "advPerfReport";
    // ---- Anti Crash
    public static final String KEY_IGNORE_UNKNOWN_FLAGS = "advIgnoreUnknownFlags";
    public static final String KEY_LOW_MEMORY_GUARD = "advLowMemoryGuard";
    public static final String KEY_JIT_CRASH_GUARD = "advJitCrashGuard";

    /** One option: a switch, or a choice among fixed values. The default of a choice is its first value. */
    public static final class Spec {
        public final String key;
        public final boolean isSwitch;
        /** For a switch "true" or "false"; for a choice one of {@link #values}. */
        public final String defaultValue;
        /** The values a choice offers, in the order the screen lists them; {"true", "false"} for a switch. */
        public final String[] values;

        private Spec(String key, boolean isSwitch, String defaultValue, String[] values) {
            this.key = key;
            this.isSwitch = isSwitch;
            this.defaultValue = defaultValue;
            this.values = values;
        }

        boolean accepts(String value) {
            for (String allowed : values) if (allowed.equals(value)) return true;
            return false;
        }
    }

    private static Spec sw(String key, boolean defaultOn) {
        return new Spec(key, true, Boolean.toString(defaultOn), new String[] {"true", "false"});
    }

    private static Spec pick(String key, String... values) {
        return new Spec(key, false, values[0], values);
    }

    /** The 24 performance options, in the order the screen shows them. */
    public static final Spec[] PERFORMANCE_SPECS = {
            // Memory
            pick(KEY_INITIAL_HEAP, AUTO, "small", "max"),
            pick(KEY_METASPACE, AUTO, "64", "256", "512"),
            pick(KEY_CODE_CACHE, AUTO, "128", "256", "512"),
            sw(KEY_PRE_TOUCH, false),
            sw(KEY_STRING_DEDUP, false),
            // Garbage collection
            pick(KEY_GC, AUTO, "g1", "parallel", "serial"),
            pick(KEY_PAUSE_GOAL, AUTO, "20", "35", "100", "200"),
            pick(KEY_YOUNG_GEN, AUTO, "small", "large"),
            pick(KEY_REGION_SIZE, AUTO, "2", "4", "8"),
            pick(KEY_G1_RESERVE, AUTO, "15", "20", "25"),
            pick(KEY_GC_THREADS, AUTO, "2", "3", "4"),
            sw(KEY_IGNORE_SYSTEM_GC, true),
            sw(KEY_PARALLEL_REFS, true),
            // CPU and JIT
            pick(KEY_JIT, AUTO, "c1"),
            pick(KEY_JIT_THREADS, AUTO, "2", "3", "4"),
            pick(KEY_CORES, AUTO, "6", "4", "3", "2"),
            sw(KEY_HELPER_THREADS, true),
            // Screen and heat
            pick(KEY_REFRESH_RATE, AUTO, "60", "90", "120"),
            sw(KEY_REFRESH_WATCH, true),
            sw(KEY_THERMAL_GUARD, true),
            sw(KEY_GAME_STATE, true),
            // Network and logs
            sw(KEY_IPV4, false),
            sw(KEY_GC_LOG, true),
            sw(KEY_PERF_REPORT, true),
    };

    /** The three Anti Crash options. */
    public static final Spec[] ANTI_CRASH_SPECS = {
            sw(KEY_IGNORE_UNKNOWN_FLAGS, true),
            sw(KEY_LOW_MEMORY_GUARD, true),
            sw(KEY_JIT_CRASH_GUARD, true),
    };

    private static final Map<String, Spec> BY_KEY = new LinkedHashMap<>();

    static {
        for (Spec spec : PERFORMANCE_SPECS) BY_KEY.put(spec.key, spec);
        for (Spec spec : ANTI_CRASH_SPECS) BY_KEY.put(spec.key, spec);
    }

    private final Map<String, String> values;

    private AdvancedPerformance(Map<String, String> values) {
        this.values = values;
    }

    /** Every option at its default. */
    public static AdvancedPerformance defaults() {
        return fromMap(Collections.<String, Object>emptyMap());
    }

    /**
     * The options as stored in the preferences. A missing key, a wrong type or a value the screen never
     * offers (an older build, a restored backup) means the default for that option.
     */
    public static AdvancedPerformance fromMap(Map<String, ?> stored) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Spec spec : BY_KEY.values()) {
            Object raw = stored == null ? null : stored.get(spec.key);
            String value = null;
            if (spec.isSwitch) {
                if (raw instanceof Boolean) value = raw.toString();
                else if ("true".equals(raw) || "false".equals(raw)) value = (String) raw;
            } else if (raw instanceof String && spec.accepts((String) raw)) {
                value = (String) raw;
            }
            values.put(spec.key, value != null ? value : spec.defaultValue);
        }
        return new AdvancedPerformance(values);
    }

    /** Key to default (a Boolean for a switch, a String for a choice), for the screen's reset action. */
    public static Map<String, Object> defaultValues() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (Spec spec : BY_KEY.values()) {
            defaults.put(spec.key, spec.isSwitch ? (Object) Boolean.valueOf(spec.defaultValue) : spec.defaultValue);
        }
        return defaults;
    }

    /** The table entry for a key, or null when there is none. */
    public static Spec specFor(String key) {
        return BY_KEY.get(key);
    }

    /** Every key, performance options first. */
    public static List<String> allKeys() {
        return Collections.unmodifiableList(new ArrayList<>(BY_KEY.keySet()));
    }

    /** The value of a choice; throws for a key that is not in the table (a typo in the code, not in the data). */
    public String choice(String key) {
        Spec spec = require(key);
        if (spec.isSwitch) throw new IllegalArgumentException(key + " is a switch");
        return values.get(key);
    }

    /** The state of a switch; throws for a key that is not in the table. */
    public boolean on(String key) {
        Spec spec = require(key);
        if (!spec.isSwitch) throw new IllegalArgumentException(key + " is a choice");
        return "true".equals(values.get(key));
    }

    /** The number a choice stands for (every value except "auto" is a number), or -1 for "auto". */
    public int number(String key) {
        String value = choice(key);
        if (AUTO.equals(value)) return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /** True when the option is at its default. */
    public boolean isDefault(String key) {
        return require(key).defaultValue.equals(values.get(key));
    }

    /** "key=value" for each option that is not at its default, in table order; empty when none is. */
    public List<String> changed() {
        List<String> changed = new ArrayList<>();
        for (Spec spec : BY_KEY.values()) {
            if (!isDefault(spec.key)) changed.add(spec.key + "=" + values.get(spec.key));
        }
        return changed;
    }

    private static Spec require(String key) {
        Spec spec = BY_KEY.get(key);
        if (spec == null) throw new IllegalArgumentException("Unknown advanced setting " + key);
        return spec;
    }

    @Override
    public String toString() {
        return Arrays.toString(changed().toArray());
    }
}
