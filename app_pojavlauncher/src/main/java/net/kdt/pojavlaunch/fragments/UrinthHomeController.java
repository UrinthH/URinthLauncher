package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
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
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;

import java.io.IOException;
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
    private static final int MAX_HOME_INSTALLATIONS = 5;
    private static final int MAX_HOME_ACTIVITY = 4;
    private static final int GREEN = 0xFF1FC77A;

    private final FragmentActivity activity;
    private final MainMenuFragment host;
    private final Context context;

    private final View sidebar;
    private final ScrollView homeScroll;
    private final TextView statusText;
    private final TextView playButton;
    private final TextView lastPlayedName;
    private final TextView accountName;
    private final TextView accountStatus;
    private final ImageView accountAvatar;
    private final TextView versionsText;
    private final LinearLayout installationsList;
    private final TextView installationsEmpty;
    private final LinearLayout activityList;
    private final TextView activityEmpty;

    private List<DisplayInstance> instances = new ArrayList<>();
    private Account currentAccount;
    private Account[] savedAccounts = new Account[0];
    private Bitmap avatar;
    private boolean released;

    private final ExtraListener<Boolean> accountRefreshListener = (key, value) -> {
        refresh();
        return false;
    };

    public UrinthHomeController(FragmentActivity activity, MainMenuFragment host, View root) {
        this.activity = activity;
        this.host = host;
        this.context = root.getContext();

        sidebar = root.findViewById(R.id.urinth_sidebar);
        homeScroll = root.findViewById(R.id.urinth_home_scroll);
        statusText = root.findViewById(R.id.urinth_status_text);
        playButton = root.findViewById(R.id.urinth_play);
        lastPlayedName = root.findViewById(R.id.urinth_last_played_name);
        accountName = root.findViewById(R.id.urinth_account_name);
        accountStatus = root.findViewById(R.id.urinth_account_status);
        accountAvatar = root.findViewById(R.id.urinth_account_avatar);
        versionsText = root.findViewById(R.id.urinth_versions_text);
        installationsList = root.findViewById(R.id.urinth_installations_list);
        installationsEmpty = root.findViewById(R.id.urinth_installations_empty);
        activityList = root.findViewById(R.id.urinth_activity_list);
        activityEmpty = root.findViewById(R.id.urinth_activity_empty);

        colourUltra(root.findViewById(R.id.urinth_brand));
        colourUltra(root.findViewById(R.id.urinth_hero_brand));
        setPhoto(root.findViewById(R.id.urinth_hero_image), "urinth_photo_hero");
        setPhoto(root.findViewById(R.id.urinth_banner_image), "urinth_photo_banner");

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
    }

    private void bindActions(View root) {
        root.findViewById(R.id.urinth_menu_btn).setOnClickListener(v ->
                sidebar.setVisibility(sidebar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        View.OnClickListener installations = v -> openSelectedInstallation();
        root.findViewById(R.id.urinth_btn_folder).setOnClickListener(installations);
        root.findViewById(R.id.urinth_nav_installations).setOnClickListener(installations);

        View.OnClickListener accounts = v -> showAccountPicker();
        root.findViewById(R.id.urinth_btn_people).setOnClickListener(accounts);
        root.findViewById(R.id.urinth_nav_skins).setOnClickListener(accounts);
        root.findViewById(R.id.urinth_account_card).setOnClickListener(accounts);

        View.OnClickListener newInstallation = v -> createVanillaInstallation();
        root.findViewById(R.id.urinth_btn_download).setOnClickListener(newInstallation);
        root.findViewById(R.id.urinth_versions_row).setOnClickListener(newInstallation);

        View.OnClickListener settings = v -> openSettings();
        root.findViewById(R.id.urinth_btn_settings).setOnClickListener(settings);
        root.findViewById(R.id.urinth_nav_settings).setOnClickListener(settings);

        root.findViewById(R.id.urinth_btn_more).setOnClickListener(this::showMoreMenu);

        root.findViewById(R.id.urinth_nav_home).setOnClickListener(v -> homeScroll.smoothScrollTo(0, 0));
        root.findViewById(R.id.urinth_nav_news).setOnClickListener(v -> toast(R.string.urinth_news_not_set_up));
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
        renderActivity();
        renderLastPlayed();

        statusText.setText(instances.isEmpty() ? R.string.urinth_status_none : R.string.urinth_status_ready);
        versionsText.setText(instances.isEmpty()
                ? context.getString(R.string.urinth_versions_none)
                : context.getString(R.string.urinth_versions_count, instances.size()));
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
        lastPlayedName.setText(target == null ? context.getString(R.string.urinth_last_played) : target.name);
    }

    private void renderInstallations() {
        installationsList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        int shown = Math.min(instances.size(), MAX_HOME_INSTALLATIONS);
        for (int i = 0; i < shown; i++) {
            DisplayInstance instance = instances.get(i);
            View row = inflater.inflate(R.layout.item_urinth_installation, installationsList, false);
            ((TextView) row.findViewById(R.id.urinth_row_name)).setText(instance.name);
            String version = instance.versionId == null ? "" : instance.versionId;
            ((TextView) row.findViewById(R.id.urinth_row_version))
                    .setText(context.getString(R.string.urinth_row_version, version));
            row.findViewById(R.id.urinth_row_play).setOnClickListener(v -> launch(instance));
            row.findViewById(R.id.urinth_row_more).setOnClickListener(v -> openEditor(instance));
            row.setOnClickListener(v -> openEditor(instance));
            installationsList.addView(row);
        }
        installationsEmpty.setVisibility(instances.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderActivity() {
        activityList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        long now = System.currentTimeMillis();
        List<HomeActivityLog.Entry> entries = HomeActivityLog.recent(context, MAX_HOME_ACTIVITY);
        for (HomeActivityLog.Entry entry : entries) {
            View row = inflater.inflate(R.layout.item_urinth_activity, activityList, false);
            ImageView icon = row.findViewById(R.id.urinth_activity_icon);
            if (HomeActivityLog.TYPE_PLAY.equals(entry.type)) icon.setImageResource(R.drawable.ic_urinth_play);
            else if (HomeActivityLog.TYPE_INSTALL.equals(entry.type)) icon.setImageResource(R.drawable.ic_urinth_cube);
            else icon.setImageResource(R.drawable.ic_px_verify_hash);
            ((TextView) row.findViewById(R.id.urinth_activity_text)).setText(entry.text);
            ((TextView) row.findViewById(R.id.urinth_activity_age)).setText(ageLabel(now, entry.timeMillis));
            activityList.addView(row);
        }
        activityEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
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
        HomeActivityLog.setLastPlayedName(context, instance.name);
        HomeActivityLog.record(context, HomeActivityLog.TYPE_PLAY,
                context.getString(R.string.urinth_played, instance.name));
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        renderLastPlayed();
        renderActivity();
    }

    private void openSelectedInstallation() {
        DisplayInstance target = pickPlayTarget();
        if (target == null) toast(R.string.urinth_create_first);
        else openEditor(target);
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

    private void createVanillaInstallation() {
        VersionSelectorDialog.open(context, false, (id, snapshot) -> {
            try {
                Instances.createInstance(instance -> {
                    instance.name = "Vanilla " + id;
                    instance.versionId = id;
                    instance.sharedData = true;
                }, "Vanilla");
                HomeActivityLog.record(context, HomeActivityLog.TYPE_INSTALL,
                        context.getString(R.string.urinth_installed, "Vanilla " + id));
                refresh();
            } catch (IOException e) {
                Toast.makeText(context, "Could not create installation: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 0, R.string.urinth_menu_new);
        menu.getMenu().add(0, 2, 1, R.string.urinth_menu_settings);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) createVanillaInstallation();
            else if (item.getItemId() == 2) openSettings();
            return true;
        });
        menu.show();
    }

    private void showLastPlayedMenu(View anchor) {
        if (instances.isEmpty()) {
            toast(R.string.urinth_create_first);
            return;
        }
        PopupMenu menu = new PopupMenu(context, anchor);
        for (int i = 0; i < instances.size(); i++) {
            menu.getMenu().add(0, i, i, instances.get(i).name);
        }
        menu.setOnMenuItemClickListener(item -> {
            DisplayInstance chosen = instances.get(item.getItemId());
            HomeActivityLog.setLastPlayedName(context, chosen.name);
            renderLastPlayed();
            return true;
        });
        menu.show();
    }

    /** Lets the player pick a saved account, or add one with the existing sign-in options. */
    public void showAccountPicker() {
        List<String> labels = new ArrayList<>();
        for (Account account : savedAccounts) labels.add(account.username);
        labels.add(context.getString(R.string.urinth_account_add));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.urinth_account_title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which < savedAccounts.length) {
                        Accounts.setCurrent(savedAccounts[which]);
                        refresh();
                    } else {
                        showSignInOptions();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSignInOptions() {
        String[] options = {"Microsoft", "Ely.by", "Local"};
        new AlertDialog.Builder(activity)
                .setTitle(R.string.urinth_account_add)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) Tools.swapFragment(activity, MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null);
                    else if (which == 1) Tools.swapFragment(activity, ElyByLoginFragment.class, ElyByLoginFragment.TAG, null);
                    else Tools.swapFragment(activity, LocalLoginFragment.class, LocalLoginFragment.TAG, null);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
