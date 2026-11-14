package net.kdt.pojavlaunch.prefs.screens;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Side margins of the settings list, so the white cards sit inside the page with room around them.
 * On a wide screen (a phone held sideways, a tablet) the cards stop at a comfortable width and stay
 * in the middle instead of stretching across the whole screen.
 */
final class UrinthPreferenceDecoration extends RecyclerView.ItemDecoration {
    private static final int MAX_CONTENT_DP = 720;
    private final int sideways;
    private final int maxContent;

    UrinthPreferenceDecoration(float density) {
        sideways = Math.round(16f * density);
        maxContent = Math.round(MAX_CONTENT_DP * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int side = sideways + SettingsStyle.extraSide(parent.getWidth(), sideways, maxContent);
        outRect.set(side, 0, side, 0);
    }
}
