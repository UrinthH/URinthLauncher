package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Locale;
import java.util.Random;

/**
 * The animation played when Performance Mode is switched on. It is original artwork drawn from
 * plain shapes, in the style of a power-up: it charges, rushes toward the viewer, and bursts.
 *
 * <ol>
 *   <li>Charge: rings around the Performance Mode switch tighten and spin faster while light
 *       streaks are pulled in, the bolt grows and trembles, and a boost meter climbs to 100%.</li>
 *   <li>Zoom: the rings and the bolt rush toward the viewer with speed lines.</li>
 *   <li>Burst: white flash, shock rings, light rays and a spray of sparks.</li>
 *   <li>The title "PERFORMANCE MODE" pops in, holds, and everything fades.</li>
 * </ol>
 * Times come from {@link BoostTimeline}. The clock is the display's frame clock (not an Android
 * animator, which some phones switch off), and no frame can advance it by more than 50 ms.
 */
public final class PerformanceBoostView extends View {
    private static final int GREEN = 0xFF1FC77A;
    private static final int TEAL = 0xFF3CE6C0;
    private static final int GOLD = 0xFFFFC83C;
    private static final int RED = 0xFFFF2D3F;
    private static final int[] SPARK_COLORS = {GREEN, TEAL, Color.WHITE, GOLD, GREEN, TEAL};
    private static final int STREAKS = 40;
    private static final int RAYS = 14;
    private static final int SPARKS = 110;
    private static final int[] RAINBOW = {0xFFFF4B5C, 0xFFFFA43C, 0xFFFFE23C, 0xFF3CE67A, 0xFF3CD0FF, 0xFF8B6BFF, 0xFFFF5BD6};

    /** Callbacks for the owner: the burst moment (show the new state) and the end. */
    public interface Listener {
        void onBurst();
        void onFinished();
    }

    private final float density;
    private final Listener listener;
    private final float anchorX, anchorY;

