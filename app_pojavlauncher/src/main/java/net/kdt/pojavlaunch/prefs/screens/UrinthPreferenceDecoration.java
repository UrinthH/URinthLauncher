package net.kdt.pojavlaunch.prefs.screens;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Side margins of the settings list, so the white cards sit inside the page with room around them. */
final class UrinthPreferenceDecoration extends RecyclerView.ItemDecoration {
    private final int sideways;

    UrinthPreferenceDecoration(float density) {
        sideways = Math.round(16f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.set(sideways, 0, sideways, 0);
    }
}
