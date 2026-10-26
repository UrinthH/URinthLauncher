package net.kdt.pojavlaunch.prefs.screens;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Spacing around the settings cards, so each row reads as its own card. */
final class UrinthPreferenceDecoration extends RecyclerView.ItemDecoration {
    private final int sideways;
    private final int upright;

    UrinthPreferenceDecoration(float density) {
        sideways = Math.round(8f * density);
        upright = Math.round(3f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.set(sideways, upright, sideways, upright);
    }
}
