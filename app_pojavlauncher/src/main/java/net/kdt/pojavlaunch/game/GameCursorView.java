package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursor;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursorImplementor;

import git.artdeell.mojo.R;

/**
 * A view that draws the platform cursor on the screen
 */
public class GameCursorView extends View implements PlatformCursorImplementor {
    private final Paint customCursorPaint = new Paint();
    private final Drawable cursorDrawable;
    private volatile boolean noDraw = false;
    private float mouseScale = 1f;

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GameCursorView(Context context) {
        this(context, null);
    }

    public GameCursorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        cursorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mouse_pointer);
        assert cursorDrawable != null;
        cursorDrawable.setBounds(0, 0, 36, 54);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (noDraw) return;
        if (!(getParent() instanceof GameView)) return;
        GameView gameView = (GameView) getParent();
        // Scale coordinates back to the full unresized screen size
        double dx = Platform.cursorX * gameView.cursorRatioX;
        double dy = Platform.cursorY * gameView.cursorRatioY;
        PlatformCursor cursor = Platform.getCursor();
        int saved = canvas.save();
        canvas.translate(Math.round(dx), Math.round(dy));
        canvas.scale(mouseScale, mouseScale);
        if (cursor == null) {
            cursorDrawable.draw(canvas);
        } else {
            canvas.drawBitmap(cursor.bitmap, -cursor.hotX, -cursor.hotY, customCursorPaint);
        }
        canvas.restoreToCount(saved);
    }

    /**
     * Cursor events arrive far faster than the screen refreshes. Redrawing for each one piles up
     * work and makes the pointer stutter, so redraws are merged into one per screen frame.
     * postInvalidateOnAnimation is safe to call from any thread.
     */
    private void requestRedraw() {
        postInvalidateOnAnimation();
    }

    @Override
    public void onCursorPosition() {
        if (!noDraw) requestRedraw();
    }

    @Override
    public void onCursorChanged() {
        requestRedraw();
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        noDraw = isGrabbing;
        requestRedraw();
    }

    public void setCursorScale(float scale) {
        this.mouseScale = scale;
    }
}
