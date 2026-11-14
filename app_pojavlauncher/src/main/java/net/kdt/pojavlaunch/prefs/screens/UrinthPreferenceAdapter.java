package net.kdt.pojavlaunch.prefs.screens;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.GamepadRemapPreference;
import net.kdt.pojavlaunch.prefs.screens.SettingsStyle.RowPosition;

import java.util.HashSet;
import java.util.Set;

import git.artdeell.mojo.R;

/**
 * Draws the settings list as white grouped cards: the rows of one section share a card with
 * rounded outer corners and thin dividers, each row has its icon in a tinted chip, switches and
 * sliders take the accent colour, and a section starts with a small header. Nothing about what a
 * row does is changed; only how it looks.
 *
 * The accent colour, compact rows and reduced motion come from the Appearance settings (see
 * {@link SettingsStyle}); call {@link #refreshStyle()} and redraw the list after one changes.
 */
@SuppressLint("RestrictedApi")
final class UrinthPreferenceAdapter extends PreferenceGroupAdapter {
    private static final int MAX_STAGGER_STEPS = 6;

    private final PreferenceGroup root;
    private final Set<Preference> entered = new HashSet<>();
    private int accent = SettingsStyle.accentColor((String) null);
    private boolean compact;
    private boolean reduceMotion;

    UrinthPreferenceAdapter(@NonNull PreferenceGroup group) {
        super(group);
        root = group;
        refreshStyle();
    }

    /** Reads the three Appearance settings again. */
    void refreshStyle() {
        PreferenceManager manager = root.getPreferenceManager();
        SharedPreferences prefs = manager == null ? null : manager.getSharedPreferences();
        accent = SettingsStyle.accentColor(prefs);
        compact = SettingsStyle.compact(prefs);
        reduceMotion = SettingsStyle.reduceMotion(prefs);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Preference preference = getItem(position);
        View item = holder.itemView;
        // Rows are recycled: clear anything an earlier row left behind.
        item.animate().cancel();
        item.setAlpha(1f);
        item.setTranslationY(0f);
        item.setScaleX(1f);
        item.setScaleY(1f);
        item.setOnTouchListener(null);
        if (preference == null || preference instanceof SettingsHeaderPreference) return;
        if (preference instanceof PreferenceGroup) {
            styleHeader(holder);
            return;
        }
        styleRow(holder, preference, placeOf(position, preference));
        fadeIn(item, preference, position);
    }

    // ---- grouping ----------------------------------------------------------------------------

    private RowPosition placeOf(int position, Preference preference) {
        Preference above = position > 0 ? getItem(position - 1) : null;
        Preference below = position + 1 < getItemCount() ? getItem(position + 1) : null;
        return SettingsStyle.rowPosition(sameGroup(above, preference), sameGroup(preference, below));
    }

    private static boolean sameGroup(Preference a, Preference b) {
        return a != null && b != null && isRow(a) && isRow(b) && a.getParent() == b.getParent();
    }

    private static boolean isRow(Preference preference) {
        return !(preference instanceof PreferenceGroup) && !(preference instanceof SettingsHeaderPreference);
    }

    // ---- section header ----------------------------------------------------------------------

