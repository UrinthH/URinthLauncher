package net.kdt.pojavlaunch.content.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.content.ContentActions;
import net.kdt.pojavlaunch.content.ContentInfo;
import net.kdt.pojavlaunch.content.ContentItem;
import net.kdt.pojavlaunch.content.ContentKind;
import net.kdt.pojavlaunch.content.ContentList;
import net.kdt.pojavlaunch.downloads.ui.DownloadsFragment;
import net.kdt.pojavlaunch.downloads.ui.IconLoader;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything an installation has in one folder (mods, resource packs or shaders): each with its
 * own icon, a switch to turn it off without deleting it, and a Delete button. Works on the real
 * files of the installation, so what the list shows is what the game loads.
 */
public class InstalledContentFragment extends Fragment {
    public static final String TAG = "InstalledContentFragment";
    public static final String ARG_KIND = "content_kind";

    private static final int FILTER_ALL = 0, FILTER_ON = 1, FILTER_OFF = 2;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ContentLoader.Cancel mCancel = new ContentLoader.Cancel();
    private final List<ContentItem> mAll = new ArrayList<>();
    private final List<ContentItem> mShown = new ArrayList<>();

    private ContentKind mKind = ContentKind.MODS;
    private Instance mInstance;
    private File mGameDirectory;
    private int mFilter = FILTER_ALL;
    private String mSearch = "";
    private boolean mLoaded;

