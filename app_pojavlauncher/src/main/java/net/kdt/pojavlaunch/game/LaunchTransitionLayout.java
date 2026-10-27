package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

import git.artdeell.mojo.R;

/**
 * The Play animation. A tiny rounded square (a live miniature of the game's loading screen) pops
 * up in the middle of the launcher, holds for a second while the launcher dims, then the camera
 * zooms into it: the launcher behind scales up and fades, light streaks fly outward, and the
 * square grows until it is the full-screen loading screen. Timing and sizes come from
 * {@link LaunchZoomMath}.
 *
 * <p>The miniature is the real {@link LaunchLoadingView}, laid out at full-screen size and only
 * scaled and clipped to a rounded rectangle, so nothing is laid out again while it grows and the
 * last frame is exactly the loading screen the game shows next. The launcher behind it is a
 * half-resolution snapshot taken when Play was tapped, so the zoom does not redraw the whole
 * launcher on every frame.
 *
 * <p>Drawn from a Choreographer frame clock (one frame per screen refresh, and unaffected by the
 * system's animator duration scale). It stops itself when the zoom ends.
 */
public final class LaunchTransitionLayout extends FrameLayout implements Choreographer.FrameCallback {
    private static final int COLOR_BG = 0xFF060E14;
    private static final int COLOR_TEAL = 0xFF3CE6C0;
    private static final int COLOR_CYAN = 0xFF4FD8FF;
    private static final int STREAKS = 44;
    private static final float TINY_DP = 64f;
    private static final float RADIUS_DP = 16f;

    private final float density;
    private final Bitmap snapshot;
    private final LaunchLoadingView content;
    private final TextView statusText;
    private final View textBlockView;
    private final String idleStatus;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float[] streakCos = new float[STREAKS];
    private final float[] streakSin = new float[STREAKS];
    private final float[] streakLength = new float[STREAKS];

    // Outline of the miniature, in the miniature's own (unscaled) coordinates.
    private float outlineLeft;
    private float outlineRight;
    private float outlineBottom;
    private float outlineRadius;

    private boolean running;
    private long startNs;
    private float timeMs;
    private boolean zoomFinished;
    private Runnable afterZoom;

    // State computed each frame for drawing.
    private float rectW;
    private float rectH;
    private float radius;
    private float pop;
    private float u;
    private float e;

    public LaunchTransitionLayout(Context context, Bitmap snapshot, String version,
                                  boolean performanceMode, long originWallMs) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        this.snapshot = snapshot;
        setWillNotDraw(false);
        setClickable(true); // swallow touches so the launcher behind cannot be tapped

