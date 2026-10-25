package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Random;

/**
 * The animation played when Performance Mode is switched on (about 8 seconds). Original artwork
 * built from plain shapes; the only characters are the 0s and 1s of the "hacking" effect.
 *
 * <ol>
 *   <li>Hack-in: the camera roams over a frozen picture of the launcher, diving from point to
 *       point, while binary digits rain, the picture glitches, and then cracks into panels.</li>
 *   <li>Suck: panels, digits and light streaks spiral into a black hole in the middle. The camera
 *       dives in and the shaking builds.</li>
 *   <li>Big bang: flash, fracture lines, shock rings. The camera punches back out fast and the
 *       panels of the launcher blast outward and fall apart.</li>
 *   <li>The emblem draws itself: a hexagon, a gauge that fills tick by tick with a bright comet
 *       head, a bolt that powers up, orbiting rings, and a sweep of light.</li>
 *   <li>The emblem flies into the card's own icon, which pulses.</li>
 * </ol>
 * Times come from {@link BoostTimeline}. The clock is the display's frame clock (not an Android
 * animator, which some phones switch off), and no frame can advance it by more than 50 ms.
 */
public final class PerformanceBoostView extends View {
    private static final int TEAL = 0xFF3CE6C0;
    private static final int GREEN = 0xFF1FC77A;
    private static final int CYAN = 0xFF4FD8FF;
    private static final int GOLD = 0xFFFFC83C;
    private static final int BACKDROP = 0xFF02070C;
    private static final int[] SPARK_COLORS = {TEAL, Color.WHITE, GREEN, CYAN, TEAL, GOLD};
    private static final int HEX_SIDES = 6;
    private static final int GAUGE_TICKS = 44;
    private static final float GAUGE_START_DEG = 135f;
    private static final float GAUGE_SWEEP_DEG = 270f;
    private static final int SPARKS = 90;
    private static final int MOTES = 26;
    private static final int GRID_LINES = 22;

    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int TILES = COLS * ROWS;
    private static final int MAX_RAIN_COLUMNS = 64;
    private static final int RAIN_TRAIL = 11;
    private static final int STREAKS = 56;
    private static final int CRACKS = 14;
    private static final float GRAVITY_DP = 1100f;

    /** Callbacks for the owner. Both are always called, even if the screen goes away early. */
    public interface Listener {
        /** The emblem landed in the card icon: show the new state now. */
        void onBurst();

        void onFinished();
    }

    private final float density;
    private final Listener listener;
    private final RectF target = new RectF();      // the Performance Mode card, in this view's coordinates
    private final float iconX, iconY, iconSize;    // the card's icon, where the emblem lands
    private final Bitmap snapshot;

    private final Paint scrimPaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint addPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint();
    private final Paint flashPaint = new Paint();
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bolt = new Path();
    private final Path hexPath = new Path();
    private final Path hexSegment = new Path();
    private final Path[] cracks = new Path[CRACKS];
    private final PathMeasure hexMeasure = new PathMeasure();
    private final RectF rect = new RectF();
    private final RectF dstRect = new RectF();
    private final Rect srcRect = new Rect();
    private final char[] bit = new char[1];