    private void styleHeader(PreferenceViewHolder holder) {
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) title.setTextColor(SettingsStyle.HEADING);
        View iconView = holder.findViewById(android.R.id.icon);
        if (iconView instanceof ImageView) {
            ((ImageView) iconView).setImageTintList(ColorStateList.valueOf(accent));
        }
        // The older header layout (still used by the sub-screens) has a bar and a line.
        View bar = holder.findViewById(R.id.urinth_header_bar);
        if (bar != null) bar.setBackgroundTintList(ColorStateList.valueOf(accent));
        View line = holder.findViewById(R.id.urinth_header_line);
        if (line != null) line.setBackgroundTintList(ColorStateList.valueOf(SettingsStyle.withAlpha(accent, 0xAA)));
    }

    // ---- rows --------------------------------------------------------------------------------

    private void styleRow(PreferenceViewHolder holder, Preference preference, RowPosition place) {
        View item = holder.itemView;
        float density = item.getResources().getDisplayMetrics().density;
        boolean enabled = preference.isEnabled();
        boolean danger = isDanger(preference);
        boolean on = preference instanceof SwitchPreferenceCompat && ((SwitchPreferenceCompat) preference).isChecked();
        int tint = danger ? SettingsStyle.DANGER : accent;
        int chip = dp(compact ? 32 : 38, density);
        int pad = dp(14, density);
        boolean hasIcon = preference.getIcon() != null;

        item.setBackground(rowBackground(place, hasIcon, chip, density));
        item.setMinimumHeight(dp(compact ? 52 : 64, density));

        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(!enabled ? SettingsStyle.TEXT_DISABLED : danger ? SettingsStyle.DANGER : SettingsStyle.TEXT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextColor(enabled ? SettingsStyle.MUTED : SettingsStyle.TEXT_DISABLED);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        }

        // The library's own row layout: tighten its spacing. Rows with their own layout keep theirs.
        View frame = holder.findViewById(androidx.preference.R.id.icon_frame);
        if (frame != null) {
            item.setPaddingRelative(pad, 0, pad, 0);
            frame.setMinimumWidth(chip + pad);
            frame.setPaddingRelative(0, 0, pad, 0);
            if (title != null && title.getParent() instanceof View) {
                View box = (View) title.getParent();
                int vertical = dp(compact ? 8 : 14, density);
                box.setPaddingRelative(box.getPaddingStart(), vertical, box.getPaddingEnd(), vertical);
            }
        }

        styleIcon(holder, hasIcon, chip, density, tint, enabled, on);
        styleSwitch(holder);
        styleSlider(holder, preference, density);
    }

    private static boolean isDanger(Preference preference) {
        return "clearMetadataCache".equals(preference.getKey()) || preference instanceof GamepadRemapPreference;
    }

    /** One card slice: a border, the white fill inside it, a divider under the row when another row follows, and a ripple. */
    private Drawable rowBackground(RowPosition place, boolean hasIcon, int chip, float density) {
        float radius = 16f * density;
        int border = Math.max(1, Math.round(density * 0.6f));

        GradientDrawable outer = new GradientDrawable();
        outer.setColor(SettingsStyle.BORDER);
        outer.setCornerRadii(SettingsStyle.corners(place, radius));
        GradientDrawable inner = new GradientDrawable();
        inner.setColor(SettingsStyle.CARD);
        inner.setCornerRadii(SettingsStyle.corners(place, Math.max(0f, radius - border)));

        // The divider needs LayerDrawable.setLayerGravity, which exists from Android 6.
        boolean divider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !SettingsStyle.hasBottomEdge(place);
        Drawable[] layers;
        if (divider) {
            GradientDrawable line = new GradientDrawable();
            line.setColor(SettingsStyle.DIVIDER);
            layers = new Drawable[]{outer, inner, line};
        } else {
            layers = new Drawable[]{outer, inner};
        }
        LayerDrawable card = new LayerDrawable(layers);
        card.setLayerInset(1, border, SettingsStyle.hasTopEdge(place) ? border : 0,
                border, SettingsStyle.hasBottomEdge(place) ? border : 0);
        if (divider) {
            int start = dp(14, density) + (hasIcon ? chip + dp(14, density) : 0);
            card.setLayerInset(2, start, 0, dp(14, density), 0);
            card.setLayerGravity(2, Gravity.BOTTOM);
            card.setLayerHeight(2, Math.max(1, Math.round(density * 0.6f)));
        }

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(SettingsStyle.corners(place, radius));
        return new RippleDrawable(ColorStateList.valueOf(SettingsStyle.withAlpha(accent, 0x1F)), card, mask);
    }

    private void styleIcon(PreferenceViewHolder holder, boolean hasIcon, int chip, float density,
                           int tint, boolean enabled, boolean on) {
        View view = holder.findViewById(android.R.id.icon);
        if (!(view instanceof ImageView) || !hasIcon) return;
        ImageView icon = (ImageView) view;
        ViewGroup.LayoutParams params = icon.getLayoutParams();
        if (params != null && (params.width != chip || params.height != chip)) {
            params.width = chip;
            params.height = chip;
            icon.setLayoutParams(params);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(compact ? 7 : 8, density);
        icon.setPadding(pad, pad, pad, pad);
        GradientDrawable chipBackground = new GradientDrawable();
        chipBackground.setColor(enabled ? SettingsStyle.withAlpha(tint, on ? 0x2E : 0x1A) : SettingsStyle.DIVIDER);
        chipBackground.setCornerRadius(dp(10, density));
        icon.setBackground(chipBackground);
        icon.setImageTintList(ColorStateList.valueOf(enabled ? tint : SettingsStyle.TEXT_DISABLED));
    }

    private void styleSwitch(PreferenceViewHolder holder) {
        View view = holder.findViewById(androidx.preference.R.id.switchWidget);
        if (!(view instanceof SwitchCompat)) return;
        SwitchCompat toggle = (SwitchCompat) view;
        int[][] states = {{android.R.attr.state_checked}, new int[0]};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFFFFFFF}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[]{SettingsStyle.withAlpha(accent, 0xB0), SettingsStyle.TRACK_OFF}));
    }

    private void styleSlider(PreferenceViewHolder holder, Preference preference, float density) {
        if (!(preference instanceof CustomSeekBarPreference)) return;
        View bar = holder.findViewById(androidx.preference.R.id.seekbar);
        if (bar instanceof SeekBar) {
            SeekBar seekBar = (SeekBar) bar;
            ColorStateList color = ColorStateList.valueOf(accent);
            seekBar.setProgressTintList(color);
            seekBar.setThumbTintList(color);
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(SettingsStyle.TRACK_BACKGROUND));
        }
        View valueView = holder.findViewById(androidx.preference.R.id.seekbar_value);
        if (valueView instanceof TextView) {
            TextView value = (TextView) valueView;
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(SettingsStyle.withAlpha(accent, 0x1A));
            pill.setCornerRadius(dp(20, density));
            value.setBackground(pill);
            value.setTextColor(accent);
            value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            value.setPaddingRelative(dp(12, density), dp(4, density), dp(12, density), dp(4, density));
        }
    }

    /** A row fades in the first time it is shown; scrolling it back later does not repeat it. */
    private void fadeIn(View item, Preference preference, int position) {
        if (reduceMotion || !entered.add(preference)) return;
        item.setAlpha(0f);
        item.animate().alpha(1f)
                .setStartDelay(Math.min(position, MAX_STAGGER_STEPS) * 20L)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();
    }

    private static int dp(int value, float density) {
        return Math.round(value * density);
    }
}
