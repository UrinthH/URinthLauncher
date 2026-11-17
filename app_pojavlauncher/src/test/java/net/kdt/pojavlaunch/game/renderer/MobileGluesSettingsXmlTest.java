package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the MobileGlues rows of the Settings screen (res/xml/pref_all_settings.xml, res/values/urinth_mobileglues.xml)
 * and {@link MobileGluesConfig} in step, because nothing else would notice when one of them moves: a default that
 * differs, a list that offers a value MobileGlues would not take, or a text that is not defined only shows up as a
 * crash when the screen opens on a phone.
 */
public class MobileGluesSettingsXmlTest {
    /** Preference key -> the key MobileGlues reads in config.json, for the lists. */
    private static final String[][] LIST_JSON_KEYS = {
            {MobileGluesConfig.KEY_GL_VERSION, "customGLVersion"},
            {MobileGluesConfig.KEY_ANGLE, "enableANGLE"},
            {MobileGluesConfig.KEY_NO_ERROR, "enableNoError"},
            {MobileGluesConfig.KEY_GLSL_CACHE_MB, "maxGlslCacheSize"},
            {MobileGluesConfig.KEY_ANGLE_DEPTH_FIX, "angleDepthClearFixMode"},
            {MobileGluesConfig.KEY_FSR1, "fsr1Setting"}};

    private static File res() {
        for (String path : new String[] {"src/main/res", "app_pojavlauncher/src/main/res"}) {
            File dir = new File(path);
            if (new File(dir, "xml/pref_all_settings.xml").isFile()) return dir;
        }
        throw new AssertionError("cannot find src/main/res from " + new File(".").getAbsolutePath());
    }

