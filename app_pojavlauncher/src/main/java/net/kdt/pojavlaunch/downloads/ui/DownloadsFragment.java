package net.kdt.pojavlaunch.downloads.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloads.DownloadCategory;
import net.kdt.pojavlaunch.downloads.Formatters;
import net.kdt.pojavlaunch.downloads.ModrinthClient;
import net.kdt.pojavlaunch.downloads.Pagination;
import net.kdt.pojavlaunch.downloads.ProjectHit;
import net.kdt.pojavlaunch.downloads.SearchPage;
import net.kdt.pojavlaunch.downloads.SearchQuery;
import net.kdt.pojavlaunch.downloads.SortOrder;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Downloads page: Modrinth's own layout (tabs, search, sort, view size, filter, page bar and
 * project cards with their real icons) in the launcher. Nothing here is a placeholder: every
 * control changes the search that is sent to Modrinth.
 */
public class DownloadsFragment extends Fragment {
    public static final String TAG = "DownloadsFragment";
    public static final String ARG_CATEGORY = "downloads_category";

    private static final long SEARCH_DELAY_MS = 450;
    private static final int[] VIEW_SIZES = {5, 10, 15, 20, 50, 100};
    private static final String PREF_VIEW_SIZE = "downloadsViewSize";
    private static final String PREF_GRID = "downloadsGrid";
    private static final String PREF_SORT = "downloadsSort";

    private static final int TYPE_HEADER = 0, TYPE_PAGER = 1, TYPE_STATUS = 2, TYPE_CARD = 3, TYPE_CARD_GRID = 4;

    private static List<String> sGameVersions;

    private final ModrinthClient mClient = new ModrinthClient();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SearchQuery mQuery = new SearchQuery(DownloadCategory.MODS);
    private SearchQuery mShownQuery = mQuery;
    private SearchPage mPage;
    private String mError;
    private boolean mLoading;
    private int mRequest;
    private boolean mGrid;
    private boolean mStarted;
    private List<ProfileChoice> mProfiles;

