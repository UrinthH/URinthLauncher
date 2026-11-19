package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.AdvancedPerformance;
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

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_advanced_settings);
        Context context = requireContext();

        setupMemory(context);
        for (Object[] list : LISTS) describeList((String) list[0], (Integer) list[1]);

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
    }

    private void resetAdvanced() {
        for (Map.Entry<String, Object> entry : AdvancedPerformance.defaultValues().entrySet()) {
            Preference preference = findPreference(entry.getKey());
            Object value = entry.getValue();
            if (preference instanceof ListPreference) {
                ((ListPreference) preference).setValue((String) value);
            } else if (preference instanceof SwitchPreferenceCompat) {
                ((SwitchPreferenceCompat) preference).setChecked((Boolean) value);
            }
        }
        AntiCrash.clear(requireContext());
        refreshAntiCrash(requireContext());
        Toast.makeText(requireContext(), R.string.urinth_adv_reset_done, Toast.LENGTH_SHORT).show();
    }
}