    private static String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Every element of the preferences XML that has an android:key: key -> attributes (plus "tag"). */
    private static Map<String, Map<String, String>> rows() throws IOException {
        String xml = text(new File(res(), "xml/pref_all_settings.xml"));
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Matcher element = Pattern.compile("<([A-Za-z][A-Za-z0-9_.]*)\\s([^<>]*?)/?>").matcher(xml);
        Pattern attribute = Pattern.compile("(?:android|app|app2):([A-Za-z]+)=\"([^\"]*)\"");
        while (element.find()) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tag", element.group(1));
            Matcher a = attribute.matcher(element.group(2));
            while (a.find()) attributes.put(a.group(1), a.group(2));
            String key = attributes.get("key");
            if (key == null) continue;
            assertFalse("key twice in the preferences XML: " + key, out.containsKey(key));
            out.put(key, attributes);
        }
        return out;
    }

    private static Map<String, String> row(String key) throws IOException {
        Map<String, String> row = rows().get(key);
        assertTrue("no row for " + key, row != null);
        return row;
    }

    private static String mobileGluesValuesXml() throws IOException {
        return text(new File(res(), "values/urinth_mobileglues.xml"));
    }

    private static List<String> array(String reference) throws IOException {
        assertTrue(reference, reference != null && reference.startsWith("@array/"));
        String name = reference.substring("@array/".length());
        Matcher array = Pattern.compile("<string-array name=\"" + Pattern.quote(name) + "\">(.*?)</string-array>",
                Pattern.DOTALL).matcher(mobileGluesValuesXml());
        assertTrue("missing array " + name, array.find());
        List<String> items = new ArrayList<>();
        Matcher item = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(array.group(1));
        while (item.find()) items.add(item.group(1).trim());
        return items;
    }

    private static List<String> ints(int... values) {
        List<String> out = new ArrayList<>();
        for (int value : values) out.add(String.valueOf(value));
        return out;
    }

    private static List<String> expectedValues(String key) {
        switch (key) {
            case MobileGluesConfig.KEY_GL_VERSION: return ints(MobileGluesConfig.GL_VERSIONS);
            case MobileGluesConfig.KEY_GLSL_CACHE_MB: return ints(MobileGluesConfig.GLSL_CACHE_CHOICES_MB);
            case MobileGluesConfig.KEY_ANGLE:
            case MobileGluesConfig.KEY_NO_ERROR: return ints(0, 1, 2, 3);
            case MobileGluesConfig.KEY_ANGLE_DEPTH_FIX: return ints(0, 1, 2);
            case MobileGluesConfig.KEY_FSR1: return ints(0, 1, 2, 3, 4);
            default: throw new AssertionError(key);
        }
    }

    @Test
    public void everySettingHasOneRowWithTheRightKindOfControl() throws IOException {
        for (String key : MobileGluesConfig.LIST_KEYS) {
            assertTrue(key, row(key).get("tag").endsWith("ListPreference"));
        }
        for (String key : MobileGluesConfig.SWITCH_KEYS) {
            assertTrue(key, row(key).get("tag").endsWith("SwitchPreferenceCompat"));
        }
        for (String key : MobileGluesConfig.ORDER_KEYS) {
            assertTrue(key, row(key).get("tag").endsWith("EditTextPreference"));
        }
    }

    @Test
    public void theDefaultsInTheRowsAreTheOnesTheCodeUses() throws IOException {
        Map<String, Object> defaults = MobileGluesConfig.defaultValues();
        for (String key : MobileGluesConfig.LIST_KEYS) {
            assertEquals(key, String.valueOf(defaults.get(key)), row(key).get("defaultValue"));
        }
        for (String key : MobileGluesConfig.SWITCH_KEYS) {
            assertEquals(key, String.valueOf(defaults.get(key)), row(key).get("defaultValue"));
        }
        for (String key : MobileGluesConfig.ORDER_KEYS) {
            String declared = row(key).get("defaultValue");
            assertTrue(key, declared == null || declared.isEmpty());
        }
    }

    @Test
    public void everyListOffersExactlyTheValuesMobileGluesAccepts() throws IOException {
        for (String[] pair : LIST_JSON_KEYS) {
            String key = pair[0];
            List<String> values = array(row(key).get("entryValues"));
            assertEquals(key, expectedValues(key), values);
            assertEquals(key + " names", values.size(), array(row(key).get("entries")).size());
            // And each offered value is written to config.json unchanged.
            for (String value : values) {
                Map<String, Object> chosen = new HashMap<>();
                chosen.put(key, value);
                String json = MobileGluesConfig.fromMap(chosen).toJson();
                assertTrue(key + "=" + value, json.contains("\"" + pair[1] + "\": " + value));
            }
        }
    }

    @Test
    public void theDefaultIsMarkedInEveryListAndOnlyThere() throws IOException {
        Map<String, Object> defaults = MobileGluesConfig.defaultValues();
        for (String[] pair : LIST_JSON_KEYS) {
            String key = pair[0];
            List<String> values = array(row(key).get("entryValues"));
            List<String> names = array(row(key).get("entries"));
            int defaultIndex = values.indexOf(String.valueOf(defaults.get(key)));
            assertTrue(key + " does not offer its default", defaultIndex >= 0);
            for (int i = 0; i < names.size(); i++) {
                assertEquals(key + ": " + names.get(i), i == defaultIndex, names.get(i).contains("(default)"));
            }
        }
    }

    @Test
    public void everyTextAndPictureTheRowsAndTheScreenNeedExists() throws IOException {
        Set<String> strings = definedNames();
        Set<String> drawables = new HashSet<>();
        File[] dirs = res().listFiles();
        assertTrue(dirs != null);
        for (File dir : dirs) {
            if (!dir.getName().startsWith("drawable")) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File file : files) {
                String name = file.getName();
                int dot = name.indexOf('.');
                drawables.add(dot < 0 ? name : name.substring(0, dot));
            }
        }
        for (Map.Entry<String, Map<String, String>> row : rows().entrySet()) {
            for (Map.Entry<String, String> attribute : row.getValue().entrySet()) {
                String value = attribute.getValue();
                if (value.startsWith("@string/")) {
                    assertTrue(row.getKey() + " " + attribute.getKey() + " -> " + value,
                            strings.contains(value.substring("@string/".length())));
                } else if (value.startsWith("@drawable/")) {
                    assertTrue(row.getKey() + " " + attribute.getKey() + " -> " + value,
                            drawables.contains(value.substring("@drawable/".length())));
                }
            }
        }
        // The texts the screen's code asks for by name.
        File screen = new File(res(), "../java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceFragment.java");
        Matcher used = Pattern.compile("R\\.string\\.(urinth_(?:mg|set_perf_mg|set_cat_mobileglues)[a-z0-9_]*)")
                .matcher(text(screen));
        int count = 0;
        while (used.find()) {
            count++;
            assertTrue("used by the screen but not defined: " + used.group(1), strings.contains(used.group(1)));
        }
        assertTrue("the screen should use MobileGlues texts", count > 10);
    }

    @Test
    public void noTextOrListIsDefinedTwiceInTheDefaultValues() throws IOException {
        File[] files = new File(res(), "values").listFiles();
        assertTrue(files != null);
        Map<String, String> where = new HashMap<>();
        Pattern name = Pattern.compile("<(?:string|string-array)\\s+name=\"([^\"]+)\"");
        for (File file : files) {
            if (!file.getName().endsWith(".xml")) continue;
            Matcher m = name.matcher(text(file));
            while (m.find()) {
                String previous = where.put(m.group(1), file.getName());
                assertTrue(m.group(1) + " is defined in " + previous + " and in " + file.getName(), previous == null);
            }
        }
    }

    private static Set<String> definedNames() throws IOException {
        Set<String> names = new HashSet<>();
        File[] files = new File(res(), "values").listFiles();
        assertTrue(files != null);
        Pattern name = Pattern.compile("<string\\s+name=\"([^\"]+)\"");
        for (File file : files) {
            if (!file.getName().endsWith(".xml")) continue;
            Matcher m = name.matcher(text(file));
            while (m.find()) names.add(m.group(1));
        }
        return names;
    }
}
