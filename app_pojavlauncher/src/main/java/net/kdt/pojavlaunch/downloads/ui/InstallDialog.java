package net.kdt.pojavlaunch.downloads.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.downloads.DownloadCategory;
import net.kdt.pojavlaunch.downloads.Formatters;
import net.kdt.pojavlaunch.downloads.InstallPlan;
import net.kdt.pojavlaunch.downloads.InstallRunner;
import net.kdt.pojavlaunch.downloads.ModrinthClient;
import net.kdt.pojavlaunch.downloads.ProjectVersion;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The window opened by "Download": pick the installation, the release (and the world, for data
 * packs), then install. Everything it offers is checked against the installation first, so a
 * mod for the wrong loader or Minecraft version is never put into a profile.
 */
final class InstallDialog {
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ModrinthClient client = new ModrinthClient();
    private final DownloadCategory category;
    private final String projectId, title, description, iconUrl;
    private final ProjectVersion wanted;

    private Dialog dialog;
    private LinearLayout content;
    private List<ProfileChoice> profiles = Collections.emptyList();
    private int profileIndex;
    private List<File> worlds = Collections.emptyList();
    private int worldIndex;
    private List<ProjectVersion> versions = Collections.emptyList();
    private int versionIndex;
    private boolean withDependencies = true;
    private String problem;
    private boolean loading;
    private int loadToken;
    private volatile boolean cancelled;
    private boolean installing;
    private ModrinthApi packApi;
    private ModDetail packDetail;

    private InstallDialog(Activity activity, DownloadCategory category, String projectId, String title,
                          String description, String iconUrl, ProjectVersion wanted) {
        this.activity = activity;
        this.category = category;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.iconUrl = iconUrl;
        this.wanted = wanted;
    }

    /** @param wanted a release picked on the project's Versions tab, or null to offer the newest suitable one */
    static void show(Activity activity, DownloadCategory category, String projectId, String title,
                     String description, String iconUrl, ProjectVersion wanted) {
        InstallDialog install = new InstallDialog(activity, category, projectId, title, description, iconUrl, wanted);
        install.open();
    }

    private void open() {
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        dialog = Mr.dialog(activity, content);
        dialog.setOnDismissListener(d -> cancelled = true);
        dialog.show();
        if (category == DownloadCategory.MODPACKS) loadModpack();
        else loadProfiles();
    }

    private void ui(Runnable action) {
        main.post(() -> {
            if (activity.isFinishing() || dialog == null || !dialog.isShowing()) return;
            action.run();
        });
    }

    // ---- loading ----------------------------------------------------------------------------------

    private void loadProfiles() {
        loading = true;
        render();
        PojavApplication.sExecutorService.execute(() -> {
            List<ProfileChoice> loaded = Collections.emptyList();
            String error = null;
            try {
                loaded = ProfileChoice.load();
            } catch (IOException | RuntimeException e) {
                error = "Could not read your installations.";
            }
            final List<ProfileChoice> result = loaded;
            final String message = error;
            ui(() -> {
                profiles = result;
                problem = message;
                profileIndex = result.isEmpty() ? 0 : ProfileChoice.selectedIndex(result);
                afterProfileChosen();
            });
        });
    }

    private void afterProfileChosen() {
        if (profiles.isEmpty()) {
            loading = false;
            if (problem == null) problem = "You have no installation yet. Create one in Installations first.";
            render();
            return;
        }
        if (category == DownloadCategory.DATA_PACKS) loadWorlds();
        loadVersions();
    }

