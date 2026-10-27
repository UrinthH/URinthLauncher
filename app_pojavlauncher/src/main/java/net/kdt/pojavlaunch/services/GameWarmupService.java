package net.kdt.pojavlaunch.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

/**
 * Does nothing, on purpose. It lives in the game's process, so starting it makes Android create
 * that process (and run the application's start-up) while the Play animation is still showing in
 * the launcher. When the game screen is then opened, the process is already there and the screen
 * appears over the launcher without the blank wait of a cold process start.
 *
 * <p>It stops itself after a few seconds; the process stays for the game screen that follows.
 */
public class GameWarmupService extends Service {
    private static final long LIFETIME_MS = 15_000;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(this::stopSelf, LIFETIME_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
