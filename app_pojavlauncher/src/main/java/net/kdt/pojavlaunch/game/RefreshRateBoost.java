package net.kdt.pojavlaunch.game;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import net.kdt.pojavlaunch.Logger;

/**
 * Asks Android to run the screen at the highest refresh rate it supports, up to 120 Hz, while the
 * game is on screen. A lot of phones sit at 60 Hz for apps that do not ask; with the highest
 * mode requested, Minecraft's vsync follows that rate.
 *
 * Only the display mode's refresh rate is requested, and only among modes with the same physical
 * size as the current one, so nothing about the screen size, the game's render resolution or
 * options.txt is touched. A phone that has no mode above its current rate (or a 60 Hz screen)
 * simply stays as it is: the request can never raise the rate above what the hardware offers.
 */
public final class RefreshRateBoost {
    private static final String TAG = "RefreshRateBoost";
    private static final float WANTED_MAX_HZ = 120f;

    private static volatile float sTarget;

    private RefreshRateBoost() {}

    /** The rate requested by the last {@link #apply}, or 0 when none was made. */
    public static float targetRate() {
        return sTarget;
    }

    /** Requests the highest supported refresh rate (at most 120 Hz). Call from the main thread. */
    @SuppressWarnings("deprecation")
    public static void apply(Activity activity) {
        try {
            Window window = activity.getWindow();
            Display display = activity.getWindowManager().getDefaultDisplay();
            WindowManager.LayoutParams params = window.getAttributes();
            float current = display.getRefreshRate();
            float best = current;
            int bestModeId = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display.Mode now = display.getMode();
                for (Display.Mode mode : display.getSupportedModes()) {
                    if (mode.getPhysicalWidth() != now.getPhysicalWidth()
                            || mode.getPhysicalHeight() != now.getPhysicalHeight()) continue;
                    float rate = mode.getRefreshRate();
                    if (rate <= WANTED_MAX_HZ + 0.5f && rate > best + 0.5f) {
                        best = rate;
                        bestModeId = mode.getModeId();
                    }
                }
                if (bestModeId != 0) params.preferredDisplayModeId = bestModeId;
            }
            params.preferredRefreshRate = best;
            window.setAttributes(params);
            sTarget = best;
            Logger.appendToLog("[RefreshRate] screen is at " + current + " Hz, requested " + best + " Hz"
                    + (bestModeId == 0 ? " (no higher mode available)" : " (mode " + bestModeId + ")"));
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not request a higher refresh rate", error);
        }
    }
}
