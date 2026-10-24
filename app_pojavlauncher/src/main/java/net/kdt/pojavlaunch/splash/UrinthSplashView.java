package net.kdt.pojavlaunch.splash;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;
import java.util.Random;

import git.artdeell.mojo.R;

/**
 * The UrinthUltra intro, drawn from one clock value ({@link #setTime}) so every part of it stays in
 * step: a faint grid and scan line, the logo spinning in inside a set of turning HUD rings, the
 * name "UrinthUltra" popping in letter by letter (Urinth in white, Ultra in red), and a segmented
 * loading bar with a status line. Everything is original artwork made of plain shapes and the
 * launcher's own logo.
 *
 * Timeline, in milliseconds:
 * <pre>
 *     0 -  700  logo spins and scales in, corner brackets draw
 *   100 - 1000  rings sweep in and keep turning
 *   850 - 1100  shock ring leaves the logo
 *   900 - 1800  letters pop in one by one, glitch flash at 1700
 *  1150 - 2750  loading bar fills, status text changes
 *  2750 - 3000  fade out into the launcher
 * </pre>
 */
public final class UrinthSplashView extends View {
    public static final long DURATION_MS = 3000;

    private static final String NAME_WHITE = "Urinth";
    private static final String NAME_RED = "Ultra";

    private static final int COLOR_BG_TOP = 0xFF060E14;
    private static final int COLOR_BG_BOTTOM = 0xFF0C1C27;
    private static final int COLOR_TEAL = 0xFF3CE6C0;
    private static final int COLOR_GREEN = 0xFF1FC77A;
    private static final int COLOR_RED = 0xFFFF2D3F;
    private static final int COLOR_PURPLE = 0xFF9B3DFF;
    private static final int COLOR_TEXT_DIM = 0xFF8FA9B6;

    private static final int BAR_SEGMENTS = 44;
    private static final int PARTICLES = 30;

    private final float density;
    private final Bitmap logo;

    private final Paint bgPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint vignettePaint = new Paint();
    private final Paint scanPaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint();
    private final RectF rect = new RectF();

    private final float[] particleX = new float[PARTICLES];
    private final float[] particleY = new float[PARTICLES];
    private final float[] particleSpeed = new float[PARTICLES];
    private final float[] particleSize = new float[PARTICLES];
    private final float[] particlePhase = new float[PARTICLES];

    private final float[] charWidths = new float[NAME_WHITE.length() + NAME_RED.length()];
    private final String name = NAME_WHITE + NAME_RED;

    private float time;
    private float centerX;
    private float centerY;
    private float logoSize;
    private float nameBaseline;
    private float nameLeft;

