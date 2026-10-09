package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.io.ByteArrayOutputStream;
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

    private static final int BG = Color.rgb(4, 20, 32);
    private static final int CARD = Color.rgb(8, 32, 47);
    private static final int TEXT = Color.rgb(245, 250, 252);
    private static final int MUTED = Color.rgb(171, 192, 203);
    private static final int ACCENT = Color.rgb(0, 230, 170);

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
            icon = string(json, "icon_url");
        }
    }

    private static class Version {
        String id = "", name = "", number = "", gameVersion = "", loader = "", url = "", filename = "", sha1 = "", type = "";
        final List<String> gameVersions = new ArrayList<>();
        final List<String> loaders = new ArrayList<>();
        long size;
        Version(JsonObject json) {
            id = string(json, "id");
            name = string(json, "name");
            number = string(json, "version_number");
            type = string(json, "version_type");
            JsonArray games = json.has("game_versions") && json.get("game_versions").isJsonArray()
                    ? json.getAsJsonArray("game_versions") : new JsonArray();
            for (int i = 0; i < games.size(); i++) gameVersions.add(games.get(i).getAsString());
            gameVersion = gameVersions.isEmpty() ? "" : gameVersions.get(0);
            JsonArray supportedLoaders = json.has("loaders") && json.get("loaders").isJsonArray()
                    ? json.getAsJsonArray("loaders") : new JsonArray();
            for (int i = 0; i < supportedLoaders.size(); i++) loaders.add(supportedLoaders.get(i).getAsString());
            loader = loaders.isEmpty() ? "" : loaders.get(0);
            JsonArray files = json.has("files") && json.get("files").isJsonArray()
                    ? json.getAsJsonArray("files") : new JsonArray();
            if (files.size() > 0) {
                JsonObject file = files.get(0).getAsJsonObject();
                for (int i = 0; i < files.size(); i++) {
                    JsonObject candidate = files.get(i).getAsJsonObject();
                    if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                        file = candidate;
                        break;
                    }
                }
                url = string(file, "url");
                filename = string(file, "filename");
                size = file.has("size") ? file.get("size").getAsLong() : 0;
                JsonObject hashes = file.has("hashes") && file.get("hashes").isJsonObject()
                        ? file.getAsJsonObject("hashes") : new JsonObject();
                sha1 = string(hashes, "sha1");
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
        GradientDrawable pageBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(6, 27, 42), Color.rgb(3, 17, 29)});
        root.setBackground(pageBackground);
        root.setPadding(dp(22), dp(20), dp(22), dp(14));

        TextView heading = new TextView(requireContext());
        heading.setText(categoryTitle());
        heading.setTextColor(TEXT);
        heading.setTextSize(26);
        heading.setLetterSpacing(-0.02f);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        View accentRule = new View(requireContext());
        accentRule.setBackground(rounded(ACCENT, 2, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(dp(54), dp(3));
        ruleParams.topMargin = dp(7);
        root.addView(accentRule, ruleParams);

        TextView sub = new TextView(requireContext());
        sub.setText("Browse real Modrinth projects. Install only versions compatible with your selected profile.");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(12);
        root.addView(sub, subParams);

        LinearLayout searchRow = new LinearLayout(requireContext());
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(8), dp(8), dp(8), dp(8));
        searchRow.setBackground(rounded(Color.rgb(8, 32, 46), 16, Color.rgb(25, 82, 101), 1));
        LinearLayout.LayoutParams searchRowParams = new LinearLayout.LayoutParams(-1, -2);
        searchRowParams.topMargin = dp(10);
        root.addView(searchRow, searchRowParams);
        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search " + categoryTitle().toLowerCase(Locale.ROOT));
        search.setBackground(rounded(Color.rgb(7, 31, 46), 12, Color.rgb(25, 94, 112), 1));
        search.setPadding(dp(14), 0, dp(14), 0);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button searchButton = button("Search");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(104), dp(44));
        buttonParams.leftMargin = dp(8);
        searchRow.addView(searchButton, buttonParams);
        searchButton.setOnClickListener(v -> loadProjects(search.getText().toString().trim()));
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO || enterPressed) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus();
                loadProjects(search.getText().toString().trim());
                return true;
            }
            return false;
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
        b.setBackground(rounded(ACCENT, 12, Color.rgb(80, 255, 213), 1));
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
                    if (!isAdded() || getView() == null || progress == null || status == null || resultList == null) return;
                    progress.setVisibility(View.GONE);
                    projects.addAll(found);
                    if (found.isEmpty()) status.setText("No projects found. Try another search.");
                    else status.setText(found.size() + " projects found");
                    for (Project project : found) {
                        if (!isAdded() || getView() == null || resultList == null) return;
                        addProjectCard(project);
                    }
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null || progress == null || status == null) return;
                    progress.setVisibility(View.GONE);
                    status.setText("Couldn't load Modrinth. Check your connection and try again.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private android.graphics.drawable.Drawable rounded(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private void loadProjectIcon(Project project, ImageView image) {
        if (project.icon == null || project.icon.isEmpty()) return;
        image.setTag(project.icon);
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new java.net.URL(project.icon).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "URinthLauncher/1.0 (Android)");
                try (InputStream in = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) Tools.runOnUiThread(() -> {
                        if (project.icon.equals(image.getTag())) image.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
                // Keep the branded placeholder when a project has no usable icon.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void addProjectCard(Project project) {
        android.content.Context context = getContext();
        if (!isAdded() || context == null || resultList == null) return;
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cardBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(11, 43, 59), Color.rgb(6, 27, 42)});
        cardBackground.setCornerRadius(dp(16));
        cardBackground.setStroke(dp(1), Color.rgb(27, 91, 110));
        card.setBackground(cardBackground);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        resultList.addView(card, params);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(rounded(Color.rgb(6, 57, 67), 14, Color.rgb(25, 126, 132), 1));
        icon.setClipToOutline(true);
        icon.setImageDrawable(rounded(Color.rgb(6, 57, 67), 14, Color.rgb(25, 126, 132), 1));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        iconParams.rightMargin = dp(12);
        card.addView(icon, iconParams);
        loadProjectIcon(project, icon);

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(context);
        title.setText(project.title);
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setMaxLines(2);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        details.addView(title);

        TextView description = new TextView(context);
        description.setText(project.description);
        description.setTextColor(MUTED);
        description.setTextSize(12);
        description.setMaxLines(3);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(4);
        details.addView(description, descParams);

        TextView metadata = new TextView(context);
        metadata.setText(categoryTitle() + "  ·  Modrinth");
        metadata.setTextColor(ACCENT);
        metadata.setTextSize(11);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(6);
        details.addView(metadata, metaParams);

        Button install = button(category.equals("modpack") ? "Choose version" : "Install");
        install.setTextSize(11);
        install.setMinHeight(dp(40));
        install.setElevation(dp(1));
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(dp(94), dp(40));
        installParams.leftMargin = dp(12);
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
                    body = readResponse(in);
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
        layout.setPadding(dp(18), dp(8), dp(18), dp(8));
        layout.setBackgroundColor(Color.rgb(6, 25, 38));

        LinearLayout projectHeader = new LinearLayout(requireContext());
        projectHeader.setGravity(Gravity.CENTER_VERTICAL);
        projectHeader.setPadding(0, 0, 0, dp(10));
        layout.addView(projectHeader);

        ImageView projectIcon = new ImageView(requireContext());
        projectIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        projectIcon.setBackground(rounded(Color.rgb(0, 70, 72), 10, ACCENT, 1));
        projectIcon.setClipToOutline(true);
        LinearLayout.LayoutParams projectIconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        projectIconParams.rightMargin = dp(12);
        projectHeader.addView(projectIcon, projectIconParams);
        loadProjectIcon(project, projectIcon);

        LinearLayout projectTitles = new LinearLayout(requireContext());
        projectTitles.setOrientation(LinearLayout.VERTICAL);
        projectHeader.addView(projectTitles, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView title = new TextView(requireContext());
        title.setText("Install " + project.title);
        title.setTextColor(TEXT);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        projectTitles.addView(title);
        TextView subtitle = new TextView(requireContext());
        subtitle.setText("Choose a profile and check version compatibility");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(11);
        projectTitles.addView(subtitle);

        TextView profileLabel = new TextView(requireContext());
        profileLabel.setText("TARGET PROFILE");
        profileLabel.setTextColor(ACCENT);
        profileLabel.setTextSize(10);
        profileLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams profileLabelParams = new LinearLayout.LayoutParams(-1, -2);
        profileLabelParams.topMargin = dp(4);
        layout.addView(profileLabel, profileLabelParams);

        Spinner profileSpinner = new Spinner(requireContext());
        List<String> profileLabels = new ArrayList<>();
        for (Instance instance : instances) {
            profileLabels.add((instance.name == null ? "Minecraft" : instance.name) + "  ·  " + instance.versionId);
        }
        profileSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, profileLabels));
        profileSpinner.setPopupBackgroundDrawable(rounded(Color.rgb(8, 35, 49), 10, Color.rgb(21, 99, 111), 1));
        profileSpinner.setBackground(rounded(Color.rgb(8, 40, 55), 10, Color.rgb(21, 99, 111), 1));
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(-1, dp(48));
        profileParams.topMargin = dp(5);
        profileParams.bottomMargin = dp(12);
        layout.addView(profileSpinner, profileParams);

        LinearLayout legend = new LinearLayout(requireContext());
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(0, 0, 0, dp(6));
        layout.addView(legend);
        TextView compatibleLegend = new TextView(requireContext());
        compatibleLegend.setText("● Compatible");
        compatibleLegend.setTextColor(Color.rgb(54, 232, 167));
        compatibleLegend.setTextSize(11);
        legend.addView(compatibleLegend);
        TextView incompatibleLegend = new TextView(requireContext());
        incompatibleLegend.setText("    ● Incompatible");
        incompatibleLegend.setTextColor(Color.rgb(255, 112, 122));
        incompatibleLegend.setTextSize(11);
        legend.addView(incompatibleLegend);

        ScrollView versionScroll = new ScrollView(requireContext());
        versionScroll.setFillViewport(false);
        LinearLayout versionList = new LinearLayout(requireContext());
        versionList.setOrientation(LinearLayout.VERTICAL);
        versionScroll.addView(versionList);
        layout.addView(versionScroll, new LinearLayout.LayoutParams(-1, dp(210)));

        TextView compatibility = new TextView(requireContext());
        compatibility.setTextColor(MUTED);
        compatibility.setTextSize(11);
        compatibility.setPadding(0, dp(8), 0, dp(2));
        layout.addView(compatibility);

        final Version[] selectedVersion = new Version[1];
        final Instance[] selectedInstance = new Instance[]{instances.get(0)};
        Runnable refreshVersions = () -> {
            int selectedPosition = Math.max(0, Math.min(profileSpinner.getSelectedItemPosition(), instances.size() - 1));
            Instance selected = instances.get(selectedPosition);
            selectedInstance[0] = selected;
            selectedVersion[0] = null;
            versionList.removeAllViews();
            List<Version> compatibleVersions = new ArrayList<>();
            List<Version> incompatibleVersions = new ArrayList<>();
            for (Version version : versions) {
                if (isCompatible(version, selected, category)) compatibleVersions.add(version);
                else incompatibleVersions.add(version);
            }
            int compatibleCount = compatibleVersions.size();
            // Keep rendering light on mobile: show the newest compatible releases first,
            // followed by a smaller sample of incompatible releases for clear comparison.
            List<Version> visibleVersions = new ArrayList<>();
            int compatibleLimit = Math.min(compatibleVersions.size(), 24);
            visibleVersions.addAll(compatibleVersions.subList(0, compatibleLimit));
            int incompatibleLimit = Math.min(incompatibleVersions.size(), compatibleCount == 0 ? 24 : 8);
            visibleVersions.addAll(incompatibleVersions.subList(0, incompatibleLimit));
            for (Version version : visibleVersions) {
                boolean matches = isCompatible(version, selected, category);
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(9), dp(12), dp(9));
                int fill = matches ? Color.rgb(12, 58, 49) : Color.rgb(61, 32, 39);
                int stroke = matches ? Color.rgb(25, 190, 137) : Color.rgb(191, 70, 83);
                row.setBackground(rounded(fill, 10, stroke, 1));
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = dp(7);
                versionList.addView(row, rowParams);

                TextView versionName = new TextView(requireContext());
                versionName.setText((matches ? "✓  " : "✕  ") + version.number + "  ·  MC " + version.gameVersion);
                versionName.setTextColor(matches ? Color.rgb(103, 255, 190) : Color.rgb(255, 142, 150));
                versionName.setTextSize(13);
                versionName.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(versionName);

                TextView versionMeta = new TextView(requireContext());
                versionMeta.setText("Loader: " + (version.loaders.isEmpty() ? "not specified" : android.text.TextUtils.join(", ", version.loaders)));
                versionMeta.setTextColor(matches ? Color.rgb(181, 232, 214) : Color.rgb(224, 175, 180));
                versionMeta.setTextSize(10);
                LinearLayout.LayoutParams versionMetaParams = new LinearLayout.LayoutParams(-1, -2);
                versionMetaParams.topMargin = dp(3);
                row.addView(versionMeta, versionMetaParams);

                TextView verdict = new TextView(requireContext());
                verdict.setText(matches ? "COMPATIBLE WITH THIS PROFILE" : compatibilityReason(version, selected, category));
                verdict.setTextColor(matches ? Color.rgb(103, 255, 190) : Color.rgb(255, 142, 150));
                verdict.setTextSize(9);
                verdict.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams verdictParams = new LinearLayout.LayoutParams(-1, -2);
                verdictParams.topMargin = dp(3);
                row.addView(verdict, verdictParams);

                if (matches) {
                    row.setOnClickListener(v -> {
                        selectedVersion[0] = version;
                        for (int i = 0; i < versionList.getChildCount(); i++) {
                            View child = versionList.getChildAt(i);
                            child.setAlpha(child == row ? 1f : 0.78f);
                        }
                        row.setBackground(rounded(Color.rgb(9, 82, 62), 10, ACCENT, 2));
                        compatibility.setText("Selected " + version.number + " for " + (selected.name == null ? "Minecraft" : selected.name));
                        compatibility.setTextColor(Color.rgb(103, 255, 190));
                    });
                } else {
                    row.setAlpha(0.86f);
                }
            }
            if (compatibleCount == 0) {
                compatibility.setText("No compatible release found for this profile. Pick another profile.");
                compatibility.setTextColor(Color.rgb(255, 142, 150));
            } else {
                compatibility.setText(compatibleCount + " compatible release(s) · " + selected.versionId + " · Tap a green release to select it.");
                compatibility.setTextColor(Color.rgb(103, 255, 190));
                // Compatible releases are listed first, so the default selection is the newest match.
                if (versionList.getChildCount() > 0) versionList.getChildAt(0).performClick();
            }
        };
        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refreshVersions.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (selectedVersion[0] == null) {
                    compatibility.setText("Select a green compatible release before installing.");
                    compatibility.setTextColor(Color.rgb(255, 142, 150));
                    return;
                }
                Version chosen = selectedVersion[0];
                Instance target = selectedInstance[0];
                dialog.dismiss();
                downloadIntoProfile(project, chosen, target);
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(Color.rgb(6, 25, 38), 18, Color.rgb(16, 113, 121), 1));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
            dialog.getWindow().setLayout(width, -2);
        }
        refreshVersions.run();
    }

    private String compatibilityReason(Version version, Instance instance, String type) {
        String game = instance.versionId == null ? "" : instance.versionId;
        boolean gameMatches = false;
        for (String supported : version.gameVersions) {
            if (game.equals(supported) || game.endsWith("-" + supported) || game.contains("-" + supported)) {
                gameMatches = true;
                break;
            }
        }
        if (!gameMatches) return "INCOMPATIBLE · Minecraft version mismatch";
        if ("resourcepack".equals(type) || "shader".equals(type) || "world".equals(type)) return "INCOMPATIBLE · Unsupported release";
        String loader = loaderFor(game);
        if (loader.isEmpty()) return "INCOMPATIBLE · Profile loader could not be identified";
        if (!version.loaders.contains(loader.toLowerCase(Locale.ROOT))) return "INCOMPATIBLE · Loader mismatch (" + loader + ")";
        return "INCOMPATIBLE · Unsupported release";
    }

        private boolean isCompatible(Version version, Instance instance, String type) {
        String game = instance.versionId == null ? "" : instance.versionId;
        boolean gameMatches = false;
        for (String supported : version.gameVersions) {
            if (game.equals(supported) || game.endsWith("-" + supported) || game.contains("-" + supported)) {
                gameMatches = true;
                break;
            }
        }
        if (!gameMatches) return false;
        if ("resourcepack".equals(type) || "shader".equals(type) || "world".equals(type)) return true;
        String loader = loaderFor(game);
        if (loader.isEmpty()) return false;
        for (String supportedLoader : version.loaders) {
            if (supportedLoader.equalsIgnoreCase(loader)) return true;
        }
        return false;
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
        Thread installThread = new Thread(() -> {
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
        }, "urinth-modrinth-install");
        installThread.setPriority(Thread.NORM_PRIORITY);
        installThread.start();
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
