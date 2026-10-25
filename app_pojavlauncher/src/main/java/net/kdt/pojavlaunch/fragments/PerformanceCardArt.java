package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

/**
 * Decoration behind the Performance Mode card's content: a hexagon pattern, HUD corner marks, a
 * soft glow behind the bolt chip, a row of meter bars, a fine top-edge highlight and a light
 * sweep. Everything is drawn from plain shapes. When the card is selected (Performance Mode on)
 * it comes alive: the bars move, the glow breathes and a band of light sweeps across now and then.
 * Nothing moves while the mode is off, the screen is not visible, or the view is detached.
 *
 * It follows the card's own selected state, which the parent hands down to its children, and it
 * assumes the card's content layout (8 dp padding, a 26 dp chip row above a 28 dp button).
 */
public final class PerformanceCardArt extends View {
    private static final int TEAL = 0xFF3CE6C0;
    private static final int GREEN = 0xFF1FC77A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BARS = 12;
    private static final float SWEEP_PERIOD_MS = 3800f;
    private static final float[] HEX_ALPHA_ON = {0.05f, 0.09f, 0.14f};
    private static final float[] HEX_ALPHA_OFF = {0.02f, 0.035f, 0.055f};

    private final float density;
    private final Paint hexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path[] hexBands = {new Path(), new Path(), new Path()};
    private final Path bracket = new Path();
    private final RectF rect = new RectF();

    private final float[] barPhase = new float[BARS];
    private final float[] barSpeed = new float[BARS];
    private final float[] barIdle = new float[BARS];