    public UrinthSplashView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.urinth_splash_logo);

        gridPaint.setStrokeWidth(Math.max(1f, density * 0.6f));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setLetterSpacing(0.04f);
        smallPaint.setTypeface(Typeface.MONOSPACE);
        smallPaint.setLetterSpacing(0.12f);

        Random random = new Random(7);
        for (int i = 0; i < PARTICLES; i++) {
            particleX[i] = random.nextFloat();
            particleY[i] = random.nextFloat();
            particleSpeed[i] = 0.03f + random.nextFloat() * 0.06f;
            particleSize[i] = 0.8f + random.nextFloat() * 1.6f;
            particlePhase[i] = random.nextFloat() * 6.28f;
        }
    }

    /** Move the intro to this point of its timeline (milliseconds) and redraw. */
    public void setTime(float milliseconds) {
        time = milliseconds;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h * 0.36f;
        logoSize = Math.min(h * 0.34f, w * 0.26f);

        bgPaint.setShader(new LinearGradient(0, 0, 0, h, COLOR_BG_TOP, COLOR_BG_BOTTOM, Shader.TileMode.CLAMP));
        vignettePaint.setShader(new RadialGradient(centerX, h * 0.45f, Math.max(w, h) * 0.75f,
                new int[]{0x00060E14, 0x00060E14, 0xFF060E14}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(centerX, centerY, logoSize * 1.25f,
                new int[]{withAlpha(COLOR_PURPLE, 1f), withAlpha(COLOR_PURPLE, 0.4f), withAlpha(COLOR_PURPLE, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));

        // Name layout: measured once so letters can be placed and animated one by one.
        textPaint.setTextSize(Math.min(h * 0.105f, w * 0.085f));
        float total = 0;
        for (int i = 0; i < name.length(); i++) {
            charWidths[i] = textPaint.measureText(name, i, i + 1);
            total += charWidths[i];
        }
        nameLeft = centerX - total / 2f;
        nameBaseline = centerY + logoSize * 0.86f + textPaint.getTextSize() * 0.95f;
    }

    // ---- easing -----------------------------------------------------------------------------

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** 0..1 progress of the time window [start, start + length]. */
    private float window(float start, float length) {
        return clamp01((time - start) / length);
    }

    private static float easeOutCubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private static float easeOutBack(float p) {
        float c1 = 1.9f;
        float c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    private static float smooth(float p) {
        return p * p * (3f - 2f * p);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(clamp01(alpha) * Color.alpha(color));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);

        drawGrid(canvas, w, h);
        drawParticles(canvas, w, h);
        drawScanLine(canvas, w, h);
        canvas.drawRect(0, 0, w, h, vignettePaint);
        drawBrackets(canvas, w, h);
        drawLogoGlow(canvas);
        drawRings(canvas);
        drawLogo(canvas);
        drawShockRing(canvas);
        drawName(canvas);
        drawProgress(canvas, w, h);
        drawFadeOut(canvas, w, h);
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        float appear = smooth(window(0, 700));
        if (appear <= 0f) return;
        float step = density * 30f;
        float drift = (time * 0.012f * density) % step;
        gridPaint.setColor(withAlpha(COLOR_TEAL, 0.07f * appear));
        for (float x = centerX % step - step; x < w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (float y = -step + drift; y < h; y += step) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }
    }

    private void drawParticles(Canvas canvas, int w, int h) {
        float appear = smooth(window(200, 700));
        if (appear <= 0f) return;
        for (int i = 0; i < PARTICLES; i++) {
            float y = (particleY[i] - time / 1000f * particleSpeed[i]) % 1f;
            if (y < 0f) y += 1f;
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time / 260f + particlePhase[i]));
            int color = (i % 5 == 0) ? COLOR_RED : COLOR_TEAL;
            particlePaint.setColor(withAlpha(color, 0.55f * twinkle * appear));
            canvas.drawCircle(particleX[i] * w, y * h, particleSize[i] * density, particlePaint);
        }
    }

    private void drawScanLine(Canvas canvas, int w, int h) {
        float p = window(250, 1700);
        if (p <= 0f || p >= 1f) return;
        float y = -h * 0.1f + (h * 1.2f) * smooth(p);
        float band = density * 70f;
        scanPaint.setShader(new LinearGradient(0, y - band, 0, y,
                withAlpha(COLOR_TEAL, 0f), withAlpha(COLOR_TEAL, 0.16f), Shader.TileMode.CLAMP));
        canvas.drawRect(0, y - band, w, y, scanPaint);
        scanPaint.setShader(null);
        scanPaint.setColor(withAlpha(COLOR_TEAL, 0.55f));
        canvas.drawRect(0, y - density * 0.5f, w, y + density * 0.5f, scanPaint);
    }

    private void drawBrackets(Canvas canvas, int w, int h) {
        float p = easeOutCubic(window(100, 700));
        if (p <= 0f) return;
        float inset = density * 22f;
        float len = density * 26f * p;
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(density * 1.6f);
        ringPaint.setColor(withAlpha(COLOR_TEAL, 0.75f));
        // top-left, top-right, bottom-left, bottom-right
        canvas.drawLine(inset, inset, inset + len, inset, ringPaint);
        canvas.drawLine(inset, inset, inset, inset + len, ringPaint);
        canvas.drawLine(w - inset, inset, w - inset - len, inset, ringPaint);
        canvas.drawLine(w - inset, inset, w - inset, inset + len, ringPaint);
        canvas.drawLine(inset, h - inset, inset + len, h - inset, ringPaint);
        canvas.drawLine(inset, h - inset, inset, h - inset - len, ringPaint);
        canvas.drawLine(w - inset, h - inset, w - inset - len, h - inset, ringPaint);
        canvas.drawLine(w - inset, h - inset, w - inset, h - inset - len, ringPaint);
    }

    private void drawLogoGlow(Canvas canvas) {
        float appear = smooth(window(150, 800));
        if (appear <= 0f) return;
        float pulse = 0.55f + 0.2f * (float) Math.sin(time / 210f);
        glowPaint.setAlpha(Math.round(255 * clamp01(pulse * appear)));
        canvas.drawCircle(centerX, centerY, logoSize * 1.25f, glowPaint);
    }

    private void drawRings(Canvas canvas) {
        float sweep = easeOutCubic(window(100, 900));
        if (sweep <= 0f) return;
        float fade = 1f - smooth(window(2750, 250));
        ringPaint.setShader(null);

        // Ring 1: three arcs turning clockwise.
        float r1 = logoSize * 0.64f;
        ringPaint.setStrokeWidth(density * 2.2f);
        ringPaint.setColor(withAlpha(COLOR_TEAL, 0.95f * fade));
        rect.set(centerX - r1, centerY - r1, centerX + r1, centerY + r1);
        float a1 = time * 0.16f;
        for (int i = 0; i < 3; i++) {
            canvas.drawArc(rect, a1 + i * 120f, 70f * sweep, false, ringPaint);
        }

        // Ring 2: broken ring turning the other way, in red.
        float r2 = logoSize * 0.73f;
        ringPaint.setStrokeWidth(density * 1.6f);
        ringPaint.setColor(withAlpha(COLOR_RED, 0.85f * fade));
        rect.set(centerX - r2, centerY - r2, centerX + r2, centerY + r2);
        float a2 = -time * 0.22f;
        for (int i = 0; i < 4; i++) {
            canvas.drawArc(rect, a2 + i * 90f, 34f * sweep, false, ringPaint);
        }

        // Ring 3: fine tick marks with a bright comet running around them.
        float rIn = logoSize * 0.80f;
        float rOut = logoSize * 0.86f;
        float comet = (time * 0.30f) % 360f;
        tickPaint.setStrokeWidth(density * 1.3f);
        int ticks = 72;
        int shown = Math.round(ticks * sweep);
        for (int i = 0; i < shown; i++) {
            float angle = i * (360f / ticks);
            float dist = Math.abs(((angle - comet + 540f) % 360f) - 180f); // 0 at the comet head
            float near = Math.max(0f, 1f - dist / 70f);
            float len = rOut + near * logoSize * 0.05f;
            tickPaint.setColor(withAlpha(near > 0.55f ? Color.WHITE : COLOR_TEAL, (0.16f + 0.84f * near) * fade));
            double rad = Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            canvas.drawLine(centerX + cos * rIn, centerY + sin * rIn, centerX + cos * len, centerY + sin * len, tickPaint);
        }
    }

    private void drawLogo(Canvas canvas) {
        float p = window(0, 750);
        if (p <= 0f || logo == null) return;
        float scale = 0.15f + 0.85f * easeOutBack(p);
        float rotation = -140f * (1f - easeOutCubic(p));
        float breathe = 1f + 0.018f * (float) Math.sin(time / 240f) * smooth(window(750, 300));
        logoPaint.setAlpha(Math.round(255 * clamp01(p * 2.2f) * (1f - smooth(window(2850, 150)))));
        int save = canvas.save();
        canvas.translate(centerX, centerY);
        canvas.rotate(rotation);
        float size = logoSize * 0.95f * scale * breathe;
        rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
        canvas.drawBitmap(logo, null, rect, logoPaint);
        canvas.restoreToCount(save);
    }

    private void drawShockRing(Canvas canvas) {
        float p = window(850, 650);
        if (p <= 0f || p >= 1f) return;
        float radius = logoSize * (0.5f + 1.3f * easeOutCubic(p));
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(density * (3f * (1f - p) + 0.6f));
        ringPaint.setColor(withAlpha(COLOR_TEAL, 0.8f * (1f - p)));
        canvas.drawCircle(centerX, centerY, radius, ringPaint);
    }

    private void drawName(Canvas canvas) {
        float exit = 1f - smooth(window(2850, 150));
        drawNameLayer(canvas, 0f, 0f, 1f, false, exit);

        // Short glitch: two offset colour ghosts for a moment.
        float g = window(1700, 150);
        if (g > 0f && g < 1f) {
            float shift = density * 4f * (g < 0.5f ? g * 2f : (1f - g) * 2f);
            drawNameLayer(canvas, -shift, 0f, 0.5f, true, exit);
            drawNameLayer(canvas, shift, 0f, 0.5f, true, exit);
        }

        // Line under the name, growing from the middle.
        float line = easeOutCubic(window(1500, 500));
        if (line > 0f) {
            float half = (charsTotal() / 2f) * line;
            float y = nameBaseline + textPaint.getTextSize() * 0.32f;
            barPaint.setShader(new LinearGradient(centerX - half, 0, centerX + half, 0,
                    new int[]{withAlpha(COLOR_TEAL, 0f), withAlpha(COLOR_TEAL, 0.9f * exit),
                            withAlpha(COLOR_RED, 0.9f * exit), withAlpha(COLOR_RED, 0f)},
                    new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(centerX - half, y, centerX + half, y + density * 1.4f, barPaint);
            barPaint.setShader(null);
        }

        // Tagline.
        float tag = smooth(window(1550, 450));
        if (tag > 0f) {
            smallPaint.setTextSize(textPaint.getTextSize() * 0.27f);
            smallPaint.setColor(withAlpha(COLOR_TEXT_DIM, tag * exit));
            smallPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("MINECRAFT  LAUNCHER", centerX, nameBaseline + textPaint.getTextSize() * 0.78f, smallPaint);
        }
    }

    private float charsTotal() {
        float total = 0;
        for (float width : charWidths) total += width;
        return total;
    }

    /** One pass over the name: ghost passes use a tint instead of the real colours. */
    private void drawNameLayer(Canvas canvas, float dx, float dy, float alphaMul, boolean ghost, float exit) {
        float x = nameLeft + dx;
        float size = textPaint.getTextSize();
        int whiteLength = NAME_WHITE.length();
        for (int i = 0; i < name.length(); i++) {
            float p = window(900 + i * 55f, 380f);
            if (p > 0f) {
                float pop = easeOutBack(p);
                float scale = 1f + (1f - clamp01(p)) * 0.7f;
                float rise = (1f - pop) * size * 0.55f;
                boolean red = i >= whiteLength;
                int color;
                if (ghost) color = (dx < 0) ? COLOR_TEAL : COLOR_RED;
                else color = red ? COLOR_RED : Color.WHITE;
                textPaint.setColor(withAlpha(color, clamp01(p * 1.6f) * alphaMul * exit));
                if (!ghost && red) {
                    textPaint.setShadowLayer(size * 0.35f, 0, 0, withAlpha(COLOR_RED, 0.75f * clamp01(p) * exit));
                } else if (!ghost) {
                    textPaint.setShadowLayer(size * 0.18f, 0, 0, withAlpha(Color.WHITE, 0.25f * clamp01(p) * exit));
                } else {
                    textPaint.clearShadowLayer();
                }
                int save = canvas.save();
                canvas.translate(x + charWidths[i] / 2f, nameBaseline + dy + rise);
                canvas.scale(scale, scale);
                canvas.drawText(name, i, i + 1, -charWidths[i] / 2f, 0f, textPaint);
                canvas.restoreToCount(save);
            }
            x += charWidths[i];
        }
        textPaint.clearShadowLayer();
    }

    private void drawProgress(Canvas canvas, int w, int h) {
        float appear = smooth(window(1100, 350));
        if (appear <= 0f) return;
        float exit = 1f - smooth(window(2850, 150));
        float p = window(1150, 1600);
        float progress = smooth(p);

        float barWidth = Math.min(w * 0.46f, density * 440f);
        float barHeight = density * 7f;
        float left = centerX - barWidth / 2f;
        float top = h * 0.90f;
        float gap = density * 2f;
        float segWidth = (barWidth - gap * (BAR_SEGMENTS - 1)) / BAR_SEGMENTS;
        float filled = progress * BAR_SEGMENTS;

        for (int i = 0; i < BAR_SEGMENTS; i++) {
            float x = left + i * (segWidth + gap);
            rect.set(x, top, x + segWidth, top + barHeight);
            if (i + 1 <= filled) {
                float mix = i / (float) (BAR_SEGMENTS - 1);
                barPaint.setColor(withAlpha(blend(COLOR_TEAL, COLOR_GREEN, mix), appear * exit));
            } else if (i < filled) {
                barPaint.setColor(withAlpha(Color.WHITE, appear * exit * (0.25f + 0.75f * (filled - i))));
            } else {
                barPaint.setColor(withAlpha(0xFF2A4452, 0.8f * appear * exit));
            }
            canvas.drawRoundRect(rect, density, density, barPaint);
        }
        // Bright head on the leading segment.
        if (progress > 0f && progress < 1f) {
            float x = left + Math.min(filled, BAR_SEGMENTS - 1) * (segWidth + gap);
            rect.set(x - gap, top - density * 3f, x + segWidth + gap, top + barHeight + density * 3f);
            barPaint.setColor(withAlpha(Color.WHITE, 0.9f * appear * exit));
            canvas.drawRoundRect(rect, density * 1.5f, density * 1.5f, barPaint);
        }

        // Status line (left) and percentage (right), both in a console font.
        float textSize = density * 10.5f;
        smallPaint.setTextSize(textSize);
        smallPaint.setTextAlign(Paint.Align.LEFT);
        smallPaint.setColor(withAlpha(COLOR_TEXT_DIM, appear * exit));
        canvas.drawText(statusFor(progress), left, top - density * 9f, smallPaint);

        smallPaint.setTextAlign(Paint.Align.RIGHT);
        smallPaint.setColor(withAlpha(Color.WHITE, appear * exit));
        canvas.drawText(String.format(Locale.US, "%d%%", Math.round(progress * 100f)),
                left + barWidth, top - density * 9f, smallPaint);
    }

    private String statusFor(float progress) {
        String dots = "...".substring(0, 1 + (int) (time / 220f) % 3);
        if (progress < 0.28f) return "INITIALIZING CORE" + dots;
        if (progress < 0.58f) return "LOADING MODULES" + dots;
        if (progress < 0.9f) return "OPTIMIZING PERFORMANCE" + dots;
        return "READY";
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private void drawFadeOut(Canvas canvas, int w, int h) {
        float p = window(2800, 200);
        if (p <= 0f) return;
        overlayPaint.setColor(withAlpha(0xFF0A151D, smooth(p)));
        canvas.drawRect(0, 0, w, h, overlayPaint);
    }
}
