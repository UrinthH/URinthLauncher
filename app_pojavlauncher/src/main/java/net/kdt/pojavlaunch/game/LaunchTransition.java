package net.kdt.pojavlaunch.game;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;

/**
 * Starts and stops the Play animation (see {@link LaunchTransitionLayout}) on the launcher
 * screen, and lets the code that starts the game wait for the zoom to land first.
 *
 * <p>The animation is only decoration. If anything about it cannot be done (the screen has no
 * size yet, there is no room for the snapshot), the game starts exactly as it did before.
 * All calls are for the main thread; {@link #start} and {@link #cancel} hand themselves over to
 * it when called from another one.
 */
public final class LaunchTransition {
    private static LaunchTransitionLayout sActive;
    private static ProgressListener sProgressListener;
    private static OnBackPressedCallback sBackCallback;

    private LaunchTransition() {}

    /** Show the animation over this activity, as the player taps Play. */
    public static void start(Activity activity, String version) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Tools.MAIN_HANDLER.post(() -> start(activity, version));
            return;
        }
        if (sActive != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0) return;

        LaunchTransitionLayout layout = new LaunchTransitionLayout(activity, takeSnapshot(content),
                version, LauncherPreferences.PREF_PERFORMANCE_MODE);
        content.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sActive = layout;

        // Back does nothing while the square is still growing. Once it fills the screen and the
        // game is still being prepared, back hides it again (the preparation carries on).
        if (activity instanceof AppCompatActivity) {
            sBackCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (sActive != null && sActive.isZoomFinished()) cancel();
                }
            };
            ((AppCompatActivity) activity).getOnBackPressedDispatcher().addCallback((AppCompatActivity) activity, sBackCallback);
        }

        // While the game files are checked or downloaded, show what is happening on the screen.
        sProgressListener = new ProgressListener() {
            @Override public void onProgressStarted() {}
            @Override public void onProgressEnded() {
                post(() -> { if (sActive != null) sActive.setStatus(""); });
            }
            @Override public void onProgressUpdated(int progress, int resid, Object... va) {
                String text;
                try {
                    text = resid == -1 ? progress + "%" : activity.getString(resid, va);
                } catch (RuntimeException e) {
                    text = progress + "%";
                }
                final String shown = text;
                post(() -> { if (sActive != null) sActive.setStatus(shown); });
            }
        };
        ProgressKeeper.addListener(ProgressLayout.DOWNLOAD_GAME, sProgressListener);

        layout.begin();
    }

    /** A half-size picture of the launcher, or null if there is no memory for it. */
    private static Bitmap takeSnapshot(ViewGroup content) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(content.getWidth() / 2, content.getHeight() / 2, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(0.5f, 0.5f);
            content.draw(canvas);
            return bitmap;
        } catch (Throwable error) {
            return null;
        }
    }

    public static boolean isActive() {
        return sActive != null;
    }

    /**
     * Run this once the zoom has landed. If no animation is showing (the game was started from
     * a notification, say, or the animation was hidden), it runs right away.
     */
    public static void runAfterZoom(Runnable runnable) {
        LaunchTransitionLayout active = sActive;
        if (active == null) runnable.run();
        else active.runAfterZoom(runnable);
    }

    /** Remove the animation, e.g. because the download failed. */
    public static void cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Tools.MAIN_HANDLER.post(LaunchTransition::cancel);
            return;
        }
        LaunchTransitionLayout active = sActive;
        sActive = null;
        if (sProgressListener != null) {
            ProgressKeeper.removeListener(ProgressLayout.DOWNLOAD_GAME, sProgressListener);
            sProgressListener = null;
        }
        if (sBackCallback != null) {
            sBackCallback.remove();
            sBackCallback = null;
        }
        if (active != null) {
            active.stop();
            if (active.getParent() instanceof ViewGroup) ((ViewGroup) active.getParent()).removeView(active);
        }
    }

    private static void post(Runnable runnable) {
        Tools.MAIN_HANDLER.post(runnable);
    }
}
