package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A light ambient effect: a handful of tiny white specks (ash/snow) drifting slowly downward
 * with a gentle sideways sway, looping back to the top forever. Deliberately kept subtle — few
 * particles, slow motion, low opacity — so it reads as atmosphere, not a distraction.
 *
 * Drives its own frame loop with {@link View#postOnAnimation(Runnable)} (a Choreographer
 * callback) rather than the Android animation framework, so — like the glow pulse in
 * UrinthHomeController — it keeps moving even on devices where "Animator duration scale" is
 * turned off in Developer options.
 */
public final class ParticleOverlayView extends View {

    private static final int PARTICLE_COUNT = 26;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private Particle[] particles = new Particle[0];
    private long lastFrameNanos;
    private boolean running;

    private final Runnable frameCallback = this::onFrame;

    public ParticleOverlayView(Context context) {
        super(context);
        init();
    }

    public ParticleOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(Color.WHITE);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w <= 0 || h <= 0) return;
        Particle[] fresh = new Particle[PARTICLE_COUNT];
        for (int i = 0; i < fresh.length; i++) {
            fresh[i] = newParticle(random.nextFloat()); // spread across the full height to start
        }
        particles = fresh;
    }

    private Particle newParticle(float startY) {
        Particle p = new Particle();
        p.x = random.nextFloat();
        p.y = startY;
        // Mid-paced drift: not a blizzard, just a light "ash in the air" drip.
        p.speed = 0.012f + random.nextFloat() * 0.018f; // fraction of height per second
        p.swayAmplitude = 4f + random.nextFloat() * 8f; // dp either side
        p.swaySpeed = 0.4f + random.nextFloat() * 0.5f; // hz-ish
        p.swayPhase = random.nextFloat() * (float) (Math.PI * 2);
        p.radiusDp = 1f + random.nextFloat() * 2f;
        p.baseAlpha = 0.12f + random.nextFloat() * 0.22f; // kept faint on purpose
        p.twinklePhase = random.nextFloat() * (float) (Math.PI * 2);
        return p;
    }

    /** Starts the drift loop. Safe to call repeatedly. */
    public void start() {
        if (running) return;
        running = true;
        lastFrameNanos = 0L;
        removeCallbacks(frameCallback);
        postOnAnimation(frameCallback);
    }

    /** Stops the drift loop without discarding particle state, so resume() continues smoothly. */
    public void pause() {
        running = false;
        removeCallbacks(frameCallback);
    }

    public void resume() {
        start();
    }

    /** Stops the loop for good, e.g. when the home screen is torn down. */
    public void release() {
        running = false;
        removeCallbacks(frameCallback);
    }

    private void onFrame() {
        if (!running) return;
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.05f); // guard against a huge jump after the app was backgrounded

        int h = getHeight();
        if (h > 0) {
            for (Particle p : particles) {
                p.y += p.speed * dt;
                if (p.y > 1.05f) {
                    Particle fresh = newParticle(-0.05f);
                    p.x = fresh.x;
                    p.y = fresh.y;
                    p.speed = fresh.speed;
                    p.swayAmplitude = fresh.swayAmplitude;
                    p.swaySpeed = fresh.swaySpeed;
                    p.swayPhase = fresh.swayPhase;
                    p.radiusDp = fresh.radiusDp;
                    p.baseAlpha = fresh.baseAlpha;
                    p.twinklePhase = fresh.twinklePhase;
                }
                p.ageSeconds += dt;
            }
        }
        invalidate();
        postOnAnimation(frameCallback);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float density = getResources().getDisplayMetrics().density;

        for (Particle p : particles) {
            float sway = (float) Math.sin(p.ageSeconds * p.swaySpeed * Math.PI * 2 + p.swayPhase) * p.swayAmplitude * density;
            float cx = p.x * w + sway;
            float cy = p.y * h;
            float twinkle = (float) (0.75 + 0.25 * Math.sin(p.ageSeconds * 1.6 + p.twinklePhase));
            paint.setAlpha(clampAlpha(p.baseAlpha * twinkle));
            canvas.drawCircle(cx, cy, p.radiusDp * density, paint);
        }
    }

    private static int clampAlpha(float a) {
        int alpha = Math.round(a * 255f);
        if (alpha < 0) return 0;
        if (alpha > 255) return 255;
        return alpha;
    }

    private static final class Particle {
        float x, y;
        float speed;
        float swayAmplitude;
        float swaySpeed;
        float swayPhase;
        float radiusDp;
        float baseAlpha;
        float twinklePhase;
        float ageSeconds;
    }
}