    private final Paint scrimPaint = new Paint();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();
    private final android.graphics.PorterDuffColorFilter greenFilter =
            new android.graphics.PorterDuffColorFilter(GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
    private final Path bolt = new Path();
    private final RectF rect = new RectF();

    private final float[] streakAngle = new float[STREAKS];
    private final float[] streakPhase = new float[STREAKS];
    private final float[] streakLength = new float[STREAKS];
    private final float[] rayAngle = new float[RAYS];
    private final float[] sparkAngle = new float[SPARKS];
    private final float[] sparkSpeed = new float[SPARKS];
    private final float[] sparkLife = new float[SPARKS];
    private final float[] sparkSize = new float[SPARKS];
    private final int[] sparkColor = new int[SPARKS];

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            tick(frameTimeNanos);
        }
    };

    private float elapsedMs;
    private long lastFrameNanos;
    private boolean running;
    private boolean burstSent;
    private boolean finishedSent;
    private float titleHalfWidth;

    private PerformanceBoostView(Context context, float anchorX, float anchorY, Listener listener) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.listener = listener;
        setClickable(true); // swallow touches while it plays
        setFocusable(false);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setLetterSpacing(0.08f);
        smallPaint.setTypeface(Typeface.MONOSPACE);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setLetterSpacing(0.14f);

        // Lightning bolt, drawn on a 24 x 24 grid centred on (12, 12).
        bolt.moveTo(13f, 2f);
        bolt.lineTo(4f, 14f);
        bolt.lineTo(10f, 14f);
        bolt.lineTo(9f, 22f);
        bolt.lineTo(18f, 10f);
        bolt.lineTo(12f, 10f);
        bolt.close();

        Random random = new Random(11);
        for (int i = 0; i < STREAKS; i++) {
            streakAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
            streakPhase[i] = random.nextFloat();
            streakLength[i] = 0.5f + random.nextFloat();
        }
        for (int i = 0; i < RAYS; i++) rayAngle[i] = (float) (i * Math.PI * 2 / RAYS);
        for (int i = 0; i < SPARKS; i++) {
            sparkAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
            sparkSpeed[i] = 260f + random.nextFloat() * 1200f; // dp per second
            sparkLife[i] = 0.7f + random.nextFloat() * 0.8f;
            sparkSize[i] = 1.2f + random.nextFloat() * 2.8f;
            sparkColor[i] = SPARK_COLORS[random.nextInt(SPARK_COLORS.length)];
        }
    }

    /**
     * Plays the animation over the whole screen, centred on the anchor view.
     * The listener's callbacks are always called, even if the screen goes away early.
     */
    public static void play(Activity activity, View anchor, Listener listener) {
        ViewGroup content = activity == null ? null : (ViewGroup) activity.findViewById(android.R.id.content);
        if (content == null || anchor == null || content.getWidth() == 0) {
            listener.onBurst();
            listener.onFinished();
            return;
        }
        int[] anchorLocation = new int[2];
        int[] contentLocation = new int[2];
        anchor.getLocationInWindow(anchorLocation);
        content.getLocationInWindow(contentLocation);
        float x = anchorLocation[0] - contentLocation[0] + anchor.getWidth() / 2f;
        float y = anchorLocation[1] - contentLocation[1] + anchor.getHeight() / 2f;
        PerformanceBoostView view = new PerformanceBoostView(activity, x, y, listener);
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        Choreographer.getInstance().postFrameCallback(frame);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        sendBurst();
        sendFinished();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        glowPaint.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{0xFFFFFFFF, 0x99FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        textPaint.setTextSize(Math.min(h * 0.11f, w * 0.06f));
        titleHalfWidth = textPaint.measureText("PERFORMANCE MODE") / 2f;
        textPaint.setShader(new LinearGradient(-titleHalfWidth, 0f, titleHalfWidth, 0f, RAINBOW, null, Shader.TileMode.CLAMP));
    }

    private void tick(long frameTimeNanos) {
        if (!running) return;
        if (lastFrameNanos != 0) {
            long step = Math.min(frameTimeNanos - lastFrameNanos, 50_000_000L);
            if (step > 0) elapsedMs += step / 1_000_000f;
        }
        lastFrameNanos = frameTimeNanos;
        if (BoostTimeline.burstStarted(elapsedMs)) sendBurst();
        if (elapsedMs >= BoostTimeline.DURATION_MS) {
            running = false;
            sendBurst();
            sendFinished();
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            return;
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private void sendBurst() {
        if (burstSent) return;
        burstSent = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        listener.onBurst();
    }

    private void sendFinished() {
        if (finishedSent) return;
        finishedSent = true;
        listener.onFinished();
    }

    private static int alpha(int color, float a) {
        int v = Math.round(BoostTimeline.clamp01(a) * Color.alpha(color));
        return (color & 0x00FFFFFF) | (v << 24);
    }

    // ---- drawing --------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        float t = elapsedMs;
        int w = getWidth();
        int h = getHeight();
        float overall = BoostTimeline.overall(t);

        scrimPaint.setColor(alpha(0xFF02080C, BoostTimeline.scrim(t)));
        canvas.drawRect(0, 0, w, h, scrimPaint);

        float charge = BoostTimeline.charge(t);
        float zoom = BoostTimeline.zoom(t);
        float burstAge = BoostTimeline.burstAge(t);
        boolean burst = BoostTimeline.burstStarted(t);

        // Everything of the charge and zoom phases lives in one zoomed, trembling layer.
        if (!burst || zoom < BoostTimeline.MAX_ZOOM) {
            int save = canvas.save();
            float shake = density * 3.5f * charge * (1f - BoostTimeline.clamp01((t - BoostTimeline.CHARGE_END) / 300f));
            float sx = (float) Math.sin(t * 0.09f) * shake;
            float sy = (float) Math.cos(t * 0.11f) * shake;
            canvas.translate(anchorX + sx, anchorY + sy);
            canvas.scale(zoom, zoom);
            drawCore(canvas, t, charge, overall);
            canvas.restoreToCount(save);
        }

        drawStreaks(canvas, t, charge);
        drawSpeedLines(canvas, t);
        if (burst) drawBurst(canvas, burstAge, overall);
        drawTitle(canvas, t, w, h);

        float flash = BoostTimeline.flash(t);
        if (flash > 0f) {
            flashPaint.setColor(alpha(Color.WHITE, flash));
            canvas.drawRect(0, 0, w, h, flashPaint);
        }
    }

    /** Glow, rings, boost meter and bolt, drawn around (0, 0) in local units. */
    private void drawCore(Canvas canvas, float t, float charge, float overall) {
        float base = density * 38f;

        // Glow behind everything, growing and pulsing with the charge.
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * (0.012f + 0.03f * charge));
        float glowRadius = base * (1.6f + 2.2f * charge) * pulse;
        glowPaint.setColor(Color.WHITE);
        int save = canvas.save();
        canvas.scale(glowRadius, glowRadius);
        glowPaint.setAlpha(Math.round(255 * (0.25f + 0.5f * charge) * overall));
        glowPaint.setColorFilter(greenFilter);
        canvas.drawCircle(0f, 0f, 1f, glowPaint);
        glowPaint.setColorFilter(null);
        canvas.restoreToCount(save);

        // Three rings: they start wide and slow and tighten while spinning faster.
        float spin = t * (0.05f + 0.55f * charge);
        float tighten = 1.9f - 0.9f * charge;
        ringPaint.setStrokeWidth(density * (1.4f + 1.6f * charge));
        for (int i = 0; i < 3; i++) {
            float radius = base * (1.15f + i * 0.5f) * tighten;
            rect.set(-radius, -radius, radius, radius);
            ringPaint.setColor(alpha(i == 1 ? GOLD : (i == 0 ? Color.WHITE : TEAL), (0.45f + 0.55f * charge) * overall));
            float direction = (i % 2 == 0) ? 1f : -1f;
            int segments = 3 + i;
            float sweep = 360f / segments * (0.45f + 0.2f * charge);
            for (int s = 0; s < segments; s++) {
                canvas.drawArc(rect, direction * spin * (1f + 0.4f * i) + s * (360f / segments), sweep, false, ringPaint);
            }
        }

        // Boost meter: an arc that fills as the charge climbs.
        float meter = BoostTimeline.meter(t);
        float meterRadius = base * 1.0f * tighten;
        ringPaint.setStrokeWidth(density * 3f);
        ringPaint.setColor(alpha(0xFF2A4452, 0.8f * overall));
        rect.set(-meterRadius, -meterRadius, meterRadius, meterRadius);
        canvas.drawArc(rect, 0f, 360f, false, ringPaint);
        ringPaint.setColor(alpha(GREEN, overall));
        canvas.drawArc(rect, -90f, 360f * meter, false, ringPaint);

        // The bolt.
        float size = base * (0.95f + 0.55f * charge) * (1f + 0.06f * (float) Math.sin(t * 0.04f));
        save = canvas.save();
        canvas.scale(size / 24f, size / 24f);
        canvas.translate(-12f, -12f);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(alpha(Color.WHITE, overall));
        canvas.drawPath(bolt, fillPaint);
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStrokeWidth(1.4f);
        fillPaint.setColor(alpha(GREEN, overall));
        canvas.drawPath(bolt, fillPaint);
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);

        // Meter reading under the bolt (only during the charge, it is part of the zoom after).
        smallPaint.setTextSize(density * 9f);
        smallPaint.setColor(alpha(Color.WHITE, (0.4f + 0.6f * charge) * overall));
        canvas.drawText(String.format(Locale.US, "BOOST %d%%", Math.round(meter * 100f)), 0f, base * 2.6f * tighten, smallPaint);
    }

    /** Light streaks pulled toward the centre while charging. */
    private void drawStreaks(Canvas canvas, float t, float charge) {
        if (charge <= 0f || BoostTimeline.burstStarted(t)) return;
        float reach = density * 240f;
        linePaint.setStrokeWidth(density * 1.5f);
        for (int i = 0; i < STREAKS; i++) {
            float cycle = (t / (700f - 350f * charge) + streakPhase[i]) % 1f;
            float outer = reach * (1f - cycle) + density * 50f;
            float inner = outer - density * 34f * streakLength[i] * (0.4f + charge);
            float cos = (float) Math.cos(streakAngle[i]);
            float sin = (float) Math.sin(streakAngle[i]);
            linePaint.setColor(alpha(i % 4 == 0 ? GOLD : TEAL, charge * (1f - cycle) * 0.9f));
            canvas.drawLine(anchorX + cos * outer, anchorY + sin * outer, anchorX + cos * Math.max(inner, 0f), anchorY + sin * Math.max(inner, 0f), linePaint);
        }
    }

    /** Long radial lines that make the zoom feel fast. */
    private void drawSpeedLines(Canvas canvas, float t) {
        float strength = BoostTimeline.speedLines(t);
        if (strength <= 0f) return;
        float far = Math.max(getWidth(), getHeight()) * 1.1f;
        linePaint.setStrokeWidth(density * 2f);
        for (int i = 0; i < STREAKS; i++) {
            float cos = (float) Math.cos(streakAngle[i] + 0.3f);
            float sin = (float) Math.sin(streakAngle[i] + 0.3f);
            float start = far * (0.08f + 0.5f * streakPhase[i]) * (1f + strength);
            float end = Math.min(far, start + far * (0.35f + 0.5f * streakLength[i] * strength));
            linePaint.setColor(alpha(i % 3 == 0 ? Color.WHITE : TEAL, strength * 0.75f));
            canvas.drawLine(anchorX + cos * start, anchorY + sin * start, anchorX + cos * end, anchorY + sin * end, linePaint);
        }
    }

    /** Shock rings, light rays and sparks. burstAge is in seconds. */
    private void drawBurst(Canvas canvas, float age, float overall) {
        float reach = Math.max(getWidth(), getHeight()) * 1.15f;

        // Shock rings, each a little later than the last.
        for (int i = 0; i < 3; i++) {
            float local = BoostTimeline.clamp01((age - i * 0.09f) / 0.75f);
            if (local <= 0f || local >= 1f) continue;
            float radius = BoostTimeline.easeOutCubic(local) * reach * (0.55f + 0.2f * i);
            ringPaint.setStrokeWidth(density * (9f * (1f - local) + 1f));
            ringPaint.setColor(alpha(i == 0 ? Color.WHITE : (i == 1 ? TEAL : GREEN), (1f - local) * 0.9f * overall));
            canvas.drawCircle(anchorX, anchorY, radius, ringPaint);
        }

        // Light rays: wedges that open up and fade.
        float rayLife = BoostTimeline.clamp01(age / 0.9f);
        if (rayLife < 1f) {
            float length = reach * BoostTimeline.easeOutCubic(rayLife);
            linePaint.setStrokeWidth(density * (10f * (1f - rayLife) + 2f));
            for (int i = 0; i < RAYS; i++) {
                float angle = rayAngle[i] + age * 0.5f;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                linePaint.setColor(alpha(i % 2 == 0 ? GOLD : Color.WHITE, (1f - rayLife) * 0.8f * overall));
                canvas.drawLine(anchorX + cos * length * 0.15f, anchorY + sin * length * 0.15f,
                        anchorX + cos * length, anchorY + sin * length, linePaint);
            }
        }

        // Sparks with short trails, slowing down and falling a little.
        float gravity = density * 380f;
        for (int i = 0; i < SPARKS; i++) {
            float life = sparkLife[i];
            if (age >= life) continue;
            float fade = 1f - age / life;
            float speed = sparkSpeed[i] * density;
            float drag = 1f - 0.5f * (age / life);
            float distance = speed * age * drag;
            float cos = (float) Math.cos(sparkAngle[i]);
            float sin = (float) Math.sin(sparkAngle[i]);
            float x = anchorX + cos * distance;
            float y = anchorY + sin * distance + 0.5f * gravity * age * age;
            float previousAge = Math.max(0f, age - 0.035f);
            float previousDistance = speed * previousAge * (1f - 0.5f * (previousAge / life));
            float px = anchorX + cos * previousDistance;
            float py = anchorY + sin * previousDistance + 0.5f * gravity * previousAge * previousAge;
            linePaint.setStrokeWidth(density * sparkSize[i]);
            linePaint.setColor(alpha(sparkColor[i], fade * overall));
            canvas.drawLine(px, py, x, y, linePaint);
        }
    }

    private void drawTitle(Canvas canvas, float t, int w, int h) {
        float scale = BoostTimeline.titleScale(t);
        float a = BoostTimeline.titleAlpha(t);
        if (scale <= 0f || a <= 0f) return;
        float cx = w / 2f;
        float cy = h * 0.42f;
        int save = canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        textPaint.setAlpha(Math.round(255 * a));
        textPaint.setShadowLayer(density * 14f, 0f, 0f, alpha(GREEN, a));
        canvas.drawText("PERFORMANCE MODE", 0f, 0f, textPaint);
        textPaint.clearShadowLayer();
        canvas.restoreToCount(save);

        // Line and subtitle under the title.
        float lineGrow = BoostTimeline.easeOutCubic(BoostTimeline.clamp01((t - BoostTimeline.TEXT_IN - 120f) / 400f));
        float half = titleHalfWidth * scale * lineGrow;
        linePaint.setStrokeWidth(density * 2f);
        linePaint.setColor(alpha(GREEN, a));
        float lineY = cy + textPaint.getTextSize() * 0.45f * scale;
        canvas.drawLine(cx - half, lineY, cx + half, lineY, linePaint);
        smallPaint.setTextSize(density * 11f * Math.min(scale, 1.1f));
        smallPaint.setColor(alpha(Color.WHITE, a));
        canvas.drawText("BOOST ACTIVE", cx, lineY + density * 22f * Math.min(scale, 1.1f), smallPaint);
    }
}