    private void loadWorlds() {
        File[] saves = new File(profiles.get(profileIndex).gameDirectory(), "saves").listFiles();
        List<File> found = new ArrayList<>();
        if (saves != null) {
            for (File save : saves) {
                if (save.isDirectory() && new File(save, "level.dat").isFile()) found.add(save);
            }
        }
        Collections.sort(found, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        worlds = found;
        worldIndex = 0;
    }

    private void loadVersions() {
        final int token = ++loadToken;
        loading = true;
        problem = null;
        versions = Collections.emptyList();
        render();
        final ProfileChoice profile = profiles.get(profileIndex);
        PojavApplication.sExecutorService.execute(() -> {
            List<ProjectVersion> found = new ArrayList<>();
            String error = null;
            try {
                String[] loaders = category == DownloadCategory.MODS ? profile.target.modLoaders() : null;
                String[] game = profile.target.gameVersion.isEmpty() ? null : new String[]{profile.target.gameVersion};
                for (ProjectVersion version : client.projectVersions(projectId, loaders, game)) {
                    if (category == DownloadCategory.PLUGINS || version.isCompatibleWith(profile.target, category)) found.add(version);
                }
            } catch (IOException e) {
                error = e.getMessage() == null ? "Could not load the releases from Modrinth." : e.getMessage();
            } catch (RuntimeException e) {
                error = "Modrinth sent an answer this launcher could not read.";
            }
            final List<ProjectVersion> result = found;
            final String message = error;
            ui(() -> {
                if (token != loadToken) return; // the player picked another installation meanwhile
                loading = false;
                versions = result;
                problem = message;
                versionIndex = 0;
                if (wanted != null) {
                    for (int i = 0; i < result.size(); i++) if (result.get(i).id.equals(wanted.id)) versionIndex = i;
                }
                if (wanted == null || !result.isEmpty() && !result.get(versionIndex).id.equals(wanted.id)) {
                    ProjectVersion best = InstallPlan.preferred(result);
                    versionIndex = best == null ? 0 : result.indexOf(best);
                }
                render();
            });
        });
    }

    private void loadModpack() {
        loading = true;
        render();
        PojavApplication.sExecutorService.execute(() -> {
            ModDetail detail = null;
            String error = null;
            try {
                ModrinthApi api = new ModrinthApi();
                detail = api.getModDetails(new ModItem(Constants.SOURCE_MODRINTH, true, projectId, title, description, iconUrl));
                packApi = api;
            } catch (RuntimeException e) {
                error = "Could not load the releases from Modrinth.";
            }
            final ModDetail result = detail;
            final String message = error;
            ui(() -> {
                loading = false;
                packDetail = result;
                if (result == null || result.versionNames.length == 0) {
                    problem = message != null ? message : "This modpack has no downloadable release.";
                }
                versionIndex = 0;
                if (result != null && wanted != null) {
                    for (int i = 0; i < result.versionNames.length; i++) {
                        String name = result.versionNames[i];
                        if (name != null && (name.equals(wanted.name) || name.equals(wanted.number))) versionIndex = i;
                    }
                }
                render();
            });
        });
    }

    // ---- the window -------------------------------------------------------------------------------

    private void render() {
        content.removeAllViews();
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        IconLoader.load(icon, iconUrl, Mr.dp(activity, 48) * 2, 10, Mr.round(activity, Mr.WHITE, 10));
        head.addView(icon, Mr.lp(activity, 48, 48));
        LinearLayout names = new LinearLayout(activity);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Mr.text(activity, "Install", 13, Mr.TEXT_SECOND, true));
        TextView name = Mr.text(activity, title, 20, Mr.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name);
        head.addView(names, Mr.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), activity, 12, 0, 0, 0));
        content.addView(head, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 0, 0, 0, 16));

        if (installing) return; // the progress view fills the window itself

        if (loading) {
            ProgressBar bar = new ProgressBar(activity);
            bar.setIndeterminate(true);
            bar.getIndeterminateDrawable().setColorFilter(Mr.BRAND, android.graphics.PorterDuff.Mode.SRC_IN);
            content.addView(bar, Mr.margins(Mr.lp(activity, 36, 36), activity, 0, 8, 0, 8));
            content.addView(Mr.text(activity, "Loading…", 15, Mr.TEXT_SECOND, true));
            return;
        }

        if (category != DownloadCategory.MODPACKS && !profiles.isEmpty()) {
            content.addView(label("Install to"));
            ProfileChoice profile = profiles.get(profileIndex);
            final LinearLayout profilePill = Mr.pillButton(activity, profile.title() + "  ·  " + profile.subtitle(), true);
            profilePill.setOnClickListener(v -> {
                List<String> names2 = new ArrayList<>();
                for (ProfileChoice choice : profiles) names2.add(choice.title() + "  ·  " + choice.subtitle());
                Mr.menu(profilePill, names2, profileIndex, index -> {
                    if (index == profileIndex) return;
                    profileIndex = index;
                    if (category == DownloadCategory.DATA_PACKS) loadWorlds();
                    loadVersions();
                });
            });
            content.addView(profilePill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(activity, 48)), activity, 0, 0, 0, 12));
        }

        if (category == DownloadCategory.DATA_PACKS && !profiles.isEmpty()) {
            content.addView(label("World"));
            if (worlds.isEmpty()) {
                content.addView(note("This installation has no world yet. Create a world in the game first, then come back."));
            } else {
                final LinearLayout worldPill = Mr.pillButton(activity, worlds.get(worldIndex).getName(), true);
                worldPill.setOnClickListener(v -> {
                    List<String> names2 = new ArrayList<>();
                    for (File world : worlds) names2.add(world.getName());
                    Mr.menu(worldPill, names2, worldIndex, index -> {
                        worldIndex = index;
                        render();
                    });
                });
                content.addView(worldPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(activity, 48)), activity, 0, 0, 0, 12));
            }
        }

        boolean canInstall = false;
        String warning = problem;
        if (category == DownloadCategory.MODPACKS) {
            if (packDetail != null && packDetail.versionNames.length > 0) {
                content.addView(label("Release"));
                final LinearLayout versionPill = Mr.pillButton(activity, packDetail.versionNames[versionIndex], true);
                versionPill.setOnClickListener(v -> Mr.menu(versionPill, Arrays.asList(packDetail.versionNames), versionIndex, index -> {
                    versionIndex = index;
                    render();
                }));
                content.addView(versionPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(activity, 48)), activity, 0, 0, 0, 12));
                content.addView(note("A modpack is installed as a new installation of its own, with its mods and game version. Your other installations are not touched."));
                canInstall = true;
            }
        } else if (!profiles.isEmpty()) {
            ProfileChoice profile = profiles.get(profileIndex);
            if (category == DownloadCategory.MODS && !profile.target.canRunMods()) {
                warning = "This installation has no mod loader (Fabric, Quilt, Forge or NeoForge), so it cannot run mods. Pick another installation or create a modded one.";
            } else if (versions.isEmpty() && warning == null) {
                warning = "No release of this project is made for " + profile.target.describe() + ". Pick another installation.";
            }
            if (!versions.isEmpty()) {
                content.addView(label("Release"));
                final LinearLayout versionPill = Mr.pillButton(activity, versionLabel(versions.get(versionIndex)), true);
                versionPill.setOnClickListener(v -> {
                    List<String> names2 = new ArrayList<>();
                    for (ProjectVersion version : versions) names2.add(versionLabel(version));
                    Mr.menu(versionPill, names2, versionIndex, index -> {
                        versionIndex = index;
                        render();
                    });
                });
                content.addView(versionPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(activity, 48)), activity, 0, 0, 0, 6));
                ProjectVersion chosen = versions.get(versionIndex);
                String detail = chosen.gameVersionText() + (chosen.size > 0 ? "  ·  " + Formatters.compactNumber(chosen.size / 1024) + " KB" : "");
                content.addView(note(detail), Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 4, 0, 0, 8));
                if (category == DownloadCategory.MODS) {
                    final TextView check = Mr.text(activity, (withDependencies ? "☑  " : "☐  ") + "Also add the mods it needs", 15, Mr.TEXT, true);
                    check.setPadding(0, Mr.dp(activity, 8), 0, Mr.dp(activity, 8));
                    check.setClickable(true);
                    check.setOnClickListener(v -> {
                        withDependencies = !withDependencies;
                        render();
                    });
                    content.addView(check);
                }
                canInstall = category != DownloadCategory.DATA_PACKS || !worlds.isEmpty();
                if (category == DownloadCategory.MODS && !profile.target.canRunMods()) canInstall = false;
                content.addView(note("Saved in: " + describeFolder(profile)));
            } else {
                canInstall = false;
            }
        }
        if (warning != null) {
            TextView warn = Mr.text(activity, warning, 14, Mr.DANGER, true);
            warn.setLineSpacing(0, 1.15f);
            warn.setPadding(0, Mr.dp(activity, 4), 0, Mr.dp(activity, 8));
            content.addView(warn);
        }

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END);
        TextView close = Mr.button(activity, "Cancel", false);
        close.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(close, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 0, 0, 10, 0));
        TextView install = Mr.button(activity, "Install", true);
        install.setAlpha(canInstall ? 1f : 0.4f);
        install.setEnabled(canInstall);
        install.setOnClickListener(v -> start());
        buttons.addView(install);
        content.addView(buttons, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 0, 10, 0, 0));
    }

    private TextView label(String text) {
        TextView view = Mr.text(activity, text, 14, Mr.TEXT_SECOND, true);
        view.setPadding(Mr.dp(activity, 2), 0, 0, Mr.dp(activity, 6));
        return view;
    }

    private TextView note(String text) {
        TextView view = Mr.text(activity, text, 14, Mr.TEXT_SECOND, false);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(0, 0, 0, Mr.dp(activity, 8));
        return view;
    }

    private static String versionLabel(ProjectVersion version) {
        String name = version.name.isEmpty() ? version.number : version.name;
        String kind = version.type.isEmpty() || "release".equals(version.type) ? "" : "  (" + version.type + ")";
        return name + kind;
    }

    private File folderFor(ProfileChoice profile) {
        if (category == DownloadCategory.DATA_PACKS) return new File(worlds.get(worldIndex), "datapacks");
        return new File(profile.gameDirectory(), category.installFolder);
    }

    private String describeFolder(ProfileChoice profile) {
        if (category == DownloadCategory.DATA_PACKS) {
            return worlds.isEmpty() ? "saves/…/datapacks" : "saves/" + worlds.get(worldIndex).getName() + "/datapacks";
        }
        return category.installFolder + " folder of " + profile.title();
    }

    // ---- installing -------------------------------------------------------------------------------

    private void start() {
        if (category == DownloadCategory.MODPACKS) {
            if (packApi == null || packDetail == null) return;
            packApi.handleModpackInstallation(activity.getApplicationContext(), packDetail, versionIndex);
            Toast.makeText(activity, "Installing the modpack. Progress shows at the bottom of the launcher.", Toast.LENGTH_LONG).show();
            dialog.dismiss();
            return;
        }
        final ProfileChoice profile = profiles.get(profileIndex);
        final ProjectVersion version = versions.get(versionIndex);
        final File folder = folderFor(profile);
        final boolean dependencies = withDependencies;
        installing = true;
        cancelled = false;
        dialog.setCancelable(false);
        render();
        final TextView status = Mr.text(activity, "Starting…", 15, Mr.TEXT, true);
        final ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.getProgressDrawable().setColorFilter(Mr.BRAND, android.graphics.PorterDuff.Mode.SRC_IN);
        final TextView cancel = Mr.button(activity, "Cancel", false);
        cancel.setOnClickListener(v -> {
            cancelled = true;
            cancel.setText("Stopping…");
        });
        content.addView(status, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 0, 0, 0, 10));
        content.addView(bar, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(activity, 10)), activity, 0, 0, 0, 16));
        content.addView(cancel, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        PojavApplication.sExecutorService.execute(() -> {
            InstallRunner.Outcome outcome = null;
            String error = null;
            try {
                outcome = InstallRunner.run(client, version, category, profile.target, folder, dependencies, new InstallRunner.Listener() {
                    @Override
                    public void onStatus(String text) {
                        ui(() -> status.setText(text));
                    }

                    @Override
                    public void onFileProgress(int index, int count, long done, long total) {
                        final int progress = total > 0 ? (int) (1000f * (index + Math.min(1f, (float) done / total)) / count) : 0;
                        ui(() -> bar.setProgress(progress));
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }
                });
            } catch (IOException e) {
                error = cancelled ? "Cancelled. Nothing was added." : (e.getMessage() == null ? "The install failed." : e.getMessage());
            } catch (RuntimeException e) {
                error = "The install failed: " + e;
            }
            final InstallRunner.Outcome result = outcome;
            final String message = error;
            main.post(() -> {
                if (activity.isFinishing()) return;
                installing = false;
                if (dialog != null && dialog.isShowing()) {
                    dialog.setCancelable(true);
                    showResult(profile, result, message);
                }
            });
        });
    }

    private void showResult(ProfileChoice profile, InstallRunner.Outcome outcome, String error) {
        content.removeAllViews();
        boolean ok = error == null && outcome != null;
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = Mr.icon(activity, ok ? R.drawable.ic_dl_check : R.drawable.ic_dl_close, ok ? Mr.BRAND : Mr.DANGER, 28);
        head.addView(mark);
        head.addView(Mr.text(activity, ok ? "Installed" : "Not installed", 20, ok ? Mr.BRAND : Mr.DANGER, true), Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 10, 0, 0, 0));
        content.addView(head, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 0, 0, 0, 12));
        StringBuilder text = new StringBuilder();
        if (ok) {
            if (outcome.main != null) text.append(outcome.main.getName()).append("\n");
            text.append("Added to ").append(profile.title()).append(".");
            if (outcome.dependenciesAdded > 0) text.append("\n").append(outcome.dependenciesAdded).append(outcome.dependenciesAdded == 1 ? " required mod was added too." : " required mods were added too.");
            if (!outcome.alreadyThere.isEmpty() && outcome.saved.isEmpty()) text.append("\nIt was already there, exactly the same file.");
            if (!outcome.replaced.isEmpty()) text.append("\nThe older release (").append(outcome.replaced.get(0).getName()).append(") was replaced.");
        } else {
            text.append(error == null ? "The install did not finish." : error);
        }
        content.addView(note(text.toString()));
        TextView done = Mr.button(activity, "Done", true);
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.END);
        row.addView(done);
        content.addView(row, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), activity, 0, 8, 0, 0));
        if (!ok && error != null && error.toLowerCase(Locale.ROOT).contains("cancel")) done.setText("Close");
    }
}
