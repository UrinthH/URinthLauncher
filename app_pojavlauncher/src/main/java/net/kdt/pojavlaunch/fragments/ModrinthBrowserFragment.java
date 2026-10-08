package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModrinthBrowserFragment extends Fragment {
    public static final String TAG = "ModrinthBrowserFragment";
    public static final String ARG_CATEGORY = "modrinth_category";

    private static final int BG = Color.rgb(4, 24, 38);
    private static final int CARD = Color.rgb(7, 37, 54);
    private static final int TEXT = Color.rgb(242, 248, 250);
    private static final int MUTED = Color.rgb(163, 184, 195);
    private static final int ACCENT = Color.rgb(0, 225, 180);

    private String category = "mod";
    private String searchQuery = "";
    private LinearLayout resultList;
    private ProgressBar progress;
    private TextView status;
    private EditText search;
    private final List<Project> projects = new ArrayList<>();

    private static class Project {
        String id, title, description, icon;
        Project(JsonObject json) {
            id = json.has("project_id") ? json.get("project_id").getAsString() : "";
            title = json.has("title") ? json.get("title").getAsString() : "Unknown project";
            description = json.has("description") ? json.get("description").getAsString() : "";
            icon = json.has("icon_url") ? json.get("icon_url").getAsString() : "";
        }
    }

    private static class Version {
        String id = "", name = "", number = "", gameVersion = "", loader = "", url = "", filename = "", sha1 = "", type = "";
        long size;
        Version(JsonObject json) {
            id = string(json, "id");
            name = string(json, "name");
            number = string(json, "version_number");
            type = string(json, "version_type");
            JsonArray games = json.has("game_versions") ? json.getAsJsonArray("game_versions") : new JsonArray();
            gameVersion = games.size() > 0 ? games.get(0).getAsString() : "";
            JsonArray loaders = json.has("loaders") ? json.getAsJsonArray("loaders") : new JsonArray();
            loader = loaders.size() > 0 ? loaders.get(0).getAsString() : "";
            JsonArray files = json.has("files") ? json.getAsJsonArray("files") : new JsonArray();
            if (files.size() > 0) {
                JsonObject file = files.get(0).getAsJsonObject();
                for (int i=0; i<files.size(); i++) {
                    JsonObject candidate = files.get(i).getAsJsonObject();
                    if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                        file = candidate;
                        break;
                    }
                }
                url = string(file, "url");
                filename = string(file, "filename");
                size = file.has("size") ? file.get("size").getAsLong() : 0;
                JsonObject hashes = file.has("hashes") ? file.getAsJsonObject("hashes") : new JsonObject();
                sha1 = hashes.has("sha1") ? hashes.get("sha1").getAsString() : "";
            }
        }
    }

    private static String readResponse(InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString("UTF-8");
    }

    private static String sha1(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) category = getArguments().getString(ARG_CATEGORY, "mod");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(16), dp(18), dp(10));

        TextView heading = new TextView(requireContext());
        heading.setText(categoryTitle());
        heading.setTextColor(TEXT);
        heading.setTextSize(23);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(requireContext());
        sub.setText("Browse real Modrinth projects. Install only versions compatible with your selected profile.");
        sub.setTextColor(MUTED);
        sub.setTextSize(12);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(12);
        root.addView(sub, subParams);

        LinearLayout searchRow = new LinearLayout(requireContext());
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search " + categoryTitle().toLowerCase(Locale.ROOT));
        search.setBackgroundColor(CARD);
        search.setPadding(dp(12), 0, dp(12), 0);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button searchButton = button("Search");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(90), dp(44));
        buttonParams.leftMargin = dp(8);
        searchRow.addView(searchButton, buttonParams);
        root.addView(searchRow);
        searchButton.setOnClickListener(v -> loadProjects(search.getText().toString().trim()));
        search.setOnEditorActionListener((v, actionId, event) -> {
            loadProjects(search.getText().toString().trim());
            return true;
        });

        progress = new ProgressBar(requireContext());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(12);
        root.addView(progress, progressParams);
        status = new TextView(requireContext());
        status.setTextColor(MUTED);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(8);
        root.addView(status, statusParams);

        ScrollView scroll = new ScrollView(requireContext());
        resultList = new LinearLayout(requireContext());
        resultList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(10);
        scroll.addView(resultList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        if ("world".equals(category)) {
            status.setText("Modrinth does not expose Worlds as a separate project type. Search Modpacks for downloadable map packs, or use the launcher's existing world import workflow.");
            progress.setVisibility(View.GONE);
        } else {
            loadProjects("");
        }
        return root;
    }

    private String categoryTitle() {
        switch (category) {
            case "resourcepack": return "Resource Packs";
            case "modpack": return "Modpacks";
            case "shader": return "Shaders";
            case "world": return "Worlds";
            default: return "Mods";
        }
    }

    private Button button(String label) {
        Button b = new Button(requireContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(0, 34, 36));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        b.setTextSize(12);
        return b;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadProjects(String query) {
        searchQuery = query;
        progress.setVisibility(View.VISIBLE);
        status.setText("Searching Modrinth…");
        resultList.removeAllViews();
        projects.clear();
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String type = category.equals("world") ? "modpack" : category;
                String facets = "[[\"project_type:" + type + "\"]]";
                String url = "https://api.modrinth.com/v2/search?query="
                        + URLEncoder.encode(query, "UTF-8")
                        + "&facets=" + URLEncoder.encode(facets, "UTF-8")
                        + "&limit=30&index=relevance";
                connection = (HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "URinthLauncher/1.0 (Android)");
                String body;
                try (InputStream in = connection.getInputStream()) {
                    body = readResponse(in);
                }
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonArray hits = json.getAsJsonArray("hits");
                List<Project> found = new ArrayList<>();
                for (int i=0; i<hits.size(); i++) found.add(new Project(hits.get(i).getAsJsonObject()));
                Tools.runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    projects.addAll(found);
                    if (found.isEmpty()) status.setText("No projects found. Try another search.");
                    else status.setText(found.size() + " projects found");
                    for (Project project : found) addProjectCard(project);
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("Couldn't load Modrinth. Check your connection and try again.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void addProjectCard(Project project) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackgroundColor(CARD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        resultList.addView(card, params);

        TextView title = new TextView(requireContext());
        title.setText(project.title);
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);
        TextView description = new TextView(requireContext());
        description.setText(project.description);
        description.setTextColor(MUTED);
        description.setTextSize(12);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(4);
        card.addView(description, descriptionParams);
        Button install = button(category.equals("modpack") ? "Choose version" : "Install");
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(dp(140), dp(40));
        installParams.gravity = Gravity.RIGHT;
        installParams.topMargin = dp(8);
        card.addView(install, installParams);
        card.setOnClickListener(v -> openProject(project));
        install.setOnClickListener(v -> openProject(project));
    }

    private void openProject(Project project) {
        status.setText("Loading compatible versions for " + project.title + "…");
        progress.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String url = "https://api.modrinth.com/v2/project/" + project.id + "/version";
                connection = (HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "URinthLauncher/1.0 (Android)");
                String body;
                try (InputStream in = connection.getInputStream()) {
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                JsonArray json = JsonParser.parseString(body).getAsJsonArray();
                List<Version> versions = new ArrayList<>();
                for (int i=0; i<json.size(); i++) {
                    Version version = new Version(json.get(i).getAsJsonObject());
                    if (!version.url.isEmpty()) versions.add(version);
                }
                Tools.runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (versions.isEmpty()) {
                        status.setText("No downloadable versions were returned for this project.");
                    } else {
                        status.setText(project.title + " — select a version and target profile.");
                        showInstallDialog(project, versions);
                    }
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("Couldn't load versions for this project.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showInstallDialog(Project project, List<Version> versions) {
        if ("modpack".equals(category)) {
            showModpackVersionDialog(project, versions);
            return;
        }
        List<Instance> instances;
        try {
            instances = Instances.loadAllInstances();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Couldn't load your instances.", Toast.LENGTH_LONG).show();
            return;
        }
        if (instances.isEmpty()) {
            Toast.makeText(requireContext(), "Create a Minecraft instance first.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView info = new TextView(requireContext());
        info.setText("Choose the Minecraft profile and a compatible project version.");
        info.setTextColor(MUTED);
        layout.addView(info);

        Spinner profileSpinner = new Spinner(requireContext());
        List<String> profileLabels = new ArrayList<>();
        for (Instance instance : instances) {
            profileLabels.add((instance.name == null ? "Minecraft" : instance.name) + " — " + instance.versionId);
        }
        profileSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, profileLabels));
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(-1, dp(48));
        spinParams.topMargin = dp(8);
        layout.addView(profileSpinner, spinParams);

        Spinner versionSpinner = new Spinner(requireContext());
        List<Version> compatible = new ArrayList<>();
        ArrayList<String> versionLabels = new ArrayList<>();
        Instance initial = instances.get(0);
        for (Version version : versions) {
            if (isCompatible(version, initial, category)) {
                compatible.add(version);
                versionLabels.add(version.number + " · MC " + version.gameVersion + " · " + version.loader);
            }
        }
        if (compatible.isEmpty()) {
            compatible.addAll(versions);
            for (Version version : versions) versionLabels.add(version.number + " · MC " + version.gameVersion + " · " + version.loader);
        }
        ArrayAdapter<String> versionAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, versionLabels);
        versionSpinner.setAdapter(versionAdapter);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, dp(48));
        versionParams.topMargin = dp(8);
        layout.addView(versionSpinner, versionParams);
        TextView compatibility = new TextView(requireContext());
        compatibility.setTextColor(MUTED);
        compatibility.setTextSize(11);
        LinearLayout.LayoutParams compatibilityParams = new LinearLayout.LayoutParams(-1, -2);
        compatibilityParams.topMargin = dp(6);
        layout.addView(compatibility, compatibilityParams);

        Runnable refreshVersions = () -> {
            Instance selected = instances.get(profileSpinner.getSelectedItemPosition());
            compatible.clear();
            versionLabels.clear();
            for (Version version : versions) {
                if (isCompatible(version, selected, category)) {
                    compatible.add(version);
                    versionLabels.add(version.number + " · MC " + version.gameVersion + " · " + version.loader);
                }
            }
            versionAdapter.notifyDataSetChanged();
            if (compatible.isEmpty()) {
                compatibility.setText("No matching versions for this profile. Choose a profile with a supported Minecraft version and loader.");
            } else {
                compatibility.setText(compatible.size() + " compatible version(s) for " + selected.versionId);
                versionSpinner.setSelection(0);
            }
        };
        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refreshVersions.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        refreshVersions.run();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Install " + project.title)
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (compatible.isEmpty()) {
                compatibility.setText("No compatible version is available for the selected profile.");
                return;
            }
            Instance target = instances.get(profileSpinner.getSelectedItemPosition());
            Version selected = compatible.get(Math.min(versionSpinner.getSelectedItemPosition(), compatible.size()-1));
            dialog.dismiss();
            downloadIntoProfile(project, selected, target);
        }));
        dialog.show();
    }

    private boolean isCompatible(Version version, Instance instance, String type) {
        String game = instance.versionId == null ? "" : instance.versionId;
        if (!game.equals(version.gameVersion) && !game.endsWith("-" + version.gameVersion) && !game.contains(version.gameVersion)) return false;
        if ("resourcepack".equals(type) || "shader".equals(type) || "world".equals(type)) return true;
        String loader = loaderFor(game);
        return loader.isEmpty() || version.loader.equalsIgnoreCase(loader);
    }

    private String loaderFor(String versionId) {
        String value = versionId == null ? "" : versionId.toLowerCase(Locale.ROOT);
        if (value.startsWith("fabric-loader-")) return "fabric";
        if (value.startsWith("quilt-loader-")) return "quilt";
        if (value.startsWith("neoforge-")) return "neoforge";
        if (value.startsWith("forge-") || value.contains("forge")) return "forge";
        if (value.startsWith("optifine")) return "optifine";
        return "";
    }

    private void downloadIntoProfile(Project project, Version version, Instance instance) {
        ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setTitle("Installing " + project.title);
        dialog.setMessage("Downloading " + version.filename + "…");
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.show();
        PojavApplication.sExecutorService.execute(() -> {
            File destination = null;
            try {
                File gameDir = instance.getGameDirectory();
                String folder = "mods";
                if ("resourcepack".equals(category)) folder = "resourcepacks";
                else if ("shader".equals(category)) folder = "shaderpacks";
                else if ("world".equals(category)) folder = "saves";
                File targetDir = new File(gameDir, folder);
                if (!targetDir.exists() && !targetDir.mkdirs()) throw new java.io.IOException("Couldn't create " + folder + " folder");
                String filename = version.filename;
                if (filename.isEmpty()) filename = project.title.replaceAll("[^A-Za-z0-9._-]", "_") + ".jar";
                destination = new File(targetDir, filename);
                HttpURLConnection connection = (HttpURLConnection) new java.net.URL(version.url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "URinthLauncher/1.0 (Android)");
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    out.flush();
                } finally {
                    connection.disconnect();
                }
                if (!version.sha1.isEmpty()) {
                    String actual = sha1(destination);
                    if (!actual.equalsIgnoreCase(version.sha1)) {
                        destination.delete();
                        throw new java.io.IOException("Downloaded file failed its SHA-1 check.");
                    }
                }
                File finalDestination = destination;
                Tools.runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Installed to " + finalDestination.getParentFile().getName() + "/", Toast.LENGTH_LONG).show();
                    status.setText(project.title + " installed into " + (instance.name == null ? "selected profile" : instance.name));
                });
            } catch (Exception e) {
                if (destination != null && destination.exists()) destination.delete();
                Tools.runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(requireContext()).setTitle("Install failed")
                            .setMessage(e.getMessage() == null ? "The download couldn't be completed." : e.getMessage())
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void showModpackVersionDialog(Project project, List<Version> versions) {
        List<String> labels = new ArrayList<>();
        for (Version version : versions) labels.add(version.number + " · MC " + version.gameVersion + " · " + version.loader);
        Spinner spinner = new Spinner(requireContext());
        spinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels));
        new AlertDialog.Builder(requireContext())
                .setTitle("Install modpack " + project.title)
                .setMessage("Choose a pack version. Modpacks are installed as their own launcher instance.")
                .setView(spinner)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install", (dialog, which) -> {
                    List<String> names = new ArrayList<>();
                    List<String> games = new ArrayList<>();
                    List<String> urls = new ArrayList<>();
                    List<String> hashes = new ArrayList<>();
                    for (Version version : versions) {
                        names.add(version.number);
                        games.add(version.gameVersion);
                        urls.add(version.url);
                        hashes.add(version.sha1.isEmpty() ? null : version.sha1);
                    }
                    ModItem item = new ModItem(Constants.SOURCE_MODRINTH, true, project.id, project.title, project.description, project.icon);
                    ModDetail detail = new ModDetail(item, names.toArray(new String[0]), games.toArray(new String[0]), urls.toArray(new String[0]), hashes.toArray(new String[0]));
                    PojavApplication.sExecutorService.execute(() -> {
                        try {
                            new ModrinthApi().installModpack(detail, spinner.getSelectedItemPosition());
                            Tools.runOnUiThread(() -> Toast.makeText(requireContext(), "Modpack installation started", Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            Tools.runOnUiThread(() -> new AlertDialog.Builder(requireContext()).setTitle("Install failed").setMessage(e.getMessage()).setPositiveButton("OK", null).show());
                        }
                    });
                }).show();
    }
}