    private RecyclerView mList;
    private Adapter mAdapter;
    private TextView mSummary, mEmpty;
    private final TextView[] mFilterChips = new TextView[3];

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) mKind = ContentKind.fromKey(args.getString(ARG_KIND));
        mInstance = Instances.loadSelectedInstance();
        if (mInstance != null) mGameDirectory = mInstance.getGameDirectory();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = inflater.getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Fx.glass(context, 18, 0xAA00E6AA));
        int side = Fx.dp(context, 16);
        root.setPadding(side, Fx.dp(context, 12), side, Fx.dp(context, 12));

        root.addView(header(context), Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 10));
        root.addView(controls(context), Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 0, 8));

        mSummary = Fx.text(context, "", 13, Fx.MUTED, false);
        root.addView(mSummary, Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 4, 0, 0, 8));

        FrameLayout body = new FrameLayout(context);
        mList = new RecyclerView(context);
        mList.setLayoutManager(new LinearLayoutManager(context));
        mList.setItemAnimator(null);
        mList.setClipToPadding(false);
        mList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mAdapter = new Adapter();
        mList.setAdapter(mAdapter);
        body.addView(mList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mEmpty = Fx.text(context, "", 15, Fx.MUTED, false);
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setLineSpacing(0, 1.2f);
        mEmpty.setPadding(Fx.dp(context, 24), 0, Fx.dp(context, 24), 0);
        mEmpty.setVisibility(View.GONE);
        body.addView(mEmpty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (mInstance == null) {
            mEmpty.setText("No installation is selected.");
            mEmpty.setVisibility(View.VISIBLE);
        } else {
            reload();
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        mCancel.cancelled = true;
        mList = null;
        mAdapter = null;
        super.onDestroyView();
    }

    // ---- header and controls ------------------------------------------------------------------------

    private View header(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout back = new LinearLayout(context);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(Fx.dp(context, 10), 0, Fx.dp(context, 16), 0);
        Fx.ripple(back, Fx.glass(context, 14, Fx.EDGE));
        back.addView(Fx.icon(context, R.drawable.ic_dl_back, Fx.ACCENT, 20));
        back.addView(Fx.text(context, "Back", 15, Fx.TEXT, true), Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
        back.setOnClickListener(v -> InstallationChrome.goBack(InstalledContentFragment.this));
        row.addView(back, Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Fx.dp(context, 42)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Fx.text(context, mKind.label, 20, Fx.TEXT, true));
        TextView subtitle = Fx.text(context, mInstance == null ? "" : "Installation: " + mInstance.name, 13, Fx.MUTED, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitle, Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 3, 0, 0));
        row.addView(titles, Fx.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), context, 14, 0, 8, 0));

        TextView more = Fx.button(context, "Find more", Fx.PRIMARY);
        more.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(DownloadsFragment.ARG_CATEGORY, mKind.category.key);
            Tools.swapFragment(requireActivity(), DownloadsFragment.class, DownloadsFragment.TAG, args);
        });
        row.addView(more);
        return row;
    }

    private View controls(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(Fx.dp(context, 12), 0, Fx.dp(context, 12), 0);
        searchBox.setBackground(Fx.fill(context, 0x66082736, 14, 0xFF1D697E));
        searchBox.addView(Fx.icon(context, R.drawable.ic_dl_search, Fx.MUTED, 18));
        EditText search = new EditText(context);
        search.setHint("Search " + mKind.label.toLowerCase(Locale.ROOT));
        search.setHintTextColor(Fx.MUTED);
        search.setTextColor(Fx.TEXT);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(null);
        search.setPadding(Fx.dp(context, 10), 0, 0, 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mSearch = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        searchBox.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        row.addView(searchBox, Fx.margins(new LinearLayout.LayoutParams(0, Fx.dp(context, 42), 1f), context, 0, 0, 10, 0));

        String[] labels = {"All", "On", "Off"};
        for (int i = 0; i < labels.length; i++) {
            final int filter = i;
            TextView chip = Fx.text(context, labels[i], 14, Fx.TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Fx.dp(context, 14), 0, Fx.dp(context, 14), 0);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                mFilter = filter;
                styleFilterChips();
                applyFilter();
            });
            mFilterChips[i] = chip;
            row.addView(chip, Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Fx.dp(context, 42)), context, 0, 0, 6, 0));
        }
        styleFilterChips();
        return row;
    }

    private void styleFilterChips() {
        for (int i = 0; i < mFilterChips.length; i++) {
            TextView chip = mFilterChips[i];
            if (chip == null) continue;
            Context context = chip.getContext();
            boolean selected = i == mFilter;
            chip.setTextColor(selected ? Fx.ON_ACCENT : Fx.TEXT);
            chip.setBackground(selected ? Fx.accentGradient(context, 14) : Fx.glass(context, 14, Fx.EDGE_SOFT));
        }
    }

    // ---- data ---------------------------------------------------------------------------------------

    private void reload() {
        if (mGameDirectory == null) return;
        final File game = mGameDirectory;
        final ContentKind kind = mKind;
        PojavApplication.sExecutorService.execute(() -> {
            final List<ContentItem> found = ContentList.scan(game, kind);
            mMain.post(() -> {
                if (mAdapter == null) return;
                mLoaded = true;
                mAll.clear();
                mAll.addAll(found);
                applyFilter();
                ContentLoader.findOnlineIcons(new ArrayList<>(found), kind, mCancel, () -> {
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();
                });
            });
        });
    }

    private void applyFilter() {
        if (mAdapter == null) return;
        mShown.clear();
        int on = 0, off = 0;
        for (ContentItem item : mAll) {
            if (item.enabled) on++;
            else off++;
            if (mFilter == FILTER_ON && !item.enabled) continue;
            if (mFilter == FILTER_OFF && item.enabled) continue;
            if (!mSearch.isEmpty() && !item.fileName().toLowerCase(Locale.ROOT).contains(mSearch)) {
                // The name written inside the file counts too, once it has been read.
                String inside = ContentLoader.cachedName(item);
                if (!inside.toLowerCase(Locale.ROOT).contains(mSearch)) continue;
            }
            mShown.add(item);
        }
        String noun = mKind == ContentKind.MODS ? "mods" : mKind == ContentKind.SHADERS ? "shaders" : "packs";
        mSummary.setText(mAll.size() + " " + noun + "  ·  " + on + " on  ·  " + off + " off");
        mAdapter.notifyDataSetChanged();
        boolean empty = mShown.isEmpty() && mLoaded;
        if (empty) {
            mEmpty.setText(mAll.isEmpty()
                    ? "Nothing here yet.\nUse Find more to download " + mKind.label.toLowerCase(Locale.ROOT) + " for this installation."
                    : "Nothing matches.");
        }
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ---- actions ------------------------------------------------------------------------------------

    private void switchItem(final ContentItem item, final boolean enabled, final Fx.Toggle toggle) {
        PojavApplication.sExecutorService.execute(() -> {
            File renamed = null;
            String error = null;
            try {
                renamed = ContentActions.setEnabled(item, enabled);
            } catch (IOException e) {
                error = e.getMessage();
            }
            final File result = renamed;
            final String message = error;
            mMain.post(() -> {
                if (mAdapter == null) return;
                if (result == null) {
                    toggle.setChecked(!enabled, true);
                    Context context = getContext();
                    if (context != null) Toast.makeText(context, message == null ? "Could not change this file." : message, Toast.LENGTH_LONG).show();
                    return;
                }
                int index = mAll.indexOf(item);
                if (index >= 0) mAll.set(index, new ContentItem(result, item.size));
                applyFilter();
            });
        });
    }

    private void confirmDelete(final ContentItem item) {
        final Context context = getContext();
        if (context == null || mGameDirectory == null) return;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(Fx.text(context, "Delete " + item.fileName() + "?", 20, Fx.TEXT, true));
        TextView note = Fx.text(context, "This removes " + item.fileName() + " from " + (mInstance == null ? "this installation" : mInstance.name)
                + ". It cannot be undone.", 15, Fx.MUTED, false);
        note.setLineSpacing(0, 1.2f);
        content.addView(note, Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 10, 0, 18));
        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.END);
        final Dialog dialog = Fx.dialog(context, content);
        TextView cancel = Fx.button(context, "Cancel", Fx.GHOST);
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel, Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 0, 10, 0));
        TextView delete = Fx.button(context, "Delete", Fx.DANGER_STYLE);
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            deleteItem(item);
        });
        buttons.addView(delete);
        content.addView(buttons);
        dialog.show();
    }

    private void deleteItem(final ContentItem item) {
        final File folder = ContentList.folderOf(mGameDirectory, mKind);
        PojavApplication.sExecutorService.execute(() -> {
            String error = null;
            try {
                ContentActions.delete(folder, item);
            } catch (IOException e) {
                error = e.getMessage();
            }
            final String message = error;
            mMain.post(() -> {
                if (mAdapter == null) return;
                if (message != null) {
                    Context context = getContext();
                    if (context != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    return;
                }
                mAll.remove(item);
                applyFilter();
            });
        });
    }

    // ---- list ---------------------------------------------------------------------------------------

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(parent.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(mShown.get(position));
        }

        @Override
        public int getItemCount() {
            return mShown.size();
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name, details, description, badge;
        private final Fx.Toggle toggle;
        private final FrameLayout trash;
        private final LinearLayout card;
        private String boundKey = "";

        Holder(Context context) {
            super(new LinearLayout(context));
            card = (LinearLayout) itemView;
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Fx.dp(context, 12), Fx.dp(context, 10), Fx.dp(context, 8), Fx.dp(context, 10));
            card.setBackground(Fx.glass(context, 16, Fx.EDGE_SOFT));
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(icon, Fx.lp(context, 52, 52));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            name = Fx.text(context, "", 16, Fx.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            badge = Fx.text(context, "OFF", 11, Fx.DANGER, true);
            badge.setPadding(Fx.dp(context, 6), Fx.dp(context, 2), Fx.dp(context, 6), Fx.dp(context, 2));
            badge.setBackground(Fx.fill(context, Fx.DANGER_FILL, 6, 0));
            titleRow.addView(badge, Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 8, 0, 0, 0));
            texts.addView(titleRow);
            details = Fx.text(context, "", 13, Fx.ACCENT, false);
            details.setSingleLine(true);
            details.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(details, Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 3, 0, 0));
            description = Fx.text(context, "", 13, Fx.MUTED, false);
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(description, Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 3, 0, 0));
            card.addView(texts, Fx.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), context, 12, 0, 8, 0));

            toggle = new Fx.Toggle(context, true);
            card.addView(toggle);
            trash = Fx.iconButton(context, R.drawable.ic_fx_trash, Fx.DANGER, 0x66C44857, 42);
            card.addView(trash, Fx.margins(Fx.lp(context, 42, 42), context, 4, 0, 0, 0));
        }

        void bind(final ContentItem item) {
            final Context context = card.getContext();
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Fx.dp(context, 8);
            card.setLayoutParams(params);
            final String key = item.cacheKey();
            boundKey = key;
            card.setAlpha(item.enabled ? 1f : 0.62f);
            name.setText(item.fileName());
            details.setText(item.directory ? "Folder" : ContentItem.sizeText(item.size));
            description.setText("");
            description.setVisibility(View.GONE);
            badge.setVisibility(item.enabled ? View.GONE : View.VISIBLE);
            showPlaceholder(context);

            toggle.setVisibility(item.canSwitch() ? View.VISIBLE : View.GONE);
            toggle.setListener(null);
            toggle.setChecked(item.enabled, false);
            toggle.setListener(checked -> switchItem(item, checked, toggle));
            trash.setOnClickListener(v -> confirmDelete(item));

            ContentLoader.load(item, mKind, (info, bitmap) -> {
                if (!key.equals(boundKey)) return; // the row was recycled for another file
                applyInfo(context, item, info, bitmap);
            });
        }

        private void applyInfo(Context context, ContentItem item, ContentInfo info, Bitmap bitmap) {
            name.setText(info.name.isEmpty() ? item.fileName() : info.name);
            StringBuilder line = new StringBuilder();
            if (!info.version.isEmpty()) line.append(info.version).append("  ·  ");
            line.append(item.directory ? "Folder" : ContentItem.sizeText(item.size));
            if (info.unreadable) line.append("  ·  could not read this file");
            details.setText(line);
            if (!info.description.isEmpty()) {
                description.setText(info.description);
                description.setVisibility(View.VISIBLE);
            }
            if (bitmap != null) {
                showBitmap(context, bitmap);
            } else {
                String remote = ContentLoader.remoteIcon(item);
                if (remote != null) {
                    icon.setPadding(0, 0, 0, 0);
                    icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    icon.clearColorFilter();
                    icon.setBackground(null);
                    IconLoader.load(icon, remote, Fx.dp(context, 52) * 2, 12, iconBackground(context));
                }
            }
        }

        private android.graphics.drawable.Drawable iconBackground(Context context) {
            return Fx.fill(context, 0x66082736, 12, 0xFF1D697E);
        }

        private void showPlaceholder(Context context) {
            int pad = Fx.dp(context, 13);
            icon.setTag(null); // a download still running for the previous file must not land here
            icon.setPadding(pad, pad, pad, pad);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setBackground(iconBackground(context));
            icon.setColorFilter(Fx.MUTED);
            icon.setImageResource(mKind == ContentKind.MODS ? R.drawable.ic_fx_mods
                    : mKind == ContentKind.SHADERS ? R.drawable.ic_fx_shaders : R.drawable.ic_fx_packs);
        }

        private void showBitmap(Context context, Bitmap bitmap) {
            icon.setTag(null);
            icon.setPadding(0, 0, 0, 0);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.clearColorFilter();
            androidx.core.graphics.drawable.RoundedBitmapDrawable drawable =
                    androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
            drawable.setCornerRadius(Fx.dp(context, 12));
            drawable.setFilterBitmap(false); // pixel art stays crisp
            icon.setBackground(null);
            icon.setImageDrawable(drawable);
        }
    }
}
