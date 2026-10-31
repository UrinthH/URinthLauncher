package net.kdt.pojavlaunch.content.ui;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Small animations shared by the settings screens. They use view property animators, so they end instantly when animations are off. */
public final class Motion {
    private static final int MAX_STAGGER = 9;

    private Motion() {}

    /** Slides a view up into place with a little overshoot, {@code index} steps after the first. */
    public static void enter(View view, int index) {
        float density = view.getResources().getDisplayMetrics().density;
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(20f * density);
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(Math.min(index, MAX_STAGGER) * 45L)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .start();
    }

    /** Fades a view in without moving it. */
    public static void fadeIn(View view, long delay) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.animate().alpha(1f).setStartDelay(delay).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** The view shrinks a little while it is pressed. The click itself is not touched. */
    @SuppressLint("ClickableViewAccessibility")
    public static void press(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new OvershootInterpolator(2f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** A quick pop, used when a number appears. */
    public static void pop(View view) {
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.4f)).start();
    }
}
