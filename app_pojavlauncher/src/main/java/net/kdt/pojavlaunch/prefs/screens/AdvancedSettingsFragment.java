package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.AdvancedPerformance;
import net.kdt.pojavlaunch.prefs.AdvancedPresetStore;
import net.kdt.pojavlaunch.prefs.AdvancedPresets;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.AntiCrash;

import java.util.Map;

/**
 * The "Advanced settings" screen, opened from the sidebar while Performance Mode is on: the RAM slider,
 * 24 performance settings, the Anti Crash switches and the MobileGlues options.
 *
 * The rows are in res/xml/pref_advanced_settings.xml with the keys of {@link AdvancedPerformance}; nothing is
 * handled here that the game does not read. The RAM slider, the sustained performance and big-core switches
 * and the MobileGlues rows are the same preferences as on the Settings screen, so changing one in either place
 * changes both.
 */
public class AdvancedSettingsFragment extends LauncherPreferenceFragment {
    public static final String TAG = "ADVANCED_SETTINGS_FRAGMENT";
    /** The Quick setup row (res/xml/pref_advanced_settings.xml). It is not stored by the row itself. */
    private static final String KEY_PRESET = "advPreset";

    /** The performance and Anti Crash lists, with the text that follows the chosen value in the row. */
    private static final Object[][] LISTS = {
            {AdvancedPerformance.KEY_INITIAL_HEAP, R.string.urinth_adv_initial_heap_desc},
            {AdvancedPerformance.KEY_METASPACE, R.string.urinth_adv_metaspace_desc},
            {AdvancedPerformance.KEY_CODE_CACHE, R.string.urinth_adv_code_cache_desc},
            {AdvancedPerformance.KEY_GC, R.string.urinth_adv_gc_desc},
            {AdvancedPerformance.KEY_PAUSE_GOAL, R.string.urinth_adv_pause_goal_desc},
            {AdvancedPerformance.KEY_YOUNG_GEN, R.string.urinth_adv_young_gen_desc},
            {AdvancedPerformance.KEY_REGION_SIZE, R.string.urinth_adv_region_size_desc},
            {AdvancedPerformance.KEY_G1_RESERVE, R.string.urinth_adv_g1_reserve_desc},
            {AdvancedPerformance.KEY_GC_THREADS, R.string.urinth_adv_gc_threads_desc},
            {AdvancedPerformance.KEY_JIT, R.string.urinth_adv_jit_desc},
            {AdvancedPerformance.KEY_JIT_THREADS, R.string.urinth_adv_jit_threads_desc},
            {AdvancedPerformance.KEY_CORES, R.string.urinth_adv_cores_desc},
            {AdvancedPerformance.KEY_REFRESH_RATE, R.string.urinth_adv_refresh_rate_desc},
    };

    /**
     * True while a quick setup or "Reset all" is being written. Those write many options at once (in one edit), and the
     * change callbacks wait for the end: the preferences are loaded and the Quick setup row refreshed once, after it.
     */
    private boolean mBulkWrite;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // A quick setup that was picked gets its options back before the rows read them, if something rewrote them.
        if (AdvancedPresetStore.heal(LauncherPreferences.DEFAULT_PREF)) {
            LauncherPreferences.loadPreferences(requireContext());
        }
        addPreferencesFromResource(R.xml.pref_advanced_settings);
        Context context = requireContext();

        setupMemory(context);
        for (Object[] list : LISTS) describeList((String) list[0], (Integer) list[1]);
        setupPreset();

