package net.kdt.pojavlaunch.prefs;

import net.kdt.pojavlaunch.game.renderer.MobileGluesConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The settings a game is started with, written down as plain text.
 *
 * The game keeps running in the background after the player goes back to the launcher, and pressing Play
 * again brings that same game back, with the Java flags, memory and renderer it was started with. Changing
 * a setting in between does nothing to it. The game process writes this text when it starts the game, and the
 * launcher compares it with the settings as they are now when Play is pressed, to find out whether the
 * running game is out of date and the player should be asked to restart it.
 *
 * Only settings that are read when the game starts are in it: the memory, the Java arguments, the renderer,
 * Performance Mode, the Advanced settings, the MobileGlues options and a few graphics options. The Advanced
 * and MobileGlues options are written the way the game receives them (a missing or damaged value counts as
 * its default), so a value that was never touched and one that was set to its default are the same thing.
 * Pure Java, no Android classes.
 */
public final class LaunchFingerprint {
    /** What a changed setting belongs to; the restart question names these, not the individual keys. */
    public enum Group {
        MEMORY, JAVA_ARGUMENTS, RENDERER, PERFORMANCE_MODE, ADVANCED, MOBILEGLUES, OTHER
    }

    /** The stand-in key for all the MobileGlues options: the settings file the game receives for them. */
    public static final String KEY_MOBILEGLUES = "mobileGluesConfig";

    /** What a setting that depends on the phone and was never stored is written as. */
    private static final String UNSET = "-";

    private static final class Item {
        final String key;
        /** The value when nothing is stored, as text; null when it depends on the phone. */
        final String defaultText;
        final Group group;

        Item(String key, String defaultText, Group group) {
            this.key = key;
            this.defaultText = defaultText;
            this.group = group;
        }
    }

    /** The settings outside the Advanced and MobileGlues tables that decide how the game starts. */
    private static final Item[] OTHER_ITEMS = {
            new Item("allocation", null, Group.MEMORY),
            new Item("javaArgs", "", Group.JAVA_ARGUMENTS),
            new Item("renderer", "opengles2", Group.RENDERER),
            new Item("performanceMode", "true", Group.PERFORMANCE_MODE),
            new Item("perfMobileGlues", "true", Group.PERFORMANCE_MODE),
            new Item("sustainedPerformance", null, Group.ADVANCED),
            new Item("bigCoreAffinity", "false", Group.ADVANCED),
            new Item("alternate_surface", null, Group.OTHER),
            new Item("force_vsync", null, Group.OTHER),
            new Item("use_angle", "false", Group.OTHER),
            new Item("resolutionRatio", null, Group.OTHER),
            new Item("java_sandbox", "true", Group.OTHER),
            new Item("zinkPreferSystemDriver", "false", Group.OTHER),
            new Item("freedrenoSysmem", "false", Group.OTHER),
            new Item("ubwcWorkaround", "false", Group.OTHER),
            new Item("alsoftForceOpenSL", "false", Group.OTHER),
    };

    private final Map<String, String> values;

    private LaunchFingerprint(Map<String, String> values) {
        this.values = values;
    }

    /** The keys that are compared, for tests and for the restart question. */
    public static List<String> trackedKeys() {
        List<String> keys = new ArrayList<>();
        for (Item item : OTHER_ITEMS) keys.add(item.key);
        keys.addAll(AdvancedPerformance.allKeys());
        keys.add(KEY_MOBILEGLUES);
        return keys;
    }

    /** The fingerprint of the settings as stored in the preferences (the map the preferences hand out). */
    public static LaunchFingerprint of(Map<String, ?> prefs) {
        Map<String, ?> source = prefs == null ? Collections.<String, Object>emptyMap() : prefs;
        Map<String, String> values = new TreeMap<>();
        for (Item item : OTHER_ITEMS) {
            values.put(item.key, text(source.get(item.key), item.defaultText));
        }
        AdvancedPerformance advanced = AdvancedPerformance.fromMap(source);
        for (String key : AdvancedPerformance.allKeys()) {
            AdvancedPerformance.Spec spec = AdvancedPerformance.specFor(key);
            values.put(key, spec.isSwitch ? String.valueOf(advanced.on(key)) : advanced.choice(key));
        }
        values.put(KEY_MOBILEGLUES, MobileGluesConfig.fromMap(source).toJson());
        return new LaunchFingerprint(values);
    }

    private static String text(Object raw, String defaultText) {
        if (raw == null) return defaultText != null ? defaultText : UNSET;
        return String.valueOf(raw);
    }

    /** Every key whose value is not the same in both, in alphabetical order; empty when they match. */
    public List<String> differences(LaunchFingerprint other) {
        Set<String> keys = new TreeSet<>(values.keySet());
        if (other != null) keys.addAll(other.values.keySet());
        List<String> different = new ArrayList<>();
        for (String key : keys) {
            String mine = values.get(key);
            String theirs = other == null ? null : other.values.get(key);
            if (mine == null ? theirs != null : !mine.equals(theirs)) different.add(key);
        }
        return different;
    }

    /** What a key belongs to; a key this class does not know belongs to {@link Group#OTHER}. */
    public static Group groupOf(String key) {
        if (key == null) return Group.OTHER;
        if (KEY_MOBILEGLUES.equals(key)) return Group.MOBILEGLUES;
        if (AdvancedPerformance.specFor(key) != null) return Group.ADVANCED;
        for (Item item : OTHER_ITEMS) if (item.key.equals(key)) return item.group;
        return Group.OTHER;
    }

    /** The groups the keys belong to, each once, in the order of {@link Group}. */
    public static List<Group> groups(Collection<String> keys) {
        EnumSet<Group> found = EnumSet.noneOf(Group.class);
        if (keys != null) for (String key : keys) found.add(groupOf(key));
        return new ArrayList<>(found);
    }

    /** The text that is written to the file: one "key=value" line per setting, in alphabetical order. */
    public String encode() {
        StringBuilder out = new StringBuilder(1024);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.append(entry.getKey()).append('=').append(escape(entry.getValue())).append('\n');
        }
        return out.toString();
    }

    /** Reads {@link #encode()} back; null when the text holds no setting at all. */
    public static LaunchFingerprint decode(String text) {
        if (text == null) return null;
        Map<String, String> values = new TreeMap<>();
        for (String line : text.split("\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            values.put(line.substring(0, separator), unescape(line.substring(separator + 1)));
        }
        return values.isEmpty() ? null : new LaunchFingerprint(values);
    }

    /** Keeps a value on one line: the Java arguments and the MobileGlues file are several lines long. */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else out.append(c);
        }
        return out.toString();
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') out.append('\n');
                else if (next == 'r') out.append('\r');
                else out.append(next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
