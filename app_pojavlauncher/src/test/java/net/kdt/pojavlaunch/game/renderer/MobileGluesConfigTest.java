package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobileGluesConfigTest {
    private static Map<String, Object> with(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    public void nothingChosenMeansOpenGl46AndTheDocumentedDefaults() {
        MobileGluesConfig config = MobileGluesConfig.defaults();
        assertEquals(46, config.glVersion);
        assertEquals(0, config.angle);
        assertEquals(0, config.noError);
        assertFalse(config.computeShader);
        assertTrue(config.timerQuery);
        assertTrue(config.directStateAccess);
        assertEquals(32, config.glslCacheMb);
        assertEquals(0, config.angleDepthFix);
        assertEquals(0, config.fsr1);
        assertFalse(config.hideEnv);
        for (int i = 0; i < MobileGluesConfig.ORDER_KEYS.length; i++) assertEquals("", config.order(i));
    }

    @Test
    public void theDefaultFileIsExactlyWhatMobileGluesReads() {
        // Numbers, never JSON booleans: MobileGlues' cJSON_IsNumber would treat a boolean as "not there".
        String expected = "{\n"
                + "  \"enableANGLE\": 0,\n"
                + "  \"enableNoError\": 0,\n"
                + "  \"enableExtComputeShader\": 0,\n"
                + "  \"enableExtTimerQuery\": 1,\n"
                + "  \"enableExtDirectStateAccess\": 1,\n"
                + "  \"maxGlslCacheSize\": 32,\n"
                + "  \"angleDepthClearFixMode\": 0,\n"
                + "  \"customGLVersion\": 46,\n"
                + "  \"fsr1Setting\": 0,\n"
                + "  \"hideMGEnvLevel\": 0\n"
                + "}\n";
        assertEquals(expected, MobileGluesConfig.defaults().toJson());
    }

    @Test
    public void whatThePlayerChoseIsWhatIsWritten() {
        Map<String, Object> chosen = with(
                MobileGluesConfig.KEY_GL_VERSION, "45",
                MobileGluesConfig.KEY_ANGLE, "3",
                MobileGluesConfig.KEY_NO_ERROR, "2",
                MobileGluesConfig.KEY_COMPUTE_SHADER, Boolean.TRUE,
                MobileGluesConfig.KEY_TIMER_QUERY, Boolean.FALSE,
                MobileGluesConfig.KEY_DIRECT_STATE_ACCESS, Boolean.FALSE,
                MobileGluesConfig.KEY_GLSL_CACHE_MB, "128",
                MobileGluesConfig.KEY_ANGLE_DEPTH_FIX, "2",
                MobileGluesConfig.KEY_FSR1, "4",
                MobileGluesConfig.KEY_HIDE_ENV, Boolean.TRUE);
        String json = MobileGluesConfig.fromMap(chosen).toJson();
        assertTrue(json, json.contains("\"enableANGLE\": 3,"));
        assertTrue(json, json.contains("\"enableNoError\": 2,"));
        assertTrue(json, json.contains("\"enableExtComputeShader\": 1,"));
        assertTrue(json, json.contains("\"enableExtTimerQuery\": 0,"));
        assertTrue(json, json.contains("\"enableExtDirectStateAccess\": 0,"));
        assertTrue(json, json.contains("\"maxGlslCacheSize\": 128,"));
        assertTrue(json, json.contains("\"angleDepthClearFixMode\": 2,"));
        assertTrue(json, json.contains("\"customGLVersion\": 45,"));
        assertTrue(json, json.contains("\"fsr1Setting\": 4,"));
        assertTrue(json, json.contains("\"hideMGEnvLevel\": 1"));
        assertFalse(json, json.contains("true"));
        assertFalse(json, json.contains("false"));
    }

    @Test
    public void aDrawOrderIsWrittenOnlyWhenOneWasChosenAndTheFileStaysValid() {
        Map<String, Object> chosen = with(
                MobileGluesConfig.KEY_ORDER, "Native; Unroll",
                MobileGluesConfig.KEY_ORDER_ELEMENTS_BASE_VERTEX, "basevertex, indirect",
                MobileGluesConfig.KEY_ORDER_ARRAYS, "   ");
        String json = MobileGluesConfig.fromMap(chosen).toJson();
        assertTrue(json, json.contains(",\n  \"multidrawOrder\": \"native,unroll\""));
        assertTrue(json, json.contains(",\n  \"multidrawOrderElementsBaseVertex\": \"basevertex,indirect\""));
        assertFalse(json, json.contains("multidrawOrderArrays"));
        assertFalse(json, json.contains("multidrawOrderElements\""));
        assertTrue(json, json.endsWith("\"\n}\n"));
        assertValidShape(json);
        // Without any order the last entry carries no comma.
        assertValidShape(MobileGluesConfig.defaults().toJson());
    }

    /** No JSON library in a plain unit test: check what MobileGlues' parser cares about, line by line. */
    private static void assertValidShape(String json) {
        String[] lines = json.split("\n");
        assertEquals("{", lines[0]);
        assertEquals("}", lines[lines.length - 1]);
        Pattern entry = Pattern.compile("^  \"[A-Za-z0-9]+\": (-?\\d+|\"[a-z,]*\")(,?)$");
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < lines.length - 1; i++) {
            Matcher m = entry.matcher(lines[i]);
            assertTrue(lines[i], m.matches());
            boolean last = i == lines.length - 2;
            assertEquals("comma on line " + lines[i], !last, ",".equals(m.group(2)));
            assertTrue("key twice: " + lines[i], seen.add(lines[i].substring(0, lines[i].indexOf(':'))));
        }
    }

    @Test
    public void anythingMissingWrongOrOutOfRangeBecomesSomethingMobileGluesCanRead() {
        assertEquals(0, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_ANGLE, "9")).angle);
        assertEquals(0, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_ANGLE, "-1")).angle);
        assertEquals(0, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_NO_ERROR, "4")).noError);
        assertEquals(0, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_ANGLE_DEPTH_FIX, "3")).angleDepthFix);
        assertEquals(0, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_FSR1, "5")).fsr1);
        assertEquals(4, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_FSR1, " 4 ")).fsr1);
        // The cache size: out of range goes back to the default, zero is a real choice.
        assertEquals(32, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_GLSL_CACHE_MB, "5000")).glslCacheMb);
        assertEquals(32, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_GLSL_CACHE_MB, "-3")).glslCacheMb);
        assertEquals(0, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_GLSL_CACHE_MB, "0")).glslCacheMb);
        assertEquals(1024, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_GLSL_CACHE_MB, "1024")).glslCacheMb);
        // Wrong types and words.
        assertEquals(46, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_GL_VERSION, "abc")).glVersion);
        assertEquals(46, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_GL_VERSION, Boolean.TRUE)).glVersion);
        assertEquals(1, MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_ANGLE, Long.valueOf(1))).angle);
        assertTrue(MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_COMPUTE_SHADER, "TRUE")).computeShader);
        assertFalse(MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_COMPUTE_SHADER, "yes")).computeShader);
        assertTrue(MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_TIMER_QUERY, "maybe")).timerQuery);
        assertEquals("", MobileGluesConfig.fromMap(with(MobileGluesConfig.KEY_ORDER, Boolean.TRUE)).order(0));
    }

    @Test
    public void theOpenGlVersionIsMovedTheWayMobileGluesMovesIt() {
        int[][] cases = {{99, 46}, {47, 46}, {46, 46}, {40, 40}, {34, 33}, {39, 33}, {33, 33}, {32, 32}, {20, 32}, {0, 32}};
        for (int[] c : cases) {
            assertEquals("version " + c[0], c[1], MobileGluesConfig.normalizeGlVersion(c[0]));
        }
        for (int code : MobileGluesConfig.GL_VERSIONS) {
            assertEquals("a version offered in Settings must stay as chosen: " + code,
                    code, MobileGluesConfig.normalizeGlVersion(code));
        }
        assertEquals("4.6", MobileGluesConfig.glVersionText(46));
        assertEquals("3.2", MobileGluesConfig.glVersionText(32));
    }

    @Test
    public void aTypedOrderIsCleanedTheWayMobileGluesReadsIt() {
        MobileGluesConfig.Order order = MobileGluesConfig.cleanOrder("Unroll; Base_Vertex , MULTI-INDIRECT", true);
        assertEquals("unroll,basevertex,multiindirect", order.text);
        assertTrue(order.ignored.isEmpty());

        assertEquals("unroll,indirect", MobileGluesConfig.cleanOrder("unroll, unroll, indirect", false).text);
        assertEquals("unroll,indirect,compute", MobileGluesConfig.cleanOrder("unroll > indirect > compute", true).text);
        assertEquals("unroll,indirect", MobileGluesConfig.cleanOrder("unroll\nindirect\r\n", true).text);

        // "native" means something only in the global order; "auto" and unknown names are dropped.
        MobileGluesConfig.Order perCall = MobileGluesConfig.cleanOrder("native, unroll", false);
        assertEquals("unroll", perCall.text);
        assertEquals(Arrays.asList("native"), perCall.ignored);
        assertEquals("native,unroll", MobileGluesConfig.cleanOrder("native, unroll", true).text);
        MobileGluesConfig.Order junk = MobileGluesConfig.cleanOrder("auto, foo", true);
        assertEquals("", junk.text);
        assertEquals(Arrays.asList("auto", "foo"), junk.ignored);

        assertEquals("", MobileGluesConfig.cleanOrder(null, true).text);
        assertEquals("", MobileGluesConfig.cleanOrder("  ,; ", true).text);
        // Cleaning twice changes nothing, which the Settings row relies on.
        assertEquals(order.text, MobileGluesConfig.cleanOrder(order.text, true).text);
    }

    @Test
    public void everyKeyIsListedOnceAndHasADefault() {
        List<String> all = new ArrayList<>();
        all.addAll(Arrays.asList(MobileGluesConfig.LIST_KEYS));
        all.addAll(Arrays.asList(MobileGluesConfig.SWITCH_KEYS));
        all.addAll(Arrays.asList(MobileGluesConfig.ORDER_KEYS));
        assertEquals(all.size(), new HashSet<>(all).size());

        Map<String, Object> defaults = MobileGluesConfig.defaultValues();
        assertEquals(new HashSet<>(all), defaults.keySet());
        for (String key : MobileGluesConfig.LIST_KEYS) assertTrue(key, defaults.get(key) instanceof String);
        for (String key : MobileGluesConfig.SWITCH_KEYS) assertTrue(key, defaults.get(key) instanceof Boolean);
        for (String key : MobileGluesConfig.ORDER_KEYS) assertEquals(key, "", defaults.get(key));
        // What "Reset" stores produces the same file as storing nothing.
        assertEquals(MobileGluesConfig.defaults().toJson(), MobileGluesConfig.fromMap(defaults).toJson());
    }

    @Test
    public void theLogLineSaysWhatWasChosen() {
        String text = MobileGluesConfig.defaults().describe();
        assertTrue(text, text.contains("OpenGL 4.6"));
        assertTrue(text, text.contains("shader cache 32 MB"));
        assertTrue(text, text.contains("multi-draw automatic"));
        assertTrue(text, text.contains("hide env off"));
        Map<String, Object> chosen = with(
                MobileGluesConfig.KEY_GL_VERSION, "40",
                MobileGluesConfig.KEY_GLSL_CACHE_MB, "0",
                MobileGluesConfig.KEY_ORDER, "unroll",
                MobileGluesConfig.KEY_ORDER_ARRAYS, "indirect");
        String custom = MobileGluesConfig.fromMap(chosen).describe();
        assertTrue(custom, custom.contains("OpenGL 4.0"));
        assertTrue(custom, custom.contains("shader cache off"));
        assertTrue(custom, custom.contains("2 custom order(s)"));
    }
}