        SwitchPreferenceCompat sustained = requirePreference("sustainedPerformance", SwitchPreferenceCompat.class);
        sustained.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustained.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
        // Game State needs Android 13; on older Android the switch would do nothing, so it is not offered.
        requirePreference(AdvancedPerformance.KEY_GAME_STATE).setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);

        refreshAntiCrash(context);
        setupMobileGlues();
        requirePreference("adv_reset").setOnPreferenceClickListener(p -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.urinth_adv_reset_title)
                    .setMessage(R.string.urinth_adv_reset_confirm)
                    .setPositiveButton(android.R.string.ok, (d, w) -> resetAdvanced())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    /**
     * The Quick setup row ({@link AdvancedPresets}). Picking one writes the 24 rows below in one step and remembers the
     * choice ({@link AdvancedPresets#KEY_CHOSEN}). The row shows that choice until a single row below is changed by hand,
     * and from then on "Your own mix" (or the preset those rows happen to add up to).
     */
    private void setupPreset() {
        ListPreference preset = requirePreference(KEY_PRESET, ListPreference.class);
        preset.setOnPreferenceChangeListener((p, newValue) -> {
            applyPreset((String) newValue);
            return false; // the row is refreshed from what was stored, not from the tapped value
        });
        refreshPreset();
    }

    /**
     * Writes a preset: the 24 performance options and the choice itself, in one edit that is on disk when it returns.
     * Writing them row by row (24 writes, each one telling the screen and the launcher to reload) is what this replaced.
     */
    private void applyPreset(String id) {
        if (!AdvancedPresets.isPreset(id)) return;
        mBulkWrite = true;
        try {
            if (!AdvancedPresetStore.put(LauncherPreferences.DEFAULT_PREF.edit(), id).commit()) {
                Log.e(TAG, "The quick setup " + id + " could not be written to disk");
            }
            showRows(AdvancedPresets.valuesFor(id));
        } finally {
            mBulkWrite = false;
        }
        LauncherPreferences.loadPreferences(requireContext());
        refreshPreset();
    }

    /**
     * Makes the rows show these values. They are stored already (the callers write them in one edit first), so this
     * changes what is on the screen and writes nothing new.
     */
    private void showRows(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Preference preference = findPreference(entry.getKey());
            Object value = entry.getValue();
            if (preference instanceof ListPreference) {
                ((ListPreference) preference).setValue((String) value);
            } else if (preference instanceof SwitchPreferenceCompat) {
                ((SwitchPreferenceCompat) preference).setChecked((Boolean) value);
            }
        }
    }

    /** Shows which quick setup the player picked, or what the stored rows add up to, with what it does. */
    private void refreshPreset() {
        ListPreference preset = findPreference(KEY_PRESET);
        if (preset == null) return;
        String id = AdvancedPresets.shown(LauncherPreferences.DEFAULT_PREF.getAll()).id;
        boolean known = AdvancedPresets.isPreset(id);
        preset.setValue(known ? id : null);
        CharSequence name = known ? preset.getEntry() : getString(R.string.urinth_adv_preset_custom);
        int description;
        switch (id) {
            case AdvancedPresets.BALANCED:
                description = R.string.urinth_adv_preset_balanced_desc;
                break;
            case AdvancedPresets.MAX_FPS:
                description = R.string.urinth_adv_preset_maxfps_desc;
                break;
            case AdvancedPresets.SMOOTHEST:
                description = R.string.urinth_adv_preset_smoothest_desc;
                break;
            case AdvancedPresets.COOL:
                description = R.string.urinth_adv_preset_cool_desc;
                break;
            default:
                description = R.string.urinth_adv_preset_custom_desc;
                break;
        }
        preset.setSummary(getString(R.string.urinth_adv_preset_now, name) + "\n" + getString(description)
                + "\n\n" + getString(R.string.urinth_adv_preset_note));
    }

    /** The RAM slider is the Settings one; this screen says how far it goes on this phone and why. */
    private void setupMemory(Context context) {
        int maxRam = maxAllocationMb();
        CustomSeekBarPreference memory = requirePreference("allocation", CustomSeekBarPreference.class);
        memory.setMaxKeepIncrement(maxRam);
        memory.setValue(LauncherPreferences.PREF_RAM_ALLOCATION);
        memory.setSuffix(" MB");
        memory.setSummary(getString(R.string.urinth_adv_allocation_summary,
                getResources().getInteger(R.integer.memory_seekbar_min), maxRam, Tools.getTotalDeviceMemory(context)));
    }

    /**
     * Each Anti Crash switch says what it did the last time, if it did anything, so a RAM value in the game log
     * or a simple JIT that nobody chose can be traced back to it.
     */
    private void refreshAntiCrash(Context context) {
        Preference memory = requirePreference(AdvancedPerformance.KEY_LOW_MEMORY_GUARD);
        int steps = AntiCrash.lowMemorySteps(context);
        memory.setSummary(steps > 0
                ? getString(R.string.urinth_adv_low_memory_desc) + "\n" + getString(R.string.urinth_adv_low_memory_active, steps)
                : getString(R.string.urinth_adv_low_memory_desc));
        Preference jit = requirePreference(AdvancedPerformance.KEY_JIT_CRASH_GUARD);
        jit.setSummary(AntiCrash.jitFallbackActive(context)
                ? getString(R.string.urinth_adv_jit_crash_desc) + "\n" + getString(R.string.urinth_adv_jit_crash_active)
                : getString(R.string.urinth_adv_jit_crash_desc));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        // A quick setup or a reset writes many options in one edit and loads and refreshes once, at its end.
        if (mBulkWrite) return;
        super.onSharedPreferenceChanged(preferences, key);
        Context context = getContext();
        if (context == null || key == null) return;
        // Turning an Anti Crash switch off or on makes it forget what it did (the text of both rows says so).
        if (AdvancedPerformance.KEY_LOW_MEMORY_GUARD.equals(key)) {
            AntiCrash.clearLowMemory(context);
            refreshAntiCrash(context);
        } else if (AdvancedPerformance.KEY_JIT_CRASH_GUARD.equals(key)) {
            AntiCrash.clearJitFallback(context);
            refreshAntiCrash(context);
        }
        if (AdvancedPerformance.allKeys().contains(key)) {
            // One row changed by hand ends the quick setup that was picked: the Quick setup row says "Your own mix" (or
            // the preset the rows now happen to add up to) from here on, and nothing puts the old options back.
            if (AdvancedPresets.choiceIsOutdated(preferences.getAll())) AdvancedPresetStore.forget(preferences);
            refreshPreset();
        }
    }

    private void resetAdvanced() {
        mBulkWrite = true;
        try {
            if (!AdvancedPresetStore.putDefaults(LauncherPreferences.DEFAULT_PREF.edit()).commit()) {
                Log.e(TAG, "The default options could not be written to disk");
            }
            showRows(AdvancedPerformance.defaultValues());
        } finally {
            mBulkWrite = false;
        }
        Context context = requireContext();
        AntiCrash.clear(context);
        refreshAntiCrash(context);
        LauncherPreferences.loadPreferences(context);
        refreshPreset();
        Toast.makeText(context, R.string.urinth_adv_reset_done, Toast.LENGTH_SHORT).show();
    }
}
