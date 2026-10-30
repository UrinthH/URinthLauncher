package net.kdt.pojavlaunch.downloads.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloads.DownloadCategory;
import net.kdt.pojavlaunch.downloads.Formatters;
import net.kdt.pojavlaunch.downloads.MarkdownLite;
import net.kdt.pojavlaunch.downloads.ModrinthClient;
import net.kdt.pojavlaunch.downloads.ProjectInfo;
import net.kdt.pojavlaunch.downloads.ProjectVersion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One project, laid out like Modrinth's project page: icon, title, numbers, tags, a green
 * Download button, and the Description and Versions tabs. The page opens at once with what the
 * search card already knew; the long description and the releases are fetched next.
 */
public class ProjectFragment extends Fragment {
    public static final String TAG = "ProjectFragment";
    public static final String ARG_CATEGORY = "project_category";
    public static final String ARG_ID = "project_id";
    public static final String ARG_SLUG = "project_slug";
    public static final String ARG_TITLE = "project_title";
    public static final String ARG_AUTHOR = "project_author";
    public static final String ARG_DESCRIPTION = "project_description";
    public static final String ARG_ICON = "project_icon";
    public static final String ARG_ADDRESS = "project_address";
    public static final String ARG_MODIFIED = "project_modified";
    public static final String ARG_ENVIRONMENT = "project_environment";
    public static final String ARG_DOWNLOADS = "project_downloads";
    public static final String ARG_FOLLOWS = "project_follows";
    public static final String ARG_TAGS = "project_tags";

    private static final int TAB_DESCRIPTION = 0, TAB_VERSIONS = 1;

    private final ModrinthClient mClient = new ModrinthClient();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private DownloadCategory mCategory = DownloadCategory.MODS;
    private String mId = "", mSlug = "", mTitle = "", mAuthor = "", mDescription = "", mIcon = "";
    private String mAddress = "", mModified = "", mEnvironment = "";
    private long mDownloads, mFollows;
    private List<String> mTags = new ArrayList<>();

    private ProjectInfo mInfo;
    private String mInfoError;
    private boolean mInfoLoading;
    private List<ProjectVersion> mVersions;
    private String mVersionsError;
    private boolean mVersionsLoading;
    private int mTab = TAB_DESCRIPTION;
    private int mVisibleVersions = 15;