    private final ColorMatrixColorFilter redOnly = channel(0);
    private final ColorMatrixColorFilter greenOnly = channel(1);
    private final ColorMatrixColorFilter blueOnly = channel(2);
    private final PorterDuffColorFilter tealFilter = new PorterDuffColorFilter(TEAL, PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter greenFilter = new PorterDuffColorFilter(GREEN, PorterDuff.Mode.SRC_IN);

    // Random but fixed looks of the sparks, motes, rain, streaks and panels.
    private final float[] sparkAngle = new float[SPARKS];
    private final float[] sparkSpeed = new float[SPARKS];
    private final float[] sparkLife = new float[SPARKS];
    private final float[] sparkSize = new float[SPARKS];
    private final int[] sparkColor = new int[SPARKS];
    private final float[] moteAngle = new float[MOTES];
    private final float[] moteRadius = new float[MOTES];
    private final float[] moteSpeed = new float[MOTES];
    private final float[] moteSize = new float[MOTES];
    private final float[] rainSpeed = new float[MAX_RAIN_COLUMNS];   // px per ms
    private final float[] rainOffset = new float[MAX_RAIN_COLUMNS];  // px
    private final float[] streakAngle = new float[STREAKS];
    private final float[] streakSpeed = new float[STREAKS];
    private final float[] streakOffset = new float[STREAKS];
    private final float[] tileRandom = new float[TILES];
    private final float[] tileDelay = new float[TILES];
    private final float[] tileSpin = new float[TILES];       // +1 or -1
    private final float[] tileJitterX = new float[TILES];
    private final float[] tileJitterY = new float[TILES];
    private final float[] tileJitterRot = new float[TILES];
    private final float[] tileDirX = new float[TILES];
    private final float[] tileDirY = new float[TILES];
    private final float[] tileSpeed = new float[TILES];      // dp per second at the bang
    private final float[] tileSpinSpeed = new float[TILES];  // degrees per second after the bang

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            tick(frameTimeNanos);
        }
    };

    private float elapsedMs;
    private long lastFrameNanos;
    private boolean running;
    private boolean arrivedSent;
    private boolean finishedSent;
    private boolean suckBuzzed;
    private boolean bangBuzzed;

    private float centerX, centerY, emblemRadius, hexLength;
    private float rainSpacing, rainLineHeight, rainCycle;
    private int rainColumns;
    private float cameraFocusX, cameraFocusY;

    private PerformanceBoostView(Context context, Bitmap snapshot, RectF target,
                                 float iconX, float iconY, float iconSize, Listener listener) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.snapshot = snapshot;
        this.target.set(target);
        this.iconX = iconX;
        this.iconY = iconY;
        this.iconSize = iconSize;
        this.listener = listener;
        setClickable(true); // swallow touches while it plays
        setFocusable(false);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        addPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        rainPaint.setTypeface(Typeface.MONOSPACE);
        rainPaint.setTextAlign(Paint.Align.CENTER);
        rainPaint.setTextSize(12f * density);

        // Lightning bolt on a 24 x 24 grid centred on (12, 12).
        bolt.moveTo(13f, 2f);
        bolt.lineTo(4f, 14f);
        bolt.lineTo(10f, 14f);
        bolt.lineTo(9f, 22f);
        bolt.lineTo(18f, 10f);
        bolt.lineTo(12f, 10f);
        bolt.close();

        Random random = new Random(21);
        for (int i = 0; i < SPARKS; i++) {
            sparkAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
            sparkSpeed[i] = 220f + random.nextFloat() * 900f; // dp per second
            sparkLife[i] = 0.6f + random.nextFloat() * 0.9f;
            sparkSize[i] = 0.7f + random.nextFloat() * 1.3f;
            sparkColor[i] = SPARK_COLORS[random.nextInt(SPARK_COLORS.length)];
        }
        for (int i = 0; i < MOTES; i++) {
            moteAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
            moteRadius[i] = 0.55f + random.nextFloat() * 0.8f;
            moteSpeed[i] = (random.nextBoolean() ? 1f : -1f) * (0.6f + random.nextFloat() * 1.4f);
            moteSize[i] = 0.8f + random.nextFloat() * 1.4f;
        }
        for (int i = 0; i < MAX_RAIN_COLUMNS; i++) {
            rainSpeed[i] = (110f + random.nextFloat() * 300f) * density / 1000f;
            rainOffset[i] = random.nextFloat() * 4000f;
        }
        for (int i = 0; i < STREAKS; i++) {
            streakAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
            streakSpeed[i] = 0.7f + random.nextFloat() * 0.9f;
            streakOffset[i] = random.nextFloat();
        }
        for (int i = 0; i < TILES; i++) {
            tileRandom[i] = random.nextFloat();
            tileSpin[i] = random.nextBoolean() ? 1f : -1f;
            tileJitterX[i] = (random.nextFloat() - 0.5f) * 2f * (2f + random.nextFloat() * 6f);
            tileJitterY[i] = (random.nextFloat() - 0.5f) * 2f * (2f + random.nextFloat() * 6f);
            tileJitterRot[i] = (random.nextFloat() - 0.5f) * 8f;
            tileSpeed[i] = 650f + random.nextFloat() * 900f;
            tileSpinSpeed[i] = tileSpin[i] * (180f + random.nextFloat() * 440f);
        }
    }

    private static ColorMatrixColorFilter channel(int index) {
        float[] m = new float[20];
        m[index * 5 + index] = 1f;   // keep only this colour channel
        m[18] = 1f;                  // alpha passes through
        return new ColorMatrixColorFilter(m);
    }

    /**
     * Plays the animation over the whole screen, starting on the card, ending in the card's icon.
     * The listener's callbacks are always called, even if the animation cannot be shown.
     */
    public static void play(Activity activity, View card, View icon, Listener listener) {
        ViewGroup content = activity == null ? null : (ViewGroup) activity.findViewById(android.R.id.content);
        if (content == null || card == null || icon == null || content.getWidth() == 0 || content.getHeight() == 0) {
            listener.onBurst();
            listener.onFinished();
            return;
        }
        int[] a = new int[2];
        int[] b = new int[2];
        content.getLocationInWindow(b);
        card.getLocationInWindow(a);
        RectF cardRect = new RectF(a[0] - b[0], a[1] - b[1], a[0] - b[0] + card.getWidth(), a[1] - b[1] + card.getHeight());
        icon.getLocationInWindow(a);
        float iconX = a[0] - b[0] + icon.getWidth() / 2f;
        float iconY = a[1] - b[1] + icon.getHeight() / 2f;
        float iconSize = Math.max(icon.getWidth(), icon.getHeight());

        // A half-size picture of the screen is enough, and cheap to redraw many times a frame.
        Bitmap snapshot = null;
        try {
            snapshot = Bitmap.createBitmap(Math.max(1, content.getWidth() / 2), Math.max(1, content.getHeight() / 2), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(snapshot);
            canvas.scale(0.5f, 0.5f);
            content.draw(canvas);
        } catch (RuntimeException | OutOfMemoryError failed) {
            snapshot = null; // the effect still plays, just without the launcher picture
        }
        PerformanceBoostView view = new PerformanceBoostView(activity, snapshot, cardRect, iconX, iconY, iconSize, listener);
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
        sendArrived();
        sendFinished();
        if (snapshot != null && !snapshot.isRecycled()) snapshot.recycle();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w * BoostTimeline.CENTER_X;
        centerY = h * BoostTimeline.CENTER_Y;
        emblemRadius = Math.min(h * 0.19f, w * 0.115f);

        glowPaint.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{0xFFFFFFFF, 0x80FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        vignettePaint.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x00000000, 0xE6000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        // Horizontal light streak, defined from -1 to 1 and scaled when drawn.
        gradientPaint.setShader(new LinearGradient(-1f, 0f, 1f, 0f,
                new int[]{0x004FD8FF, 0xCC4FD8FF, 0xFFFFFFFF, 0xCC4FD8FF, 0x004FD8FF}, null, Shader.TileMode.CLAMP));
        // The light sweep across the emblem, drawn in a unit box and scaled.
        sweepPaint.setShader(new LinearGradient(-1f, 0f, 1f, 0f,
                new int[]{0x00FFFFFF, 0x8CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));

        // Regular hexagon with a vertex at the top, as a closed outline starting from that vertex.
        hexPath.reset();
        for (int i = 0; i < HEX_SIDES; i++) {
            double angle = -Math.PI / 2 + i * Math.PI * 2 / HEX_SIDES;
            float x = (float) Math.cos(angle) * emblemRadius;
            float y = (float) Math.sin(angle) * emblemRadius;
            if (i == 0) hexPath.moveTo(x, y);
            else hexPath.lineTo(x, y);
        }
        hexPath.close();
        hexMeasure.setPath(hexPath, true);
        hexLength = hexMeasure.getLength();

        // Rain: one column of digits every few dp, never more than the arrays hold.
        rainSpacing = Math.max(12f * density, w / 46f);
        rainColumns = Math.min(MAX_RAIN_COLUMNS, (int) (w / rainSpacing) + 1);
        rainLineHeight = rainPaint.getTextSize() * 1.15f;
        rainCycle = h + RAIN_TRAIL * rainLineHeight;

        // Panels: the ones nearest the middle are pulled in first, the outer ones last.
        float tileW = w / (float) COLS;
        float tileH = h / (float) ROWS;
        float maxDistance = (float) Math.hypot(w / 2f, h / 2f);
        Random random = new Random(8);
        for (int i = 0; i < TILES; i++) {
            float cx = (i % COLS + 0.5f) * tileW;
            float cy = (i / COLS + 0.5f) * tileH;
            float dx = cx - centerX;
            float dy = cy - centerY;
            float distance = (float) Math.hypot(dx, dy);
            tileDelay[i] = Math.min(0.55f, 0.45f * distance / maxDistance + tileRandom[i] * 0.1f);
            double angle = distance < 1f ? random.nextFloat() * Math.PI * 2 : Math.atan2(dy, dx) + (random.nextFloat() - 0.5f) * 1.1f;
            tileDirX[i] = (float) Math.cos(angle);
            tileDirY[i] = (float) Math.sin(angle);
        }

        // Fracture lines from the middle to the edge of the screen: jagged, with a few branches.
        float reach = maxDistance * 1.05f;
        for (int i = 0; i < CRACKS; i++) {
            Path path = new Path();
            double angle = Math.PI * 2 * i / CRACKS + (random.nextFloat() - 0.5f) * 0.3f;
            float x = 0f;
            float y = 0f;
            path.moveTo(0f, 0f);
            int segments = 9;
            for (int s = 1; s <= segments; s++) {
                angle += (random.nextFloat() - 0.5f) * 0.45f;
                float step = reach / segments * (0.7f + random.nextFloat() * 0.6f);
                x += (float) Math.cos(angle) * step;
                y += (float) Math.sin(angle) * step;
                path.lineTo(x, y);
                if (s == 3 || s == 6) { // a short branch
                    double branch = angle + (random.nextBoolean() ? 0.9 : -0.9);
                    path.lineTo(x + (float) Math.cos(branch) * step * 0.7f, y + (float) Math.sin(branch) * step * 0.7f);
                    path.moveTo(x, y);
                }
            }
            cracks[i] = path;
        }
    }

    private void tick(long frameTimeNanos) {
        if (!running) return;
        if (lastFrameNanos != 0) {
            long step = Math.min(frameTimeNanos - lastFrameNanos, 50_000_000L);
            if (step > 0) elapsedMs += step / 1_000_000f;
        }
        lastFrameNanos = frameTimeNanos;
        if (!suckBuzzed && elapsedMs >= BoostTimeline.SUCK_START) {
            suckBuzzed = true;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (!bangBuzzed && elapsedMs >= BoostTimeline.BANG_AT) {
            bangBuzzed = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        if (BoostTimeline.arrived(elapsedMs)) sendArrived();
        if (elapsedMs >= BoostTimeline.DURATION_MS) {
            running = false;
            sendArrived();
            sendFinished();
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            return;
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private void sendArrived() {
        if (arrivedSent) return;
        arrivedSent = true;
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

        scrimPaint.setColor(alpha(BACKDROP, BoostTimeline.scrim(t)));
        canvas.drawRect(0, 0, w, h, scrimPaint);

        int shakeSave = canvas.save();
        float shake = BoostTimeline.shake(t) * density * 11f;
        canvas.translate((float) (Math.sin(t * 0.097) * Math.cos(t * 0.041)) * shake,
                (float) (Math.cos(t * 0.083) * Math.sin(t * 0.057)) * shake);

        // The world: the picture of the launcher, seen through the roaming camera.
        int worldSave = canvas.save();
        applyCamera(canvas, t, w, h);
        if (snapshot != null && !snapshot.isRecycled()) {
            if (t < BoostTimeline.CRACK_START) drawPicture(canvas, t, w, h);
            else drawPanels(canvas, t, w, h);
        }
        drawLockOn(canvas, t);
        canvas.restoreToCount(worldSave);

        drawRain(canvas, t, w, h);
        drawSuckStreaks(canvas, t, w, h);
        drawBlackHole(canvas, t);
        drawGlitchBars(canvas, t, w, h);

        float vignette = BoostTimeline.vignette(t);
        if (vignette > 0f) {
            vignettePaint.setAlpha(Math.round(255 * vignette));
            canvas.drawRect(0, 0, w, h, vignettePaint);
        }

        drawGrid(canvas, t, w, h);
        drawBangEffects(canvas, t, w, h, overall);
        drawEmblem(canvas, t, overall);
        canvas.restoreToCount(shakeSave);

        drawLanding(canvas, t, overall);

        float flash = BoostTimeline.flash(t);
        if (flash > 0f) {
            flashPaint.setColor(alpha(0xFFEAFFFB, flash * 0.92f));
            canvas.drawRect(0, 0, w, h, flashPaint);
        }
    }

    /** Camera: scale about a focus point that glides over the screen, with a roll. */
    private void applyCamera(Canvas canvas, float t, int w, int h) {
        float zoom = BoostTimeline.cameraZoom(t);
        float pull = BoostTimeline.cameraPull(zoom);
        cameraFocusX = BoostTimeline.cameraFocusX(t, target.centerX() / w) * w;
        cameraFocusY = BoostTimeline.cameraFocusY(t, target.centerY() / h) * h;
        float sx = cameraFocusX + (centerX - cameraFocusX) * pull;
        float sy = cameraFocusY + (centerY - cameraFocusY) * pull;
        canvas.translate(sx, sy);
        canvas.rotate(BoostTimeline.cameraRoll(t));
        canvas.scale(zoom, zoom);
        canvas.translate(-cameraFocusX, -cameraFocusY);
    }

    /** The whole picture, with colour fringing, zoom blur and glitch strips. */
    private void drawPicture(Canvas canvas, float t, int w, int h) {
        float blur = BoostTimeline.zoomBlur(t);
        float fringe = BoostTimeline.aberration(t);
        float zoomed = 1f;
        rect.set(0, 0, w, h);

        if (fringe > 0.04f) {
            // Three colour passes slightly apart: red and blue drift away from green.
            flashPaint.setColor(Color.BLACK);
            canvas.drawRect(0, 0, w, h, flashPaint);
            float offset = fringe * density * 9f;
            drawColourPass(canvas, redOnly, 1f + fringe * 0.012f, offset);
            drawColourPass(canvas, greenOnly, zoomed, 0f);
            drawColourPass(canvas, blueOnly, 1f - fringe * 0.012f, -offset);
        } else {
            bitmapPaint.setAlpha(255);
            canvas.drawBitmap(snapshot, null, rect, bitmapPaint);
        }

        // Zoom blur: copies at slightly larger scales, blended with equal weight.
        if (blur > 0.03f) {
            for (int k = 1; k <= 2; k++) {
                float s = 1f + blur * 0.05f * k;
                bitmapPaint.setAlpha(Math.round(255f / (k + 2)));
                canvas.save();
                canvas.scale(s, s, cameraFocusX, cameraFocusY);
                canvas.drawBitmap(snapshot, null, rect, bitmapPaint);
                canvas.restore();
            }
            bitmapPaint.setAlpha(255);
        }

        // Glitch: strips of the picture knocked sideways.
        float glitch = BoostTimeline.glitch(t);
        if (glitch > 0f) {
            int slot = (int) (t / 150f);
            for (int i = 0; i < 4; i++) {
                int hash = BoostTimeline.hash(slot * 7 + i);
                float top = h * ((hash & 255) / 255f) * 0.9f;
                float height = h * (0.02f + 0.04f * (((hash >>> 8) & 255) / 255f));
                float shift = (((hash >>> 16) & 1) == 0 ? 1f : -1f) * density * 40f * glitch;
                canvas.save();
                canvas.clipRect(0, top, w, top + height);
                canvas.translate(shift, 0f);
                canvas.drawBitmap(snapshot, null, rect, bitmapPaint);
                canvas.restore();
            }
        }
    }

    private void drawColourPass(Canvas canvas, ColorMatrixColorFilter filter, float scale, float dx) {
        addPaint.setColorFilter(filter);
        canvas.save();
        canvas.translate(dx, 0f);
        canvas.scale(scale, scale, cameraFocusX, cameraFocusY);
        canvas.drawBitmap(snapshot, null, rect, addPaint);
        canvas.restore();
        addPaint.setColorFilter(null);
    }

    /**
     * The picture as a grid of loose panels. They shudder, get sucked into the black hole one by
     * one (the middle first), and after the bang blast outward, spinning, and fall.
     */
    private void drawPanels(Canvas canvas, float t, int w, int h) {
        float visible = BoostTimeline.shardAlpha(t);
        if (visible <= 0f) return;
        float suck = BoostTimeline.suck(t);
        float age = BoostTimeline.popAge(t);
        float loose = BoostTimeline.looseness(t);
        float tileW = w / (float) COLS;
        float tileH = h / (float) ROWS;
        int bw = snapshot.getWidth();
        int bh = snapshot.getHeight();
        boolean exploding = t >= BoostTimeline.BANG_AT;
        float grow = BoostTimeline.easeOutCubic(BoostTimeline.clamp01(age / 0.25f));
        float fall = 0.5f * GRAVITY_DP * density * age * age;

        bitmapPaint.setAlpha(Math.round(255 * visible));
        ringPaint.setStrokeWidth(Math.max(1f, density));
        for (int i = 0; i < TILES; i++) {
            int col = i % COLS;
            int row = i / COLS;
            srcRect.set(col * bw / COLS, row * bh / ROWS, (col + 1) * bw / COLS, (row + 1) * bh / ROWS);
            float cx = (col + 0.5f) * tileW;
            float cy = (row + 0.5f) * tileH;

            float sp = BoostTimeline.clamp01((suck - tileDelay[i]) / (1f - tileDelay[i]));
            float e = BoostTimeline.easeInCubic(sp);
            float px, py, rotation, scale;
            float shudder = (0.55f + 0.45f * (float) Math.sin(t * 0.045f + i * 1.7f)) * loose;
            if (!exploding) {
                // Spiral in: turn the offset from the middle while it shrinks.
                float dx = cx - centerX;
                float dy = cy - centerY;
                double turn = Math.toRadians(tileSpin[i] * 140f * e);
                float cos = (float) Math.cos(turn);
                float sin = (float) Math.sin(turn);
                float keep = 1f - e;
                px = centerX + (dx * cos - dy * sin) * keep + tileJitterX[i] * density * shudder;
                py = centerY + (dx * sin + dy * cos) * keep + tileJitterY[i] * density * shudder;
                rotation = tileSpin[i] * 540f * e + tileJitterRot[i] * shudder;
                scale = (1f - 0.03f * loose) * (1f - 0.9f * e);
            } else {
                // Blast out from the middle, slowing down, then fall under gravity.
                float travel = tileSpeed[i] * density * (1f - (float) Math.exp(-2.4f * age)) / 2.4f;
                px = centerX + tileDirX[i] * travel;
                py = centerY + tileDirY[i] * travel + fall;
                rotation = tileSpin[i] * 540f + tileSpinSpeed[i] * age;
                scale = 0.1f + 0.9f * grow;
            }

            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(rotation);
            canvas.scale(scale, scale);
            dstRect.set(-tileW / 2f - 0.5f, -tileH / 2f - 0.5f, tileW / 2f + 0.5f, tileH / 2f + 0.5f);
            canvas.drawBitmap(snapshot, srcRect, dstRect, bitmapPaint);
            // A glowing edge makes the break line of each panel visible.
            float edge = exploding ? 0.8f * (1f - BoostTimeline.clamp01(age / 1.2f)) : 0.7f * loose;
            if (edge > 0.02f) {
                ringPaint.setColor(alpha(i % 4 == 0 ? Color.WHITE : CYAN, edge * visible));
                dstRect.set(-tileW / 2f, -tileH / 2f, tileW / 2f, tileH / 2f);
                canvas.drawRect(dstRect, ringPaint);
            }
            canvas.restore();
        }
        bitmapPaint.setAlpha(255);
    }

    /** Targeting brackets and a scan line that lock on the card and follow it while the camera roams. */
    private void drawLockOn(Canvas canvas, float t) {
        float lock = BoostTimeline.easeOutCubic(BoostTimeline.clamp01(t / BoostTimeline.LOCK_END));
        float visible = 1f - BoostTimeline.clamp01((t - 1100f) / 300f);
        if (lock <= 0f || visible <= 0f) return;
        float grow = (1f - lock) * density * 46f;
        float left = target.left - grow, top = target.top - grow, right = target.right + grow, bottom = target.bottom + grow;
        float arm = density * 18f;
        linePaint.setStrokeWidth(density * 1.8f);
        linePaint.setColor(alpha(TEAL, visible * (0.4f + 0.6f * lock)));
        canvas.drawLine(left, top, left + arm, top, linePaint);
        canvas.drawLine(left, top, left, top + arm, linePaint);
        canvas.drawLine(right, top, right - arm, top, linePaint);
        canvas.drawLine(right, top, right, top + arm, linePaint);
        canvas.drawLine(left, bottom, left + arm, bottom, linePaint);
        canvas.drawLine(left, bottom, left, bottom - arm, linePaint);
        canvas.drawLine(right, bottom, right - arm, bottom, linePaint);
        canvas.drawLine(right, bottom, right, bottom - arm, linePaint);

        // Scan line running down the card, and small crosshair ticks on the middle of each side.
        float scanY = target.top + target.height() * ((t / 420f) % 1f);
        linePaint.setStrokeWidth(density * 1.2f);
        linePaint.setColor(alpha(CYAN, visible * 0.7f));
        canvas.drawLine(target.left, scanY, target.right, scanY, linePaint);
        float tick = density * 8f;
        linePaint.setColor(alpha(Color.WHITE, visible * 0.6f * lock));
        canvas.drawLine(left, target.centerY(), left + tick, target.centerY(), linePaint);
        canvas.drawLine(right, target.centerY(), right - tick, target.centerY(), linePaint);
        canvas.drawLine(target.centerX(), top, target.centerX(), top + tick, linePaint);
        canvas.drawLine(target.centerX(), bottom, target.centerX(), bottom - tick, linePaint);
    }

    /** Falling columns of 0s and 1s, turned and shrunk into the black hole during the suck. */
    private void drawRain(Canvas canvas, float t, int w, int h) {
        float level = BoostTimeline.rainAlpha(t);
        if (level <= 0.01f || rainColumns == 0) return;
        float scale = BoostTimeline.rainScale(t);
        int save = canvas.save();
        canvas.translate(centerX, centerY);
        canvas.rotate(BoostTimeline.rainSpin(t));
        canvas.scale(scale, scale);
        canvas.translate(-centerX, -centerY);
        int tick = (int) (t / 95f);
        for (int col = 0; col < rainColumns; col++) {
            float x = (col + 0.5f) * rainSpacing;
            float head = (rainOffset[col] + t * rainSpeed[col]) % rainCycle;
            for (int j = 0; j < RAIN_TRAIL; j++) {
                float y = head - j * rainLineHeight;
                if (y < -rainLineHeight || y > h + rainLineHeight) continue;
                int hash = BoostTimeline.hash(col * 131 + j * 17 + tick / (2 + col % 3));
                bit[0] = (char) ('0' + (hash & 1));
                float fade = 1f - j / (float) RAIN_TRAIL;
                if (j == 0) rainPaint.setColor(alpha(0xFFE6FFF4, level));
                else rainPaint.setColor(alpha(col % 5 == 0 ? CYAN : GREEN, level * fade * 0.6f));
                canvas.drawText(bit, 0, 1, x, y, rainPaint);
            }
        }
        canvas.restoreToCount(save);
    }

    /** Thin lines of light racing in from the edges of the screen towards the middle. */
    private void drawSuckStreaks(Canvas canvas, float t, int w, int h) {
        if (t >= BoostTimeline.BANG_AT) return;
        float visible = BoostTimeline.clamp01((t - BoostTimeline.SUCK_START) / 500f);
        if (visible <= 0f) return;
        float maxRadius = (float) Math.hypot(w, h) / 2f;
        for (int i = 0; i < STREAKS; i++) {
            float p = (t * 0.0009f * streakSpeed[i] + streakOffset[i]) % 1f;
            float radius = maxRadius * (1f - p * p);
            float length = radius * (0.12f + 0.3f * p);
            float cos = (float) Math.cos(streakAngle[i]);
            float sin = (float) Math.sin(streakAngle[i]);
            linePaint.setStrokeWidth(density * (0.8f + 0.9f * p));
            linePaint.setColor(alpha(i % 3 == 0 ? Color.WHITE : CYAN, visible * (float) Math.sin(p * Math.PI) * 0.85f));
            canvas.drawLine(centerX + cos * radius, centerY + sin * radius,
                    centerX + cos * (radius + length), centerY + sin * (radius + length), linePaint);
        }
    }

    /** The black hole that grows in the middle while everything is pulled in. */
    private void drawBlackHole(Canvas canvas, float t) {
        float s = BoostTimeline.singularity(t);
        if (s <= 0.01f || emblemRadius <= 0f) return;
        float radius = emblemRadius * (0.12f + 0.75f * s);
        float spin = t * 0.28f;

        int save = canvas.save();
        canvas.translate(centerX, centerY);
        int glowSave = canvas.save();
        float glowRadius = radius * 3.2f;
        canvas.scale(glowRadius, glowRadius);
        glowPaint.setColorFilter(tealFilter);
        glowPaint.setAlpha(Math.round(255 * 0.5f * s));
        canvas.drawCircle(0f, 0f, 1f, glowPaint);
        glowPaint.setColorFilter(null);
        canvas.restoreToCount(glowSave);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0xFF000000);
        canvas.drawCircle(0f, 0f, radius, fillPaint);

        ringPaint.setStrokeWidth(density * 2.4f);
        for (int ring = 0; ring < 2; ring++) {
            float r = radius * (1.28f + 0.3f * ring);
            rect.set(-r, -r, r, r);
            ringPaint.setColor(alpha(ring == 0 ? Color.WHITE : CYAN, s * 0.9f));
            float direction = ring == 0 ? 1f : -1f;
            for (int arc = 0; arc < 4; arc++) {
                canvas.drawArc(rect, direction * spin * (1f + 0.4f * ring) + arc * 90f, 55f, false, ringPaint);
            }
        }
        ringPaint.setStrokeWidth(density * 1.2f);
        ringPaint.setColor(alpha(Color.WHITE, s));
        canvas.drawCircle(0f, 0f, radius * 1.06f, ringPaint);
        canvas.restoreToCount(save);
    }

    /** Hacker glitch: green bars flicker across the screen, and a scan line sweeps down it. */
    private void drawGlitchBars(Canvas canvas, float t, int w, int h) {
        if (t < 300f || t >= BoostTimeline.BANG_AT) return;
        float glitch = BoostTimeline.glitch(t);
        fillPaint.setStyle(Paint.Style.FILL);
        if (glitch > 0f) {
            int slot = (int) (t / 150f);
            for (int i = 0; i < 3; i++) {
                int hash = BoostTimeline.hash(slot * 13 + i + 5);
                float top = h * ((hash & 255) / 255f);
                float height = density * (4f + ((hash >>> 8) & 31));
                fillPaint.setColor(alpha(i == 1 ? CYAN : GREEN, glitch * 0.22f));
                canvas.drawRect(0, top, w, top + height, fillPaint);
                linePaint.setStrokeWidth(density);
                linePaint.setColor(alpha(Color.WHITE, glitch * 0.35f));
                canvas.drawLine(0, top, w, top, linePaint);
            }
        }
        float scanY = h * ((t / 1100f) % 1f);
        fillPaint.setColor(alpha(CYAN, 0.07f));
        canvas.drawRect(0, scanY - density * 22f, w, scanY, fillPaint);
        linePaint.setStrokeWidth(density);
        linePaint.setColor(alpha(CYAN, 0.35f));
        canvas.drawLine(0, scanY, w, scanY, linePaint);
    }

    /** Faint grid that appears behind the emblem after the panels have gone. */
    private void drawGrid(Canvas canvas, float t, int w, int h) {
        float a = BoostTimeline.clamp01((t - BoostTimeline.BANG_AT - 500f) / 600f) * BoostTimeline.overall(t);
        if (a <= 0f) return;
        linePaint.setStrokeWidth(Math.max(1f, density * 0.7f));
        float step = h / (float) GRID_LINES * 1.9f;
        float drift = (t * 0.02f * density) % step;
        for (float x = centerX % step - step; x < w; x += step) {
            float distance = Math.abs(x - centerX) / (w / 2f);
            linePaint.setColor(alpha(TEAL, 0.12f * a * (1.1f - distance)));
            canvas.drawLine(x, 0, x, h, linePaint);
        }
        for (float y = -step + drift; y < h; y += step) {
            float distance = Math.abs(y - centerY) / (h / 2f);
            linePaint.setColor(alpha(TEAL, 0.12f * a * (1.1f - distance)));
            canvas.drawLine(0, y, w, y, linePaint);
        }
    }

    /** Fracture lines, shock rings, light streak, sparks and flying bits that leave the centre at the bang. */
    private void drawBangEffects(Canvas canvas, float t, int w, int h, float overall) {
        float age = BoostTimeline.popAge(t);
        if (age <= 0f) return;
        float reach = Math.max(w, h) * 1.1f;

        // Fracture lines of light, drawn in quickly and then fading out.
        float crack = BoostTimeline.clamp01(1f - age / 1.4f);
        if (crack > 0f) {
            float grow = 0.35f + 0.65f * BoostTimeline.easeOutCubic(BoostTimeline.clamp01(age / 0.25f));
            int save = canvas.save();
            canvas.translate(centerX, centerY);
            canvas.scale(grow, grow);
            ringPaint.setStrokeJoin(Paint.Join.ROUND);
            ringPaint.setStrokeWidth(density * 3.4f);
            ringPaint.setColor(alpha(CYAN, crack * 0.45f * overall));
            for (int i = 0; i < CRACKS; i++) canvas.drawPath(cracks[i], ringPaint);
            ringPaint.setStrokeWidth(density * 1.1f);
            ringPaint.setColor(alpha(Color.WHITE, crack * 0.95f * overall));
            for (int i = 0; i < CRACKS; i++) canvas.drawPath(cracks[i], ringPaint);
            canvas.restoreToCount(save);
        }

        for (int i = 0; i < 3; i++) {
            float local = BoostTimeline.clamp01((age - i * 0.12f) / 0.85f);
            if (local <= 0f || local >= 1f) continue;
            float radius = BoostTimeline.easeOutCubic(local) * reach * (0.55f + 0.22f * i);
            ringPaint.setStrokeWidth(density * (5f * (1f - local) + 0.8f));
            ringPaint.setColor(alpha(i == 0 ? Color.WHITE : (i == 1 ? CYAN : TEAL), (1f - local) * 0.85f * overall));
            canvas.drawCircle(centerX, centerY, radius, ringPaint);
        }

        // Anamorphic light streak: a thin bright line stretched across the screen.
        float streak = BoostTimeline.clamp01(age / 0.9f);
        if (streak < 1f) {
            float length = w * (0.15f + 0.65f * BoostTimeline.easeOutCubic(streak));
            float thickness = density * (3.4f * (1f - streak) + 0.6f);
            gradientPaint.setAlpha(Math.round(255 * (1f - streak) * overall));
            canvas.save();
            canvas.translate(centerX, centerY);
            canvas.scale(length, thickness);
            canvas.drawRect(-1f, -1f, 1f, 1f, gradientPaint);
            canvas.restore();
        }

        // Thin sparks with short trails; every third one is a 0 or a 1 instead.
        int tick = (int) (t / 110f);
        for (int i = 0; i < SPARKS; i++) {
            float life = sparkLife[i];
            if (age >= life) continue;
            float fade = 1f - age / life;
            float speed = sparkSpeed[i] * density;
            float distance = speed * age * (1f - 0.55f * (age / life));
            float cos = (float) Math.cos(sparkAngle[i]);
            float sin = (float) Math.sin(sparkAngle[i]);
            if (i % 3 == 0) {
                bit[0] = (char) ('0' + (BoostTimeline.hash(i * 7 + tick) & 1));
                rainPaint.setColor(alpha(sparkColor[i], fade * overall));
                canvas.drawText(bit, 0, 1, centerX + cos * distance, centerY + sin * distance, rainPaint);
            } else {
                float previousAge = Math.max(0f, age - 0.03f);
                float previousDistance = speed * previousAge * (1f - 0.55f * (previousAge / life));
                linePaint.setStrokeWidth(density * sparkSize[i]);
                linePaint.setColor(alpha(sparkColor[i], fade * overall));
                canvas.drawLine(centerX + cos * previousDistance, centerY + sin * previousDistance,
                        centerX + cos * distance, centerY + sin * distance, linePaint);
            }
        }
    }

    /** The emblem: hexagon, gauge, bolt, orbit rings and a sweep of light. */
    private void drawEmblem(Canvas canvas, float t, float overall) {
        float alphaNow = BoostTimeline.emblemAlpha(t) * overall;
        float pop = BoostTimeline.emblemPop(t);
        if (alphaNow <= 0f || pop <= 0f || emblemRadius <= 0f) return;

        float fly = BoostTimeline.fly(t);
        float landingScale = iconSize * 1.7f / (emblemRadius * 2f);
        float scale = pop * (1f + (landingScale - 1f) * fly);
        float x = centerX + (iconX - centerX) * fly;
        float y = centerY + (iconY - centerY) * fly;
        float radius = emblemRadius;

        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);

        // Glow behind the emblem; it pulses once the bolt is on.
        float core = BoostTimeline.core(t);
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 0.012f);
        float glowRadius = radius * (1.5f + 0.5f * core) * pulse;
        int glowSave = canvas.save();
        canvas.scale(glowRadius, glowRadius);
        glowPaint.setColorFilter(greenFilter);
        glowPaint.setAlpha(Math.round(255 * (0.18f + 0.32f * core) * alphaNow));
        canvas.drawCircle(0f, 0f, 1f, glowPaint);
        glowPaint.setColorFilter(null);
        canvas.restoreToCount(glowSave);

        // Orbit rings turning in opposite directions, each carrying a small bright dot.
        float frameProgress = BoostTimeline.frame(t);
        float spin = t * 0.07f;
        for (int i = 0; i < 2; i++) {
            float ringRadius = radius * (1.12f + 0.16f * i);
            float direction = i == 0 ? 1f : -1f;
            rect.set(-ringRadius, -ringRadius, ringRadius, ringRadius);
            ringPaint.setStrokeWidth(density * 1.1f);
            ringPaint.setColor(alpha(i == 0 ? CYAN : TEAL, 0.55f * alphaNow * frameProgress));
            for (int s = 0; s < 3; s++) {
                canvas.drawArc(rect, direction * spin * (1f + 0.5f * i) + s * 120f, 52f, false, ringPaint);
            }
            double dot = Math.toRadians(direction * spin * (1f + 0.5f * i) * 1.3f);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(alpha(Color.WHITE, alphaNow * frameProgress));
            canvas.drawCircle((float) Math.cos(dot) * ringRadius, (float) Math.sin(dot) * ringRadius, density * 2.2f, fillPaint);
        }

        // Hexagon outline, drawn on from its top vertex.
        if (frameProgress > 0f) {
            hexSegment.reset();
            hexMeasure.getSegment(0f, hexLength * frameProgress, hexSegment, true);
            ringPaint.setStrokeWidth(density * 2.4f);
            ringPaint.setStrokeJoin(Paint.Join.ROUND);
            ringPaint.setColor(alpha(TEAL, alphaNow));
            canvas.drawPath(hexSegment, ringPaint);
            ringPaint.setStrokeWidth(density * 0.9f);
            ringPaint.setColor(alpha(Color.WHITE, 0.5f * alphaNow));
            canvas.drawPath(hexSegment, ringPaint);
        }

        // Inner disc: dark glass behind the gauge so it reads on any background.
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(alpha(0xFF04141B, 0.78f * alphaNow * frameProgress));
        canvas.drawCircle(0f, 0f, radius * 0.8f, fillPaint);

        drawGauge(canvas, t, radius, alphaNow);
        drawBolt(canvas, radius, core, alphaNow);

        // Light sweep across the hexagon.
        float sweep = BoostTimeline.sweep(t);
        if (sweep > 0f && sweep < 1f) {
            int sweepSave = canvas.save();
            canvas.clipPath(hexPath);
            canvas.translate(-radius * 1.6f + radius * 3.2f * sweep, 0f);
            canvas.rotate(20f);
            canvas.scale(radius * 0.25f, radius * 2f);
            sweepPaint.setAlpha(Math.round(255 * alphaNow));
            canvas.drawRect(-1f, -1f, 1f, 1f, sweepPaint);
            canvas.restoreToCount(sweepSave);
        }

        // Motes circling the emblem before it flies off.
        float moteVisible = BoostTimeline.clamp01((t - BoostTimeline.GAUGE_START) / 400f) * alphaNow * (1f - fly);
        if (moteVisible > 0f) {
            fillPaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < MOTES; i++) {
                double angle = moteAngle[i] + moteSpeed[i] * t * 0.0012f;
                float r = radius * (moteRadius[i] + 0.06f * (float) Math.sin(t * 0.004f + i));
                fillPaint.setColor(alpha(i % 3 == 0 ? Color.WHITE : TEAL, moteVisible * 0.8f));
                canvas.drawCircle((float) Math.cos(angle) * r, (float) Math.sin(angle) * r, density * moteSize[i], fillPaint);
            }
        }
        canvas.restoreToCount(save);
    }

    /** Gauge: a dim track of ticks, and bright ticks filling up to a comet head. */
    private void drawGauge(Canvas canvas, float t, float radius, float alphaNow) {
        float gauge = BoostTimeline.gauge(t);
        float visible = BoostTimeline.frame(t);
        float inner = radius * 0.58f;
        float outer = radius * 0.72f;
        linePaint.setStrokeWidth(density * 2.2f);
        for (int i = 0; i < GAUGE_TICKS; i++) {
            float fraction = i / (float) (GAUGE_TICKS - 1);
            double angle = Math.toRadians(GAUGE_START_DEG + GAUGE_SWEEP_DEG * fraction);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            boolean lit = fraction <= gauge;
            float length = outer;
            if (lit && gauge - fraction < 0.05f) length = outer + radius * 0.07f; // the head sticks out
            int color;
            if (!lit) color = alpha(0xFF2B4A58, 0.9f * alphaNow * visible);
            else if (fraction > 0.82f) color = alpha(GOLD, alphaNow);
            else color = alpha(blend(CYAN, GREEN, fraction), alphaNow);
            linePaint.setColor(color);
            canvas.drawLine(cos * inner, sin * inner, cos * length, sin * length, linePaint);
        }
        if (gauge > 0f && gauge < 1f) {
            double angle = Math.toRadians(GAUGE_START_DEG + GAUGE_SWEEP_DEG * gauge);
            float hx = (float) Math.cos(angle) * outer;
            float hy = (float) Math.sin(angle) * outer;
            int save = canvas.save();
            canvas.translate(hx, hy);
            canvas.scale(radius * 0.22f, radius * 0.22f);
            glowPaint.setColorFilter(tealFilter);
            glowPaint.setAlpha(Math.round(255 * alphaNow));
            canvas.drawCircle(0f, 0f, 1f, glowPaint);
            glowPaint.setColorFilter(null);
            canvas.restoreToCount(save);
        }
    }

    private void drawBolt(Canvas canvas, float radius, float core, float alphaNow) {
        if (core <= 0f) return;
        float size = radius * 0.62f * core;
        int save = canvas.save();
        canvas.scale(size / 24f, size / 24f);
        canvas.translate(-12f, -12f);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(alpha(Color.WHITE, alphaNow));
        canvas.drawPath(bolt, fillPaint);
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStrokeWidth(1.2f);
        fillPaint.setColor(alpha(GREEN, alphaNow));
        canvas.drawPath(bolt, fillPaint);
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    /** Ring and glow on the card icon when the emblem has landed. */
    private void drawLanding(Canvas canvas, float t, float overall) {
        float p = BoostTimeline.arrival(t);
        if (p <= 0f || p >= 1f) return;
        float radius = iconSize * (0.9f + 3.2f * BoostTimeline.easeOutCubic(p));
        ringPaint.setStrokeWidth(density * (3f * (1f - p) + 0.6f));
        ringPaint.setColor(alpha(TEAL, (1f - p) * overall));
        canvas.drawCircle(iconX, iconY, radius, ringPaint);
        int save = canvas.save();
        canvas.translate(iconX, iconY);
        float glow = iconSize * (1.2f + 1.4f * p);
        canvas.scale(glow, glow);
        glowPaint.setColorFilter(greenFilter);
        glowPaint.setAlpha(Math.round(255 * 0.55f * (1f - p) * overall));
        canvas.drawCircle(0f, 0f, 1f, glowPaint);
        glowPaint.setColorFilter(null);
        canvas.restoreToCount(save);
    }

    private static int blend(int from, int to, float f) {
        f = BoostTimeline.clamp01(f);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }
}
