package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.Locale;
import java.util.Random;

import git.artdeell.mojo.R;

/**
 * The loading screen shown while the game starts: a futuristic HUD in the same style as the
 * UrinthUltra intro. A grid and a scan line sweep over a dark gradient, particles drift (or, in
 * Performance Mode, binary rain falls), the logo sits inside turning rings with a progress ring
 * around it, and a five-step bar along the bottom lights up as start-up moves through its stages.
 *
 * <p>The bar shows real progress only: a step lights when the game log shows that step has begun
 * (see {@link LaunchStage}). Inside a step the fill creeps forward but never reaches the next
 * step by itself, and nothing shows 100% until the game window has actually appeared.
 *
 * <p>It is drawn from a Choreographer frame clock (so it keeps moving even when the system's
 * animator duration scale is off), capped at about 30 frames a second so the animation does not
 * take CPU time from the Java VM that is starting at the same moment, and it stops when it is
 * hidden or detached. Everything is original artwork made of plain shapes and the launcher's own
 * logo.
 */
public final class LaunchLoadingView extends View implements Choreographer.FrameCallback {
    private static final int COLOR_BG_TOP = 0xFF060E14;
    private static final int COLOR_BG_BOTTOM = 0xFF0C1C27;
    private static final int COLOR_TEAL = 0xFF3CE6C0;
    private static final int COLOR_CYAN = 0xFF4FD8FF;
    private static final int COLOR_RED = 0xFFFF2D3F;
    private static final int COLOR_PURPLE = 0xFF9B3DFF;
    private static final int COLOR_DIM = 0xFF8FA9B6;

    private static final String[] STEP_LABELS = {"PREPARE", "JAVA", "GAME", "WINDOW", "ASSETS"};
    private static final int STEPS = STEP_LABELS.length;

    /** About 30 frames a second; a little under 33 ms so frame jitter does not skip a frame. */
    private static final long FRAME_INTERVAL_NS = 31_000_000L;
    /** How long a step's fill takes to creep most of the way to the next step. */
    private static final float CREEP_MS = 18000f;
    private static final long DONE_FLASH_MS = 450;

    private static final int PARTICLES = 34;
    private static final int RAIN_COLUMNS = 14;
    private static final int RAIN_LENGTH = 6;

    private final float density;
    private final Bitmap logo;

    private final Paint bgPaint = new Paint();
    private final Paint vignettePaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint scanPaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint();
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private final float[] particleX = new float[PARTICLES];
    private final float[] particleY = new float[PARTICLES];
    private final float[] particleSpeed = new float[PARTICLES];
    private final float[] particleSize = new float[PARTICLES];
    private final float[] particlePhase = new float[PARTICLES];
    private final float[] rainOffset = new float[RAIN_COLUMNS];
    private final float[] rainSpeed = new float[RAIN_COLUMNS];

    private LinearGradient scanBandShader;
    private LinearGradient sweepShader;
    private float sweepWidth;

    private boolean running;
    private long startNs;
    private long lastFrameNs;
    private float timeMs;

    private int stage = LaunchStage.PREPARING;
    private float stageSinceMs;
    private float shown;
    private boolean done;
    private float doneSinceMs;
    private boolean rain;
    private String infoVersion = "";
    private String infoRenderer = "";
    private boolean infoPerformance;

    private float centerX;
    private float centerY;
    private float logoSize;
    private float nameBaseline;

    public LaunchLoadingView(Context context) {
        this(context, null);
    }