        content = new LaunchLoadingView(context);
        content.setInfo(version, performanceMode);
        content.setRain(performanceMode);
        content.setTimeOriginWallMs(originWallMs);
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(Math.round(outlineLeft), 0, Math.round(outlineRight),
                        Math.round(outlineBottom), outlineRadius);
            }
        });
        content.setClipToOutline(true);
        content.setVisibility(INVISIBLE);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The same text block the game's loading screen has, in the same place and style, so that
        // when the game's screen takes over the words do not move. The second line is only there
        // to take up its space; the game fades it in.
        idleStatus = context.getString(R.string.loading_stage_prepare);
        LinearLayout textBlock = new LinearLayout(context);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER);
        statusText = new TextView(context);
        statusText.setTextColor(0xFF3CE6C0);
        statusText.setTextSize(13f);
        statusText.setLetterSpacing(0.12f);
        statusText.setAllCaps(true);
        statusText.setTypeface(statusText.getTypeface(), android.graphics.Typeface.BOLD);
        statusText.setText(idleStatus);
        textBlock.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView spacer = new TextView(context);
        spacer.setTextSize(11f);
        spacer.setText(context.getString(R.string.loading_stage_elapsed, "0:00"));
        spacer.setVisibility(INVISIBLE);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spacerParams.topMargin = Math.round(density * 4f);
        textBlock.addView(spacer, spacerParams);
        textBlock.setAlpha(0f);
        textBlockView = textBlock;
        LayoutParams blockParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        blockParams.bottomMargin = Math.round(density * 64f);
        addView(textBlock, blockParams);

        Random random = new Random(5);
        for (int i = 0; i < STREAKS; i++) {
            double angle = (i + random.nextFloat() * 0.8f) * (2 * Math.PI / STREAKS);
            streakCos[i] = (float) Math.cos(angle);
            streakSin[i] = (float) Math.sin(angle);
            streakLength[i] = 0.25f + 0.75f * random.nextFloat();
        }
    }

    // ---- control ----------------------------------------------------------------------------

    /** Start the animation. Call once, after the layout has been added to the screen. */
    public void begin() {
        if (running) return;
        running = true;
        startNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** Stop and let go of the frame clock. Safe to call more than once. */
    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        afterZoom = null;
    }

    public boolean isZoomFinished() {
        return zoomFinished;
    }

    /** Run this once the square fills the screen (right away if it already does). */
    public void runAfterZoom(Runnable runnable) {
        if (zoomFinished) post(runnable);
        else afterZoom = runnable;
    }

    /** A line of text shown under the loading screen once it fills the screen, e.g. download progress. */
    public void setStatus(CharSequence text) {
        statusText.setText(text == null || text.length() == 0 ? idleStatus : text);
    }

    // ---- frame clock ------------------------------------------------------------------------

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (startNs == 0) startNs = frameTimeNanos;
        timeMs = (frameTimeNanos - startNs) / 1_000_000f;
        update((long) timeMs);
        invalidate();
        if (timeMs >= LaunchZoomMath.TOTAL_MS) {
            running = false;
            zoomFinished = true;
            // The words under the logo arrive as the square lands, as they do on the game's screen.
            textBlockView.animate().alpha(1f).setDuration(250).start();
            Runnable callback = afterZoom;
            afterZoom = null;
            if (callback != null) post(callback);
            return;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** Work out this moment's sizes and move the miniature to match. */
    private void update(long elapsedMs) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        pop = LaunchZoomMath.pop(elapsedMs);
        u = LaunchZoomMath.zoomProgress(elapsedMs);
        e = LaunchZoomMath.zoomEase(u);

        float tiny = density * TINY_DP;
        if (e <= 0f) {
            // Pop and hold: the square breathes a little.
            float breathe = 1f + 0.035f * (float) Math.sin(elapsedMs / 230f) * LaunchZoomMath.smooth(pop);
            rectW = tiny * pop * breathe;
            rectH = rectW;
        } else {
            rectW = LaunchZoomMath.rectSize(tiny, w, e);
            rectH = LaunchZoomMath.rectSize(tiny, h, e);
        }
        radius = Math.min(LaunchZoomMath.cornerRadius(density * RADIUS_DP * pop, e), Math.min(rectW, rectH) / 2f);

        float k = rectH / h; // the miniature is laid out at full size and scaled down by this
        if (k < 0.002f) {
            content.setVisibility(INVISIBLE);
            return;
        }
        content.setVisibility(VISIBLE);
        content.setAlpha(LaunchZoomMath.clamp01(pop * 2.5f));
        content.setPivotX(w / 2f);
        content.setPivotY(h / 2f);
        content.setScaleX(k);
        content.setScaleY(k);

        float halfWidth = rectW / (2f * k);
        outlineLeft = w / 2f - halfWidth;
        outlineRight = w / 2f + halfWidth;
        outlineBottom = h;
        outlineRadius = radius / k;
        content.invalidateOutline();


    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(COLOR_BG);

        // The launcher as it was when Play was tapped: it dims, then the camera pushes in on it.
        float backdropAlpha = LaunchZoomMath.backdropAlpha(e);
        if (snapshot != null && backdropAlpha > 0.003f) {
            float zoomScale = LaunchZoomMath.backdropScale(e);
            float drift = 1f + 0.03f * LaunchZoomMath.smooth(timeMs / LaunchZoomMath.HOLD_END_MS);
            float scale = zoomScale * drift * (w / (float) snapshot.getWidth());
            paint.setAlpha(Math.round(255 * backdropAlpha));
            int save = canvas.save();
            canvas.translate(w / 2f, h / 2f);
            canvas.scale(scale, scale);
            canvas.translate(-snapshot.getWidth() / 2f, -snapshot.getHeight() / 2f);
            canvas.drawBitmap(snapshot, 0, 0, paint);
            canvas.restoreToCount(save);
            // Dim the launcher so the square is what the eye goes to.
            paint.setColor(Color.BLACK);
            paint.setAlpha(Math.round(255 * 0.55f * pop * backdropAlpha));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setAlpha(255);
        }

        drawStreaks(canvas, w, h);
    }

    /** Light streaks fly out from the square during the zoom. */
    private void drawStreaks(Canvas canvas, int w, int h) {
        float amount = LaunchZoomMath.streakAmount(u);
        if (amount <= 0.01f) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float inner = 0.5f * (float) Math.hypot(rectW, rectH) * 0.85f;
        float reach = Math.max(w, h) * 0.6f;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeWidth(density * 1.3f);
        for (int i = 0; i < STREAKS; i++) {
            float start = inner + reach * 0.05f * ((i * 5) % 7) / 7f * amount;
            float end = start + reach * streakLength[i] * amount;
            int color = (i % 3 == 0) ? Color.WHITE : (i % 2 == 0 ? COLOR_TEAL : COLOR_CYAN);
            strokePaint.setColor((color & 0x00FFFFFF) | (Math.round(255 * 0.45f * amount) << 24));
            canvas.drawLine(cx + streakCos[i] * start, cy + streakSin[i] * start,
                    cx + streakCos[i] * end, cy + streakSin[i] * end, strokePaint);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || pop <= 0f) return;

        float cx = w / 2f;
        float cy = h / 2f;
        rect.set(cx - rectW / 2f, cy - rectH / 2f, cx + rectW / 2f, cy + rectH / 2f);

        // The glowing edge of the square: gone by the time it fills the screen.
        float edge = LaunchZoomMath.clamp01(1f - e * 1.15f);
        if (edge > 0.01f) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(withAlpha(COLOR_CYAN, 0.07f * edge));
            strokePaint.setStrokeWidth(density * 14f);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
            strokePaint.setColor(withAlpha(COLOR_CYAN, 0.14f * edge));
            strokePaint.setStrokeWidth(density * 7f);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
            strokePaint.setColor(withAlpha(COLOR_TEAL, 0.95f * edge));
            strokePaint.setStrokeWidth(density * 1.8f);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }

        // One ring of light leaves the square as it pops in.
        float ringP = LaunchZoomMath.clamp01(timeMs / 520f);
        if (ringP > 0f && ringP < 1f) {
            float tiny = density * TINY_DP;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(density * 2f);
            strokePaint.setColor(withAlpha(COLOR_CYAN, 0.6f * (1f - ringP)));
            float r = tiny * (0.5f + 1.2f * LaunchZoomMath.smooth(ringP));
            canvas.drawCircle(cx, cy, r, strokePaint);
        }
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(LaunchZoomMath.clamp01(alpha) * Color.alpha(color));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