    private RecyclerView mRecycler;
    private GridLayoutManager mLayout;
    private ResultAdapter mAdapter;
    private LinearLayout mHeader;
    private LinearLayout mTabRow;
    private EditText mSearchBox;
    private View mSearchClear;
    private LinearLayout mSortPill, mViewPill, mFilterPill;
    private ImageView mGridButton;
    private TextView mFilterSummary;
    private final List<Object> mRows = new ArrayList<>();
    private final Object mHeaderRow = new Object(), mPagerRow = new Object(), mStatusRow = new Object();
    private final Runnable mSearchTask = () -> {
        mQuery.page = 0;
        search(false);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = inflater.getContext();
        if (!mStarted) {
            mStarted = true;
            SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
            if (prefs != null) {
                int size = prefs.getInt(PREF_VIEW_SIZE, 20);
                mQuery.pageSize = isViewSize(size) ? size : 20;
                mGrid = prefs.getBoolean(PREF_GRID, false);
                for (SortOrder order : SortOrder.values()) {
                    if (order.index.equals(prefs.getString(PREF_SORT, ""))) mQuery.sort = order;
                }
            }
            Bundle args = getArguments();
            if (args != null) switchCategory(DownloadCategory.fromKey(args.getString(ARG_CATEGORY)), false);
        }
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Mr.PAGE);
        mRecycler = new RecyclerView(context);
        mRecycler.setClipToPadding(false);
        int side = Mr.dp(context, 18);
        mRecycler.setPadding(side, Mr.dp(context, 14), side, Mr.dp(context, 24));
        mRecycler.setItemAnimator(null);
        mRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mLayout = new GridLayoutManager(context, 2);
        mLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mAdapter != null && mAdapter.getItemViewType(position) == TYPE_CARD_GRID ? 1 : 2;
            }
        });
        mRecycler.setLayoutManager(mLayout);
        mAdapter = new ResultAdapter();
        mRecycler.setAdapter(mAdapter);
        root.addView(mRecycler, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildHeader(context);
        rebuildRows(true);
        if (mPage == null && !mLoading) search(false);
        return root;
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mSearchTask);
        mRecycler = null;
        mLayout = null;
        mAdapter = null;
        mHeader = null;
        mTabRow = null;
        mSearchBox = null;
        mSearchClear = null;
        mSortPill = mViewPill = mFilterPill = null;
        mGridButton = null;
        mFilterSummary = null;
        super.onDestroyView();
    }

    // ---- header: tabs, search, sort / view / filter ---------------------------------------------

    private void buildHeader(Context context) {
        mHeader = new LinearLayout(context);
        mHeader.setOrientation(LinearLayout.VERTICAL);
        mHeader.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView tabScroll = new HorizontalScrollView(context);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setClipToPadding(false);
        tabScroll.setPadding(0, 0, 0, Mr.dp(context, 6));
        mTabRow = new LinearLayout(context);
        mTabRow.setOrientation(LinearLayout.HORIZONTAL);
        mTabRow.setGravity(Gravity.CENTER_VERTICAL);
        mTabRow.setPadding(Mr.dp(context, 6), Mr.dp(context, 6), Mr.dp(context, 6), Mr.dp(context, 6));
        mTabRow.setBackground(Mr.round(context, Mr.WHITE, 30));
        mTabRow.setElevation(Mr.dp(context, 2));
        for (final DownloadCategory category : DownloadCategory.values()) {
            TextView tab = Mr.text(context, category.label, 16, Mr.TEXT, true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(Mr.dp(context, 20), Mr.dp(context, 11), Mr.dp(context, 20), Mr.dp(context, 11));
            tab.setTag(category);
            tab.setClickable(true);
            tab.setOnClickListener(v -> {
                if (category != mQuery.category) switchCategory(category, true);
            });
            mTabRow.addView(tab, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        tabScroll.addView(mTabRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mHeader.addView(tabScroll, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 10));

        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Mr.dp(context, 18), 0, Mr.dp(context, 10), 0);
        searchRow.setBackground(Mr.round(context, Mr.WHITE, 28));
        searchRow.setElevation(Mr.dp(context, 2));
        searchRow.addView(Mr.icon(context, R.drawable.ic_dl_search, Mr.TEXT_MUTED, 22));
        mSearchBox = new EditText(context);
        mSearchBox.setBackground(null);
        mSearchBox.setSingleLine(true);
        mSearchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        mSearchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mSearchBox.setTextColor(Mr.TEXT);
        mSearchBox.setHintTextColor(Mr.TEXT_MUTED);
        mSearchBox.setTextSize(17);
        mSearchBox.setPadding(Mr.dp(context, 12), 0, Mr.dp(context, 8), 0);
        mSearchBox.setText(mQuery.text);
        mSearchBox.setHint("Search " + mQuery.category.label.toLowerCase(java.util.Locale.ROOT) + "…");
        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (mSearchClear != null) mSearchClear.setVisibility(text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                if (text.equals(mQuery.text)) return;
                mQuery.text = text;
                mHandler.removeCallbacks(mSearchTask);
                mHandler.postDelayed(mSearchTask, SEARCH_DELAY_MS);
            }
        });
        mSearchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mHandler.removeCallbacks(mSearchTask);
                mQuery.page = 0;
                search(false);
                InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        searchRow.addView(mSearchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageView clear = Mr.icon(context, R.drawable.ic_dl_close, Mr.TEXT_MUTED, 20);
        clear.setPadding(Mr.dp(context, 8), Mr.dp(context, 8), Mr.dp(context, 8), Mr.dp(context, 8));
        clear.setLayoutParams(Mr.lp(context, 40, 40));
        clear.setVisibility(mQuery.text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        clear.setOnClickListener(v -> mSearchBox.setText(""));
        mSearchClear = clear;
        searchRow.addView(clear);
        mHeader.addView(searchRow, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(context, 54)), context, 0, 0, 0, 12));

        HorizontalScrollView controlScroll = new HorizontalScrollView(context);
        controlScroll.setHorizontalScrollBarEnabled(false);
        controlScroll.setClipToPadding(false);
        controlScroll.setPadding(0, Mr.dp(context, 2), 0, Mr.dp(context, 8));
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        mSortPill = Mr.pillButton(context, "", true);
        mSortPill.setOnClickListener(v -> pickSort());
        controls.addView(mSortPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Mr.dp(context, 48)), context, 0, 0, 10, 0));
        mViewPill = Mr.pillButton(context, "", true);
        mViewPill.setOnClickListener(v -> pickViewSize());
        controls.addView(mViewPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Mr.dp(context, 48)), context, 0, 0, 10, 0));
        mFilterPill = Mr.pillButton(context, "Filter results…", false);
        mFilterPill.setOnClickListener(v -> showFilter());
        mFilterPill.addView(Mr.icon(context, R.drawable.ic_dl_filter, Mr.TEXT_SECOND, 18), Mr.margins(Mr.lp(context, 18, 18), context, 8, 0, 0, 0));
        controls.addView(mFilterPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Mr.dp(context, 48)), context, 0, 0, 10, 0));
        FrameLayout gridHolder = new FrameLayout(context);
        mGridButton = Mr.icon(context, R.drawable.ic_dl_list, Mr.TEXT, 22);
        mGridButton.setLayoutParams(new FrameLayout.LayoutParams(Mr.dp(context, 22), Mr.dp(context, 22), Gravity.CENTER));
        gridHolder.addView(mGridButton);
        gridHolder.setElevation(Mr.dp(context, 2));
        gridHolder.setClickable(true);
        gridHolder.setOnClickListener(v -> {
            mGrid = !mGrid;
            saveView();
            styleControls();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
        });
        gridHolder.setTag("grid");
        controls.addView(gridHolder, Mr.lp(context, 48, 48));
        controlScroll.addView(controls, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mHeader.addView(controlScroll, Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mFilterSummary = Mr.text(context, "", 14, Mr.TEXT_SECOND, true);
        mFilterSummary.setPadding(Mr.dp(context, 6), Mr.dp(context, 4), Mr.dp(context, 6), Mr.dp(context, 8));
        mFilterSummary.setClickable(true);
        mFilterSummary.setOnClickListener(v -> {
            mQuery.gameVersion = null;
            mQuery.loader = null;
            mQuery.environment = null;
            mQuery.page = 0;
            styleControls();
            search(false);
        });
        mHeader.addView(mFilterSummary, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        styleTabs();
        styleControls();
    }

    private void styleTabs() {
        if (mTabRow == null) return;
        Context context = mTabRow.getContext();
        for (int i = 0; i < mTabRow.getChildCount(); i++) {
            TextView tab = (TextView) mTabRow.getChildAt(i);
            boolean active = tab.getTag() == mQuery.category;
            tab.setTextColor(active ? Color.WHITE : Mr.TEXT);
            Mr.background(tab, active ? Mr.BRAND : Color.TRANSPARENT, 24);
        }
    }

    private void styleControls() {
        if (mSortPill == null) return;
        Mr.pillLabel(mSortPill).setText("Sort by:  " + mQuery.sort.label);
        Mr.pillLabel(mViewPill).setText("View:  " + mQuery.pageSize);
        boolean filtered = mQuery.describeFilters().length() > 0;
        Mr.pillLabel(mFilterPill).setTextColor(filtered ? Mr.BRAND : Mr.TEXT);
        mFilterSummary.setVisibility(filtered ? View.VISIBLE : View.GONE);
        mFilterSummary.setText("Filtered: " + mQuery.describeFilters() + "   ·   Clear");
        View holder = (View) mGridButton.getParent();
        Mr.background(holder, mGrid ? Mr.BRAND_SOFT : Mr.WHITE, 24);
        mGridButton.setColorFilter(mGrid ? Mr.BRAND : Mr.TEXT);
    }

    private void saveView() {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return;
        prefs.edit().putInt(PREF_VIEW_SIZE, mQuery.pageSize).putBoolean(PREF_GRID, mGrid).putString(PREF_SORT, mQuery.sort.index).apply();
    }

    private static boolean isViewSize(int size) {
        for (int value : VIEW_SIZES) if (value == size) return true;
        return false;
    }

    private void pickSort() {
        final SortOrder[] orders = SortOrder.values();
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < orders.length; i++) {
            labels.add(orders[i].label);
            if (orders[i] == mQuery.sort) selected = i;
        }
        Mr.menu(mSortPill, labels, selected, index -> {
            if (orders[index] == mQuery.sort) return;
            mQuery.sort = orders[index];
            mQuery.page = 0;
            saveView();
            styleControls();
            search(true);
        });
    }

    private void pickViewSize() {
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < VIEW_SIZES.length; i++) {
            labels.add(String.valueOf(VIEW_SIZES[i]));
            if (VIEW_SIZES[i] == mQuery.pageSize) selected = i;
        }
        Mr.menu(mViewPill, labels, selected, index -> {
            if (VIEW_SIZES[index] == mQuery.pageSize) return;
            mQuery.pageSize = VIEW_SIZES[index];
            mQuery.page = 0;
            saveView();
            styleControls();
            search(true);
        });
    }

    private void switchCategory(DownloadCategory category, boolean doSearch) {
        SearchQuery next = new SearchQuery(category);
        next.text = mQuery.text;
        next.sort = mQuery.sort;
        next.pageSize = mQuery.pageSize;
        next.gameVersion = mQuery.gameVersion;
        next.loader = Arrays.asList(category.loaderChoices()).contains(mQuery.loader) ? mQuery.loader : null;
        next.environment = category.hasEnvironmentFilter ? mQuery.environment : null;
        mQuery = next;
        if (mSearchBox != null) mSearchBox.setHint("Search " + category.label.toLowerCase(java.util.Locale.ROOT) + "…");
        styleTabs();
        if (mSortPill != null) styleControls();
        if (doSearch) search(true);
    }

    // ---- filter dialog --------------------------------------------------------------------------

    private void showFilter() {
        final Context context = requireContext();
        final SearchQuery draft = mQuery.copy();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Mr.text(context, "Filter results", 20, Mr.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(titleRow, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 14));

        content.addView(Mr.text(context, "Minecraft version", 14, Mr.TEXT_SECOND, true), Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 2, 0, 0, 6));
        final LinearLayout versionPill = Mr.pillButton(context, draft.gameVersion == null ? "Any version" : draft.gameVersion, true);
        content.addView(versionPill, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, Mr.dp(context, 48)), context, 0, 0, 0, 8));
        final TextView profileButton = Mr.text(context, "", 14, Mr.BRAND, true);
        profileButton.setVisibility(View.GONE);
        profileButton.setPadding(Mr.dp(context, 4), Mr.dp(context, 4), Mr.dp(context, 4), Mr.dp(context, 10));
        content.addView(profileButton, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final List<TextView> loaderChips = new ArrayList<>();
        final List<String> loaderValues = new ArrayList<>();
        final Runnable[] restyle = new Runnable[1];
        final List<TextView> environmentChips = new ArrayList<>();
        final List<String> environmentValues = new ArrayList<>();
        if (draft.category.hasLoaderFilter) {
            content.addView(Mr.text(context, "Loader", 14, Mr.TEXT_SECOND, true), Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 2, 4, 0, 6));
            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(context);
            loaderValues.add(null);
            for (String loader : draft.category.loaderChoices()) loaderValues.add(loader);
            for (final String value : loaderValues) {
                TextView chip = Mr.chip(context, value == null ? "Any" : Formatters.tagLabel(value), false);
                chip.setOnClickListener(v -> {
                    draft.loader = value;
                    restyle[0].run();
                });
                loaderChips.add(chip);
                row.addView(chip, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 8, 0));
            }
            scroll.addView(row);
            content.addView(scroll, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 10));
        }
        if (draft.category.hasEnvironmentFilter) {
            content.addView(Mr.text(context, "Works on", 14, Mr.TEXT_SECOND, true), Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 2, 4, 0, 6));
            LinearLayout row = new LinearLayout(context);
            String[] labels = {"Any", "Client", "Server"};
            environmentValues.addAll(Arrays.asList(null, "client", "server"));
            for (int i = 0; i < labels.length; i++) {
                final String value = environmentValues.get(i);
                TextView chip = Mr.chip(context, labels[i], false);
                chip.setOnClickListener(v -> {
                    draft.environment = value;
                    restyle[0].run();
                });
                environmentChips.add(chip);
                row.addView(chip, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 8, 0));
            }
            content.addView(row, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 10));
        }
        restyle[0] = () -> {
            for (int i = 0; i < loaderChips.size(); i++) {
                boolean on = TextUtils.equals(loaderValues.get(i), draft.loader);
                loaderChips.get(i).setTextColor(on ? Color.WHITE : Mr.TEXT);
                Mr.background(loaderChips.get(i), on ? Mr.BRAND : Mr.WHITE, 18);
            }
            for (int i = 0; i < environmentChips.size(); i++) {
                boolean on = TextUtils.equals(environmentValues.get(i), draft.environment);
                environmentChips.get(i).setTextColor(on ? Color.WHITE : Mr.TEXT);
                Mr.background(environmentChips.get(i), on ? Mr.BRAND : Mr.WHITE, 18);
            }
            Mr.pillLabel(versionPill).setText(draft.gameVersion == null ? "Any version" : draft.gameVersion);
        };
        restyle[0].run();

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.END);
        TextView reset = Mr.button(context, "Reset", false);
        TextView apply = Mr.button(context, "Apply", true);
        buttons.addView(reset, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 10, 0));
        buttons.addView(apply);
        content.addView(buttons, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 8, 0, 0));

        final Dialog dialog = Mr.dialog(context, content);
        versionPill.setOnClickListener(v -> withGameVersions(versions -> {
            List<String> labels = new ArrayList<>();
            labels.add("Any version");
            labels.addAll(versions);
            int selected = draft.gameVersion == null ? 0 : Math.max(0, labels.indexOf(draft.gameVersion));
            Mr.menu(versionPill, labels, selected, index -> {
                draft.gameVersion = index == 0 ? null : labels.get(index);
                restyle[0].run();
            });
        }));
        loadProfiles(profiles -> {
            if (profiles.isEmpty() || !dialog.isShowing()) return;
            final ProfileChoice mine = profiles.get(ProfileChoice.selectedIndex(profiles));
            if (mine.target.gameVersion.isEmpty() && mine.target.loader.isEmpty()) return;
            profileButton.setText("Use my installation: " + mine.title() + " (" + mine.subtitle() + ")");
            profileButton.setVisibility(View.VISIBLE);
            profileButton.setOnClickListener(v -> {
                if (!mine.target.gameVersion.isEmpty()) draft.gameVersion = mine.target.gameVersion;
                if (draft.category.hasLoaderFilter && Arrays.asList(draft.category.loaderChoices()).contains(mine.target.loader)) {
                    draft.loader = mine.target.loader;
                }
                restyle[0].run();
            });
        });
        reset.setOnClickListener(v -> {
            draft.gameVersion = null;
            draft.loader = null;
            draft.environment = null;
            restyle[0].run();
        });
        apply.setOnClickListener(v -> {
            mQuery.gameVersion = draft.gameVersion;
            mQuery.loader = draft.loader;
            mQuery.environment = draft.environment;
            mQuery.page = 0;
            dialog.dismiss();
            styleControls();
            search(true);
        });
        dialog.show();
    }

    private interface Consumer<T> {
        void accept(T value);
    }

    private void withGameVersions(final Consumer<List<String>> then) {
        if (sGameVersions != null) {
            then.accept(sGameVersions);
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            List<String> versions = null;
            try {
                versions = mClient.releaseGameVersions();
            } catch (IOException | RuntimeException ignored) {
                // Told below.
            }
            final List<String> result = versions;
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (result == null || result.isEmpty()) {
                    Tools.showError(requireContext(), new IOException("Could not load the list of Minecraft versions from Modrinth."));
                    return;
                }
                sGameVersions = result;
                then.accept(result);
            });
        });
    }

    private void loadProfiles(final Consumer<List<ProfileChoice>> then) {
        if (mProfiles != null) {
            then.accept(mProfiles);
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            List<ProfileChoice> loaded = new ArrayList<>();
            try {
                loaded = ProfileChoice.load();
            } catch (IOException | RuntimeException ignored) {
                // No profiles: the shortcut simply is not offered.
            }
            final List<ProfileChoice> result = loaded;
            mHandler.post(() -> {
                if (!isAdded()) return;
                mProfiles = result;
                then.accept(result);
            });
        });
    }

    // ---- searching ------------------------------------------------------------------------------

    private void search(final boolean scrollToTop) {
        final SearchQuery asked = mQuery.copy();
        final int request = ++mRequest;
        mLoading = true;
        mError = null;
        styleControls();
        rebuildRows(false);
        if (scrollToTop && mRecycler != null) mRecycler.scrollToPosition(0);
        PojavApplication.sExecutorService.execute(() -> {
            SearchPage result = null;
            String error = null;
            try {
                result = mClient.search(asked);
            } catch (IOException e) {
                error = friendly(e);
            } catch (RuntimeException e) {
                error = "Modrinth sent an answer this launcher could not read.";
            }
            final SearchPage page = result;
            final String message = error;
            mHandler.post(() -> {
                if (request != mRequest) return; // a newer search is already on its way
                mLoading = false;
                mPage = page;
                mError = message;
                mShownQuery = asked;
                rebuildRows(false);
                if (scrollToTop && mRecycler != null) mRecycler.scrollToPosition(0);
            });
        });
    }

    private static String friendly(IOException e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) return "Can't reach Modrinth. Check your internet connection and try again.";
        if (e instanceof SocketTimeoutException) return "Modrinth took too long to answer. Try again.";
        String message = e.getMessage();
        return message == null || message.isEmpty() ? "Something went wrong while talking to Modrinth." : message;
    }

    private void gotoPage(int zeroBased) {
        if (zeroBased == mQuery.page || zeroBased < 0) return;
        mQuery.page = zeroBased;
        search(true);
    }

    // ---- list -----------------------------------------------------------------------------------

    private void rebuildRows(boolean everything) {
        if (mAdapter == null) return;
        List<Object> rows = new ArrayList<>();
        rows.add(mHeaderRow);
        int pageCount = mPage == null ? 0 : Pagination.pageCount(mPage.totalHits, mShownQuery.pageSize);
        if (mLoading || mError != null || mPage == null || mPage.hits.isEmpty()) {
            rows.add(mStatusRow);
        } else {
            if (pageCount > 1) rows.add(mPagerRow);
            rows.addAll(mPage.hits);
            if (pageCount > 1) rows.add(new Object() { /* bottom pager */ });
        }
        int oldCount = mRows.size();
        mRows.clear();
        mRows.addAll(rows);
        if (everything || oldCount == 0) {
            mAdapter.notifyDataSetChanged();
        } else {
            // The header (with the search box the player may be typing in) is left alone.
            if (oldCount > 1) mAdapter.notifyItemRangeRemoved(1, oldCount - 1);
            if (mRows.size() > 1) mAdapter.notifyItemRangeInserted(1, mRows.size() - 1);
        }
    }

    private void openProject(ProjectHit hit) {
        Bundle args = new Bundle();
        args.putString(ProjectFragment.ARG_CATEGORY, mShownQuery.category.key);
        args.putString(ProjectFragment.ARG_ID, hit.id);
        args.putString(ProjectFragment.ARG_SLUG, hit.slug);
        args.putString(ProjectFragment.ARG_TITLE, hit.title);
        args.putString(ProjectFragment.ARG_AUTHOR, hit.author);
        args.putString(ProjectFragment.ARG_DESCRIPTION, hit.description);
        args.putString(ProjectFragment.ARG_ICON, hit.iconUrl);
        args.putString(ProjectFragment.ARG_ADDRESS, hit.serverAddress);
        args.putString(ProjectFragment.ARG_MODIFIED, hit.dateModified);
        args.putString(ProjectFragment.ARG_ENVIRONMENT, hit.environment());
        args.putLong(ProjectFragment.ARG_DOWNLOADS, hit.downloads);
        args.putLong(ProjectFragment.ARG_FOLLOWS, hit.follows);
        args.putStringArrayList(ProjectFragment.ARG_TAGS, new ArrayList<>(hit.orderedTags()));
        Tools.swapFragment(requireActivity(), ProjectFragment.class, ProjectFragment.TAG, args);
    }

    private final class ResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return mRows.size();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = mRows.get(position);
            if (row == mHeaderRow) return TYPE_HEADER;
            if (row == mStatusRow) return TYPE_STATUS;
            if (row instanceof ProjectHit) return mGrid ? TYPE_CARD_GRID : TYPE_CARD;
            return TYPE_PAGER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context context = parent.getContext();
            switch (type) {
                case TYPE_HEADER:
                    if (mHeader.getParent() instanceof ViewGroup) ((ViewGroup) mHeader.getParent()).removeView(mHeader);
                    return new RecyclerView.ViewHolder(mHeader) {};
                case TYPE_PAGER: {
                    LinearLayout pager = new LinearLayout(context);
                    pager.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    pager.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    pager.setPadding(0, Mr.dp(context, 4), 0, Mr.dp(context, 10));
                    return new RecyclerView.ViewHolder(pager) {};
                }
                case TYPE_STATUS: {
                    LinearLayout status = new LinearLayout(context);
                    status.setOrientation(LinearLayout.VERTICAL);
                    status.setGravity(Gravity.CENTER);
                    status.setPadding(Mr.dp(context, 20), Mr.dp(context, 40), Mr.dp(context, 20), Mr.dp(context, 40));
                    status.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new RecyclerView.ViewHolder(status) {};
                }
                default:
                    return new CardHolder(context, type == TYPE_CARD_GRID);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = mRows.get(position);
            if (holder instanceof CardHolder) {
                ((CardHolder) holder).bind((ProjectHit) row, mShownQuery.category);
            } else if (holder.getItemViewType() == TYPE_PAGER) {
                bindPager((LinearLayout) holder.itemView);
            } else if (holder.getItemViewType() == TYPE_STATUS) {
                bindStatus((LinearLayout) holder.itemView);
            }
        }
    }

    private void bindStatus(LinearLayout status) {
        Context context = status.getContext();
        status.removeAllViews();
        if (mLoading) {
            ProgressBar bar = new ProgressBar(context);
            bar.setIndeterminate(true);
            bar.getIndeterminateDrawable().setColorFilter(Mr.BRAND, android.graphics.PorterDuff.Mode.SRC_IN);
            status.addView(bar, Mr.lp(context, 36, 36));
            status.addView(Mr.text(context, "Searching Modrinth…", 15, Mr.TEXT_SECOND, true), Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 12, 0, 0));
        } else if (mError != null) {
            TextView message = Mr.text(context, mError, 16, Mr.DANGER, true);
            message.setGravity(Gravity.CENTER);
            status.addView(message, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView retry = Mr.button(context, "Try again", true);
            retry.setOnClickListener(v -> search(false));
            status.addView(retry, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 14, 0, 0));
        } else {
            TextView empty = Mr.text(context, "No results found.\nTry other words, or take a filter off.", 16, Mr.TEXT_SECOND, true);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(0, 1.2f);
            status.addView(empty);
        }
    }

    private void bindPager(LinearLayout pager) {
        Context context = pager.getContext();
        pager.removeAllViews();
        if (mPage == null) return;
        final int count = Pagination.pageCount(mPage.totalHits, mShownQuery.pageSize);
        final int current = Math.min(mShownQuery.page, Math.max(0, count - 1));
        if (current > 0) pager.addView(pagerButton(context, 0, R.drawable.ic_dl_chevron_left, false, current - 1), pagerParams(context));
        for (int item : Pagination.items(current, count)) {
            if (item == Pagination.GAP) {
                TextView gap = Mr.text(context, "…", 16, Mr.TEXT_SECOND, true);
                gap.setGravity(Gravity.CENTER);
                pager.addView(gap, Mr.lp(context, 28, 44));
            } else {
                pager.addView(pagerButton(context, item, 0, item - 1 == current, item - 1), pagerParams(context));
            }
        }
        if (current < count - 1) pager.addView(pagerButton(context, 0, R.drawable.ic_dl_chevron_right, false, current + 1), pagerParams(context));
    }

    private static LinearLayout.LayoutParams pagerParams(Context context) {
        return Mr.margins(Mr.lp(context, 44, 44), context, 2, 0, 2, 0);
    }

    private View pagerButton(Context context, int number, int iconRes, boolean current, final int target) {
        FrameLayout button = new FrameLayout(context);
        Mr.background(button, current ? Mr.BRAND_SOFT : Color.TRANSPARENT, 22);
        if (iconRes != 0) {
            ImageView icon = Mr.icon(context, iconRes, Mr.TEXT, 22);
            icon.setLayoutParams(new FrameLayout.LayoutParams(Mr.dp(context, 22), Mr.dp(context, 22), Gravity.CENTER));
            button.addView(icon);
        } else {
            TextView label = Mr.text(context, String.valueOf(number), 16, current ? Mr.BRAND : Mr.TEXT, true);
            label.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            button.addView(label);
        }
        button.setClickable(true);
        button.setOnClickListener(v -> gotoPage(target));
        return button;
    }

    // ---- one card ---------------------------------------------------------------------------------

    private final class CardHolder extends RecyclerView.ViewHolder {
        private final boolean compact;
        private final ImageView icon;
        private final TextView title, author, description, downloads, follows, updated;
        private final LinearLayout tags, stats;
        private ProjectHit hit;

        CardHolder(Context context, boolean compact) {
            super(new LinearLayout(context));
            this.compact = compact;
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setPadding(Mr.dp(context, 14), Mr.dp(context, 14), Mr.dp(context, 16), Mr.dp(context, 14));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(compact ? Mr.dp(context, 5) : 0, Mr.dp(context, 5), compact ? Mr.dp(context, 5) : 0, Mr.dp(context, 5));
            root.setLayoutParams(params);
            Mr.background(root, Mr.CARD, 22);
            root.setOnClickListener(v -> {
                if (hit != null) openProject(hit);
            });

            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(icon, Mr.lp(context, compact ? 56 : 76, compact ? 56 : 76));

            LinearLayout middle = new LinearLayout(context);
            middle.setOrientation(LinearLayout.VERTICAL);
            root.addView(middle, Mr.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), context, 14, 0, 0, 0));

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            title = Mr.text(context, "", compact ? 16 : 20, Mr.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(title, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            author = Mr.text(context, "", 14, Mr.TEXT_SECOND, false);
            author.setSingleLine(true);
            author.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(author, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 8, 2, 0, 0));
            middle.addView(titleRow, Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            description = Mr.text(context, "", 15, Mr.TEXT, false);
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            description.setLineSpacing(0, 1.1f);
            middle.addView(description, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 6, 0, 8));

            tags = new LinearLayout(context);
            tags.setGravity(Gravity.CENTER_VERTICAL);
            middle.addView(tags, Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            stats = new LinearLayout(context);
            stats.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            stats.setGravity(compact ? Gravity.CENTER_VERTICAL : Gravity.END);
            LinearLayout counts = new LinearLayout(context);
            counts.setGravity(Gravity.CENTER_VERTICAL);
            counts.addView(Mr.icon(context, R.drawable.ic_dl_download, Mr.TEXT_SECOND, 20));
            downloads = Mr.text(context, "", 17, Mr.TEXT_SECOND, true);
            counts.addView(downloads, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 16, 0));
            counts.addView(Mr.icon(context, R.drawable.ic_dl_heart, Mr.TEXT_SECOND, 20));
            follows = Mr.text(context, "", 17, Mr.TEXT_SECOND, true);
            counts.addView(follows, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
            stats.addView(counts, Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout time = new LinearLayout(context);
            time.setGravity(Gravity.CENTER_VERTICAL);
            time.addView(Mr.icon(context, R.drawable.ic_dl_history, Mr.TEXT_SECOND, 20));
            updated = Mr.text(context, "", 17, Mr.TEXT_SECOND, true);
            time.addView(updated, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
            stats.addView(time, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, compact ? 16 : 0, compact ? 0 : 12, 0, 0));
            if (compact) middle.addView(stats, Mr.margins(Mr.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 10, 0, 0));
            else root.addView(stats, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 12, 0, 0, 0));
        }

        void bind(ProjectHit project, DownloadCategory category) {
            hit = project;
            Context context = itemView.getContext();
            IconLoader.load(icon, project.iconUrl, Mr.dp(context, compact ? 56 : 76) * 2, 14, Mr.round(context, Mr.WHITE, 14));
            title.setText(project.title);
            author.setText(project.author.isEmpty() ? "" : "by " + project.author);
            description.setText(project.description);
            boolean server = category == DownloadCategory.SERVERS;
            downloads.setText(Formatters.compactNumber(project.downloads));
            follows.setText(Formatters.compactNumber(project.follows));
            ((View) downloads.getParent()).setVisibility(server && project.downloads == 0 && project.follows == 0 ? View.GONE : View.VISIBLE);
            String when = Formatters.relativeTime(project.dateModified, System.currentTimeMillis());
            ((View) updated.getParent()).setVisibility(when.isEmpty() ? View.GONE : View.VISIBLE);
            updated.setText(when);
            bindTags(context, project, category);
        }

        private void bindTags(Context context, ProjectHit project, DownloadCategory category) {
            tags.removeAllViews();
            int limit = compact ? 3 : 4;
            int shown = 0;
            if (!project.serverAddress.isEmpty()) {
                tags.addView(tagChip(context, R.drawable.ic_dl_globe, true, project.serverAddress), tagParams(context));
                shown++;
            }
            String environment = category.hasEnvironmentFilter ? project.environment() : "";
            if (!environment.isEmpty()) {
                tags.addView(tagChip(context, R.drawable.ic_dl_globe, true, environment), tagParams(context));
                shown++;
            }
            for (String tag : project.orderedTags()) {
                if (shown >= limit) break;
                int logo = loaderLogo(tag);
                tags.addView(tagChip(context, logo != 0 ? logo : R.drawable.ic_dl_tag, logo == 0, Formatters.tagLabel(tag)), tagParams(context));
                shown++;
            }
        }
    }

    private static LinearLayout.LayoutParams tagParams(Context context) {
        return Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 8, 0);
    }

    /** A small white label with an icon, like Modrinth's category and loader tags. */
    static View tagChip(Context context, int iconRes, boolean tinted, String label) {
        LinearLayout chip = new LinearLayout(context);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(Mr.dp(context, 10), Mr.dp(context, 6), Mr.dp(context, 12), Mr.dp(context, 6));
        chip.setBackground(Mr.roundStroke(context, Mr.WHITE, 16, Mr.LINE, 1));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        if (tinted) icon.setColorFilter(Mr.TEXT_SECOND);
        chip.addView(icon, Mr.lp(context, 16, 16));
        TextView text = Mr.text(context, label, 14, Mr.TEXT_SECOND, false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Mr.dp(context, 170));
        chip.addView(text, Mr.margins(Mr.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
        return chip;
    }

    /** The launcher's own logo of a loader, or 0 when it has none. */
    static int loaderLogo(String loader) {
        switch (loader == null ? "" : loader.toLowerCase(java.util.Locale.ROOT)) {
            case "fabric": return R.drawable.ic_fabric;
            case "forge": return R.drawable.ic_forge;
            case "neoforge": return R.drawable.ic_neoforge;
            case "quilt": return R.drawable.ic_quilt;
            case "optifine": return R.drawable.ic_optifine;
            default: return 0;
        }
    }
}
