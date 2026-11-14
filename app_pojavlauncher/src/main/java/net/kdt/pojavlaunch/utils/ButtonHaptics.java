package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/** The short buzz of the "Button vibration" setting. Never throws: a phone that cannot vibrate just stays quiet. */
public final class ButtonHaptics {
    private ButtonHaptics() {}

    public static final int MIN_MS = 5;
    public static final int MAX_MS = 40;

    /** Keeps a stored length inside the range the setting allows. */
    public static int clampMs(int ms) {
        return Math.max(MIN_MS, Math.min(MAX_MS, ms));
    }

    public static void buzz(Context context, int ms) {
        if (context == null) return;
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long length = clampMs(ms);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(length, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(length);
            }
        } catch (RuntimeException e) {
            Log.w("ButtonHaptics", "Could not vibrate", e);
        }
    }
}
