package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.home.HomeActivityLog;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.home.InstallationLabels;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import git.artdeell.mojo.R;

/**
 * Drives the UrinthUltra home screen (view_urinth_home.xml). Every control calls the same
 * launcher code as the rest of the app: Play sets the instance and fires LAUNCH_GAME, the editor,
 * settings and browsers open in the centre host, and account changes use Accounts.
 */
public final class UrinthHomeController {
    /** The home page does not scroll, so the lists show only what fits the card: one installation and three activity rows. */
    private static final int MAX_HOME_ACTIVITY = 3;
    private static final int GREEN = 0xFF1FC77A;

    private final FragmentActivity activity;
    private final MainMenuFragment host;
    private final Context context;

    private final View sidebar;
    private final TextView statusText;
    private final TextView playButton;
    private final TextView lastPlayedName;
    private final TextView accountName;
    private final TextView accountStatus;
    private final ImageView accountAvatar;
    private final LinearLayout installationsList;
    private final TextView installationsEmpty;
    private final View perfPanel;
    private final ImageView perfIcon;
    private final View perfChip;
    private final TextView perfState;
    private final TextView perfButton;

    private List<DisplayInstance> instances = new ArrayList<>();
    private Account currentAccount;
    private Account[] savedAccounts = new Account[0];
    private Bitmap avatar;
    private boolean released;
    private MediaPlayer themeMusic;

    // The glow pulse is driven by hand with a Handler loop instead of ValueAnimator: on a lot of
    // devices (especially ones people tweak for performance) "Developer options > Animator
    // duration scale" is set to Off, which makes every ValueAnimator jump straight to its end
    // value instead of animating — the glow would just sit there fully lit. A plain Handler timer
    // runs on real wall-clock time and ignores that setting entirely, so the pulse always animates.
    private View heroGlowView;
    private View bannerGlowView;
    private View edgeGlowView;
    private final Handler glowHandler = new Handler(Looper.getMainLooper());
    private boolean glowPulsing;
    private long glowStartUptime;
    private final Runnable glowTick = this::onGlowTick;

    /** The faint drifting ash/snow specks; drives its own frame loop, see ParticleOverlayView. */
    private ParticleOverlayView particleView;

    /** The shimmering "Made by" credit line in the top bar; drives its own frame loop too. */
    private ShimmerTextView creditName;

    /** The 5 sidebar categories, in the same order as the layout, for the active-highlight. */
    private View navHome, navNews, navInstallations, navSkins, navSettings;

    private final ExtraListener<Boolean> accountRefreshListener = (key, value) -> {
        refresh();
        return false;
    };

