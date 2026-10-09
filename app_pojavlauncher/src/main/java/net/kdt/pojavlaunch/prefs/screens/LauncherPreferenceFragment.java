package net.kdt.pojavlaunch.prefs.screens;


import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.content.Context;
import android.app.Activity;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceCategory;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.EditTextPreference;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.DataMigrator;
import net.kdt.pojavlaunch.game.renderer.RendererCache;
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider;
import net.kdt.pojavlaunch.utils.GpuUtils;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};
    private MultiRTConfigDialog mRuntimeDialog;
    private final ActivityResultLauncher<Object> mRuntimeInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), data -> {
                if (data != null) Tools.installRuntimeFromUri(getContext(), data);
            });
    private final ActivityResultLauncher<Uri> mMigrationLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) new AlertDialog.Builder(getLauncherActivity())
                        .setTitle(R.string.migration_progress_warning_title)
                        .setMessage(R.string.migration_progress_warning_summary)
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                new DataMigrator(getLauncherActivity(), uri).migrateData())
                        .setNegativeButton(android.R.string.cancel, null).show();
            });

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(Color.rgb(4, 21, 34));
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        boolean allSettingsPage = getClass() == LauncherPreferenceFragment.class;
        mVisibilityUpdater = allSettingsPage ? this::updateAllSettingsVisibility : this::updateVisibility;
        addPreferencesFromResource(allSettingsPage ? R.xml.pref_all_settings : R.xml.pref_main);
        if (allSettingsPage) setupAllSettings();
        setupNotificationRequestPreference();
        tintPreferenceIcons(getPreferenceScreen());
    }

    private void tintPreferenceIcons(PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            Drawable icon = preference.getIcon();
            if (icon != null) {
                Drawable tinted = icon.mutate();
                tinted.setTint(Color.rgb(0, 225, 180));
                preference.setIcon(tinted);
            }
            if (preference instanceof PreferenceGroup) {
                tintPreferenceIcons((PreferenceGroup) preference);
            }
        }
    }


    private void setupAllSettings() {
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);
        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio", CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");
        resolutionSeekbar.setValue(resolution < 25 ? 100 : resolution);
        SwitchPreferenceCompat sustained = requirePreference("sustainedPerformance", SwitchPreferenceCompat.class);
        sustained.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustained.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);
        boolean supportsTurnip = GpuUtils.checkVulkanSupport(requireContext().getPackageManager()) && GpuUtils.getGlInfo().isAdreno();
        requirePreference("zinkPreferSystemDriver").setVisible(supportsTurnip);
        GLESProvider provider = GLESProvider.getGlesProvider(getContext(), true);
        requirePreference("use_angle", SwitchPreferenceCompat.class).setVisible(
                provider instanceof GLESProvider.ExternalAngleProvider || provider instanceof GLESProvider.SystemAngleProvider);
        requirePreference("use_angle", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ANGLE);
        ListPreference renderer = requirePreference("renderer", ListPreference.class);
        RendererCache cache = RendererCache.getCompatibleRenderers(getContext());
        renderer.setEntries(cache.rendererDisplayNames);
        renderer.setEntryValues(cache.rendererIds.toArray(new String[0]));
        requirePreference("ignoreNotch").setVisible(LauncherPreferences.hasNotch(getLauncherActivity()));

        requirePreference("timeLongPressTrigger", CustomSeekBarPreference.class).setValue(LauncherPreferences.PREF_LONGPRESS_TRIGGER);
        requirePreference("timeLongPressTrigger", CustomSeekBarPreference.class).setSuffix(" ms");
        requirePreference("buttonscale", CustomSeekBarPreference.class).setValue((int) LauncherPreferences.PREF_BUTTONSIZE);
        requirePreference("buttonscale", CustomSeekBarPreference.class).setSuffix(" %");
        requirePreference("mousescale", CustomSeekBarPreference.class).setValue((int)(LauncherPreferences.PREF_MOUSESCALE * 100));
        requirePreference("mousescale", CustomSeekBarPreference.class).setSuffix(" %");
        requirePreference("mousespeed", CustomSeekBarPreference.class).setValue((int)(LauncherPreferences.PREF_MOUSESPEED * 100f));
        requirePreference("mousespeed", CustomSeekBarPreference.class).setSuffix(" %");
        requirePreference("gamepad_deadzone_scale", CustomSeekBarPreference.class).setValue((int)(LauncherPreferences.PREF_DEADZONE_SCALE * 100f));
        requirePreference("gamepad_deadzone_scale", CustomSeekBarPreference.class).setSuffix(" %");
        requirePreference("buttonTransparency", CustomSeekBarPreference.class).setValue(LauncherPreferences.PREF_BUTTON_TRANSPARENCY);
        requirePreference("buttonTransparency", CustomSeekBarPreference.class).setSuffix(" %");
        requirePreference("gyroSensitivity", CustomSeekBarPreference.class).setValue((int)(LauncherPreferences.PREF_GYRO_SENSITIVITY * 100f));
        requirePreference("gyroSensitivity", CustomSeekBarPreference.class).setSuffix(" %");
        requirePreference("gyroSampleRate", CustomSeekBarPreference.class).setValue(LauncherPreferences.PREF_GYRO_SAMPLE_RATE);
        requirePreference("gyroSampleRate", CustomSeekBarPreference.class).setSuffix(" ms");
        requirePreference("gyroCategory", PreferenceCategory.class).setVisible(Tools.deviceSupportsGyro(requireContext()));

        int deviceRam = Tools.getTotalDeviceMemory(requireContext());
        int maxRam = (Architecture.is32BitsDevice() || deviceRam < 2048) ? Math.min(1024, deviceRam) : deviceRam - (deviceRam < 3064 ? 800 : 1024);
        CustomSeekBarPreference memory = requirePreference("allocation", CustomSeekBarPreference.class);
        memory.setMaxKeepIncrement(maxRam);
        memory.setValue(LauncherPreferences.PREF_RAM_ALLOCATION);
        memory.setSuffix(" MB");
        EditTextPreference javaArgs = requirePreference("javaArgs", EditTextPreference.class);
        javaArgs.setOnBindEditTextListener(TextView::setSingleLine);
        requirePreference("install_jre").setOnPreferenceClickListener(p -> {
            if (mRuntimeDialog == null) {
                mRuntimeDialog = new MultiRTConfigDialog();
                mRuntimeDialog.prepare(getContext(), mRuntimeInstallLauncher);
            }
            mRuntimeDialog.show();
            return true;
        });

        requirePreference("runDataMigration").setOnPreferenceClickListener(p -> {
            if (ProgressKeeper.getTaskCount() > 0) {
                Toast.makeText(getContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            } else mMigrationLauncher.launch(null);
            return true;
        });
        requirePreference("microphoneAccessRequest").setOnPreferenceClickListener(p -> {
            getLauncherActivity().askForPermission(23, Manifest.permission.RECORD_AUDIO);
            return true;
        });
        requirePreference("clearMetadataCache").setOnPreferenceClickListener(p -> {
            if (ProgressKeeper.getTaskCount() > 0) {
                Toast.makeText(getContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
                return true;
            }
            PojavApplication.sExecutorService.submit(() -> {
                try {
                    FileUtils.deleteDirectory(new File(Tools.DIR_CACHE, "string_cache"));
                } catch (IOException e) {
                    Tools.showErrorRemote(getLauncherActivity(), R.string.preference_metadata_clear_fail, e);
                    return;
                }
                Tools.runOnUiThread(() -> {
                    Toast.makeText(getLauncherActivity(), R.string.preference_metadata_clear_complete, Toast.LENGTH_LONG).show();
                    updateAllSettingsVisibility();
                });
            });
            return true;
        });
        requirePreference("freedrenoSysmem", SwitchPreferenceCompat.class).setVisible(GpuUtils.getGlInfo().isAdreno());
        requirePreference("ubwcWorkaround", SwitchPreferenceCompat.class).setVisible(GpuUtils.getGlInfo().isAdreno());
        updateAllSettingsVisibility();
    }

    private void updateAllSettingsVisibility() {
        if (getPreferenceScreen() == null) return;
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
        requirePreference("microphoneAccessRequest").setVisible(!getLauncherActivity().checkForPermissionRationale(33, Manifest.permission.RECORD_AUDIO));
        requirePreference("clearMetadataCache").setVisible(new File(Tools.DIR_CACHE, "string_cache").exists());
        requirePreference("timeLongPressTrigger").setVisible(!LauncherPreferences.PREF_DISABLE_GESTURES);
        boolean gyro = LauncherPreferences.PREF_ENABLE_GYRO;
        requirePreference("gyroSensitivity").setVisible(gyro);
        requirePreference("gyroSampleRate").setVisible(gyro);
        requirePreference("gyroInvertX").setVisible(gyro);
        requirePreference("gyroInvertY").setVisible(gyro);
        requirePreference("gyroSmoothing").setVisible(gyro);
        requirePreference("force_vsync").setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
    }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
