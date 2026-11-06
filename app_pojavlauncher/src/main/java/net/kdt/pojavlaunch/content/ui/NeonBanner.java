package net.kdt.pojavlaunch.content.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * The title banner of the settings screens: a white rounded card with a hairline edge, a mint icon tile,
 * a title and a subtitle. It is still: no sweep, no pulsing edge, no frame loop.
 */
public final class NeonBanner extends View {
    private static final long CYCLE_MS = 5200L;
    private static final float SWEEP_PART = 0.42f; // the first part of each cycle is the sweep

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final RectF tileBounds = new RectF();
    private final Path clip = new Path();
    private final Matrix sweepMatrix = new Matrix();
    private LinearGradient sweepShader, underlineShader;
    private final Matrix underlineMatrix = new Matrix();
    private float lineLength;

    private Drawable icon;
    private String title = "";
    private String subtitle = "";
    private int accent = Fx.ACCENT;
    private boolean running;
    private float phase;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            phase = (SystemClock.uptimeMillis() % CYCLE_MS) / (float) CYCLE_MS;
            invalidate();
            postOnAnimation(this);
        }
    };

    public NeonBanner(Context context) {
        super(context);
        init();
    }

    public NeonBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, Fx.dp(getContext(), 1.2f)));
        titlePaint.setColor(Fx.TEXT);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        titlePaint.setTextSize(sp(21));
        subtitlePaint.setColor(Fx.MUTED);
        subtitlePaint.setTextSize(sp(13));
        underline.setStyle(Paint.Style.STROKE);
        underline.setStrokeCap(Paint.Cap.ROUND);
        underline.setStrokeWidth(Fx.dp(getContext(), 2));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    /** @param icon drawable shown in the tile (may be null); it is drawn as it is, so tint it first if it should match */
    public void setContent(Drawable icon, String title, String subtitle, int accent) {
        // Own copy: the banner sets the bounds of what it draws, and the same drawable may be shown elsewhere.
        if (icon != null && icon.getConstantState() != null) {
            icon = icon.getConstantState().newDrawable(getResources()).mutate();
        }
        this.icon = icon;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.accent = accent;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        float stroke = edge.getStrokeWidth();
        bounds.set(stroke, stroke, w - stroke, h - stroke);
        float radius = Fx.dp(getContext(), 18);
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        fill.setColor(0xFFFFFFFF);
        float band = w * 0.30f;
        sweepShader = new LinearGradient(0, 0, band, 0,
                new int[]{0x00FFFFFF, withAlpha(accent, 0x48), 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        sweep.setShader(sweepShader);

        Context context = getContext();
        float pad = Fx.dp(context, 12);
        float tileSize = h - 2 * pad;
        tileBounds.set(pad, pad, pad + tileSize, pad + tileSize);
        tile.setColor(0xFFE4F7EE);
        lineLength = w * 0.22f;
        underlineShader = new LinearGradient(0, 0, lineLength, 0,
                new int[]{0x00FFFFFF, accent, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        underline.setShader(underlineShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        Context context = getContext();
        float radius = Fx.dp(context, 18);
        canvas.drawRoundRect(bounds, radius, radius, fill);

        edge.setColor(0xFFE1E8EF);
        canvas.drawRoundRect(bounds, radius, radius, edge);

        // Icon tile.
        float pad = Fx.dp(context, 12);
        float tileSize = tileBounds.height();
        canvas.drawRoundRect(tileBounds, Fx.dp(context, 12), Fx.dp(context, 12), tile);
        if (icon != null) {
            int inset = Math.round(tileSize * 0.22f);
            icon.setBounds(Math.round(tileBounds.left) + inset, Math.round(tileBounds.top) + inset,
                    Math.round(tileBounds.right) - inset, Math.round(tileBounds.bottom) - inset);
            icon.draw(canvas);
        }

        // Title and the line under it.
        float textLeft = tileBounds.right + Fx.dp(context, 14);
        float available = w - textLeft - pad;
        CharSequence shownTitle = TextUtils.ellipsize(title, titlePaint, available, TextUtils.TruncateAt.END);
        CharSequence shownSubtitle = TextUtils.ellipsize(subtitle, subtitlePaint, available, TextUtils.TruncateAt.END);
        Paint.FontMetrics titleMetrics = titlePaint.getFontMetrics();
        Paint.FontMetrics subtitleMetrics = subtitlePaint.getFontMetrics();
        float titleHeight = titleMetrics.descent - titleMetrics.ascent;
        float subtitleHeight = subtitleMetrics.descent - subtitleMetrics.ascent;
        float gap = Fx.dp(context, 6);
        float block = titleHeight + gap + subtitleHeight;
        float top = (h - block) / 2f;
        canvas.drawText(shownTitle, 0, shownTitle.length(), textLeft, top - titleMetrics.ascent, titlePaint);
        canvas.drawText(shownSubtitle, 0, shownSubtitle.length(), textLeft, top + titleHeight + gap - subtitleMetrics.ascent, subtitlePaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
