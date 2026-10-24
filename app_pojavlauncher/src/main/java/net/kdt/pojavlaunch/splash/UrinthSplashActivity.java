package net.kdt.pojavlaunch.splash;

import android.view.Choreographer;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import net.kdt.pojavlaunch.LauncherActivity;

/**
 * The animated UrinthUltra intro. It plays for {@link UrinthSplashView#DURATION_MS} and then opens
 * the launcher. It plays on every open of the app.
 */
public class UrinthSplashActivity extends Activity {
    /** A second intro opened on top of a running one (installer Open, then the icon) is dropped. */
    private static boolean sShowing;

    private UrinthSplashView mView;
    private boolean mOwnsSlot;
    private final FrameClock mClock = new FrameClock();
    private boolean mFinished;
    private boolean mLaunched;
    private boolean mResumed;

    /** Starts the intro. It plays every time the app is opened from its icon. */
    public static void open(Context context) {
        context.startActivity(new Intent(context, UrinthSplashActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sShowing) {
            finish();
            return;
        }
        sShowing = true;
        mOwnsSlot = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        mView = new UrinthSplashView(this);
        setContentView(mView);
        hideSystemBars();

        mClock.start();
    }

    /**
     * Drives the intro from the display's frame clock instead of an Android animator. Animators
     * are shortened or switched off by the "animator duration scale" developer setting, which
     * made the intro vanish on some phones. Each frame also advances the intro by at most 50 ms,
     * so a slow frame while the launcher starts up can no longer make it skip ahead.
     */
    private final class FrameClock implements Choreographer.FrameCallback {
        private static final long MAX_STEP_NANOS = 50_000_000L;
        private long mLastFrameNanos;
        private float mElapsedMs;
        private boolean mRunning;

        void start() {
            mRunning = true;
            mView.setTime(0f);
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            mRunning = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mRunning) return;
            if (mLastFrameNanos != 0) {
                long step = Math.min(frameTimeNanos - mLastFrameNanos, MAX_STEP_NANOS);
                if (step > 0) mElapsedMs += step / 1_000_000f;
            }
            mLastFrameNanos = frameTimeNanos;
            if (mElapsedMs >= UrinthSplashView.DURATION_MS) {
                mView.setTime(UrinthSplashView.DURATION_MS);
                mRunning = false;
                mFinished = true;
                openLauncher();
                return;
            }
            mView.setTime(mElapsedMs);
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        // The intro ended while the app was in the background: open the launcher now.
        if (mFinished) openLauncher();
    }

    @Override
    protected void onPause() {
        mResumed = false;
        super.onPause();
    }

    private void openLauncher() {
        if (mLaunched || isFinishing()) return;
        if (!mResumed) return; // retried from onResume
        mLaunched = true;
        startActivity(new Intent(this, LauncherActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        // The intro is only three seconds long; ignore back so the launcher always opens.
    }

    @Override
    protected void onDestroy() {
        mClock.stop();
        if (mOwnsSlot) sShowing = false;
        super.onDestroy();
    }
}
