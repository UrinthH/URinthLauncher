package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import net.kdt.pojavlaunch.game.renderer.GraphicsApi;

import git.artdeell.mojo.R;

/**
 * The OpenGL | Vulkan switch. It stores nothing itself: a tap is handed to the listener (see
 * LauncherPreferenceFragment), which decides what to do with the renderer setting, and then calls
 * {@link #setState} with what is now in use.
 */
public class GraphicsApiPreference extends Preference {
    public interface Listener {
        void onApiTapped(@NonNull GraphicsApi api);
    }

    private GraphicsApi selected = GraphicsApi.OPENGL;
    private boolean vulkanDimmed;
    private CharSequence status = "";
    @Nullable private Listener listener;

    public GraphicsApiPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphicsApiPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_graphics_api);
        setSelectable(false);
        setPersistent(false);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * @param selected the API of the renderer in use
     * @param vulkanDimmed show the Vulkan segment dimmed (Vulkan cannot be used now); it can still be tapped to learn why
     * @param status the text under the switch
     */
    public void setState(@NonNull GraphicsApi selected, boolean vulkanDimmed, @NonNull CharSequence status) {
        this.selected = selected;
        this.vulkanDimmed = vulkanDimmed;
        this.status = status;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int accent = SettingsStyle.accentColor(getSharedPreferences());
        float density = getContext().getResources().getDisplayMetrics().density;

        View segments = holder.findViewById(R.id.graphics_api_segments);
        if (segments != null) {
            GradientDrawable track = new GradientDrawable();
            track.setColor(0xFFF2F4F7);
            track.setCornerRadius(14f * density);
            segments.setBackground(track);
        }
        bindSegment(holder, R.id.graphics_api_opengl, R.id.graphics_api_opengl_icon,
                R.id.graphics_api_opengl_label, GraphicsApi.OPENGL, false, accent, density);
        bindSegment(holder, R.id.graphics_api_vulkan, R.id.graphics_api_vulkan_icon,
                R.id.graphics_api_vulkan_label, GraphicsApi.VULKAN, vulkanDimmed, accent, density);

        TextView statusView = (TextView) holder.findViewById(R.id.graphics_api_status);
        if (statusView != null) statusView.setText(status);
    }

    private void bindSegment(PreferenceViewHolder holder, int segmentId, int iconId, int labelId,
                             GraphicsApi api, boolean dimmed, int accent, float density) {
        View segment = holder.findViewById(segmentId);
        ImageView icon = (ImageView) holder.findViewById(iconId);
        TextView label = (TextView) holder.findViewById(labelId);
        if (segment == null || icon == null || label == null) return;
        boolean on = api == selected;

        if (on) {
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(SettingsStyle.CARD);
            pill.setCornerRadius(11f * density);
            pill.setStroke(Math.max(1, Math.round(density * 0.6f)), SettingsStyle.BORDER);
            segment.setBackground(pill);
        } else {
            segment.setBackground(null);
        }
        int color = on ? accent : SettingsStyle.MUTED;
        icon.setImageTintList(ColorStateList.valueOf(color));
        label.setTextColor(color);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        segment.setAlpha(dimmed && !on ? 0.55f : 1f);
        segment.setSelected(on);
        segment.setContentDescription(label.getText());
        segment.setOnClickListener(v -> {
            if (listener != null) listener.onApiTapped(api);
        });
    }
}