    private LinearLayout mBody;
    private LinearLayout mTabRow;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) return;
        mCategory = DownloadCategory.fromKey(args.getString(ARG_CATEGORY));
        mId = nonNull(args.getString(ARG_ID));
        mSlug = nonNull(args.getString(ARG_SLUG));
        mTitle = nonNull(args.getString(ARG_TITLE));
        mAuthor = nonNull(args.getString(ARG_AUTHOR));
        mDescription = nonNull(args.getString(ARG_DESCRIPTION));
        mIcon = nonNull(args.getString(ARG_ICON));
        mAddress = nonNull(args.getString(ARG_ADDRESS));
        mModified = nonNull(args.getString(ARG_MODIFIED));
        mEnvironment = nonNull(args.getString(ARG_ENVIRONMENT));
        mDownloads = args.getLong(ARG_DOWNLOADS);
        mFollows = args.getLong(ARG_FOLLOWS);
        ArrayList<String> tags = args.getStringArrayList(ARG_TAGS);
        if (tags != null) mTags = tags;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = inflater.getContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Mr.PAGE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = Mr.dp(context, 18);
        page.setPadding(side, Mr.dp(context, 14), side, Mr.dp(context, 28));
        scroll.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(backButton(context), Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Mr.dp(context, 40)), context, 0, 0, 0, 12));
        page.addView(headerCard(context), Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 14));

        mTabRow = new LinearLayout(context);
        mTabRow.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(mTabRow, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 12));

        mBody = new LinearLayout(context);
        mBody.setOrientation(LinearLayout.VERTICAL);
        page.addView(mBody, Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        renderTabs(context);
        renderBody(context);
        if (mInfo == null && !mInfoLoading) loadInfo();
        return scroll;
    }

    @Override
    public void onDestroyView() {
        mBody = null;
        mTabRow = null;
        super.onDestroyView();
    }

    // ---- header -----------------------------------------------------------------------------------

    private View backButton(Context context) {
        LinearLayout back = new LinearLayout(context);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(Mr.dp(context, 10), 0, Mr.dp(context, 16), 0);
        Mr.background(back, Mr.WHITE, 20);
        back.setClickable(true);
        back.addView(Mr.icon(context, R.drawable.ic_dl_back, Mr.TEXT, 20));
        back.addView(Mr.text(context, mCategory.label, 15, Mr.TEXT, true),
                Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
        back.setOnClickListener(v -> Tools.removeCurrentFragment(requireActivity()));
        return back;
    }

    private View headerCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Mr.dp(context, 16), Mr.dp(context, 16), Mr.dp(context, 16), Mr.dp(context, 16));
        card.setBackground(Mr.round(context, Mr.CARD, 20));

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        IconLoader.load(icon, mIcon, Mr.dp(context, 88) * 2, 18, Mr.round(context, Mr.WHITE, 18));
        top.addView(icon, Mr.lp(context, 88, 88));

        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView title = Mr.text(context, mTitle, 24, Mr.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(title);
        if (!mAuthor.isEmpty()) {
            names.addView(Mr.text(context, "by " + mAuthor, 15, Mr.TEXT_SECOND, false),
                    Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 4, 0, 0));
        }
        top.addView(names, Mr.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), context, 14, 0, 0, 0));
        card.addView(top);

        if (!mDescription.isEmpty()) {
            TextView description = Mr.text(context, mDescription, 16, Mr.TEXT, false);
            description.setLineSpacing(0, 1.18f);
            card.addView(description, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 14, 0, 0));
        }

        LinearLayout stats = new LinearLayout(context);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(Mr.icon(context, R.drawable.ic_dl_download, Mr.TEXT_SECOND, 18));
        stats.addView(Mr.text(context, Formatters.compactNumber(mDownloads) + " downloads", 15, Mr.TEXT_SECOND, true),
                Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 18, 0));
        stats.addView(Mr.icon(context, R.drawable.ic_dl_heart, Mr.TEXT_SECOND, 18));
        stats.addView(Mr.text(context, Formatters.compactNumber(mFollows) + " followers", 15, Mr.TEXT_SECOND, true),
                Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
        card.addView(stats, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 14, 0, 0));

        LinearLayout tags = new LinearLayout(context);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView tagScroll = new android.widget.HorizontalScrollView(context);
        tagScroll.setHorizontalScrollBarEnabled(false);
        tagScroll.addView(tags);
        boolean any = false;
        if (mCategory == DownloadCategory.SERVERS && !mAddress.isEmpty()) {
            tags.addView(DownloadsFragment.tagChip(context, R.drawable.ic_dl_globe, true, mAddress), tagParams(context));
            any = true;
        }
        if (mCategory.hasEnvironmentFilter && !mEnvironment.isEmpty()) {
            tags.addView(DownloadsFragment.tagChip(context, R.drawable.ic_dl_globe, true, mEnvironment), tagParams(context));
            any = true;
        }
        for (String tag : mTags) {
            int logo = DownloadsFragment.loaderLogo(tag);
            tags.addView(DownloadsFragment.tagChip(context, logo != 0 ? logo : R.drawable.ic_dl_tag, logo == 0, Formatters.tagLabel(tag)), tagParams(context));
            any = true;
        }
        if (any) card.addView(tagScroll, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 12, 0, 0));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView primary;
        if (mCategory == DownloadCategory.SERVERS) {
            primary = Mr.button(context, mAddress.isEmpty() ? "No address listed" : "Copy address", true);
            primary.setEnabled(!mAddress.isEmpty());
            primary.setAlpha(mAddress.isEmpty() ? 0.4f : 1f);
            primary.setOnClickListener(v -> copyAddress());
        } else {
            primary = Mr.button(context, "Download", true);
            primary.setCompoundDrawablePadding(Mr.dp(context, 8));
            primary.setOnClickListener(v -> startInstall(null));
        }
        actions.addView(primary, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 10, 0));
        TextView website = Mr.button(context, "Open on Modrinth", false);
        website.setOnClickListener(v -> openWebsite());
        actions.addView(website);
        card.addView(actions, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 16, 0, 0));
        return card;
    }

    private static LinearLayout.LayoutParams tagParams(Context context) {
        return Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 8, 0);
    }

    private void copyAddress() {
        Context context = getContext();
        if (context == null || mAddress.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Server address", mAddress));
        Toast.makeText(context, "Copied " + mAddress, Toast.LENGTH_SHORT).show();
    }

    private void openWebsite() {
        String type;
        switch (mCategory) {
            case RESOURCE_PACKS: type = "resourcepack"; break;
            case DATA_PACKS: type = "datapack"; break;
            case SHADERS: type = "shader"; break;
            case MODPACKS: type = "modpack"; break;
            case PLUGINS: type = "plugin"; break;
            case SERVERS: type = "server"; break;
            default: type = "mod";
        }
        String key = !mSlug.isEmpty() ? mSlug : mId;
        openLink("https://modrinth.com/" + type + "/" + key);
    }

    private void openLink(String url) {
        Context context = getContext();
        if (context == null || !MarkdownLite.isSafeLink(url)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startInstall(@Nullable ProjectVersion version) {
        if (getActivity() == null) return;
        String description = mInfo != null && !mInfo.description.isEmpty() ? mInfo.description : mDescription;
        InstallDialog.show(requireActivity(), mCategory, mId, mTitle, description, mIcon, version);
    }

    // ---- tabs -------------------------------------------------------------------------------------

    private void renderTabs(Context context) {
        mTabRow.removeAllViews();
        if (mCategory == DownloadCategory.SERVERS) return; // a server has a description only
        String[] labels = {"Description", "Versions"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView chip = Mr.chip(context, labels[i], mTab == i);
            chip.setOnClickListener(v -> {
                if (mTab == tab) return;
                mTab = tab;
                Context c = getContext();
                if (c == null || mBody == null) return;
                renderTabs(c);
                renderBody(c);
                if (tab == TAB_VERSIONS && mVersions == null && !mVersionsLoading) loadVersions();
            });
            mTabRow.addView(chip, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 8, 0));
        }
    }

    private void renderBody(Context context) {
        if (mBody == null) return;
        mBody.removeAllViews();
        if (mTab == TAB_VERSIONS && mCategory != DownloadCategory.SERVERS) {
            renderVersions(context);
        } else {
            renderDescription(context);
        }
    }

    // ---- description ------------------------------------------------------------------------------

    private void loadInfo() {
        mInfoLoading = true;
        mInfoError = null;
        final String id = mId;
        PojavApplication.sExecutorService.execute(() -> {
            ProjectInfo info = null;
            String error = null;
            try {
                info = mClient.project(id);
            } catch (IOException e) {
                error = "Could not load the full description from Modrinth.";
            } catch (RuntimeException e) {
                error = "Modrinth sent an answer this launcher could not read.";
            }
            final ProjectInfo result = info;
            final String message = error;
            mHandler.post(() -> {
                mInfoLoading = false;
                mInfo = result;
                mInfoError = message;
                if (result != null) {
                    if (result.downloads > 0) mDownloads = result.downloads;
                    if (result.followers > 0) mFollows = result.followers;
                }
                Context context = getContext();
                if (context != null && mBody != null && mTab == TAB_DESCRIPTION) renderBody(context);
            });
        });
    }

    private void renderDescription(Context context) {
        if (mInfoLoading && mInfo == null) {
            mBody.addView(loadingRow(context, "Loading the description…"));
            return;
        }
        if (mInfo == null) {
            mBody.addView(errorRow(context, mInfoError == null ? "No description available." : mInfoError, () -> {
                loadInfo();
                renderBody(context);
            }));
            return;
        }
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Mr.dp(context, 18), Mr.dp(context, 16), Mr.dp(context, 18), Mr.dp(context, 16));
        card.setBackground(Mr.round(context, Mr.CARD, 20));
        List<MarkdownLite.Block> blocks = MarkdownLite.parse(mInfo.body);
        if (blocks.isEmpty()) {
            card.addView(Mr.text(context, "The author has not written a description.", 15, Mr.TEXT_SECOND, false));
        }
        for (MarkdownLite.Block block : blocks) card.addView(blockView(context, block));
        mBody.addView(card, Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addLinks(context);
    }

    private void addLinks(Context context) {
        if (mInfo == null) return;
        List<String[]> links = new ArrayList<>();
        if (!mInfo.sourceUrl.isEmpty()) links.add(new String[]{"Source code", mInfo.sourceUrl});
        if (!mInfo.issuesUrl.isEmpty()) links.add(new String[]{"Report issues", mInfo.issuesUrl});
        if (!mInfo.wikiUrl.isEmpty()) links.add(new String[]{"Wiki", mInfo.wikiUrl});
        if (!mInfo.discordUrl.isEmpty()) links.add(new String[]{"Discord", mInfo.discordUrl});
        if (links.isEmpty() && mInfo.licenseName.isEmpty()) return;
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Mr.dp(context, 18), Mr.dp(context, 16), Mr.dp(context, 18), Mr.dp(context, 12));
        card.setBackground(Mr.round(context, Mr.CARD, 20));
        card.addView(Mr.text(context, "Links and details", 17, Mr.TEXT, true));
        if (!mInfo.licenseName.isEmpty()) {
            card.addView(Mr.text(context, "License: " + mInfo.licenseName, 15, Mr.TEXT_SECOND, false),
                    Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 10, 0, 0));
        }
        if (!mInfo.updated.isEmpty()) {
            card.addView(Mr.text(context, "Updated " + Formatters.relativeTime(mInfo.updated, System.currentTimeMillis()), 15, Mr.TEXT_SECOND, false),
                    Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 6, 0, 0));
        }
        for (final String[] link : links) {
            TextView row = Mr.text(context, link[0], 15, Mr.BRAND, true);
            row.setPadding(0, Mr.dp(context, 10), 0, Mr.dp(context, 6));
            row.setClickable(true);
            row.setOnClickListener(v -> openLink(link[1]));
            card.addView(row);
        }
        mBody.addView(card, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 14, 0, 0));
    }

    private View blockView(Context context, MarkdownLite.Block block) {
        LinearLayout.LayoutParams params = Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 10);
        if (block.kind == MarkdownLite.Kind.RULE) {
            View rule = new View(context);
            rule.setBackgroundColor(Mr.LINE);
            LinearLayout.LayoutParams ruleParams = Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(context, 1)), context, 0, 6, 0, 12);
            rule.setLayoutParams(ruleParams);
            return rule;
        }
        String prefix = "";
        float size = 15.5f;
        boolean bold = false;
        int color = Mr.TEXT;
        int indent = 0;
        switch (block.kind) {
            case H1: size = 22; bold = true; params.topMargin = Mr.dp(context, 8); break;
            case H2: size = 19; bold = true; params.topMargin = Mr.dp(context, 6); break;
            case H3: size = 17; bold = true; params.topMargin = Mr.dp(context, 4); break;
            case BULLET: prefix = "•  "; indent = 6 + block.level * 18; break;
            case NUMBERED: prefix = block.number + ".  "; indent = 6 + block.level * 18; break;
            case QUOTE: color = Mr.TEXT_SECOND; indent = 12; break;
            case CODE: size = 13.5f; break;
            default: break;
        }
        SpannableString text = new SpannableString(prefix + block.text);
        int shift = prefix.length();
        for (final MarkdownLite.Span span : block.spans) {
            int start = Math.max(0, Math.min(text.length(), span.start + shift));
            int end = Math.max(start, Math.min(text.length(), span.end + shift));
            if (start == end) continue;
            switch (span.type) {
                case 'b': text.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case 'i': text.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case 's': text.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case 'c':
                    text.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new BackgroundColorSpan(0xFFE4E4E4), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case 'l':
                    if (span.url != null && MarkdownLite.isSafeLink(span.url)) {
                        text.setSpan(new ClickableSpan() {
                            @Override
                            public void onClick(@NonNull View widget) {
                                openLink(span.url);
                            }

                            @Override
                            public void updateDrawState(@NonNull TextPaint paint) {
                                paint.setColor(Mr.BRAND);
                                paint.setUnderlineText(true);
                            }
                        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    break;
                default: break;
            }
        }
        TextView view = Mr.text(context, text, size, color, bold);
        view.setLineSpacing(0, 1.2f);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setPadding(Mr.dp(context, indent), 0, 0, 0);
        if (block.kind == MarkdownLite.Kind.CODE) {
            view.setTypeface(Typeface.MONOSPACE);
            view.setBackground(Mr.round(context, 0xFFE4E4E4, 10));
            view.setPadding(Mr.dp(context, 12), Mr.dp(context, 10), Mr.dp(context, 12), Mr.dp(context, 10));
        } else if (block.kind == MarkdownLite.Kind.QUOTE) {
            view.setBackground(Mr.round(context, Color.TRANSPARENT, 0));
            view.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        }
        view.setLayoutParams(params);
        return view;
    }

    // ---- versions ---------------------------------------------------------------------------------

    private void loadVersions() {
        mVersionsLoading = true;
        mVersionsError = null;
        final String id = mId;
        Context context = getContext();
        if (context != null && mBody != null && mTab == TAB_VERSIONS) renderBody(context);
        PojavApplication.sExecutorService.execute(() -> {
            List<ProjectVersion> found = null;
            String error = null;
            try {
                found = mClient.projectVersions(id, null, null);
            } catch (IOException e) {
                error = "Could not load the releases from Modrinth.";
            } catch (RuntimeException e) {
                error = "Modrinth sent an answer this launcher could not read.";
            }
            final List<ProjectVersion> result = found;
            final String message = error;
            mHandler.post(() -> {
                mVersionsLoading = false;
                mVersions = result;
                mVersionsError = message;
                Context c = getContext();
                if (c != null && mBody != null && mTab == TAB_VERSIONS) renderBody(c);
            });
        });
    }

    private void renderVersions(Context context) {
        if (mVersions == null && !mVersionsLoading && mVersionsError == null) {
            loadVersions();
            return;
        }
        if (mVersionsLoading) {
            mBody.addView(loadingRow(context, "Loading releases…"));
            return;
        }
        if (mVersions == null) {
            mBody.addView(errorRow(context, mVersionsError == null ? "No releases found." : mVersionsError, this::loadVersions));
            return;
        }
        if (mVersions.isEmpty()) {
            mBody.addView(Mr.text(context, "This project has no downloadable release yet.", 15, Mr.TEXT_SECOND, false));
            return;
        }
        long now = System.currentTimeMillis();
        int shown = Math.min(mVisibleVersions, mVersions.size());
        for (int i = 0; i < shown; i++) {
            final ProjectVersion version = mVersions.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Mr.dp(context, 16), Mr.dp(context, 14), Mr.dp(context, 12), Mr.dp(context, 14));
            row.setBackground(Mr.round(context, Mr.CARD, 18));

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            String name = version.name.isEmpty() ? version.number : version.name;
            TextView title = Mr.text(context, name, 16, Mr.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(title);
            StringBuilder sub = new StringBuilder();
            if (!version.type.isEmpty() && !"release".equals(version.type)) sub.append(version.type).append("  ·  ");
            for (String loader : version.loaders) sub.append(Formatters.tagLabel(loader)).append(" ");
            if (!version.loaders.isEmpty()) sub.append(" ·  ");
            sub.append(version.gameVersionText());
            if (!version.datePublished.isEmpty()) sub.append("  ·  ").append(Formatters.relativeTime(version.datePublished, now));
            TextView details = Mr.text(context, sub.toString(), 13.5f, Mr.TEXT_SECOND, false);
            details.setLineSpacing(0, 1.15f);
            info.addView(details, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 4, 0, 0));
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView get = Mr.button(context, "Download", true);
            get.setPadding(Mr.dp(context, 16), Mr.dp(context, 9), Mr.dp(context, 16), Mr.dp(context, 9));
            get.setOnClickListener(v -> startInstall(version));
            row.addView(get, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 10, 0, 0, 0));
            mBody.addView(row, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 8));
        }
        if (shown < mVersions.size()) {
            TextView more = Mr.button(context, "Show more (" + (mVersions.size() - shown) + " left)", false);
            more.setOnClickListener(v -> {
                mVisibleVersions += 15;
                Context c = getContext();
                if (c != null && mBody != null) renderBody(c);
            });
            mBody.addView(more, Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // ---- status rows ------------------------------------------------------------------------------

    private View loadingRow(Context context, String text) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, Mr.dp(context, 28), 0, Mr.dp(context, 28));
        ProgressBar bar = new ProgressBar(context);
        bar.setIndeterminate(true);
        bar.getIndeterminateDrawable().setColorFilter(Mr.BRAND, android.graphics.PorterDuff.Mode.SRC_IN);
        row.addView(bar, Mr.lp(context, 28, 28));
        row.addView(Mr.text(context, text, 15, Mr.TEXT_SECOND, true),
                Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 12, 0, 0, 0));
        return row;
    }

    private View errorRow(Context context, String message, Runnable retry) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(0, Mr.dp(context, 24), 0, Mr.dp(context, 24));
        row.addView(Mr.text(context, message, 15, Mr.TEXT_SECOND, true));
        TextView again = Mr.button(context, "Try again", true);
        again.setOnClickListener(v -> retry.run());
        row.addView(again, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 14, 0, 0));
        return row;
    }
}
