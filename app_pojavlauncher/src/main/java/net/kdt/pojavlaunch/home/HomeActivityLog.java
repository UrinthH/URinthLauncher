package net.kdt.pojavlaunch.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * On-device log of what happened in the launcher: games played, installations created and
 * launcher updates. Shown in the home screen's Recent Activity card.
 */
public final class HomeActivityLog {
    public static final String TYPE_PLAY = "play";
    public static final String TYPE_INSTALL = "install";
    public static final String TYPE_UPDATE = "update";

    /** Newest entries are kept; older ones are dropped. */
    public static final int MAX_ENTRIES = 20;

    private static final String PREFS = "urinth_home";
    private static final String KEY_LOG = "activity_log";
    private static final String KEY_LAST_VERSION = "last_launcher_version";
    private static final String KEY_LAST_PLAYED = "last_played";

    private HomeActivityLog() {
    }

    /** One logged event. */
    public static final class Entry {
        public final long timeMillis;
        public final String type;
        public final String text;

        public Entry(long timeMillis, String type, String text) {
            this.timeMillis = timeMillis;
            this.type = type;
            this.text = text == null ? "" : text;
        }
    }

    /** Coarse age of an entry, for "x hours ago" labels. */
    public enum AgeUnit { NOW, MINUTES, HOURS, DAYS, OLDER }

    /** An age expressed as a unit and a count (the count is 0 for NOW and OLDER). */
    public static final class Age {
        public final AgeUnit unit;
        public final int count;

        Age(AgeUnit unit, int count) {
            this.unit = unit;
            this.count = count;
        }
    }

    /** Returns the newest-first list with {@code entry} added and capped at {@code max}. */
    public static List<Entry> append(List<Entry> current, Entry entry, int max) {
        List<Entry> next = new ArrayList<>(current.size() + 1);
        next.add(entry);
        next.addAll(current);
        if (next.size() > max) return new ArrayList<>(next.subList(0, max));
        return next;
    }

    /** Text form, one entry per line: time, type and text separated by tabs. */
    public static String serialize(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (out.length() > 0) out.append('\n');
            out.append(entry.timeMillis).append('\t')
                    .append(clean(entry.type)).append('\t')
                    .append(clean(entry.text));
        }
        return out.toString();
    }

    /** Parses the output of {@link #serialize(List)}. Malformed lines are skipped. */
    public static List<Entry> parse(String raw) {
        List<Entry> entries = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return entries;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length != 3) continue;
            try {
                entries.add(new Entry(Long.parseLong(parts[0]), parts[1], parts[2]));
            } catch (NumberFormatException ignored) {
                // Skip a damaged line rather than losing the whole log.
            }
        }
        return entries;
    }

    /** How long ago {@code thenMillis} was, as a coarse unit. Future times count as NOW. */
    public static Age age(long nowMillis, long thenMillis) {
        long diff = nowMillis - thenMillis;
        if (diff < 60_000L) return new Age(AgeUnit.NOW, 0);
        if (diff < 3_600_000L) return new Age(AgeUnit.MINUTES, (int) (diff / 60_000L));
        if (diff < 86_400_000L) return new Age(AgeUnit.HOURS, (int) (diff / 3_600_000L));
        if (diff < 7L * 86_400_000L) return new Age(AgeUnit.DAYS, (int) (diff / 86_400_000L));
        return new Age(AgeUnit.OLDER, 0);
    }

    /** Stores an event on the device. Failures are ignored: the log must never break the launcher. */
    public static void record(Context context, String type, String text) {
        try {
            SharedPreferences prefs = prefs(context);
            List<Entry> current = parse(prefs.getString(KEY_LOG, ""));
            List<Entry> next = append(current, new Entry(System.currentTimeMillis(), type, text), MAX_ENTRIES);
            prefs.edit().putString(KEY_LOG, serialize(next)).apply();
        } catch (RuntimeException ignored) {
            // Storage can be unavailable; the activity card just stays shorter.
        }
    }

    /** Newest-first entries, at most {@code limit}. */
    public static List<Entry> recent(Context context, int limit) {
        try {
            List<Entry> all = parse(prefs(context).getString(KEY_LOG, ""));
            return Collections.unmodifiableList(all.subList(0, Math.min(limit, all.size())));
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    /** Logs one "updated" entry when the installed launcher version differs from the last one seen. */
    public static void recordLauncherUpdateIfNeeded(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            String current = info.versionName == null ? "" : info.versionName;
            SharedPreferences prefs = prefs(context);
            String last = prefs.getString(KEY_LAST_VERSION, null);
            if (last == null) {
                prefs.edit().putString(KEY_LAST_VERSION, current).apply();
            } else if (!last.equals(current)) {
                prefs.edit().putString(KEY_LAST_VERSION, current).apply();
                record(context, TYPE_UPDATE, "Updated launcher to " + current);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Without the package info there is simply no update entry.
        }
    }

    /** Name of the instance chosen in the "Last Played" control, or null if none was chosen. */
    public static String lastPlayedName(Context context) {
        try {
            return prefs(context).getString(KEY_LAST_PLAYED, null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static void setLastPlayedName(Context context, String name) {
        try {
            prefs(context).edit().putString(KEY_LAST_PLAYED, name).apply();
        } catch (RuntimeException ignored) {
            // The choice simply isn't remembered.
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Tabs and line breaks would corrupt the format, so they become spaces. */
    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
