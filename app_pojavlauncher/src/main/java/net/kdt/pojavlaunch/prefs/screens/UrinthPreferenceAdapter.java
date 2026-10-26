package net.kdt.pojavlaunch.prefs.screens;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import git.artdeell.mojo.R;

/**
 * Gives every row of the settings list the launcher's card look, without changing what the rows
 * do: a dark glass card (lit green for a switch that is on), an icon in a tinted rounded chip, a
 * coloured section header, a pill around slider values, an entrance slide-in the first time a
 * row appears, a small press effect and a pulse when a switch is turned on.
 *
 * Each section of the settings gets its own accent colour; rows take the colour of the section
 * they are in. The animations use view property animators, so with "Animator duration scale"
 * turned off they simply end instantly and the rows are still fully visible.
 */
@SuppressLint("RestrictedApi")
final class UrinthPreferenceAdapter extends PreferenceGroupAdapter {
    private static final int DEFAULT_ACCENT = 0xFF1FE0B4;
    private static final int[] SECTION_COLORS = {
            0xFF1FE0B4, // teal
            0xFF4FD8FF, // cyan
            0xFFFFC83C, // amber
            0xFFB58CFF, // violet
            0xFFFF6B8A, // coral
    };
    private static final int TITLE_COLOR = 0xFFF2F8FA;
    private static final int SUMMARY_COLOR = 0xFF8FA6B3;
    private static final int MAX_STAGGER_STEPS = 8;

    private final Map<Preference, Integer> accents = new HashMap<>();
    private final Set<Preference> entered = new HashSet<>();
    private final Set<Preference> lastOn = new HashSet<>();

    UrinthPreferenceAdapter(@NonNull PreferenceGroup group) {
        super(group);
        int section = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference child = group.getPreference(i);
            if (child instanceof PreferenceGroup) {
                int color = SECTION_COLORS[section++ % SECTION_COLORS.length];
                paintGroup((PreferenceGroup) child, color);
            } else {
                accents.put(child, DEFAULT_ACCENT);
            }
        }
    }

    private void paintGroup(PreferenceGroup group, int color) {
        accents.put(group, color);
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference child = group.getPreference(i);
            if (child instanceof PreferenceGroup) paintGroup((PreferenceGroup) child, color);
            else accents.put(child, color);
        }
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
        if (preference == null) return;

        int accent = accentOf(preference);
        if (preference instanceof PreferenceCategory) {
            styleHeader(holder, accent);
            return;
        }
        styleRow(holder, preference, accent);
        animateIn(item, preference, position);
    }

    private int accentOf(Preference preference) {
        Integer color = accents.get(preference);
        return color == null ? DEFAULT_ACCENT : color;
    }

    private void styleHeader(PreferenceViewHolder holder, int accent) {
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) title.setTextColor(accent);
        View bar = holder.findViewById(R.id.urinth_header_bar);
        if (bar != null) bar.setBackgroundTintList(ColorStateList.valueOf(accent));
        View line = holder.findViewById(R.id.urinth_header_line);
        if (line != null) line.setBackgroundTintList(ColorStateList.valueOf(withAlpha(accent, 0xAA)));
    }

    private void styleRow(PreferenceViewHolder holder, Preference preference, int accent) {
        View item = holder.itemView;
        boolean on = preference instanceof SwitchPreferenceCompat && ((SwitchPreferenceCompat) preference).isChecked();
        item.setBackgroundResource(on ? R.drawable.urinth_pref_card_on : R.drawable.urinth_pref_card);

        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) title.setTextColor(TITLE_COLOR);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) summary.setTextColor(SUMMARY_COLOR);

        ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
        if (icon != null && icon.getDrawable() != null) {
            float density = item.getResources().getDisplayMetrics().density;
            GradientDrawable chip = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{withAlpha(accent, on ? 0x66 : 0x48), withAlpha(accent, 0x14)});
            chip.setCornerRadius(10f * density);
            chip.setStroke(Math.max(1, Math.round(density)), withAlpha(accent, on ? 0xB0 : 0x70));
            icon.setBackground(chip);
            int pad = Math.round(7f * density);
            icon.setPadding(pad, pad, pad, pad);
            icon.setImageTintList(ColorStateList.valueOf(accent));
        }

        if (preference instanceof CustomSeekBarPreference) {
            TextView value = (TextView) holder.findViewById(R.id.seekbar_value);
            if (value != null) {
                float density = item.getResources().getDisplayMetrics().density;
                value.setBackgroundResource(R.drawable.urinth_pref_value_pill);
                value.setTextColor(accent);
                value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
                int sideways = Math.round(12f * density);
                int upright = Math.round(4f * density);
                value.setPadding(sideways, upright, sideways, upright);
                value.setBackgroundTintList(null);
            }
        }

        pulseIfTurnedOn(item, preference, on);
        addPressEffect(item);
    }

    /** One small pulse when a switch has just been turned on. */
    private void pulseIfTurnedOn(View item, Preference preference, boolean on) {
        if (!(preference instanceof SwitchPreferenceCompat)) return;
        boolean was = lastOn.contains(preference);
        if (on) lastOn.add(preference); else lastOn.remove(preference);
        if (on && !was && entered.contains(preference)) {
            item.animate().scaleX(1.025f).scaleY(1.025f).setDuration(110)
                    .withEndAction(() -> item.animate().scaleX(1f).scaleY(1f).setDuration(170).start())
                    .start();
        }
    }

    private void animateIn(View item, Preference preference, int position) {
        if (!entered.add(preference)) return;
        float density = item.getResources().getDisplayMetrics().density;
        item.setAlpha(0f);
        item.setTranslationY(18f * density);
        item.animate().alpha(1f).translationY(0f)
                .setStartDelay((position % MAX_STAGGER_STEPS) * 28L)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addPressEffect(View item) {
        item.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    break;
                default:
                    break;
            }
            return false; // the press itself is still handled by the row
        });
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
