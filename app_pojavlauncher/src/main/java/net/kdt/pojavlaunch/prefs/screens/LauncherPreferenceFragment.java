package net.kdt.pojavlaunch.prefs.screens;


import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.app.Activity;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import android.os.Bundle;

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
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import net.kdt.pojavlaunch.LauncherActivity;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.DataMigrator;
import net.kdt.pojavlaunch.game.renderer.GraphicsApi;
import net.kdt.pojavlaunch.game.renderer.MobileGluesConfig;
import net.kdt.pojavlaunch.game.renderer.RendererApiPolicy;
import net.kdt.pojavlaunch.game.renderer.PerformanceMobileGluesGuard;
import net.kdt.pojavlaunch.game.renderer.RendererCache;
import net.kdt.pojavlaunch.game.renderer.RendererChoices;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider;
import net.kdt.pojavlaunch.utils.ButtonHaptics;
import net.kdt.pojavlaunch.utils.GpuUtils;

import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.BuildConfig;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};
    private MultiRTConfigDialog mRuntimeDialog;
    /** Only set on the full settings page. */
    private UrinthPreferenceAdapter mAdapter;
    private SettingsHeaderPreference mHeader;
    private GraphicsApiPreference mApiPreference;
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
        view.setBackgroundColor(SettingsStyle.PAGE);
        super.onViewCreated(view, savedInstanceState);
        // Card spacing, and no cross-fade when a row is redrawn (the rows animate themselves).
        float density = getResources().getDisplayMetrics().density;
        RecyclerView list = getListView();
        list.addItemDecoration(new UrinthPreferenceDecoration(density));
        list.setClipToPadding(false);
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), Math.round(24f * density));
        if (list.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
    }

    /** Settings rows get the launcher's card look; what they do is unchanged. */
    @Override
    protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        mAdapter = new UrinthPreferenceAdapter(preferenceScreen);
        return mAdapter;
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        boolean allSettingsPage = getClass() == LauncherPreferenceFragment.class;
        mVisibilityUpdater = allSettingsPage ? this::updateAllSettingsVisibility : this::updateVisibility;
        addPreferencesFromResource(allSettingsPage ? R.xml.pref_all_settings : R.xml.pref_main);
        if (allSettingsPage) {
            setupAllSettings();
            setupMobileGlues();
        }
        setupNotificationRequestPreference();
        // The title and state chips on top of the full settings list (it sorts before every other row).
        if (allSettingsPage && getPreferenceScreen() != null) {
            mHeader = new SettingsHeaderPreference(requireContext());
            getPreferenceScreen().addPreference(mHeader);
            refreshGraphicsState();
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
        setupGraphics();
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
        CustomSeekBarPreference hapticsLength = requirePreference("buttonHapticsMs", CustomSeekBarPreference.class);
        hapticsLength.setValue(LauncherPreferences.PREF_BUTTON_HAPTICS_MS);
        hapticsLength.setSuffix(" ms");
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
        setupAbout();
        requirePreference("freedrenoSysmem", SwitchPreferenceCompat.class).setVisible(GpuUtils.getGlInfo().isAdreno());
        requirePreference("ubwcWorkaround", SwitchPreferenceCompat.class).setVisible(GpuUtils.getGlInfo().isAdreno());
        updateAllSettingsVisibility();
    }


    // ---- graphics: the OpenGL | Vulkan switch, the renderer list and the state shown on the page ----

    private void setupGraphics() {
        requirePreference("renderer", ListPreference.class)
                .setOnPreferenceChangeListener((p, value) -> acceptRendererChoice(String.valueOf(value)));
        mApiPreference = requirePreference("graphics_api", GraphicsApiPreference.class);
        mApiPreference.setListener(this::onGraphicsApiTapped);
        // Performance Mode has its own rules (it also moves the renderer), so it goes through LauncherPreferences.
        requirePreference("performanceMode", SwitchPreferenceCompat.class).setOnPreferenceChangeListener((p, value) -> {
            LauncherPreferences.setPerformanceMode(Boolean.TRUE.equals(value));
            return true;
        });
        requirePreference("perf_mobileglues_get").setOnPreferenceClickListener(p -> {
            Tools.openURL(getLauncherActivity(), MOBILEGLUES_RELEASES_URL);
            return true;
        });
        GpuUtils.GLInfo gl = GpuUtils.getGlInfo();
        PackageManager pm = requireContext().getPackageManager();
        String vulkan = vulkanLabel(pm);
        String chip = gl == null || TextUtils.isEmpty(gl.renderer) ? getString(R.string.urinth_set_gpu_unknown) : gl.renderer;
        requirePreference("graphics_info").setSummary(getString(R.string.urinth_set_gpu_summary, chip,
                gl == null ? 0 : gl.glesMajorVersion, vulkan));
    }

    /** "Vulkan 1.1", "Vulkan" when the version is not reported, or "No Vulkan". */
    private String vulkanLabel(PackageManager pm) {
        if (!GpuUtils.checkVulkanSupport(pm)) return getString(R.string.urinth_set_vulkan_none);
        String version = RendererApiPolicy.formatVulkanVersion(GpuUtils.vulkanVersion(pm));
        return version == null ? getString(R.string.urinth_set_api_vulkan)
                : getString(R.string.urinth_set_vulkan_version, version);
    }

    /** A renderer picked in the list that cannot start here is turned down with the reason, instead of being stored. */
    private boolean acceptRendererChoice(String id) {
        Context context = getContext();
        if (context == null) return true;
        RendererChoices choices = RendererChoices.load(context, LauncherPreferences.PREF_PERFORMANCE_MODE);
        RendererApiPolicy.Choice choice = choices.find(id);
        if (choice != null && !choice.usable) {
            showMessage(R.string.urinth_set_renderer_unavailable_title,
                    getString(R.string.urinth_set_renderer_unavailable_message, RendererApiPolicy.shortName(choice.name)));
            return false;
        }
        return true;
    }

    private void onGraphicsApiTapped(@NonNull GraphicsApi target) {
        Context context = getContext();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (context == null || prefs == null || getPreferenceScreen() == null) return;
        boolean performanceMode = LauncherPreferences.PREF_PERFORMANCE_MODE;
        RendererChoices choices = RendererChoices.load(context, performanceMode);
        GraphicsApi current = choices.apiOf(LauncherPreferences.PREF_RENDERER);
        PackageManager pm = context.getPackageManager();
        RendererApiPolicy.Result result = RendererApiPolicy.decideSwitch(target, current, choices.all,
                prefs.getString(lastKey(target), null), performanceMode, GpuUtils.checkVulkanSupport(pm));
        final String rendererId = result.rendererId;
        switch (result.outcome) {
            case ALREADY:
                break;
            case SELECT:
                selectRenderer(rendererId);
                break;
            case NO_VULKAN_HARDWARE:
                showMessage(R.string.urinth_set_vulkan_none_title, getString(R.string.urinth_set_vulkan_none_message));
                break;
            case NO_VULKAN_RENDERER:
                showVulkanPluginDialog(pm);
                break;
            case NO_OPENGL_RENDERER:
                showMessage(R.string.urinth_set_renderer_unavailable_title, getString(R.string.urinth_set_opengl_none_message));
                break;
        }
    }

    private static String lastKey(GraphicsApi api) {
        return api == GraphicsApi.VULKAN ? RendererApiPolicy.KEY_LAST_VULKAN : RendererApiPolicy.KEY_LAST_OPENGL;
    }

    /** MojoLauncher's Zink plugin (Legacy Zink for Mali GPUs): package git.mojo.zink, about 8.5 MB, Android 10 or newer. */
    private static final String ZINK_PLUGIN_URL =
            "https://github.com/MojoLauncher/ZinkPlugin/releases/download/rel/MJZinkPlugin-debug.apk";

    /**
     * Says which plugin app adds a Vulkan renderer on this phone. For a Mali GPU it offers the
     * download of the Zink plugin; the screen refreshes when the player comes back (see onResume).
     */
    private void showVulkanPluginDialog(PackageManager pm) {
        GpuUtils.GLInfo gl = GpuUtils.getGlInfo();
        boolean mali = gl != null && gl.isMali();
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.urinth_set_vulkan_none_title)
                .setMessage(getString(mali ? R.string.urinth_set_vulkan_plugin_mali : R.string.urinth_set_vulkan_plugin_other,
                        vulkanLabel(pm)))
                .setPositiveButton(android.R.string.ok, null);
        if (mali) {
            dialog.setNeutralButton(R.string.urinth_set_vulkan_get_plugin,
                    (d, w) -> Tools.openURL(getLauncherActivity(), ZINK_PLUGIN_URL));
        }
        dialog.show();
    }

    /**
     * The MobileGlues category. Each list shows what it is set to and what it does, the typed multi-draw
     * orders are cleaned the way MobileGlues reads them, and the last row puts everything back to the
     * defaults. The values themselves are written into MobileGlues' config file at launch
     * (MobileGluesLaunch).
     */
    private void setupMobileGlues() {
        describeList(MobileGluesConfig.KEY_GL_VERSION, R.string.urinth_mg_gl_desc);
        describeList(MobileGluesConfig.KEY_ANGLE, R.string.urinth_mg_angle_desc);
        describeList(MobileGluesConfig.KEY_NO_ERROR, R.string.urinth_mg_noerror_desc);
        describeList(MobileGluesConfig.KEY_GLSL_CACHE_MB, R.string.urinth_mg_cache_desc);
        describeList(MobileGluesConfig.KEY_ANGLE_DEPTH_FIX, R.string.urinth_mg_depthfix_desc);
        describeList(MobileGluesConfig.KEY_FSR1, R.string.urinth_mg_fsr_desc);
        for (int i = 0; i < MobileGluesConfig.ORDER_KEYS.length; i++) {
            describeOrder(MobileGluesConfig.ORDER_KEYS[i], i == 0);
        }
        requirePreference("mg_reset").setOnPreferenceClickListener(p -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.urinth_mg_reset_title)
                    .setMessage(R.string.urinth_mg_reset_confirm)
                    .setPositiveButton(android.R.string.ok, (d, w) -> resetMobileGlues())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    /** A list row whose summary is the chosen entry followed by what the setting does. */
    private void describeList(String key, int descriptionRes) {
        ListPreference list = requirePreference(key, ListPreference.class);
        final String description = getString(descriptionRes);
        list.setSummaryProvider((Preference.SummaryProvider<ListPreference>) p -> {
            CharSequence entry = p.getEntry();
            return entry == null ? description : entry + "\n" + description;
        });
    }

    /** A typed draw-method order: its summary shows the order (or that it is automatic), and what is typed is cleaned. */
    private void describeOrder(String key, boolean global) {
        EditTextPreference order = requirePreference(key, EditTextPreference.class);
        final String empty = getString(global ? R.string.urinth_mg_order_auto : R.string.urinth_mg_order_inherit);
        order.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) p ->
                TextUtils.isEmpty(p.getText()) ? empty : p.getText());
        order.setOnPreferenceChangeListener((p, value) -> {
            String typed = String.valueOf(value);
            MobileGluesConfig.Order cleaned = MobileGluesConfig.cleanOrder(typed, global);
            if (!cleaned.ignored.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.urinth_mg_order_ignored,
                        TextUtils.join(", ", cleaned.ignored)), Toast.LENGTH_LONG).show();
            }
            if (cleaned.text.equals(typed)) return true;
            // Keep what MobileGlues will really read, not what was typed.
            ((EditTextPreference) p).setText(cleaned.text);
            return false;
        });
    }

    private void resetMobileGlues() {
        for (java.util.Map.Entry<String, Object> entry : MobileGluesConfig.defaultValues().entrySet()) {
            Preference preference = findPreference(entry.getKey());
            Object value = entry.getValue();
            if (preference instanceof ListPreference) {
                ((ListPreference) preference).setValue((String) value);
            } else if (preference instanceof SwitchPreferenceCompat) {
                ((SwitchPreferenceCompat) preference).setChecked((Boolean) value);
            } else if (preference instanceof EditTextPreference) {
                ((EditTextPreference) preference).setText((String) value);
            }
        }
        Toast.makeText(requireContext(), R.string.urinth_mg_reset_done, Toast.LENGTH_SHORT).show();
    }

    /** The official MobileGlues releases page; the plugin app for launchers like this one is the APK there. */
    private static final String MOBILEGLUES_RELEASES_URL = "https://github.com/MobileGL-Dev/MobileGlues-release/releases/latest";

    /**
     * The "MobileGlues in Performance Mode" switch says what the next launch will really use, and
     * the "Get the plugin" row is only there while the plugin app is missing.
     */
    private void refreshMobileGluesRows(Context context) {
        SwitchPreferenceCompat toggle = findPreference("perfMobileGlues");
        Preference get = findPreference("perf_mobileglues_get");
        if (toggle == null) return;
        boolean usable = RendererCache.isMobileGluesUsable(context);
        // A launch that used MobileGlues and then stopped with an error soon after starting holds the swap
        // back (PerformanceMobileGluesGuard); the row says so until the switch is turned off and on.
        boolean failed = LauncherPreferences.PREF_PERF_MOBILEGLUES && usable
                && PerformanceMobileGluesGuard.lastSwapFailed(context);
        int summary = !LauncherPreferences.PREF_PERF_MOBILEGLUES ? R.string.urinth_set_perf_mg_off
                : failed ? R.string.urinth_set_perf_mg_failed
                : usable ? R.string.urinth_set_perf_mg_found : R.string.urinth_set_perf_mg_missing;
        toggle.setSummary(summary);
        if (get != null) get.setVisible(!usable);
    }

    private void showMessage(int title, CharSequence message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Stores the renderer through its list preference, so the list and the stored value stay in step. */
    private void selectRenderer(String id) {
        ListPreference renderer = findPreference("renderer");
        if (renderer == null || id == null) return;
        LauncherPreferences.PREF_RENDERER = id;
        renderer.setValue(id);
        refreshGraphicsState();
    }

    /** Remembers the renderer last used with each API, so switching back brings it back. */
    private void rememberRenderer(String id, RendererChoices choices) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        // LTW is only ever chosen by Performance Mode, which keeps its own memory (see LauncherPreferences).
        if (prefs == null || id == null || Renderers.LTW_RENDERER.equals(id)) return;
        String key = lastKey(choices.apiOf(id));
        if (!id.equals(prefs.getString(key, null))) prefs.edit().putString(key, id).apply();
    }

    /**
     * Brings everything that depends on the renderer up to date: the OpenGL | Vulkan switch and its
     * explanation, the renderer list (it lists the renderers of the API in use), the Performance Mode
     * switch and the chips on top.
     */
    private void refreshGraphicsState() {
        Context context = getContext();
        if (context == null || getPreferenceScreen() == null || mApiPreference == null) return;
        boolean performanceMode = LauncherPreferences.PREF_PERFORMANCE_MODE;
        String current = LauncherPreferences.PREF_RENDERER;
        RendererChoices choices = RendererChoices.load(context, performanceMode);
        GraphicsApi api = choices.apiOf(current);
        PackageManager pm = context.getPackageManager();
        boolean vulkanHardware = GpuUtils.checkVulkanSupport(pm);
        boolean vulkanUsable = vulkanHardware && RendererApiPolicy.usableCount(GraphicsApi.VULKAN, choices.all) > 0;
        rememberRenderer(current, choices);

        fillRendererList(choices, api, performanceMode, current);
        mApiPreference.setState(api, !vulkanUsable,
                graphicsStatus(choices, api, current, vulkanHardware, vulkanUsable, vulkanLabel(pm)));

        SwitchPreferenceCompat performance = findPreference("performanceMode");
        if (performance != null) performance.setChecked(performanceMode);
        refreshMobileGluesRows(context);

        if (mHeader != null) {
            mHeader.setChips(
                    getString(R.string.urinth_set_chip_graphics, RendererApiPolicy.shortName(choices.nameOf(current)),
                            getString(api == GraphicsApi.VULKAN ? R.string.urinth_set_api_vulkan : R.string.urinth_set_api_opengl)),
                    getString(R.string.urinth_set_chip_memory, LauncherPreferences.PREF_RAM_ALLOCATION),
                    getString(performanceMode ? R.string.urinth_set_chip_perf_on : R.string.urinth_set_chip_perf_off));
        }
    }

    private void fillRendererList(RendererChoices choices, GraphicsApi api, boolean performanceMode, String current) {
        ListPreference renderer = findPreference("renderer");
        if (renderer == null) return;
        ArrayList<CharSequence> names = new ArrayList<>();
        ArrayList<CharSequence> values = new ArrayList<>();
        boolean hasCurrent = false;
        List<RendererApiPolicy.Choice> listed = choices.listed(api, performanceMode);
        for (RendererApiPolicy.Choice choice : listed) {
            names.add(choice.usable ? choice.name : getString(R.string.urinth_set_renderer_unavailable, choice.name));
            values.add(choice.id);
            if (choice.id.equals(current)) hasCurrent = true;
        }
        if (!hasCurrent) {
            // The renderer in use is not in the list (for example a plugin that was removed): still show it.
            names.add(0, choices.nameOf(current));
            values.add(0, current);
        }
        renderer.setEntries(names.toArray(new CharSequence[0]));
        renderer.setEntryValues(values.toArray(new CharSequence[0]));
        if (!TextUtils.equals(renderer.getValue(), current)) renderer.setValue(current);
        // Setting the provider again redraws the row, so the summary shows the entry of the value now stored.
        renderer.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }

    private CharSequence graphicsStatus(RendererChoices choices, GraphicsApi api, String current,
                                        boolean vulkanHardware, boolean vulkanUsable, String vulkanLabel) {
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.urinth_set_status_using, RendererApiPolicy.shortName(choices.nameOf(current))));
        text.append(' ');
        if (Renderers.GL4ES_RENDERER.equals(current)) text.append(getString(R.string.urinth_set_note_gl4es));
        else if (Renderers.LTW_RENDERER.equals(current)) text.append(getString(R.string.urinth_set_note_ltw));
        else if (api == GraphicsApi.VULKAN) text.append(getString(R.string.urinth_set_note_vulkan));
        else text.append(getString(R.string.urinth_set_note_plugin));
        if (api == GraphicsApi.VULKAN && LauncherPreferences.PREF_PERFORMANCE_MODE) {
            text.append(' ').append(getString(R.string.urinth_set_status_perf_vulkan));
        }
        if (api == GraphicsApi.OPENGL) {
            text.append("\n\n");
            if (!vulkanHardware) text.append(getString(R.string.urinth_set_status_vulkan_no_hardware));
            else if (!vulkanUsable) text.append(getString(R.string.urinth_set_status_vulkan_no_renderer, vulkanLabel));
            else text.append(getString(R.string.urinth_set_status_vulkan_ready, vulkanLabel));
        }
        String override = instanceOverrideNote(choices, current);
        if (override != null) text.append("\n\n").append(override);
        return text;
    }

    /**
     * A line about the selected instance when it has its own renderer (set in the instance editor):
     * such an instance ignores the choice made here, which would otherwise look like the switch not
     * working. Null when the instance follows the launcher's renderer.
     */
    private String instanceOverrideNote(RendererChoices choices, String current) {
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance == null || !Tools.isValidString(instance.renderer) || instance.renderer.equals(current)) return null;
            String name = Tools.isValidString(instance.name) ? instance.name : instance.versionId;
            return getString(R.string.urinth_set_status_instance_override, String.valueOf(name),
                    RendererApiPolicy.shortName(choices.nameOf(instance.renderer)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- about -------------------------------------------------------------------------------

    private void setupAbout() {
        requirePreference("app_version").setSummary(
                getString(R.string.urinth_set_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        requirePreference("copy_system_info").setOnPreferenceClickListener(p -> {
            copySystemInfo();
            return true;
        });
        requirePreference("copy_game_log").setOnPreferenceClickListener(p -> {
            copyGameLog();
            return true;
        });
    }

    /**
     * Puts a short excerpt of the last game log on the clipboard (start, telling lines from the
     * middle and the end, login tokens hidden), so it can be pasted into a chat. The log file is
     * read off the main thread.
     */
    private void copyGameLog() {
        Context context = getContext();
        if (context == null) return;
        File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        PojavApplication.sExecutorService.execute(() -> {
            String text;
            try {
                text = GameLogExcerpt.fromFile(log);
            } catch (IOException e) {
                Log.w("Settings", "The game log could not be read", e);
                text = null;
            }
            final String excerpt = text;
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (excerpt == null) {
                    Toast.makeText(activity, R.string.urinth_set_log_none, Toast.LENGTH_LONG).show();
                    return;
                }
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) return;
                clipboard.setPrimaryClip(ClipData.newPlainText("URinth game log", excerpt));
                Toast.makeText(activity, R.string.urinth_set_log_done, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void copySystemInfo() {
        Context context = getContext();
        if (context == null) return;
        PackageManager pm = context.getPackageManager();
        GpuUtils.GLInfo gl = GpuUtils.getGlInfo();
        RendererChoices choices = RendererChoices.load(context, LauncherPreferences.PREF_PERFORMANCE_MODE);
        String renderer = LauncherPreferences.PREF_RENDERER;
        GraphicsApi api = choices.apiOf(renderer);
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
        String vulkan = GpuUtils.checkVulkanSupport(pm)
                ? String.valueOf(RendererApiPolicy.formatVulkanVersion(GpuUtils.vulkanVersion(pm)))
                : null;
        if ("null".equals(vulkan)) vulkan = "supported, version not reported";
        String text = SystemInfoText.build(
                BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")",
                Build.MANUFACTURER + " " + Build.MODEL,
                Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                abi,
                Tools.getTotalDeviceMemory(context), LauncherPreferences.PREF_RAM_ALLOCATION,
                gl == null ? null : gl.renderer, gl == null ? 0 : gl.glesMajorVersion,
                vulkan,
                choices.nameOf(renderer) + " [" + renderer + "]",
                api == GraphicsApi.VULKAN ? "Vulkan" : "OpenGL",
                LauncherPreferences.PREF_PERFORMANCE_MODE);
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("URinth system info", text));
        Toast.makeText(context, R.string.urinth_set_copy_done, Toast.LENGTH_SHORT).show();
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
        if (mApiPreference != null) {
            // A renderer plugin app may have been installed or removed while the screen was away.
            RendererCache.releaseRendererCache();
            refreshGraphicsState();
        }
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        Context context = getContext();
        if (context == null) return;
        LauncherPreferences.loadPreferences(context);
        if (mApiPreference == null || s == null) return;
        // Turning the MobileGlues switch off or on gives it a fresh start after an error exit.
        if ("perfMobileGlues".equals(s)) PerformanceMobileGluesGuard.clearFailure(context);
        switch (s) {
            case "renderer":
            case "performanceMode":
            case "perfMobileGlues":
            case "allocation":
                refreshGraphicsState();
                break;
            case SettingsStyle.KEY_ACCENT:
            case SettingsStyle.KEY_COMPACT:
            case SettingsStyle.KEY_REDUCE_MOTION:
                if (mAdapter != null) {
                    mAdapter.refreshStyle();
                    mAdapter.notifyDataSetChanged();
                }
                break;
            case "buttonHaptics":
            case "buttonHapticsMs":
                // A short buzz of the chosen length, so the setting can be felt right away.
                if (LauncherPreferences.PREF_BUTTON_HAPTICS) ButtonHaptics.buzz(context, LauncherPreferences.PREF_BUTTON_HAPTICS_MS);
                break;
            default:
                break;
        }
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
