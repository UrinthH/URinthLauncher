package net.kdt.pojavlaunch.game.renderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything a player can choose for MobileGlues in Settings, and the {@code config.json} that carries it.
 *
 * <p>MobileGlues reads that file at start-up, but only when it knows the launcher: a launcher it does not
 * recognise gets built-in defaults and every other choice is ignored. Setting {@code MG_DIR_PATH} to a
 * folder of the launcher's own makes MobileGlues read the file from there ({@code is_custom_mg_dir} in its
 * source), so the choices made here really apply. The key names and value ranges below follow MobileGlues'
 * {@code config/settings.cpp}:
 *
 * <ul>
 *   <li>the three extension switches are read as numbers ({@code cJSON_IsNumber}), so they are written as
 *       0/1, never as JSON booleans, which MobileGlues would treat as "not there";</li>
 *   <li>an absent key means "off" for the switches and "no cache" for the cache size, so every key is
 *       written explicitly;</li>
 *   <li>the OpenGL version is the code major*10+minor, and MobileGlues itself moves anything else to the
 *       nearest valid one;</li>
 *   <li>a multi-draw order is a list of method names separated by commas or semicolons; the whole key is left
 *       out when nothing was chosen, which keeps MobileGlues' own automatic order.</li>
 * </ul>
 * Plain Java, so it can be tested without a device.
 */
public final class MobileGluesConfig {
    // Preference keys. They live next to the model so the settings XML and this class cannot drift apart
    // (a unit test compares the XML with these).
    public static final String KEY_GL_VERSION = "mgGlVersion";
    public static final String KEY_ANGLE = "mgAngle";
    public static final String KEY_NO_ERROR = "mgNoError";
    public static final String KEY_COMPUTE_SHADER = "mgComputeShader";
    public static final String KEY_TIMER_QUERY = "mgTimerQuery";
    public static final String KEY_DIRECT_STATE_ACCESS = "mgDirectStateAccess";
    public static final String KEY_GLSL_CACHE_MB = "mgGlslCacheMb";
    public static final String KEY_ANGLE_DEPTH_FIX = "mgAngleDepthFix";
    public static final String KEY_FSR1 = "mgFsr1";
    public static final String KEY_HIDE_ENV = "mgHideEnv";
    public static final String KEY_ORDER = "mgMultidrawOrder";
    public static final String KEY_ORDER_ARRAYS = "mgMultidrawOrderArrays";
    public static final String KEY_ORDER_ELEMENTS = "mgMultidrawOrderElements";
    public static final String KEY_ORDER_ELEMENTS_BASE_VERTEX = "mgMultidrawOrderElementsBaseVertex";
    public static final String KEY_ORDER_ARRAYS_INDIRECT = "mgMultidrawOrderArraysIndirect";
    public static final String KEY_ORDER_ELEMENTS_INDIRECT = "mgMultidrawOrderElementsIndirect";

    /** Settings that are a choice from a list; their values are stored as text. */
    public static final String[] LIST_KEYS = {
            KEY_GL_VERSION, KEY_ANGLE, KEY_NO_ERROR, KEY_GLSL_CACHE_MB, KEY_ANGLE_DEPTH_FIX, KEY_FSR1};
    /** Settings that are an on/off switch. */
    public static final String[] SWITCH_KEYS = {
            KEY_COMPUTE_SHADER, KEY_TIMER_QUERY, KEY_DIRECT_STATE_ACCESS, KEY_HIDE_ENV};
    /** Settings that are a typed list of draw methods. The first one is the global order. */
    public static final String[] ORDER_KEYS = {
            KEY_ORDER, KEY_ORDER_ARRAYS, KEY_ORDER_ELEMENTS, KEY_ORDER_ELEMENTS_BASE_VERTEX,
            KEY_ORDER_ARRAYS_INDIRECT, KEY_ORDER_ELEMENTS_INDIRECT};

