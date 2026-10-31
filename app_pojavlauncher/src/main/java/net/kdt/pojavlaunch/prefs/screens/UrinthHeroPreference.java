package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.content.ui.Fx;
import net.kdt.pojavlaunch.content.ui.NeonBanner;
import net.kdt.pojavlaunch.content.ui.Motion;

/** The first row of the settings: the animated banner. It holds no setting and cannot be clicked. */
final class UrinthHeroPreference extends Preference {
    private boolean shown;

    UrinthHeroPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_urinth_hero);
        setKey("urinth_settings_hero");
        setSelectable(false);
        setPersistent(false);
        setOrder(-1);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        NeonBanner banner = (NeonBanner) holder.findViewById(R.id.urinth_hero);
        if (banner == null) return;
        Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_dl_filter);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(Fx.ACCENT);
        }
        banner.setContent(icon, "Settings", "Video, controls, performance and more", Fx.ACCENT);
        if (!shown) {
            shown = true;
            Motion.fadeIn(holder.itemView, 0);
        }
    }
}
