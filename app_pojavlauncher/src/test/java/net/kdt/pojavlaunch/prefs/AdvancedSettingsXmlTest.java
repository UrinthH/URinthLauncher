package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the Advanced settings screen (res/xml/pref_advanced_settings.xml, res/values/urinth_advanced.xml,
 * AdvancedSettingsFragment.java) and {@link AdvancedPerformance} in step. Nothing else would notice when one
 * of them moves: a default that differs, a list that offers a value the code would not take, a row nobody reads or a
 * text that is not defined only shows up as a crash or a dead switch when the screen opens on a phone.
 */
public class AdvancedSettingsXmlTest {
    /** Rows that are not options of {@link AdvancedPerformance}: the shared Settings rows and the two helper rows. */
    private static final Set<String> NOT_IN_THE_MODEL = new HashSet<>(Arrays.asList(
            "adv_info", "adv_reset", "advPreset", "allocation", "sustainedPerformance", "bigCoreAffinity"));

    private static File res() {
        for (String path : new String[] {"src/main/res", "app_pojavlauncher/src/main/res"}) {
            File dir = new File(path);
            if (new File(dir, "xml/pref_advanced_settings.xml").isFile()) return dir;
        }
        throw new AssertionError("cannot find src/main/res from " + new File(".").getAbsolutePath());
    }

    private static File sources() {
        return new File(res(), "../java/net/kdt/pojavlaunch");
    }