    /** The OpenGL versions MobileGlues can report, as major*10+minor. */
    public static final int[] GL_VERSIONS = {32, 33, 40, 41, 42, 43, 44, 45, 46};
    /** The cache sizes offered in Settings, in MB (0 = no cache). */
    public static final int[] GLSL_CACHE_CHOICES_MB = {0, 16, 32, 64, 128, 256};

    public static final int DEFAULT_GL_VERSION = 46;
    public static final int DEFAULT_ANGLE = 0;
    public static final int DEFAULT_NO_ERROR = 0;
    public static final boolean DEFAULT_COMPUTE_SHADER = false;
    public static final boolean DEFAULT_TIMER_QUERY = true;
    public static final boolean DEFAULT_DIRECT_STATE_ACCESS = true;
    public static final int DEFAULT_GLSL_CACHE_MB = 32;
    public static final int DEFAULT_ANGLE_DEPTH_FIX = 0;
    public static final int DEFAULT_FSR1 = 0;
    public static final boolean DEFAULT_HIDE_ENV = false;

    /** The most the cache setting accepts: MobileGlues multiplies it by 1024*1024 in an int. */
    static final int MAX_GLSL_CACHE_MB = 1024;

    /** The names MobileGlues knows for a draw method ("auto" is rejected by MobileGlues itself). */
    private static final Set<String> BACKENDS = new LinkedHashSet<>(java.util.Arrays.asList(
            "unroll", "basevertex", "indirect", "multiindirect", "multibasevertex", "multiarrays", "compute"));
    private static final String NATIVE = "native";

    public final int glVersion;
    public final int angle;
    public final int noError;
    public final boolean computeShader;
    public final boolean timerQuery;
    public final boolean directStateAccess;
    public final int glslCacheMb;
    public final int angleDepthFix;
    public final int fsr1;
    public final boolean hideEnv;
    /** Cleaned orders in the order of {@link #ORDER_KEYS}; an empty text means "not chosen". */
    private final String[] orders;

    private MobileGluesConfig(int glVersion, int angle, int noError, boolean computeShader, boolean timerQuery,
                              boolean directStateAccess, int glslCacheMb, int angleDepthFix, int fsr1,
                              boolean hideEnv, String[] orders) {
        this.glVersion = glVersion;
        this.angle = angle;
        this.noError = noError;
        this.computeShader = computeShader;
        this.timerQuery = timerQuery;
        this.directStateAccess = directStateAccess;
        this.glslCacheMb = glslCacheMb;
        this.angleDepthFix = angleDepthFix;
        this.fsr1 = fsr1;
        this.hideEnv = hideEnv;
        this.orders = orders;
    }

    /** What a launch uses when nothing was changed in Settings. */
    public static MobileGluesConfig defaults() {
        return fromMap(Collections.<String, Object>emptyMap());
    }

    /**
     * Builds the settings from the stored preference values. Anything missing, of the wrong type or out of
     * range becomes its default (or the nearest valid value), so a damaged preference file can never produce
     * a config MobileGlues cannot read.
     */
    public static MobileGluesConfig fromMap(Map<String, ?> values) {
        String[] orders = new String[ORDER_KEYS.length];
        for (int i = 0; i < ORDER_KEYS.length; i++) {
            orders[i] = cleanOrder(asString(values.get(ORDER_KEYS[i])), i == 0).text;
        }
        return new MobileGluesConfig(
                normalizeGlVersion(asInt(values.get(KEY_GL_VERSION), DEFAULT_GL_VERSION)),
                inRange(asInt(values.get(KEY_ANGLE), DEFAULT_ANGLE), 0, 3, DEFAULT_ANGLE),
                inRange(asInt(values.get(KEY_NO_ERROR), DEFAULT_NO_ERROR), 0, 3, DEFAULT_NO_ERROR),
                asBoolean(values.get(KEY_COMPUTE_SHADER), DEFAULT_COMPUTE_SHADER),
                asBoolean(values.get(KEY_TIMER_QUERY), DEFAULT_TIMER_QUERY),
                asBoolean(values.get(KEY_DIRECT_STATE_ACCESS), DEFAULT_DIRECT_STATE_ACCESS),
                inRange(asInt(values.get(KEY_GLSL_CACHE_MB), DEFAULT_GLSL_CACHE_MB), 0, MAX_GLSL_CACHE_MB,
                        DEFAULT_GLSL_CACHE_MB),
                inRange(asInt(values.get(KEY_ANGLE_DEPTH_FIX), DEFAULT_ANGLE_DEPTH_FIX), 0, 2,
                        DEFAULT_ANGLE_DEPTH_FIX),
                inRange(asInt(values.get(KEY_FSR1), DEFAULT_FSR1), 0, 4, DEFAULT_FSR1),
                asBoolean(values.get(KEY_HIDE_ENV), DEFAULT_HIDE_ENV),
                orders);
    }