    public LaunchLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.urinth_splash_logo);

        gridPaint.setStrokeWidth(Math.max(1f, density * 0.6f));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        namePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        namePaint.setLetterSpacing(0.04f);
        smallPaint.setTypeface(Typeface.MONOSPACE);
        smallPaint.setLetterSpacing(0.1f);
        barPaint.setStyle(Paint.Style.FILL);

        Random random = new Random(11);
        for (int i = 0; i < PARTICLES; i++) {
            particleX[i] = random.nextFloat();
            particleY[i] = random.nextFloat();
            particleSpeed[i] = 0.03f + random.nextFloat() * 0.06f;
            particleSize[i] = 0.8f + random.nextFloat() * 1.6f;
            particlePhase[i] = random.nextFloat() * 6.28f;
        }
        for (int i = 0; i < RAIN_COLUMNS; i++) {
            rainOffset[i] = random.nextFloat();
            rainSpeed[i] = 0.10f + random.nextFloat() * 0.14f;
        }
    }

    // ---- state set from the game activity ---------------------------------------------------

    /** Move to this stage ({@link LaunchStage}). The view never goes back to an earlier stage. */
    public void setStage(int newStage) {
        if (newStage <= stage || done) return;
        stage = Math.min(newStage, STEPS - 1);
        stageSinceMs = timeMs;
    }

    /** The game window appeared: fill the bar, flash the rings. */
    public void setDone() {
        if (done) return;
        done = true;
        doneSinceMs = timeMs;
    }

    /** Binary rain instead of drifting particles (used while Performance Mode is on). */
    public void setRain(boolean enabled) {
        rain = enabled;
    }

    /** What is being launched, for the corner readouts. */
    public void setInfo(String version, String renderer, boolean performanceMode) {
        infoVersion = version == null ? "" : version;
        infoRenderer = renderer == null ? "" : renderer;
        infoPerformance = performanceMode;
    }

    // ---- frame clock ------------------------------------------------------------------------

    private void startClock() {
        if (running) return;
        running = true;
        startNs = 0;
        lastFrameNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stopClock() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) startClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopClock();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) startClock();
        else stopClock();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (startNs == 0) startNs = frameTimeNanos;
        if (lastFrameNs == 0 || frameTimeNanos - lastFrameNs >= FRAME_INTERVAL_NS) {
            float dt = lastFrameNs == 0 ? 0.033f : (frameTimeNanos - lastFrameNs) / 1_000_000_000f;
            lastFrameNs = frameTimeNanos;
            timeMs = (frameTimeNanos - startNs) / 1_000_000f;
            step(Math.min(dt, 0.25f));
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** Ease the shown progress toward where the stage says start-up is. */
    private void step(float dt) {
        float target;
        if (done) {
            target = 1f;
        } else {
            // Within a step the fill creeps toward (but never reaches) the next step.
            float creep = 0.9f * (1f - (float) Math.exp(-(timeMs - stageSinceMs) / CREEP_MS));
            target = (stage + creep) / STEPS;
        }
        shown += (target - shown) * Math.min(1f, dt * 3f);
    }

    // ---- layout -----------------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h * 0.36f;
        logoSize = Math.min(h * 0.21f, w * 0.15f);

        bgPaint.setShader(new LinearGradient(0, 0, 0, h, COLOR_BG_TOP, COLOR_BG_BOTTOM, Shader.TileMode.CLAMP));
        vignettePaint.setShader(new RadialGradient(centerX, h * 0.45f, Math.max(w, h) * 0.75f,
                new int[]{0x00060E14, 0x00060E14, 0xFF060E14}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(centerX, centerY, logoSize * 1.3f,
                new int[]{withAlpha(COLOR_PURPLE, 1f), withAlpha(COLOR_PURPLE, 0.4f), withAlpha(COLOR_PURPLE, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));

        // The scan band: drawn above y=0 and moved down the screen by a canvas translation.
        float band = density * 70f;
        scanBandShader = new LinearGradient(0, -band, 0, 0,
                withAlpha(COLOR_TEAL, 0f), withAlpha(COLOR_TEAL, 0.16f), Shader.TileMode.CLAMP);

        namePaint.setTextSize(Math.min(h * 0.066f, w * 0.05f));
        nameBaseline = centerY + logoSize * 0.9f + namePaint.getTextSize() * 1.05f;

        // A bright highlight that slides along the line under the name.
        sweepWidth = Math.max(density * 60f, w * 0.08f);
        sweepShader = new LinearGradient(0, 0, sweepWidth, 0,
                new int[]{withAlpha(COLOR_CYAN, 0f), withAlpha(Color.WHITE, 0.95f), withAlpha(COLOR_CYAN, 0f)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float smooth(float p) {
        p = clamp01(p);
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
        if (rain) drawRain(canvas, w, h);
        else drawParticles(canvas, w, h);
        drawScanLine(canvas, w, h);
        canvas.drawRect(0, 0, w, h, vignettePaint);
        drawBrackets(canvas, w, h);
        drawReadouts(canvas, w, h);
        drawLogoGlow(canvas);
        drawRings(canvas);
        drawProgressRing(canvas);
        drawLogo(canvas);
        drawDoneFlash(canvas);
        drawName(canvas, w);
        drawStepBar(canvas, w, h);
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        float step = density * 30f;
        float drift = (timeMs * 0.012f * density) % step;
        gridPaint.setColor(withAlpha(COLOR_TEAL, 0.07f));
        for (float x = centerX % step - step; x < w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (float y = -step + drift; y < h; y += step) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }
    }

    private void drawParticles(Canvas canvas, int w, int h) {
        for (int i = 0; i < PARTICLES; i++) {
            float y = (particleY[i] - timeMs / 1000f * particleSpeed[i]) % 1f;
            if (y < 0f) y += 1f;
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(timeMs / 260f + particlePhase[i]));
            int color = (i % 5 == 0) ? COLOR_RED : COLOR_TEAL;
            particlePaint.setColor(withAlpha(color, 0.55f * twinkle));
            canvas.drawCircle(particleX[i] * w, y * h, particleSize[i] * density, particlePaint);
        }
    }

    /** Binary rain, a few columns of 0s and 1s falling; the head of each column is brightest. */
    private void drawRain(Canvas canvas, int w, int h) {
        float size = density * 11f;
        smallPaint.setTextSize(size);
        smallPaint.setLetterSpacing(0f);
        int frame = (int) (timeMs / 160f);
        for (int i = 0; i < RAIN_COLUMNS; i++) {
            float x = (i + 0.5f) / RAIN_COLUMNS * w;
            float head = ((timeMs / 1000f * rainSpeed[i] + rainOffset[i]) % 1f) * (h + size * RAIN_LENGTH);
            for (int k = 0; k < RAIN_LENGTH; k++) {
                float y = head - k * size * 1.25f;
                if (y < 0 || y > h) continue;
                float fade = 1f - k / (float) RAIN_LENGTH;
                int bit = ((i * 31 + k * 17 + frame * (k + 1)) & 1);
                smallPaint.setColor(k == 0 ? withAlpha(Color.WHITE, 0.55f)
                        : withAlpha(COLOR_TEAL, 0.42f * fade));
                canvas.drawText(bit == 0 ? "0" : "1", x, y, smallPaint);
            }
        }
        smallPaint.setLetterSpacing(0.1f);
    }

    /** A band of light that sweeps down the screen every few seconds. */
    private void drawScanLine(Canvas canvas, int w, int h) {
        float cycle = 3600f;
        float p = (timeMs % cycle) / cycle;
        float y = -h * 0.1f + h * 1.25f * smooth(p);
        int save = canvas.save();
        canvas.translate(0, y);
        scanPaint.setShader(scanBandShader);
        canvas.drawRect(0, -density * 70f, w, 0, scanPaint);
        scanPaint.setShader(null);
        scanPaint.setColor(withAlpha(COLOR_TEAL, 0.5f));
        canvas.drawRect(0, -density * 0.5f, w, density * 0.5f, scanPaint);
        canvas.restoreToCount(save);
    }

    private void drawBrackets(Canvas canvas, int w, int h) {
        float inset = density * 16f;
        float len = density * 18f;
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(density * 1.6f);
        ringPaint.setColor(withAlpha(COLOR_TEAL, 0.75f));
        canvas.drawLine(inset, inset, inset + len, inset, ringPaint);
        canvas.drawLine(inset, inset, inset, inset + len, ringPaint);
        canvas.drawLine(w - inset, inset, w - inset - len, inset, ringPaint);
        canvas.drawLine(w - inset, inset, w - inset, inset + len, ringPaint);
        canvas.drawLine(inset, h - inset, inset + len, h - inset, ringPaint);
        canvas.drawLine(inset, h - inset, inset, h - inset - len, ringPaint);
        canvas.drawLine(w - inset, h - inset, w - inset - len, h - inset, ringPaint);
        canvas.drawLine(w - inset, h - inset, w - inset, h - inset - len, ringPaint);
    }

    /** Small corner readouts: what is launching, on which renderer, and whether Performance Mode is on. */
    private void drawReadouts(Canvas canvas, int w, int h) {
        float margin = density * 30f;
        float size = density * 9f;
        smallPaint.setTextSize(size);
        float top = margin + size;

        // A dot that blinks next to the title, like a recording light.
        boolean on = ((int) (timeMs / 500f)) % 2 == 0;
        particlePaint.setColor(withAlpha(COLOR_RED, on ? 0.95f : 0.25f));
        canvas.drawCircle(margin + density * 3f, top - size * 0.35f, density * 2.6f, particlePaint);

        smallPaint.setTextAlign(Paint.Align.LEFT);
        smallPaint.setColor(withAlpha(COLOR_TEAL, 0.9f));
        canvas.drawText("LAUNCH SEQUENCE", margin + density * 12f, top, smallPaint);
        smallPaint.setColor(withAlpha(COLOR_DIM, 0.9f));
        canvas.drawText(shorten("VERSION " + infoVersion.toUpperCase(Locale.ROOT), 40),
                margin, top + size * 1.5f, smallPaint);

        smallPaint.setTextAlign(Paint.Align.RIGHT);
        smallPaint.setColor(withAlpha(COLOR_DIM, 0.9f));
        canvas.drawText(shorten("RENDERER " + infoRenderer.toUpperCase(Locale.ROOT), 32),
                w - margin, top, smallPaint);
        if (infoPerformance) {
            smallPaint.setColor(withAlpha(COLOR_TEAL, 0.95f));
            canvas.drawText("PERFORMANCE MODE ON", w - margin, top + size * 1.5f, smallPaint);
        }
        smallPaint.setTextAlign(Paint.Align.LEFT);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private void drawLogoGlow(Canvas canvas) {
        float pulse = 0.55f + 0.2f * (float) Math.sin(timeMs / 210f);
        glowPaint.setAlpha(Math.round(255 * clamp01(pulse)));
        canvas.drawCircle(centerX, centerY, logoSize * 1.3f, glowPaint);
    }

    private void drawRings(Canvas canvas) {
        ringPaint.setShader(null);

        // Ring 1: three arcs turning clockwise.
        float r1 = logoSize * 0.64f;
        ringPaint.setStrokeWidth(density * 1.6f);
        ringPaint.setColor(withAlpha(COLOR_TEAL, 0.95f));
        rect.set(centerX - r1, centerY - r1, centerX + r1, centerY + r1);
        float a1 = timeMs * 0.08f;
        for (int i = 0; i < 3; i++) {
            canvas.drawArc(rect, a1 + i * 120f, 70f, false, ringPaint);
        }

        // Ring 2: broken ring turning the other way, in red.
        float r2 = logoSize * 0.73f;
        ringPaint.setStrokeWidth(density * 1.3f);
        ringPaint.setColor(withAlpha(COLOR_RED, 0.85f));
        rect.set(centerX - r2, centerY - r2, centerX + r2, centerY + r2);
        float a2 = -timeMs * 0.11f;
        for (int i = 0; i < 4; i++) {
            canvas.drawArc(rect, a2 + i * 90f, 34f, false, ringPaint);
        }

        // Ring 3: fine tick marks with a bright comet running around them.
        float rIn = logoSize * 0.80f;
        float rOut = logoSize * 0.86f;
        float comet = (timeMs * 0.15f) % 360f;
        tickPaint.setStrokeWidth(density * 1.3f);
        int ticks = 72;
        for (int i = 0; i < ticks; i++) {
            float angle = i * (360f / ticks);
            float dist = Math.abs(((angle - comet + 540f) % 360f) - 180f); // 0 at the comet head
            float near = Math.max(0f, 1f - dist / 70f);
            float len = rOut + near * logoSize * 0.05f;
            tickPaint.setColor(withAlpha(near > 0.55f ? Color.WHITE : COLOR_TEAL, 0.16f + 0.84f * near));
            double rad = Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            canvas.drawLine(centerX + cos * rIn, centerY + sin * rIn, centerX + cos * len, centerY + sin * len, tickPaint);
        }
    }

    /** The outer ring fills as start-up moves on: the same progress as the bar below. */
    private void drawProgressRing(Canvas canvas) {
        float r = logoSize * 1.0f;
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(density * 2.2f);
        rect.set(centerX - r, centerY - r, centerX + r, centerY + r);
        ringPaint.setColor(withAlpha(COLOR_TEAL, 0.12f));
        canvas.drawCircle(centerX, centerY, r, ringPaint);
        float sweep = 360f * clamp01(shown);
        if (sweep <= 0.5f) return;
        ringPaint.setColor(withAlpha(COLOR_CYAN, 0.95f));
        canvas.drawArc(rect, -90f, sweep, false, ringPaint);
        // A bright head at the leading edge.
        double rad = Math.toRadians(-90f + sweep);
        particlePaint.setColor(withAlpha(Color.WHITE, 0.95f));
        canvas.drawCircle(centerX + (float) Math.cos(rad) * r, centerY + (float) Math.sin(rad) * r,
                density * 2.6f, particlePaint);
    }

    private void drawLogo(Canvas canvas) {
        if (logo == null) return;
        float breathe = 1f + 0.025f * (float) Math.sin(timeMs / 240f);
        float half = logoSize * 0.5f * breathe;
        rect.set(centerX - half, centerY - half, centerX + half, centerY + half);
        canvas.drawBitmap(logo, null, rect, logoPaint);
    }

    /** When the window appears: one ring of light leaves the logo. */
    private void drawDoneFlash(Canvas canvas) {
        if (!done) return;
        float p = clamp01((timeMs - doneSinceMs) / DONE_FLASH_MS);
        if (p >= 1f) return;
        float r = logoSize * (1.0f + 1.6f * smooth(p));
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(density * 3f);
        ringPaint.setColor(withAlpha(Color.WHITE, 0.85f * (1f - p)));
        canvas.drawCircle(centerX, centerY, r, ringPaint);
    }

    private void drawName(Canvas canvas, int w) {
        namePaint.setTextAlign(Paint.Align.LEFT);
        float whiteWidth = namePaint.measureText("Urinth");
        float redWidth = namePaint.measureText("Ultra");
        float left = centerX - (whiteWidth + redWidth) / 2f;
        namePaint.setColor(Color.WHITE);
        canvas.drawText("Urinth", left, nameBaseline, namePaint);
        namePaint.setColor(COLOR_RED);
        canvas.drawText("Ultra", left + whiteWidth, nameBaseline, namePaint);

        // A thin line under the name with a highlight sliding along it.
        float lineW = (whiteWidth + redWidth) * 1.25f;
        float lineLeft = centerX - lineW / 2f;
        float lineY = nameBaseline + namePaint.getTextSize() * 0.35f;
        barPaint.setShader(null);
        barPaint.setColor(withAlpha(COLOR_TEAL, 0.25f));
        canvas.drawRect(lineLeft, lineY, lineLeft + lineW, lineY + density * 1.2f, barPaint);
        float travel = lineW + sweepWidth;
        float x = lineLeft - sweepWidth + travel * ((timeMs % 2200f) / 2200f);
        int save = canvas.save();
        canvas.clipRect(lineLeft, lineY - density, lineLeft + lineW, lineY + density * 2.2f);
        matrix.setTranslate(x, 0);
        sweepShader.setLocalMatrix(matrix);
        sweepPaint.setShader(sweepShader);
        canvas.drawRect(x, lineY, x + sweepWidth, lineY + density * 1.2f, sweepPaint);
        canvas.restoreToCount(save);
    }

    /** Five steps along the bottom; done steps are lit, the current one fills and pulses. */
    private void drawStepBar(Canvas canvas, int w, int h) {
        float barLeft = w * 0.2f;
        float barRight = w * 0.8f;
        float gap = density * 6f;
        float segW = (barRight - barLeft - gap * (STEPS - 1)) / STEPS;
        float barH = density * 3.5f;
        float barTop = h - density * 34f;
        float labelSize = density * 8.5f;
        smallPaint.setTextSize(labelSize);
        smallPaint.setTextAlign(Paint.Align.CENTER);

        float filled = clamp01(shown) * STEPS;
        for (int i = 0; i < STEPS; i++) {
            float left = barLeft + i * (segW + gap);
            float fraction = clamp01(filled - i);
            barPaint.setShader(null);
            barPaint.setColor(withAlpha(Color.WHITE, 0.09f));
            canvas.drawRect(left, barTop, left + segW, barTop + barH, barPaint);
            if (fraction > 0f) {
                barPaint.setColor(withAlpha(i == STEPS - 1 ? COLOR_CYAN : COLOR_TEAL, 0.95f));
                canvas.drawRect(left, barTop, left + segW * fraction, barTop + barH, barPaint);
            }

            boolean current = !done && i == Math.min(stage, STEPS - 1);
            boolean reached = fraction >= 1f || i < stage || done;
            float pulse = 0.65f + 0.35f * (float) Math.sin(timeMs / 220f);
            if (current) {
                smallPaint.setColor(withAlpha(Color.WHITE, pulse));
            } else if (reached) {
                smallPaint.setColor(withAlpha(COLOR_TEAL, 0.95f));
            } else {
                smallPaint.setColor(withAlpha(COLOR_DIM, 0.6f));
            }
            canvas.drawText(String.format(Locale.US, "%02d %s", i + 1, STEP_LABELS[i]),
                    left + segW / 2f, barTop - density * 6f, smallPaint);
        }
        smallPaint.setTextAlign(Paint.Align.LEFT);
    }
}