    private static String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Every element of a preferences XML that has an android:key: key -> attributes (plus "tag"). */
    private static Map<String, Map<String, String>> rows(String file) throws IOException {
        String xml = text(new File(res(), "xml/" + file)).replaceAll("(?s)<!--.*?-->", "");
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
            assertFalse("key twice in " + file + ": " + key, out.containsKey(key));
            out.put(key, attributes);
        }
        return out;
    }

    private static Map<String, String> row(String key) throws IOException {
        Map<String, String> row = rows("pref_advanced_settings.xml").get(key);
        assertTrue("no row for " + key, row != null);
        return row;
    }

    private static List<AdvancedPerformance.Spec> allSpecs() {
        List<AdvancedPerformance.Spec> specs = new ArrayList<>(Arrays.asList(AdvancedPerformance.PERFORMANCE_SPECS));
        specs.addAll(Arrays.asList(AdvancedPerformance.ANTI_CRASH_SPECS));
        return specs;
    }

    private static String advancedValuesXml() throws IOException {
        return text(new File(res(), "values/urinth_advanced.xml"));
    }

    private static List<String> array(String reference) throws IOException {
        assertTrue(reference, reference != null && reference.startsWith("@array/"));
        String name = reference.substring("@array/".length());
        Matcher array = Pattern.compile("<string-array name=\"" + Pattern.quote(name) + "\">(.*?)</string-array>",
                Pattern.DOTALL).matcher(advancedValuesXml());
        assertTrue("missing array " + name, array.find());
        List<String> items = new ArrayList<>();
        Matcher item = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(array.group(1));
        while (item.find()) items.add(item.group(1).trim());
        return items;
    }

    @Test
    public void theQuickSetupRowListsEveryPresetInOrderWithTheTextTheScreenShows() throws IOException {
        List<String> values = array("@array/urinth_adv_preset_values");
        assertEquals(AdvancedPresets.PRESETS.length, values.size());
        for (int i = 0; i < values.size(); i++) assertEquals(AdvancedPresets.PRESETS[i], values.get(i));
        assertEquals(values.size(), array("@array/urinth_adv_preset_names").size());
        Map<String, String> preset = row("advPreset");
        assertTrue("the Quick setup row is a list", preset.get("tag").endsWith("ListPreference"));
        assertEquals("false", preset.get("persistent"));
        Set<String> strings = definedNames();
        for (String id : AdvancedPresets.PRESETS) {
            assertTrue("no description for " + id, strings.contains("urinth_adv_preset_" + id + "_desc"));
        }
        assertTrue(strings.contains("urinth_adv_preset_custom"));
        assertTrue(strings.contains("urinth_adv_preset_custom_desc"));
        assertTrue(strings.contains("urinth_adv_preset_now"));
        assertTrue(strings.contains("urinth_adv_preset_note"));
    }

    @Test
    public void thereAreTwentyFourPerformanceOptionsAndThreeAntiCrashOnes() {
        assertEquals(24, AdvancedPerformance.PERFORMANCE_SPECS.length);
        assertEquals(3, AdvancedPerformance.ANTI_CRASH_SPECS.length);
    }

    @Test
    public void everyOptionHasOneRowWithTheRightKindOfControl() throws IOException {
        for (AdvancedPerformance.Spec spec : allSpecs()) {
            String tag = row(spec.key).get("tag");
            if (spec.isSwitch) assertTrue(spec.key + " is " + tag, tag.endsWith("SwitchPreferenceCompat"));
            else assertTrue(spec.key + " is " + tag, tag.endsWith("ListPreference"));
        }
    }

    @Test
    public void everyRowIsAnOptionOfTheModelOrASharedSettingsRowOrAMobileGluesRow() throws IOException {
        Set<String> modelKeys = new HashSet<>(AdvancedPerformance.allKeys());
        Set<String> mobileGluesKeys = new HashSet<>();
        for (String key : rows("pref_all_settings.xml").keySet()) if (key.startsWith("mg")) mobileGluesKeys.add(key);
        assertTrue("Settings has no MobileGlues rows?", mobileGluesKeys.size() > 10);
        for (String key : rows("pref_advanced_settings.xml").keySet()) {
            assertTrue("a row nothing reads: " + key,
                    modelKeys.contains(key) || NOT_IN_THE_MODEL.contains(key) || mobileGluesKeys.contains(key));
        }
    }

    @Test
    public void everyOptionOfTheModelHasARowAndEveryKeyIsUsedOnce() throws IOException {
        Set<String> rowKeys = rows("pref_advanced_settings.xml").keySet();
        for (String key : AdvancedPerformance.allKeys()) assertTrue("no row for " + key, rowKeys.contains(key));
        for (String key : NOT_IN_THE_MODEL) assertTrue("no row for " + key, rowKeys.contains(key));
    }

    @Test
    public void theDefaultsInTheRowsAreTheOnesTheModelUses() throws IOException {
        for (AdvancedPerformance.Spec spec : allSpecs()) {
            assertEquals(spec.key, spec.defaultValue, row(spec.key).get("defaultValue"));
        }
    }

    @Test
    public void everyListOffersExactlyTheValuesTheModelAcceptsInTheSameOrder() throws IOException {
        for (AdvancedPerformance.Spec spec : allSpecs()) {
            if (spec.isSwitch) continue;
            List<String> values = array(row(spec.key).get("entryValues"));
            assertEquals(spec.key, Arrays.asList(spec.values), values);
            assertEquals(spec.key + " names", values.size(), array(row(spec.key).get("entries")).size());
            // The default is the first one, and its name says so ("Auto ..." or "Highest ...").
            assertEquals(spec.key + " default first", spec.defaultValue, values.get(0));
        }
    }

    @Test
    public void everyChoiceTheScreenOffersIsReadBackByTheModel() throws IOException {
        for (AdvancedPerformance.Spec spec : allSpecs()) {
            if (spec.isSwitch) continue;
            for (String value : array(row(spec.key).get("entryValues"))) {
                Map<String, Object> stored = new LinkedHashMap<>();
                stored.put(spec.key, value);
                assertEquals(spec.key + "=" + value, value, AdvancedPerformance.fromMap(stored).choice(spec.key));
            }
        }
    }

    @Test
    public void everyListRowHasItsDescriptionAndTheFragmentListsItToo() throws IOException {
        String fragment = text(new File(sources(), "prefs/screens/AdvancedSettingsFragment.java"));
        Set<String> strings = definedNames();
        for (AdvancedPerformance.Spec spec : AdvancedPerformance.PERFORMANCE_SPECS) {
            if (spec.isSwitch) continue;
            // The list rows have no summary of their own: the fragment writes "chosen value + description".
            assertTrue(spec.key + " is not described by the fragment",
                    fragment.contains("AdvancedPerformance." + constantNameFor(spec.key)));
            assertFalse(spec.key + " row has a fixed summary", row(spec.key).containsKey("summary"));
        }
        for (AdvancedPerformance.Spec spec : allSpecs()) {
            if (!spec.isSwitch) continue;
            assertTrue(spec.key + " needs a summary", row(spec.key).containsKey("summary"));
            assertTrue(spec.key + " summary", strings.contains(row(spec.key).get("summary").substring("@string/".length())));
        }
    }

    /** KEY_INITIAL_HEAP for "advInitialHeap": found by looking the constant up in the model's source. */
    private static String constantNameFor(String key) throws IOException {
        Matcher m = Pattern.compile("String (KEY_[A-Z0-9_]+) = \"" + Pattern.quote(key) + "\"")
                .matcher(text(new File(sources(), "prefs/AdvancedPerformance.java")));
        assertTrue("no constant for " + key, m.find());
        return m.group(1);
    }

    @Test
    public void theSharedSettingsRowsAreTheSamePreferencesAsInSettings() throws IOException {
        Map<String, Map<String, String>> settings = rows("pref_all_settings.xml");
        for (String key : new String[] {"allocation", "sustainedPerformance", "bigCoreAffinity"}) {
            assertTrue("Settings has no " + key, settings.containsKey(key));
            assertEquals(key + " tag", settings.get(key).get("tag"), row(key).get("tag"));
        }
        // The default of the switches is decided in code (device dependent) in both places.
        assertEquals(settings.get("bigCoreAffinity").get("defaultValue"), row("bigCoreAffinity").get("defaultValue"));
    }

    @Test
    public void theMobileGluesRowsAreACopyOfTheOnesInSettings() throws IOException {
        Map<String, Map<String, String>> settings = rows("pref_all_settings.xml");
        Map<String, Map<String, String>> advanced = rows("pref_advanced_settings.xml");
        int count = 0;
        for (Map.Entry<String, Map<String, String>> row : settings.entrySet()) {
            if (!row.getKey().startsWith("mg")) continue;
            count++;
            assertTrue("missing from the Advanced screen: " + row.getKey(), advanced.containsKey(row.getKey()));
            assertEquals(row.getKey(), row.getValue(), advanced.get(row.getKey()));
        }
        assertTrue(count > 10);
        for (String key : advanced.keySet()) {
            if (key.startsWith("mg")) assertTrue("not in Settings: " + key, settings.containsKey(key));
        }
    }

    @Test
    public void everyReferenceInTheAdvancedRowsExists() throws IOException {
        Set<String> strings = definedNames();
        Set<String> arrays = definedArrays();
        Set<String> integers = new HashSet<>();
        Set<String> drawables = fileNames("drawable");
        Set<String> layouts = fileNames("layout");
        File[] values = new File(res(), "values").listFiles();
        assertTrue(values != null);
        Pattern integer = Pattern.compile("<integer\\s+name=\"([^\"]+)\"");
        for (File file : values) {
            if (!file.getName().endsWith(".xml")) continue;
            Matcher m = integer.matcher(text(file));
            while (m.find()) integers.add(m.group(1));
        }
        for (Map.Entry<String, Map<String, String>> row : rows("pref_advanced_settings.xml").entrySet()) {
            for (Map.Entry<String, String> attribute : row.getValue().entrySet()) {
                String value = attribute.getValue();
                String where = row.getKey() + " " + attribute.getKey() + " -> " + value;
                if (value.startsWith("@string/")) assertTrue(where, strings.contains(value.substring(8)));
                else if (value.startsWith("@array/")) assertTrue(where, arrays.contains(value.substring(7)));
                else if (value.startsWith("@drawable/")) assertTrue(where, drawables.contains(value.substring(10)));
                else if (value.startsWith("@layout/")) assertTrue(where, layouts.contains(value.substring(8)));
                else if (value.startsWith("@integer/")) assertTrue(where, integers.contains(value.substring(9)));
            }
        }
        // The categories have titles and icons too (rows without a key).
        String xml = text(new File(res(), "xml/pref_advanced_settings.xml")).replaceAll("(?s)<!--.*?-->", "");
        Matcher reference = Pattern.compile("@(string|array|drawable|layout|integer)/([A-Za-z0-9_]+)").matcher(xml);
        while (reference.find()) {
            String name = reference.group(2);
            Set<String> known;
            switch (reference.group(1)) {
                case "string": known = strings; break;
                case "array": known = arrays; break;
                case "drawable": known = drawables; break;
                case "layout": known = layouts; break;
                default: known = integers; break;
            }
            assertTrue(reference.group(0), known.contains(name));
        }
    }

    @Test
    public void everyTextTheScreenCodeAsksForExists() throws IOException {
        Set<String> strings = definedNames();
        int count = 0;
        for (String file : new String[] {"prefs/screens/AdvancedSettingsFragment.java", "utils/AntiCrash.java"}) {
            Matcher used = Pattern.compile("(?<!android\\.)R\\.string\\.([a-z0-9_]+)").matcher(text(new File(sources(), file)));
            while (used.find()) {
                count++;
                assertTrue(file + " uses a text that is not defined: " + used.group(1), strings.contains(used.group(1)));
            }
        }
        assertTrue("the screen should use its own texts", count > 10);
    }

    @Test
    public void theSidebarEntryIsCalledAdvancedSettingsAndTheServersEntryIsGone() throws IOException {
        String strings = text(new File(res(), "values/strings.xml"));
        Matcher name = Pattern.compile("<string name=\"urinth_nav_advanced\">([^<]*)</string>").matcher(strings);
        assertTrue("urinth_nav_advanced is not defined", name.find());
        assertEquals("Advanced settings", name.group(1));
        assertFalse(strings.contains("urinth_nav_skins"));
        String layout = text(new File(res(), "layout/view_urinth_home.xml"));
        assertTrue(layout.contains("@+id/urinth_nav_advanced"));
        assertTrue(layout.contains("@string/urinth_nav_advanced"));
        assertFalse(layout.contains("urinth_nav_skins"));
        String controller = text(new File(sources(), "fragments/UrinthHomeController.java"));
        assertTrue(controller.contains("R.id.urinth_nav_advanced"));
        assertFalse(controller.contains("urinth_nav_skins"));
    }

    @Test
    public void noTextOrListIsDefinedTwice() throws IOException {
        File[] files = new File(res(), "values").listFiles();
        assertTrue(files != null);
        Map<String, String> where = new LinkedHashMap<>();
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

    // ---- helpers

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

    private static Set<String> definedArrays() throws IOException {
        Set<String> names = new HashSet<>();
        File[] files = new File(res(), "values").listFiles();
        assertTrue(files != null);
        Pattern name = Pattern.compile("<(?:string-array|integer-array|array)\\s+name=\"([^\"]+)\"");
        for (File file : files) {
            if (!file.getName().endsWith(".xml")) continue;
            Matcher m = name.matcher(text(file));
            while (m.find()) names.add(m.group(1));
        }
        return names;
    }

    /** The names (without extension) of the files in the resource folders that start with this prefix. */
    private static Set<String> fileNames(String folderPrefix) {
        Set<String> names = new HashSet<>();
        File[] dirs = res().listFiles();
        assertTrue(dirs != null);
        for (File dir : dirs) {
            if (!dir.getName().startsWith(folderPrefix)) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File file : files) {
                String name = file.getName();
                int dot = name.indexOf('.');
                names.add(dot < 0 ? name : name.substring(0, dot));
            }
        }
        return names;
    }
}
