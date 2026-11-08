package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import git.artdeell.mojo.R;

/**
 * The Play animation, on the launcher's side. When Play is tapped the launcher's own screen
 * shrinks into a small, wide, rounded window in the middle of a clean white page, and the game's
 * loading screen fades in inside it. The window then stays where it is, under a line of status
 * text, while the game's files are checked and the game starts. (When the game is up, the game's
 * screen takes over the very same window and zooms it to full screen, fast; see
 * {@link LaunchWindowLayout} and {@link LaunchZoomMath}.)
 *
 * <p>The window is the real {@link LaunchLoadingView}, laid out at the size of the display and
 * only scaled down and clipped to a rounded rectangle, so the picture that shows when the shrink
 * ends is exactly the loading screen the game shows next, at exactly the same place. The launcher
 * inside the window is a picture of it taken when Play was tapped, so the shrink does not redraw
 * the whole launcher on every frame.
 *
 * <p>Drawn from a Choreographer frame clock (one frame per screen refresh, and unaffected by the
 * system's animator duration scale). The clock stops itself when the shrink ends.
 */
public final class LaunchTransitionLayout extends LaunchWindowLayout implements Choreographer.FrameCallback {
    /** The white page behind the window. */
    private static final int COLOR_PAGE = 0xFFFFFFFF;
    /** The launcher's own page colour, under its picture where the picture does not reach. */
    private static final int COLOR_LAUNCHER_PAGE = 0xFFF3F6FB;

    private Bitmap snapshot;
    private BitmapShader snapshotShader;
    private final LaunchLoadingView loadingView;
    private final TextView statusText;
    private final String idleStatus;

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix faceMatrix = new Matrix();

    private boolean running;
    private long startNs;
    /** How much of the loading screen shows over the launcher's picture: 0 at the start, 1 at the end. */
    private float hudAlpha;
    private boolean settled;
    private Runnable afterSettle;

    public LaunchTransitionLayout(Context context, Bitmap snapshot, String version,
                                  boolean performanceMode, long originWallMs) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        this.snapshot = snapshot;
        if (snapshot != null) {
            snapshotShader = new BitmapShader(snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        setBackgroundColor(COLOR_PAGE);
        setClickable(true); // swallow touches so the launcher behind cannot be tapped

        loadingView = new LaunchLoadingView(context);
        loadingView.setInfo(version, performanceMode);
        loadingView.setRain(false); // no falling binary digits
        loadingView.setTimeOriginWallMs(originWallMs);
        loadingView.setAlpha(0f);
        addView(loadingView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The same text block the game's loading screen has, in the same style, so that when the
        // game's screen takes over the words do not change. LaunchWindowLayout puts it under the
        // window, where the game's one goes. The second line is only there to take up its space;
        // the game fades it in.
        idleStatus = context.getString(R.string.loading_stage_prepare);
        LinearLayout textBlock = new LinearLayout(context);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER);
        statusText = new TextView(context);
        statusText.setTextColor(0xFF0F8A52);
        statusText.setTextSize(13f);
        statusText.setLetterSpacing(0.12f);
        statusText.setAllCaps(true);
        statusText.setTypeface(statusText.getTypeface(), Typeface.BOLD);
        statusText.setText(idleStatus);
        textBlock.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView spacer = new TextView(context);
        spacer.setTextSize(11f);
        spacer.setText(context.getString(R.string.loading_stage_elapsed, "0:00"));
        spacer.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spacerParams.topMargin = Math.round(density * 4f);
        textBlock.addView(spacer, spacerParams);
        addView(textBlock, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Starts as the full screen, which is the launcher's own picture, and shrinks from there.
        setExpansion(1f);
    }

    // ---- control ----------------------------------------------------------------------------

    /** Start the animation. Call once, after the layout has been added to the screen. */
    public void begin() {
        if (running || settled) return;
        running = true;
        startNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** Stop and let go of the frame clock. Safe to call more than once. */
    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        afterSettle = null;
    }

    /** The window has shrunk to its small size and is staying there. */
    public boolean isSettled() {
        return settled;
    }

    /** Run this once the window has settled (right away if it already has). */
    public void runWhenSettled(Runnable runnable) {
        if (settled) post(runnable);
        else afterSettle = runnable;
    }

    /** A line of text shown under the window, e.g. download progress. Empty puts the usual first line back. */
    public void setStatus(CharSequence text) {
        statusText.setText(text == null || text.length() == 0 ? idleStatus : text);
    }

    // ---- frame clock ------------------------------------------------------------------------

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (startNs == 0) startNs = frameTimeNanos;
        long elapsedMs = (frameTimeNanos - startNs) / 1_000_000L;
        float progress = LaunchZoomMath.shrinkProgress(elapsedMs);

        hudAlpha = LaunchZoomMath.hudAlpha(progress);
        loadingView.setAlpha(hudAlpha);
        setExpansion(LaunchZoomMath.shrinkExpansion(progress));

        if (progress >= 1f) {
            running = false;
            settled = true;
            // The loading screen covers the launcher's picture now: give the picture's memory back.
            snapshotShader = null;
            snapshot = null;
            Runnable callback = afterSettle;
            afterSettle = null;
            if (callback != null) post(callback);
            return;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ---- drawing ----------------------------------------------------------------------------

    /**
     * What is inside the window under the loading screen: the launcher as it was when Play was
     * tapped, drawn at the same place and scale as the display is inside the window, so the first
     * frame of the shrink is the launcher itself.
     */
    @Override
    protected void drawFace(Canvas canvas, RectF window, float cornerRadius) {
        if (hudAlpha >= 0.999f) return; // the loading screen covers it all

        facePaint.setShader(null);
        facePaint.setStyle(Paint.Style.FILL);
        facePaint.setColor(COLOR_LAUNCHER_PAGE);
        canvas.drawRoundRect(window, cornerRadius, cornerRadius, facePaint);

        Bitmap bitmap = snapshot;
        BitmapShader shader = snapshotShader;
        if (bitmap == null || shader == null) return;

        // The picture is this view's own content, which sits displayLeft/Top inside the display.
        float scale = windowScaleNow();
        float left = window.left - displayLeft() * scale;
        float top = window.top - displayTop() * scale;
        float width = getWidth() * scale;
        float height = getHeight() * scale;
        if (width <= 0f || height <= 0f) return;
        faceMatrix.setScale(width / bitmap.getWidth(), height / bitmap.getHeight());
        faceMatrix.postTranslate(left, top);
        shader.setLocalMatrix(faceMatrix);

        facePaint.setShader(shader);
        facePaint.setColor(Color.WHITE); // the picture supplies the colour; this keeps it fully opaque
        int save = canvas.save();
        canvas.clipRect(left, top, left + width, top + height);
        canvas.drawRoundRect(window, cornerRadius, cornerRadius, facePaint);
        canvas.restoreToCount(save);
        facePaint.setShader(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
