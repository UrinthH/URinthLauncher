package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * The frame around the loading screen: the whole loading screen drawn smaller, as a wide rounded
 * window in the middle of a clean white page, with a soft shadow, a thin green edge and the status
 * text underneath. {@link #setExpansion} moves it between that small window (0) and the full
 * screen (1); {@link #expand} zooms it to full screen, fast. See {@link LaunchZoomMath}.
 *
 * <p>The window is the real {@link LaunchLoadingView}, laid out at the size of the <i>display</i>
 * and only scaled down and clipped to a rounded rectangle. So nothing is laid out again while it
 * grows, its last frame is exactly the full loading screen, and, because the display is the same
 * for the launcher and for the game, both screens that show this put the window at the same place
 * to the pixel, whatever their own insets are (the game keeps clear of a camera cut-out unless told
 * not to, the launcher does not). A view that is not the whole screen (split screen) is laid out by
 * its own size instead.
 *
 * <p>The child that is a {@link LaunchLoadingView} is the window; the first other child is the
 * caption under it. The view's own background is the page behind the window.
 *
 * <p>Only the loading screen is scaled for display. The game's render size, its resolution and its
 * options are not touched by any of this.
 */
public class LaunchWindowLayout extends FrameLayout {
    private static final int COLOR_GREEN = 0xFF1FC77A;
    private static final int COLOR_SHADOW = 0xFF1B2733;
    private static final float RADIUS_DP = 22f;
    private static final float CAPTION_GAP_DP = 14f;
    private static final int SHADOW_LAYERS = 7;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF windowRect = new RectF();
    private final RectF shadowRect = new RectF();
    private final int[] origin = new int[2];
    private final Point realSize = new Point();

    private LaunchLoadingView hud;
    private View caption;

    /** The size of the screen the window is a picture of, and where its top left corner is in this view. */
    private float screenW;
    private float screenH;
    private float screenLeft;
    private float screenTop;
    private boolean usesDisplay;

    /** 0 = the small window, 1 = the full screen. */
    private float expansion;
    private float radius;
    private float outlineRadius;

    private boolean expanding;
    private long expandStartNs;
    private float expandFrom;
    private Runnable whenFull;

    private final ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), outlineRadius);
        }
    };

    private final Choreographer.FrameCallback expandClock = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!expanding) return;
            if (expandStartNs == 0) expandStartNs = frameTimeNanos;
            long elapsedMs = (frameTimeNanos - expandStartNs) / 1_000_000L;
            float u = LaunchZoomMath.expandProgress(elapsedMs);
            setExpansion(expandFrom + (1f - expandFrom) * LaunchZoomMath.expandEase(u));
            if (u >= 1f) {
                expanding = false;
                Runnable callback = whenFull;
                whenFull = null;
                if (callback != null) callback.run();
                return;
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public LaunchWindowLayout(Context context) {
        this(context, null);
    }

    public LaunchWindowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    // ---- control ----------------------------------------------------------------------------

    /** Move the window: 0 is the small window, 1 is the full screen. */
    public void setExpansion(float e) {
        expansion = LaunchZoomMath.clamp01(e);
        applyState();
    }

    public float getExpansion() {
        return expansion;
    }

    /**
     * Zoom to full screen, fast, from wherever the window is now, then run {@code whenFull} (on
     * the main thread). Does not wait for the zoom if there is nothing to zoom: a window that is
     * already full, or one that has not been laid out yet, jumps to full and calls straight away.
     * Drawn from a Choreographer clock, so it keeps its speed whatever the system's animation
     * scale is set to.
     */
    public void expand(Runnable whenFull) {
        if (expanding) {
            this.whenFull = whenFull;
            return;
        }
        if (expansion >= 1f || screenW <= 0f || !isAttachedToWindow()) {
            setExpansion(1f);
            if (whenFull != null) post(whenFull);
            return;
        }
        expanding = true;
        expandFrom = expansion;
        expandStartNs = 0;
        this.whenFull = whenFull;
        Choreographer.getInstance().postFrameCallback(expandClock);
    }

    /** The window as it is now, in this view's coordinates. Valid after the first layout. */
    protected RectF windowRect() {
        return windowRect;
    }

    /** The corner radius of the window as it is now, in pixels. */
    protected float windowRadius() {
        return radius;
    }

    /** Where the display's top left corner is in this view's coordinates (negative when this view is inset from it). */
    protected float displayLeft() {
        return screenLeft;
    }

    protected float displayTop() {
        return screenTop;
    }

    /** How big the window is compared with the display right now: 1 is the full screen. */
    protected float windowScaleNow() {
        return screenW <= 0f ? 1f : windowRect.width() / screenW;
    }

    /**
     * Draw what is inside the window, under the loading screen itself and over the shadow. The
     * launcher draws a picture of itself here while it shrinks. Nothing by default.
     */
    protected void drawFace(Canvas canvas, RectF window, float cornerRadius) {
    }

    // ---- layout -----------------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
        findParts();
        chooseScreen(w, h);
        if (hud != null) {
            hud.measure(MeasureSpec.makeMeasureSpec(Math.round(screenW), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(screenH), MeasureSpec.EXACTLY));
        }
        if (caption != null) {
            caption.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        findParts();
        int w = right - left;
        int h = bottom - top;
        chooseScreen(w, h);
        getLocationOnScreen(origin);
        screenLeft = usesDisplay ? -origin[0] : 0f;
        screenTop = usesDisplay ? -origin[1] : 0f;
        if (hud != null) {
            int hudLeft = Math.round(screenLeft);
            int hudTop = Math.round(screenTop);
            hud.layout(hudLeft, hudTop, hudLeft + Math.round(screenW), hudTop + Math.round(screenH));
            hud.setPivotX(screenW / 2f);
            hud.setPivotY(screenH / 2f);
        }
        if (caption != null) {
            int captionW = caption.getMeasuredWidth();
            int captionH = caption.getMeasuredHeight();
            float smallBottom = screenTop + screenH / 2f + screenH * LaunchZoomMath.MINI_SCALE / 2f;
            float captionTop = LaunchZoomMath.captionTop(smallBottom, density * CAPTION_GAP_DP, captionH, h);
            int captionLeft = Math.round(screenLeft + screenW / 2f - captionW / 2f);
            caption.layout(captionLeft, Math.round(captionTop), captionLeft + captionW, Math.round(captionTop) + captionH);
        }
        applyState();
    }

    /** Find the loading screen and the caption among the children. */
    private void findParts() {
        if (hud != null && hud.getParent() == this && caption != null && caption.getParent() == this) return;
        hud = null;
        caption = null;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof LaunchLoadingView) {
                hud = (LaunchLoadingView) child;
                break;
            }
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != hud) {
                caption = child;
                break;
            }
        }
        if (hud != null) {
            hud.setOutlineProvider(outlineProvider);
            hud.setClipToOutline(true);
        }
    }

    /** The screen the window is a picture of: the display if this view is all of it, else this view. */
    @SuppressWarnings("deprecation")
    private void chooseScreen(int w, int h) {
        usesDisplay = false;
        screenW = w;
        screenH = h;
        try {
            WindowManager windows = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windows == null) return;
            windows.getDefaultDisplay().getRealSize(realSize);
        } catch (RuntimeException error) {
            return;
        }
        if (LaunchZoomMath.coversScreen(w, h, realSize.x, realSize.y)) {
            usesDisplay = true;
            screenW = realSize.x;
            screenH = realSize.y;
        }
    }

    /** Put the loading screen, the window outline and the caption where the expansion says. */
    private void applyState() {
        if (hud == null || screenW <= 0f || screenH <= 0f) return;
        float e = expansion;
        float scale = LaunchZoomMath.windowScale(LaunchZoomMath.MINI_SCALE, e);
        radius = LaunchZoomMath.cornerRadius(density * RADIUS_DP, e);
        // The outline is in the loading screen's own, unscaled, coordinates.
        outlineRadius = radius / scale;

        hud.setScaleX(scale);
        hud.setScaleY(scale);
        hud.invalidateOutline();

        float centerX = screenLeft + screenW / 2f;
        float centerY = screenTop + screenH / 2f;
        float halfW = screenW * scale / 2f;
        float halfH = screenH * scale / 2f;
        windowRect.set(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH);

        if (caption != null) caption.setAlpha(LaunchZoomMath.captionAlpha(e));
        invalidate();
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (windowRect.isEmpty()) return;
        drawShadow(canvas);
        drawFace(canvas, windowRect, radius);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (windowRect.isEmpty()) return;
        drawEdge(canvas);
    }

    /** A soft shadow under the window, made of a few faint rounded rectangles that grow outward. */
    private void drawShadow(Canvas canvas) {
        float alpha = LaunchZoomMath.shadowAlpha(expansion);
        if (alpha <= 0.01f) return;
        float spread = density * 28f;
        float drop = density * 10f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(COLOR_SHADOW, alpha * 0.028f));
        for (int i = SHADOW_LAYERS; i >= 1; i--) {
            float grow = spread * i / SHADOW_LAYERS;
            shadowRect.set(windowRect.left - grow, windowRect.top - grow + drop,
                    windowRect.right + grow, windowRect.bottom + grow + drop);
            canvas.drawRoundRect(shadowRect, radius + grow, radius + grow, paint);
        }
    }

    /** The thin green edge of the window, with a faint halo. */
    private void drawEdge(Canvas canvas) {
        float alpha = LaunchZoomMath.edgeAlpha(expansion);
        if (alpha <= 0.01f) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(withAlpha(COLOR_GREEN, 0.08f * alpha));
        paint.setStrokeWidth(density * 10f);
        canvas.drawRoundRect(windowRect, radius, radius, paint);
        paint.setColor(withAlpha(COLOR_GREEN, 0.14f * alpha));
        paint.setStrokeWidth(density * 4.5f);
        canvas.drawRoundRect(windowRect, radius, radius, paint);
        paint.setColor(withAlpha(COLOR_GREEN, 0.9f * alpha));
        paint.setStrokeWidth(density * 1.6f);
        canvas.drawRoundRect(windowRect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(LaunchZoomMath.clamp01(alpha) * Color.alpha(color));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    protected void onDetachedFromWindow() {
        expanding = false;
        whenFull = null;
        Choreographer.getInstance().removeFrameCallback(expandClock);
        super.onDetachedFromWindow();
    }
}