    /**
     * The default of every setting, the way the preferences store it (text for lists and typed orders,
     * a boolean for switches). Used by "Reset", and compared with the Settings XML by a unit test.
     */
    public static Map<String, Object> defaultValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_GL_VERSION, String.valueOf(DEFAULT_GL_VERSION));
        map.put(KEY_ANGLE, String.valueOf(DEFAULT_ANGLE));
        map.put(KEY_NO_ERROR, String.valueOf(DEFAULT_NO_ERROR));
        map.put(KEY_COMPUTE_SHADER, DEFAULT_COMPUTE_SHADER);
        map.put(KEY_TIMER_QUERY, DEFAULT_TIMER_QUERY);
        map.put(KEY_DIRECT_STATE_ACCESS, DEFAULT_DIRECT_STATE_ACCESS);
        map.put(KEY_GLSL_CACHE_MB, String.valueOf(DEFAULT_GLSL_CACHE_MB));
        map.put(KEY_ANGLE_DEPTH_FIX, String.valueOf(DEFAULT_ANGLE_DEPTH_FIX));
        map.put(KEY_FSR1, String.valueOf(DEFAULT_FSR1));
        map.put(KEY_HIDE_ENV, DEFAULT_HIDE_ENV);
        for (String key : ORDER_KEYS) map.put(key, "");
        return map;
    }

    /** The order chosen for one of {@link #ORDER_KEYS}, cleaned; empty when none was chosen. */
    public String order(int index) {
        return orders[index];
    }

    /** The text of {@code config.json}. */
    public String toJson() {
        StringBuilder json = new StringBuilder(512);
        json.append("{\n");
        json.append("  \"enableANGLE\": ").append(angle).append(",\n");
        json.append("  \"enableNoError\": ").append(noError).append(",\n");
        json.append("  \"enableExtComputeShader\": ").append(computeShader ? 1 : 0).append(",\n");
        json.append("  \"enableExtTimerQuery\": ").append(timerQuery ? 1 : 0).append(",\n");
        json.append("  \"enableExtDirectStateAccess\": ").append(directStateAccess ? 1 : 0).append(",\n");
        json.append("  \"maxGlslCacheSize\": ").append(glslCacheMb).append(",\n");
        json.append("  \"angleDepthClearFixMode\": ").append(angleDepthFix).append(",\n");
        json.append("  \"customGLVersion\": ").append(glVersion).append(",\n");
        json.append("  \"fsr1Setting\": ").append(fsr1).append(",\n");
        json.append("  \"hideMGEnvLevel\": ").append(hideEnv ? 1 : 0);
        String[] names = {"multidrawOrder", "multidrawOrderArrays", "multidrawOrderElements",
                "multidrawOrderElementsBaseVertex", "multidrawOrderArraysIndirect", "multidrawOrderElementsIndirect"};
        for (int i = 0; i < names.length; i++) {
            // The text only holds a-z and commas (see cleanOrder), so it needs no JSON escaping.
            if (!orders[i].isEmpty()) json.append(",\n  \"").append(names[i]).append("\": \"").append(orders[i]).append('"');
        }
        json.append("\n}\n");
        return json.toString();
    }

    /** One short line for the game log. */
    public String describe() {
        StringBuilder text = new StringBuilder(160);
        text.append("OpenGL ").append(glVersionText(glVersion));
        text.append(", ANGLE ").append(angleText(angle));
        text.append(", errors ").append(noErrorText(noError));
        text.append(", compute ").append(computeShader ? "on" : "off");
        text.append(", timer query ").append(timerQuery ? "on" : "off");
        text.append(", DSA ").append(directStateAccess ? "on" : "off");
        text.append(", shader cache ").append(glslCacheMb == 0 ? "off" : glslCacheMb + " MB");
        if (angleDepthFix != 0) text.append(", ANGLE depth fix ").append(angleDepthFix);
        text.append(", FSR1 ").append(fsr1 == 0 ? "off" : String.valueOf(fsr1));
        text.append(", hide env ").append(hideEnv ? "on" : "off");
        int chosen = 0;
        for (String order : orders) if (!order.isEmpty()) chosen++;
        text.append(", multi-draw ").append(chosen == 0 ? "automatic" : chosen + " custom order(s)");
        return text.toString();
    }

    // ---------------------------------------------------------------- values

    /** 46 -> "4.6". */
    public static String glVersionText(int code) {
        return (code / 10) + "." + (code % 10);
    }

    private static String angleText(int value) {
        switch (value) {
            case 1: return "on if possible";
            case 2: return "always off";
            case 3: return "always on";
            default: return "off if possible";
        }
    }

    private static String noErrorText(int value) {
        switch (value) {
            case 1: return "off";
            case 2: return "level 1";
            case 3: return "level 2";
            default: return "auto";
        }
    }

    /** MobileGlues' own rule: above 4.6 is 4.6, below 3.2 is 3.2, and 3.4 to 3.9 is 3.3. */
    static int normalizeGlVersion(int code) {
        if (code > 46) return 46;
        if (code < 32) return 32;
        if (code > 33 && code < 40) return 33;
        return code;
    }

    private static int inRange(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    // ---------------------------------------------------------------- typed orders

    /** A typed multi-draw order, cleaned, and the words in it that MobileGlues does not know. */
    public static final class Order {
        /** Lower-case names separated by single commas, without repeats; empty when nothing valid was typed. */
        public final String text;
        /** What was typed but is not a method name (or "native" where it cannot be used), as typed. */
        public final List<String> ignored;

        Order(String text, List<String> ignored) {
            this.text = text;
            this.ignored = Collections.unmodifiableList(ignored);
        }
    }

    /**
     * Cleans a typed order the way MobileGlues reads it: words separated by commas or semicolons (a ">" or a
     * line break is accepted too, because MobileGlues' log writes its own orders as "a > b"), spaces,
     * underscores and dashes ignored, capitals ignored. "native" is only meaningful in the global order.
     */
    public static Order cleanOrder(String raw, boolean allowNative) {
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        List<String> ignored = new ArrayList<>();
        if (raw != null) {
            for (String token : raw.split("[,;>\\n\\r]")) {
                String name = normalizeName(token);
                if (name.isEmpty()) continue;
                if (NATIVE.equals(name) ? allowNative : BACKENDS.contains(name)) {
                    kept.add(name);
                } else {
                    ignored.add(token.trim());
                }
            }
        }
        StringBuilder text = new StringBuilder();
        for (String name : kept) {
            if (text.length() > 0) text.append(',');
            text.append(name);
        }
        return new Order(text.toString(), ignored);
    }

    private static String normalizeName(String token) {
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == ' ' || c == '\t' || c == '_' || c == '-') continue;
            out.append(Character.toLowerCase(c));
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- tolerant conversions

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
        }
        return fallback;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : "";
    }
}