    public UrinthHomeController(FragmentActivity activity, MainMenuFragment host, View root) {
        this.activity = activity;
        this.host = host;
        this.context = root.getContext();

        sidebar = root.findViewById(R.id.urinth_sidebar);
        statusText = root.findViewById(R.id.urinth_status_text);
        playButton = root.findViewById(R.id.urinth_play);
        lastPlayedName = root.findViewById(R.id.urinth_last_played_name);
        accountName = root.findViewById(R.id.urinth_account_name);
        accountStatus = root.findViewById(R.id.urinth_account_status);
        accountAvatar = root.findViewById(R.id.urinth_account_avatar);
        installationsList = root.findViewById(R.id.urinth_installations_list);
        installationsEmpty = root.findViewById(R.id.urinth_installations_empty);
        perfPanel = root.findViewById(R.id.urinth_perf_panel);
        perfIcon = root.findViewById(R.id.urinth_perf_icon);
        perfChip = root.findViewById(R.id.urinth_perf_chip);
        perfState = root.findViewById(R.id.urinth_perf_state);
        perfButton = root.findViewById(R.id.urinth_perf_button);
        renderPerformanceMode();
        perfButton.setOnClickListener(v -> togglePerformanceMode());

        colourUltra(root.findViewById(R.id.urinth_brand));
        // The hero brand/tagline text view was removed from the layout — branding is
        // now baked into the hero photo itself, so there's nothing left here to colour.
        setPhoto(root.findViewById(R.id.urinth_hero_image), "urinth_photo_hero");
        setPhoto(root.findViewById(R.id.urinth_banner_image), "urinth_photo_banner");
        startMusic();
        heroGlowView = root.findViewById(R.id.urinth_hero_glow);
        bannerGlowView = root.findViewById(R.id.urinth_banner_glow);
        edgeGlowView = root.findViewById(R.id.urinth_red_edge_glow);
        // The edge-glow group is 4 overlapping strips; render it as one flattened layer so the
        // pulsed alpha applies evenly across the whole border instead of double-darkening corners.
        if (edgeGlowView != null) edgeGlowView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        startGlowLoop();
        particleView = root.findViewById(R.id.urinth_particles);
        if (particleView != null) particleView.start();
        creditName = root.findViewById(R.id.urinth_credit_name);
        if (creditName != null) creditName.start();

        navHome = root.findViewById(R.id.urinth_nav_home);
        navNews = root.findViewById(R.id.urinth_nav_news);
        navInstallations = root.findViewById(R.id.urinth_nav_installations);
        navSkins = root.findViewById(R.id.urinth_nav_skins);
        navSettings = root.findViewById(R.id.urinth_nav_settings);
        markHomeActive();

        bindActions(root);
        bindNews(root);
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_ACCOUNT_SPINNER, accountRefreshListener);
        HomeActivityLog.recordLauncherUpdateIfNeeded(context);
        refresh();
    }

    /** Stop listening when the home screen goes away. */
    public void release() {
        released = true;
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, accountRefreshListener);
        if (themeMusic != null) {
            try {
                themeMusic.stop();
            } catch (IllegalStateException ignored) {
                // already stopped/released elsewhere
            }
            themeMusic.release();
            themeMusic = null;
        }
        stopGlowLoop();
        if (particleView != null) {
            particleView.release();
            particleView = null;
        }
        if (creditName != null) {
            creditName.stop();
            creditName = null;
        }
    }

    /** Starts the looping home-screen theme (res/raw/urinth_theme). Safe to call once. */
    private void startMusic() {
        try {
            themeMusic = MediaPlayer.create(context, R.raw.urinth_theme);
            if (themeMusic != null) {
                themeMusic.setLooping(true);
                themeMusic.start();
            }
        } catch (Exception e) {
            // Missing/corrupt audio resource shouldn't break the home screen.
            themeMusic = null;
        }
    }

    /** Pause the theme and the glow pulses when the app goes into the background. */
    public void pauseMusic() {
        if (themeMusic != null && themeMusic.isPlaying()) themeMusic.pause();
        pauseGlowLoop();
        if (particleView != null) particleView.pause();
    }

    /** Resume the theme and the glow pulses when the app comes back to the foreground. */
    public void resumeMusic() {
        if (released) return;
        if (themeMusic != null && !themeMusic.isPlaying()) themeMusic.start();
        resumeGlowLoop();
        if (particleView != null) particleView.resume();
    }

    /** Starts the self-driven glow pulse loop (see the field comment on glowHandler for why). */
    private void startGlowLoop() {
        glowPulsing = true;
        glowStartUptime = SystemClock.uptimeMillis();
        glowHandler.removeCallbacks(glowTick);
        glowHandler.post(glowTick);
    }

    private void pauseGlowLoop() {
        glowPulsing = false;
        glowHandler.removeCallbacks(glowTick);
    }

    private void resumeGlowLoop() {
        if (released || glowPulsing) return;
        // Shift the start time so the pulse continues smoothly instead of jumping on resume.
        glowStartUptime = SystemClock.uptimeMillis() - glowElapsedAtPause;
        glowPulsing = true;
        glowHandler.removeCallbacks(glowTick);
        glowHandler.post(glowTick);
    }

    private void stopGlowLoop() {
        glowPulsing = false;
        glowHandler.removeCallbacks(glowTick);
        heroGlowView = null;
        bannerGlowView = null;
        edgeGlowView = null;
    }

    private long glowElapsedAtPause;

    private void onGlowTick() {
        if (!glowPulsing || released) return;
        long elapsed = SystemClock.uptimeMillis() - glowStartUptime;
        glowElapsedAtPause = elapsed;
        applyGlowAlpha(heroGlowView, 0.12f, 1f, 1800L, elapsed, 0L);
        applyGlowAlpha(bannerGlowView, 0.12f, 1f, 1800L, elapsed, 0L);
        applyGlowAlpha(edgeGlowView, 0.2f, 1f, 1500L, elapsed, 150L);
        // ~60fps; smooth enough for an alpha pulse without burning battery like a tight loop would.
        glowHandler.postDelayed(glowTick, 16L);
    }

    /**
     * Sets a view's alpha to a smooth, continuously repeating brighter-then-dimmer-then-brighter
     * wave between minAlpha and maxAlpha. A sine easing is used so the pulse accelerates into and
     * out of each peak instead of ticking linearly. Does nothing if the view is missing.
     */
    private static void applyGlowAlpha(View view, float minAlpha, float maxAlpha, long periodMs,
                                        long elapsedMs, long phaseOffsetMs) {
        if (view == null) return;
        double progress = ((elapsedMs + phaseOffsetMs) % periodMs) / (double) periodMs; // 0..1
        double wave = (1 - Math.cos(progress * Math.PI * 2)) / 2; // 0 -> 1 -> 0, eased
        float alpha = (float) (minAlpha + (maxAlpha - minAlpha) * wave);
        view.setAlpha(alpha);
    }

    private void bindActions(View root) {
        root.findViewById(R.id.urinth_menu_btn).setOnClickListener(v ->
                sidebar.setVisibility(sidebar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        // Accounts are reached from the account card itself now that the top bar's icon row
        // (folder/people/download/settings/more) was replaced with the credit line.
        root.findViewById(R.id.urinth_account_card).setOnClickListener(v -> showAccountPicker());

        // Home closes any screen opened over the page; the page itself is always the home screen.
        navHome.setOnClickListener(v -> {
            if (host != null) host.closeCenterContent();
            markHomeActive();
        });
        // "Installations" is the entry point for creating a new one (pick Vanilla/a loader);
        // managing an existing installation happens from its row on the home card below.
        navInstallations.setOnClickListener(v -> {
            setActiveNav(navInstallations);
            if (host != null) {
                host.showCenterFragment(ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null);
            } else {
                toast(R.string.urinth_not_set_up);
            }
        });
        // Downloads and Servers aren't built yet, so they stay on the home screen and just say so
        // rather than moving the active highlight somewhere nothing actually opened.
        navNews.setOnClickListener(v -> toast(R.string.urinth_news_not_set_up));
        navSkins.setOnClickListener(v -> toast(R.string.urinth_not_set_up));
        navSettings.setOnClickListener(v -> {
            setActiveNav(navSettings);
            openSettings();
        });

        root.findViewById(R.id.urinth_news_all).setOnClickListener(v -> toast(R.string.urinth_news_not_set_up));
        root.findViewById(R.id.urinth_news_1).setOnClickListener(v -> toast(R.string.urinth_news_not_set_up));
        root.findViewById(R.id.urinth_news_2).setOnClickListener(v -> toast(R.string.urinth_news_not_set_up));
        root.findViewById(R.id.urinth_news_3).setOnClickListener(v -> toast(R.string.urinth_news_not_set_up));

        playButton.setOnClickListener(v -> launch(pickPlayTarget()));
        root.findViewById(R.id.urinth_last_played_btn).setOnClickListener(this::showLastPlayedMenu);
        root.findViewById(R.id.urinth_explore_worlds).setOnClickListener(v -> openBrowser("world"));
        root.findViewById(R.id.urinth_addons_btn).setOnClickListener(v -> openBrowser("mod"));

        root.findViewById(R.id.urinth_link_quick).setOnClickListener(v -> toast(R.string.urinth_not_set_up));
        root.findViewById(R.id.urinth_link_website).setOnClickListener(v -> toast(R.string.urinth_not_set_up));
        root.findViewById(R.id.urinth_link_discord).setOnClickListener(v -> toast(R.string.urinth_not_set_up));
        root.findViewById(R.id.urinth_link_docs).setOnClickListener(v -> toast(R.string.urinth_not_set_up));
    }

    /** Highlights the given sidebar category and un-highlights the rest. */
    private void setActiveNav(View active) {
        View[] all = {navHome, navNews, navInstallations, navSkins, navSettings};
        for (View item : all) {
            if (item == null) continue;
            item.setBackgroundResource(item == active ? R.drawable.urinth_nav_active : R.drawable.urinth_nav_idle);
        }
    }

    /** Moves the sidebar highlight back to Home, e.g. once a screen opened over the page closes. */
    public void markHomeActive() {
        if (navHome != null) setActiveNav(navHome);
    }

    /** Placeholder news cards until a news feed is connected. Dates say "Not set up yet" rather than inventing one. */
    private void bindNews(View root) {
        bindNewsCard(root.findViewById(R.id.urinth_news_1), "urinth_photo_news_1",
                R.string.urinth_news_1_title, R.string.urinth_news_1_desc);
        bindNewsCard(root.findViewById(R.id.urinth_news_2), "urinth_photo_news_2",
                R.string.urinth_news_2_title, R.string.urinth_news_2_desc);
        bindNewsCard(root.findViewById(R.id.urinth_news_3), "urinth_photo_news_3",
                R.string.urinth_news_3_title, R.string.urinth_news_3_desc);
    }

    private void bindNewsCard(View card, String photoName, int title, int description) {
        setPhoto((ImageView) card.findViewById(R.id.urinth_news_image), photoName);
        ((TextView) card.findViewById(R.id.urinth_news_title)).setText(title);
        ((TextView) card.findViewById(R.id.urinth_news_desc)).setText(description);
        ((TextView) card.findViewById(R.id.urinth_news_date)).setText(R.string.urinth_not_set_up);
    }

    /** Reads accounts, instances and activity off the UI thread, then updates the screen. */
    public void refresh() {
        if (released) return;
        PojavApplication.sExecutorService.execute(() -> {
            Account account = null;
            Account[] accounts = new Account[0];
            try {
                account = Accounts.getCurrent();
                accounts = Accounts.load().accounts.toArray(new Account[0]);
            } catch (Exception ignored) {
                // Account storage can be unavailable; the card then shows "Not signed in".
            }
            Bitmap face = null;
            if (account != null) {
                try {
                    face = account.getSkinFace();
                } catch (RuntimeException ignored) {
                    // Keep the default avatar.
                }
            }
            List<DisplayInstance> loaded = new ArrayList<>();
            try {
                loaded.addAll(Instances.loadDisplay().list);
            } catch (Exception ignored) {
                // Keep the screen usable if the instance folder is unavailable.
            }
            final Account currentResult = account;
            final Account[] accountsResult = accounts;
            final Bitmap faceResult = face;
            Tools.runOnUiThread(() -> {
                if (released) return;
                currentAccount = currentResult;
                savedAccounts = accountsResult;
                avatar = faceResult;
                instances = loaded;
                render();
            });
        });
    }

    private void render() {
        renderAccount();
        renderInstallations();
        renderPerformanceMode();
        renderLastPlayed();

        statusText.setText(instances.isEmpty() ? R.string.urinth_status_none : R.string.urinth_status_ready);
    }

    private void renderAccount() {
        if (currentAccount == null) {
            accountName.setText(R.string.urinth_account_guest);
            accountStatus.setText(R.string.urinth_account_choose);
            accountAvatar.setImageResource(R.drawable.urinth_home_block);
        } else {
            accountName.setText(currentAccount.username);
            accountStatus.setText(currentAccount.isMicrosoft
                    ? R.string.urinth_account_microsoft : R.string.urinth_account_local);
            if (avatar != null) accountAvatar.setImageBitmap(avatar);
            else accountAvatar.setImageResource(R.drawable.urinth_home_block);
        }
    }

    private void renderLastPlayed() {
        DisplayInstance target = pickPlayTarget();
        if (target == null) {
            lastPlayedName.setText(R.string.urinth_last_played);
        } else {
            String version = latestLabel(target.versionId);
            if (version == null) version = InstallationLabels.minecraftVersion(target.versionId);
            lastPlayedName.setText(version == null ? labelFor(target)
                    : context.getString(R.string.urinth_last_played_with_version, labelFor(target), version));
        }
    }

    private void renderInstallations() {
        installationsList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < instances.size(); i++) {
            DisplayInstance instance = instances.get(i);
            View row = inflater.inflate(R.layout.item_urinth_installation, installationsList, false);
            ((ImageView) row.findViewById(R.id.urinth_row_icon)).setImageDrawable(
                    InstanceIconProvider.fetchIcon(context.getResources(), instance));
            ((TextView) row.findViewById(R.id.urinth_row_name)).setText(labelFor(instance));
            String version = instance.versionId == null ? "" : instance.versionId;
            ((TextView) row.findViewById(R.id.urinth_row_version))
                    .setText(context.getString(R.string.urinth_row_version, version));
            // The hero Play button already starts this installation; the row's own Play button
            // was removed (item_urinth_installation.xml) so only Edit (the 3-dot menu) is left.
            row.findViewById(R.id.urinth_row_more).setOnClickListener(v -> openEditor(instance));
            row.setOnClickListener(v -> openEditor(instance));
            installationsList.addView(row);
        }
        installationsEmpty.setVisibility(instances.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Performance Mode is a plain preference; the game process reads it when it starts. */
    private void togglePerformanceMode() {
        boolean enabled = !LauncherPreferences.PREF_PERFORMANCE_MODE;
        LauncherPreferences.setPerformanceMode(enabled);
        renderPerformanceMode();
        perfPanel.setScaleX(0.97f);
        perfPanel.setScaleY(0.97f);
        perfPanel.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        Toast.makeText(context, enabled ? R.string.urinth_perf_toast_on : R.string.urinth_perf_toast_off,
                Toast.LENGTH_SHORT).show();
    }

    private void renderPerformanceMode() {
        boolean on = LauncherPreferences.PREF_PERFORMANCE_MODE;
        perfPanel.setSelected(on);
        perfButton.setSelected(on);
        perfChip.setSelected(on);
        perfState.setSelected(on);
        perfState.setText(on ? R.string.urinth_perf_state_on : R.string.urinth_perf_state_off);
        perfState.setTextColor(on ? 0xFF1FC77A : 0xFFABC0CB);
        perfButton.setText(on ? R.string.urinth_perf_button_on : R.string.urinth_perf_button_off);
        perfIcon.setAlpha(on ? 1f : 0.6f);
    }

    private String ageLabel(long now, long then) {
        HomeActivityLog.Age age = HomeActivityLog.age(now, then);
        switch (age.unit) {
            case NOW: return context.getString(R.string.urinth_activity_now);
            case MINUTES: return context.getString(R.string.urinth_activity_minutes, age.count);
            case HOURS: return context.getString(R.string.urinth_activity_hours, age.count);
            case DAYS: return context.getString(R.string.urinth_activity_days, age.count);
            default: return DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(then));
        }
    }

    /** The installation chosen for Play: the last one played if it still exists, otherwise the first. */
    private DisplayInstance pickPlayTarget() {
        if (instances.isEmpty()) return null;
        String remembered = HomeActivityLog.lastPlayedName(context);
        if (remembered != null) {
            // What is remembered is the installation's unique id; older builds remembered its
            // name, so a name still matches (the first one with that name).
            for (DisplayInstance instance : instances) {
                if (remembered.equals(instance.getId())) return instance;
            }
            for (DisplayInstance instance : instances) {
                if (remembered.equals(instance.name)) return instance;
            }
        }
        return instances.get(0);
    }

    private void launch(DisplayInstance instance) {
        if (instance == null) {
            toast(R.string.urinth_create_first);
            return;
        }
        Instances.setSelectedInstance(instance);
        HomeActivityLog.setLastPlayedName(context, rememberKey(instance));
        HomeActivityLog.record(context, HomeActivityLog.TYPE_PLAY,
                context.getString(R.string.urinth_played, labelFor(instance)));
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        renderLastPlayed();
    }

    private void openEditor(DisplayInstance instance) {
        Instances.setSelectedInstance(instance);
        if (host != null) {
            host.showCenterFragment(InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
        } else {
            Tools.swapFragment(activity, InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
        }
    }

    private void openSettings() {
        if (host != null) {
            host.showCenterFragment(LauncherPreferenceFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null);
        } else {
            toast(R.string.urinth_not_set_up);
        }
    }

    private void openBrowser(String category) {
        if (host == null) {
            toast(R.string.urinth_not_set_up);
            return;
        }
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ModrinthBrowserFragment.ARG_CATEGORY, category);
        args.putString(ModrinthBrowserFragment.ARG_QUERY, "");
        host.showCenterFragment(ModrinthBrowserFragment.class, ModrinthBrowserFragment.TAG, args);
    }

    /** "Latest release" / "Latest snapshot" for the two moving version ids, otherwise null. */
    private String latestLabel(String versionId) {
        if (Instance.VERSION_LATEST_RELEASE.equalsIgnoreCase(versionId))
            return context.getString(R.string.profiles_latest_release);
        if (Instance.VERSION_LATEST_SNAPSHOT.equalsIgnoreCase(versionId))
            return context.getString(R.string.profiles_latest_snapshot);
        return null;
    }

    private String rememberKey(DisplayInstance instance) {
        String id = instance.getId();
        return id != null ? id : instance.name;
    }

    /** Name shown for an installation; never blank, so none is ever left out of a list. */
    private String labelFor(DisplayInstance instance) {
        String versionName = instance.versionId;
        if (Instance.VERSION_LATEST_RELEASE.equalsIgnoreCase(versionName))
            versionName = context.getString(R.string.profiles_latest_release);
        else if (Instance.VERSION_LATEST_SNAPSHOT.equalsIgnoreCase(versionName))
            versionName = context.getString(R.string.profiles_latest_snapshot);
        return InstallationLabels.displayName(instance.name, versionName,
                context.getString(R.string.urinth_installation_unnamed));
    }

    /** Opens the chooser on a fresh read of the installations, so new installs are always in it. */
    private void showLastPlayedMenu(View anchor) {
        PojavApplication.sExecutorService.execute(() -> {
            List<DisplayInstance> loaded = new ArrayList<>();
            try {
                loaded.addAll(Instances.loadDisplay().list);
            } catch (Exception ignored) {
                loaded.addAll(instances);
            }
            Tools.runOnUiThread(() -> {
                if (released) return;
                instances = loaded;
                renderInstallations();
                renderLastPlayed();
                openInstallationChooser();
            });
        });
    }

    private void openInstallationChooser() {
        if (instances.isEmpty()) {
            toast(R.string.urinth_create_first);
            return;
        }
        AlertDialog dialog = themedDialog(R.string.urinth_last_played_choose);
        LinearLayout rows = themedDialogBody(dialog);
        DisplayInstance current = pickPlayTarget();
        // Two installations can share a name (two Fabric installs, say); number the later ones.
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (DisplayInstance instance : instances) {
            if (instance == null) continue;
            String title = labelFor(instance);
            String subtitle = instance.versionId == null || instance.versionId.trim().isEmpty()
                    ? context.getString(R.string.urinth_installation_setting_up)
                    : (latestLabel(instance.versionId) != null ? latestLabel(instance.versionId)
                            : InstallationLabels.subtitle(instance.versionId));
            String duplicateKey = title + "|" + subtitle;
            Integer count = seen.get(duplicateKey);
            seen.put(duplicateKey, count == null ? 1 : count + 1);
            if (count != null) title = title + " #" + (count + 1);
            addInstallationRow(rows, instance, title, subtitle, instance == current, v -> {
                HomeActivityLog.setLastPlayedName(context, rememberKey(instance));
                renderLastPlayed();
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    /** One installation in the chooser: loader icon, name, version underneath, tick when current. */
    private void addInstallationRow(LinearLayout container, DisplayInstance instance, String title,
                                    String subtitle, boolean highlighted, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(highlighted ? R.drawable.urinth_dialog_row_current_bg : R.drawable.urinth_dialog_row_bg);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dpToPx(2);
        rowParams.bottomMargin = dpToPx(2);
        row.setLayoutParams(rowParams);
        row.setOnClickListener(onClick);

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(InstanceIconProvider.fetchIcon(context.getResources(), instance));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28));
        iconParams.rightMargin = dpToPx(12);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(highlighted ? GREEN : 0xFFF2F8FA);
        name.setTextSize(14f);
        texts.addView(name);
        if (subtitle != null) {
            TextView version = new TextView(context);
            version.setText(subtitle);
            version.setSingleLine(true);
            version.setEllipsize(android.text.TextUtils.TruncateAt.END);
            version.setTextColor(0xFFABC0CB);
            version.setTextSize(11f);
            texts.addView(version);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (highlighted) {
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.ic_urinth_check);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16));
            checkParams.leftMargin = dpToPx(8);
            row.addView(check, checkParams);
        }
        container.addView(row);
    }

    /** Lets the player pick a saved account, delete one, or add one with the existing sign-in options. */
    public void showAccountPicker() {
        AlertDialog dialog = themedDialog(R.string.urinth_account_title);
        LinearLayout rows = themedDialogBody(dialog);
        for (Account account : savedAccounts) {
            boolean isCurrent = currentAccount != null && currentAccount.username.equals(account.username);
            addDialogRow(rows, account.username, isCurrent, v -> {
                Accounts.setCurrent(account);
                refresh();
                dialog.dismiss();
            }, v -> {
                dialog.dismiss();
                confirmDeleteAccount(account);
            });
        }
        addDialogRow(rows, context.getString(R.string.urinth_account_add), false, v -> {
            dialog.dismiss();
            showSignInOptions();
        }, null, R.drawable.ic_urinth_plus);
        dialog.show();
    }

    private void confirmDeleteAccount(Account account) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.urinth_account_delete_title)
                .setMessage(context.getString(R.string.urinth_account_delete_message, account.username))
                .setPositiveButton(R.string.urinth_account_delete_confirm, (d, w) -> {
                    Accounts.delete(account);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSignInOptions() {
        AlertDialog dialog = themedDialog(R.string.urinth_account_add);
        LinearLayout rows = themedDialogBody(dialog);
        addDialogRow(rows, "Microsoft", false, v -> {
            dialog.dismiss();
            Tools.swapFragment(activity, MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null);
        }, null);
        addDialogRow(rows, "Ely.by", false, v -> {
            dialog.dismiss();
            Tools.swapFragment(activity, ElyByLoginFragment.class, ElyByLoginFragment.TAG, null);
        }, null);
        addDialogRow(rows, "Local", false, v -> {
            dialog.dismiss();
            Tools.swapFragment(activity, LocalLoginFragment.class, LocalLoginFragment.TAG, null);
        }, null);
        dialog.show();
    }

    /**
     * Builds an otherwise-empty AlertDialog with the dark/green card chrome
     * (urinth_dialog_bg) instead of the plain system dialog look, and a title drawn in the
     * same style as the rest of the launcher.
     */
    private AlertDialog themedDialog(int titleRes) {
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextColor(0xFFF2F8FA);
        title.setTextSize(16f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        int pad = dpToPx(18);
        title.setPadding(pad, pad, pad, dpToPx(6));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(title)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.urinth_dialog_bg);
        return dialog;
    }

    /** Creates the scrollable row container for a themedDialog and attaches it as the dialog's body. */
    private LinearLayout themedDialogBody(AlertDialog dialog) {
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        int sidePad = dpToPx(10);
        rows.setPadding(sidePad, 0, sidePad, dpToPx(6));
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(rows, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.setView(scroll);
        return rows;
    }

    /** A single selectable row in a themed dialog, with an optional trailing delete (trash) icon. */
    private void addDialogRow(LinearLayout container, String text, boolean highlighted,
                               View.OnClickListener onClick, View.OnClickListener onDelete) {
        addDialogRow(container, text, highlighted, onClick, onDelete, 0);
    }

    private void addDialogRow(LinearLayout container, String text, boolean highlighted,
                               View.OnClickListener onClick, View.OnClickListener onDelete, int leadingIconRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(highlighted ? R.drawable.urinth_dialog_row_current_bg : R.drawable.urinth_dialog_row_bg);
        int vPad = dpToPx(12);
        int hPad = dpToPx(12);
        row.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dpToPx(2);
        rowParams.bottomMargin = dpToPx(2);
        row.setLayoutParams(rowParams);
        row.setOnClickListener(onClick);

        if (leadingIconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(leadingIconRes);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(18), dpToPx(18));
            iconParams.rightMargin = dpToPx(10);
            icon.setLayoutParams(iconParams);
            row.addView(icon);
        }

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(highlighted ? GREEN : 0xFFF2F8FA);
        label.setTextSize(14f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelParams);

        if (highlighted) {
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.ic_urinth_check);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16));
            checkParams.leftMargin = dpToPx(8);
            row.addView(check, checkParams);
        }

        if (onDelete != null) {
            ImageView trash = new ImageView(context);
            trash.setImageResource(R.drawable.ic_urinth_trash);
            trash.setBackgroundResource(R.drawable.urinth_dialog_row_bg);
            int trashPad = dpToPx(6);
            trash.setPadding(trashPad, trashPad, trashPad, trashPad);
            LinearLayout.LayoutParams trashParams = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30));
            trashParams.leftMargin = dpToPx(10);
            trash.setLayoutParams(trashParams);
            trash.setOnClickListener(onDelete);
            row.addView(trash);
        }

        container.addView(row);
    }

    private int dpToPx(float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Gives the green "Ultra" part of the UrinthUltra name. Both name labels start it at character 6. */
    private static void colourUltra(TextView view) {
        SpannableString text = new SpannableString(view.getText());
        text.setSpan(new ForegroundColorSpan(GREEN), 6, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
    }

    /**
     * Shows the real photo drawable with this name (for example urinth_photo_hero) when the project
     * has one in res/drawable. Without that file the view stays a plain dark panel, so no artwork is
     * drawn in its place.
     */
    private void setPhoto(ImageView view, String name) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id != 0) view.setImageResource(id);
        else view.setImageDrawable(null);
    }

    private void toast(int messageRes) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show();
    }
}