    private float timeMs;
    private long lastFrameNanos;
    private boolean ticking;
    private boolean skipFrame;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!ticking) return;
            if (lastFrameNanos != 0) {
                long step = Math.min(frameTimeNanos - lastFrameNanos, 100_000_000L);
                if (step > 0) timeMs += step / 1_000_000f;
            }
            lastFrameNanos = frameTimeNanos;
            skipFrame = !skipFrame;        // redraw every other frame: plenty for slow decoration
            if (!skipFrame) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public PerformanceCardArt(Context context) {
        this(context, null);
    }

    public PerformanceCardArt(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(Math.max(1f, density * 0.7f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        edgePaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < BARS; i++) {
            int h = BoostTimeline.hash(i * 31 + 7);
            barPhase[i] = (h & 255) / 255f * 6.2832f;
            barSpeed[i] = 0.0021f + ((h >>> 8) & 255) / 255f * 0.0032f;
            barIdle[i] = 0.16f + ((h >>> 16) & 255) / 255f * 0.2f;
        }
    }

    @Override
    public void setSelected(boolean selected) {
        boolean changed = isSelected() != selected;
        super.setSelected(selected);
        if (changed) {
            updateTicking();
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTicking();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicking();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTicking();
    }

    private void updateTicking() {
        boolean should = isSelected() && isAttachedToWindow() && getWindowVisibility() == VISIBLE;
        if (should && !ticking) {
            ticking = true;
            lastFrameNanos = 0;
            Choreographer.getInstance().postFrameCallback(frame);
        } else if (!should) {
            stopTicking();
        }
    }

    private void stopTicking() {
        ticking = false;
        Choreographer.getInstance().removeFrameCallback(frame);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, density * 10f, density * 10f, Path.Direction.CW);

        // Hexagon pattern on the right part of the card, in three bands that fade towards the left.
        for (Path band : hexBands) band.reset();
        float r = density * 9f;
        float dx = (float) Math.sqrt(3) * r;
        float dy = 1.5f * r;
        int row = 0;
        for (float y = -r; y < h + r; y += dy, row++) {
            float offset = (row % 2 == 0) ? 0f : dx / 2f;
            for (float x = w * 0.38f - dx + offset; x < w + dx; x += dx) {
                float fraction = x / w;
                Path band = hexBands[fraction < 0.58f ? 0 : (fraction < 0.78f ? 1 : 2)];
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI / 6 + i * Math.PI / 3;
                    float px = x + (float) Math.cos(angle) * r;
                    float py = y + (float) Math.sin(angle) * r;
                    if (i == 0) band.moveTo(px, py);
                    else band.lineTo(px, py);
                }
                band.close();
            }
        }

        // L-shaped corner mark, drawn at the origin and moved into place.
        bracket.reset();
        float arm = density * 8f;
        bracket.moveTo(0f, arm);
        bracket.lineTo(0f, 0f);
        bracket.lineTo(arm, 0f);

        glowPaint.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{0xFFFFFFFF, 0x66FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        sweepPaint.setShader(new LinearGradient(-1f, 0f, 1f, 0f,
                new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        edgePaint.setShader(new LinearGradient(0f, 0f, w, 0f,
                new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
    }

    private static int alpha(int color, float a) {
        int v = Math.round(BoostTimeline.clamp01(a) * Color.alpha(color));
        return (color & 0x00FFFFFF) | (v << 24);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        boolean on = isSelected();
        float t = timeMs;
        int accent = on ? TEAL : WHITE;

        int save = canvas.save();
        canvas.clipPath(clip);

        // Hexagon pattern: stronger towards the right edge.
        float[] bandAlpha = on ? HEX_ALPHA_ON : HEX_ALPHA_OFF;
        for (int i = 0; i < hexBands.length; i++) {
            hexPaint.setColor(alpha(accent, bandAlpha[i]));
            canvas.drawPath(hexBands[i], hexPaint);
        }

        // Soft glow behind the bolt chip (chip centre: 8 dp padding + half of 26 dp; row centred above the button).
        float rowHeight = h - density * (16f + 28f);
        float chipX = density * 21f;
        float chipY = density * 8f + Math.max(0f, rowHeight) / 2f;
        float glowRadius = density * (on ? 30f + 3f * (float) Math.sin(t * 0.003f) : 26f);
        glowPaint.setColor(on ? GREEN : TEAL);
        glowPaint.setAlpha(Math.round(255 * (on ? 0.30f + 0.08f * (float) Math.sin(t * 0.003f) : 0.07f)));
        canvas.save();
        canvas.translate(chipX, chipY);
        canvas.scale(glowRadius, glowRadius);
        canvas.drawCircle(0f, 0f, 1f, glowPaint);
        canvas.restore();

        // Meter bars on the right, sitting on the line where the button begins.
        drawBars(canvas, w, h, on, t, rowHeight);

        // Light sweep: a slanted band of light crossing the card every few seconds while on.
        if (on) {
            float p = (t % SWEEP_PERIOD_MS) / SWEEP_PERIOD_MS;
            float travel = BoostTimeline.easeInOutCubic(BoostTimeline.clamp01(p / 0.45f)); // sweeps, then rests
            if (p < 0.45f) {
                float cx = -0.25f * w + 1.5f * w * travel;
                sweepPaint.setAlpha(Math.round(255 * 0.16f));
                canvas.save();
                canvas.translate(cx, h / 2f);
                canvas.rotate(18f);
                canvas.scale(w * 0.11f, h * 1.4f);
                canvas.drawRect(-1f, -1f, 1f, 1f, sweepPaint);
                canvas.restore();
            }
        }

        // Fine highlight along the top edge.
        edgePaint.setStrokeWidth(Math.max(1f, density * 1.1f));
        edgePaint.setAlpha(Math.round(255 * (on ? 0.7f : 0.16f)));
        canvas.drawLine(0f, density * 0.6f, w, density * 0.6f, edgePaint);
        edgePaint.setAlpha(255);

        // HUD marks in the top-left and bottom-right corners.
        linePaint.setStrokeWidth(Math.max(1f, density * 1.3f));
        linePaint.setColor(alpha(on ? TEAL : WHITE, on ? 0.75f : 0.2f));
        float inset = density * 3.5f;
        canvas.save();
        canvas.translate(inset, inset);
        canvas.drawPath(bracket, linePaint);
        canvas.restore();
        canvas.save();
        canvas.translate(w - inset, h - inset);
        canvas.rotate(180f);
        canvas.drawPath(bracket, linePaint);
        canvas.restore();

        canvas.restoreToCount(save);
    }

    private void drawBars(Canvas canvas, int w, int h, boolean on, float t, float rowHeight) {
        float left = w * 0.5f;
        float right = w - density * 10f;
        float gap = density * 2f;
        float barWidth = ((right - left) - gap * (BARS - 1)) / BARS;
        if (barWidth <= 0f || rowHeight <= 0f) return;
        float bottom = h - density * (8f + 28f + 3f);
        float maxHeight = rowHeight * 0.62f;
        fillPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BARS; i++) {
            float level;
            if (on) {
                // Alive: a restless meter that keeps near the top.
                float wave = 0.5f + 0.5f * (float) Math.sin(t * barSpeed[i] + barPhase[i]);
                level = 0.28f + 0.72f * wave;
            } else {
                level = barIdle[i];
            }
            float x = left + i * (barWidth + gap);
            float top = bottom - maxHeight * level;
            int color = on ? (i > BARS - 4 ? 0xFFFFC83C : (i % 2 == 0 ? TEAL : GREEN)) : WHITE;
            fillPaint.setColor(alpha(color, on ? 0.28f : 0.07f));
            canvas.drawRoundRect(x, top, x + barWidth, bottom, density, density, fillPaint);
        }
    }
}
