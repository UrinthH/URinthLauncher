package net.kdt.pojavlaunch.splash;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;

import net.kdt.pojavlaunch.LauncherActivity;

/**
 * The animated UrinthUltra intro. It plays for {@link UrinthSplashView#DURATION_MS} and then opens
 * the launcher. It is shown once per app start; opening the launcher again while the app is still
 * alive goes straight in.
 */
public class UrinthSplashActivity extends Activity {
    private static boolean sPlayed;

    private UrinthSplashView mView;
    private ValueAnimator mAnimator;
    private boolean mFinished;
    private boolean mLaunched;
    private boolean mResumed;

    /** Starts the intro the first time, or the launcher directly on later opens. */
    public static void open(Context context) {
        Class<?> target = sPlayed ? LauncherActivity.class : UrinthSplashActivity.class;
        context.startActivity(new Intent(context, target));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sPlayed = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        mView = new UrinthSplashView(this);
        setContentView(mView);
        hideSystemBars();

        mAnimator = ValueAnimator.ofFloat(0f, UrinthSplashView.DURATION_MS);
        mAnimator.setDuration(UrinthSplashView.DURATION_MS);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(a -> mView.setTime((float) a.getAnimatedValue()));
        mAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                mFinished = true;
                openLauncher();
            }
        });
        mAnimator.start();
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
        if (mAnimator != null) {
            mAnimator.removeAllListeners();
            mAnimator.cancel();
        }
        super.onDestroy();
    }
}
