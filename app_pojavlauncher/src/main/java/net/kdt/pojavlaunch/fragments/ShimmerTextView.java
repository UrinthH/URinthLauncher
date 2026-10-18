package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * A text view whose text is painted with a repeating, slowly-sliding rainbow-ish gradient —
 * a "glass diamond" / LED-sign shimmer rather than a flat color. Used for the credit line in the
 * top bar.
 *
 * Drives its own frame loop with {@link View#postOnAnimation(Runnable)} rather than the Android
 * animation framework, for the same reason as the home screen's glow pulse and particle drift:
 * it keeps animating even when a device has "Animator duration scale" turned off.
 */
public final class ShimmerTextView extends AppCompatTextView {

    private static final int[] SHIMMER_COLORS = {
            0xFFFFFFFF, 0xFF8FE8FF, 0xFFB388FF, 0xFFFF9BE0, 0xFFFFE28A, 0xFFFFFFFF,
    };

    private final Matrix shaderMatrix = new Matrix();
    private LinearGradient shader;
    private float shimmerWidth;
    private long startNanos;
    private boolean running;

    private final Runnable frameCallback = this::onFrame;

    public ShimmerTextView(Context context) {
        super(context);
    }

    public ShimmerTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w <= 0) return;
        // The gradient repeats across 2x the view width so the slide loops seamlessly.
        shimmerWidth = w * 2f;
        shader = new LinearGradient(0, 0, shimmerWidth, 0, SHIMMER_COLORS, null, Shader.TileMode.REPEAT);
        getPaint().setShader(shader);
    }

    public void start() {
        if (running) return;
        running = true;
        startNanos = 0L;
        removeCallbacks(frameCallback);
        postOnAnimation(frameCallback);
    }

    public void stop() {
        running = false;
        removeCallbacks(frameCallback);
    }

    private void onFrame() {
        if (!running) return;
        if (startNanos == 0L) startNanos = System.nanoTime();
        if (shader != null && shimmerWidth > 0f) {
            float elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000f;
            // A full loop every ~2.6s; slow enough to read as a glint, not a strobe.
            float progress = (elapsedSeconds % 2.6f) / 2.6f;
            shaderMatrix.setTranslate(progress * shimmerWidth, 0);
            shader.setLocalMatrix(shaderMatrix);
            invalidate();
        }
        postOnAnimation(frameCallback);
    }
}
