package net.kdt.pojavlaunch.content.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.content.ContentKind;
import net.kdt.pojavlaunch.content.ContentList;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.instances.Instance;

import java.io.File;

/**
 * The top of the installation settings: a Back button, the title, and three big buttons that
 * open the installation's Mods, Resource Packs and Shaders. Added around the existing editor
 * fields, which keep working as they were.
 */
public final class InstallationChrome {
    private InstallationChrome() {}

    /** Puts the header and the three buttons above everything else in {@code root} (a vertical LinearLayout). */
    public static void attach(final Fragment fragment, LinearLayout root, final Instance instance) {
        Context context = root.getContext();
        root.addView(header(fragment, context, instance), 0,
                Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 14, 0, 14, 8));
        root.addView(shortcuts(fragment, context, instance), 1,
                Fx.margins(Fx.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 14, 0, 14, 4));
    }

    /** Back to where the settings were opened from (the home page, or the screen before). */
    public static void goBack(Fragment fragment) {
        Fragment parent = fragment.getParentFragment();
        if (parent instanceof MainMenuFragment) ((MainMenuFragment) parent).handleCenterBack();
        else Tools.removeCurrentFragment(fragment.requireActivity());
    }

    private static View header(final Fragment fragment, Context context, Instance instance) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout back = new LinearLayout(context);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(Fx.dp(context, 10), 0, Fx.dp(context, 16), 0);
        Fx.ripple(back, Fx.glass(context, 14, Fx.EDGE));
        back.addView(Fx.icon(context, R.drawable.ic_dl_back, Fx.ACCENT, 20));
        back.addView(Fx.text(context, "Back", 15, Fx.TEXT, true),
                Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 6, 0, 0, 0));
        back.setOnClickListener(v -> goBack(fragment));
        row.addView(back, Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Fx.dp(context, 42)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView small = Fx.text(context, "INSTALLATION", 11, Fx.ACCENT, true);
        small.setLetterSpacing(0.2f);
        titles.addView(small);
        TextView name = Fx.text(context, instance == null || instance.name == null || instance.name.isEmpty() ? "Settings" : instance.name, 20, Fx.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name, Fx.margins(Fx.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), context, 0, 3, 0, 0));
        row.addView(titles, Fx.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), context, 14, 0, 0, 0));
        return row;
    }

    private static View shortcuts(final Fragment fragment, Context context, Instance instance) {
        LinearLayout row = new LinearLayout(context);
        final ContentKind[] kinds = ContentKind.values();
        final int[] icons = {R.drawable.ic_fx_mods, R.drawable.ic_fx_packs, R.drawable.ic_fx_shaders};
        final TextView[] counts = new TextView[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            final ContentKind kind = kinds[i];
            LinearLayout tile = new LinearLayout(context);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(Fx.dp(context, 10), 0, Fx.dp(context, 8), 0);
            Fx.ripple(tile, Fx.glass(context, 14, Fx.EDGE));
            tile.addView(Fx.icon(context, icons[i], Fx.ACCENT, 22));
            TextView label = Fx.text(context, kind.label, 13, Fx.TEXT, true);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(label, Fx.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), context, 8, 0, 6, 0));
            counts[i] = Fx.text(context, "", 11, Fx.ACCENT, true);
            counts[i].setPadding(Fx.dp(context, 7), Fx.dp(context, 3), Fx.dp(context, 7), Fx.dp(context, 3));
            counts[i].setBackground(Fx.fill(context, 0x3300E6AA, 8, 0));
            counts[i].setVisibility(View.GONE);
            tile.addView(counts[i]);
            tile.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString(InstalledContentFragment.ARG_KIND, kind.key);
                Tools.swapFragment(fragment.requireActivity(), InstalledContentFragment.class, InstalledContentFragment.TAG, args);
            });
            row.addView(tile, Fx.margins(new LinearLayout.LayoutParams(0, Fx.dp(context, 44), 1f), context, i == 0 ? 0 : 5, 0, i == kinds.length - 1 ? 0 : 5, 0));
        }
        countFiles(instance, kinds, counts);
        return row;
    }

    /** Fills in "12 installed" under each button, counting the real folders off the main thread. */
    private static void countFiles(Instance instance, final ContentKind[] kinds, final TextView[] counts) {
        if (instance == null) return;
        final File game = instance.getGameDirectory();
        final Handler main = new Handler(Looper.getMainLooper());
        PojavApplication.sExecutorService.execute(() -> {
            final int[] found = new int[kinds.length];
            for (int i = 0; i < kinds.length; i++) found[i] = ContentList.count(game, kinds[i]);
            main.post(() -> {
                for (int i = 0; i < kinds.length; i++) {
                    counts[i].setText(String.valueOf(found[i]));
                    counts[i].setVisibility(View.VISIBLE);
                }
            });
        });
    }
}
