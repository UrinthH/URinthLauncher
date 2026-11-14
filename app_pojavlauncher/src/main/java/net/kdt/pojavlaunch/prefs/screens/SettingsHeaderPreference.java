package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import git.artdeell.mojo.R;

/**
 * The first row of the settings: the title and three chips (graphics, memory, Performance Mode)
 * that show the current state. It holds no setting and cannot be clicked.
 */
final class SettingsHeaderPreference extends Preference {
    private CharSequence graphics = "";
    private CharSequence memory = "";
    private CharSequence mode = "";

    SettingsHeaderPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_settings_header);
        setKey("urinth_settings_header");
        setSelectable(false);
        setPersistent(false);
        setOrder(-1);
    }

    /** Replaces the three chip texts and redraws the row. */
    void setChips(CharSequence graphics, CharSequence memory, CharSequence mode) {
        this.graphics = graphics;
        this.memory = memory;
        this.mode = mode;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int accent = SettingsStyle.accentColor(getSharedPreferences());
        bindChip(holder, R.id.settings_chip_graphics, R.drawable.ic_set_renderer, graphics, accent);
        bindChip(holder, R.id.settings_chip_memory, R.drawable.ic_set_memory, memory, accent);
        bindChip(holder, R.id.settings_chip_mode, R.drawable.ic_set_bolt, mode, accent);
    }

    private void bindChip(PreferenceViewHolder holder, int id, @DrawableRes int icon, CharSequence text, int accent) {
        TextView chip = (TextView) holder.findViewById(id);
        if (chip == null) return;
        float density = getContext().getResources().getDisplayMetrics().density;
        chip.setText(text);

        GradientDrawable background = new GradientDrawable();
        background.setColor(SettingsStyle.CARD);
        background.setCornerRadius(20f * density);
        background.setStroke(Math.max(1, Math.round(density * 0.6f)), SettingsStyle.BORDER);
        chip.setBackground(background);

        Drawable glyph = ContextCompat.getDrawable(getContext(), icon);
        if (glyph != null) {
            glyph = glyph.mutate();
            glyph.setTint(accent);
            int size = Math.round(16f * density);
            glyph.setBounds(0, 0, size, size);
            chip.setCompoundDrawablesRelative(glyph, null, null, null);
            chip.setCompoundDrawablePadding(Math.round(7f * density));
        }
    }
}
