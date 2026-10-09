package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import java.util.concurrent.Executor;

/**
 * Device thermal diagnostics only. Renderer choice is controlled by the launcher's
 * normal Renderer setting; this class does not select or wrap a backend.
 */
public final class URinthRender {
    private static final String TAG = "DeviceThermalMonitor";
    private static PowerManager thermalPowerManager;
    private static PowerManager.OnThermalStatusChangedListener thermalStatusListener;

    private URinthRender() {}

    public static synchronized void startThermalMonitoring(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || context == null) return;
        stopThermalMonitoring();
        try {
            PowerManager manager = (PowerManager) context.getApplicationContext()
                    .getSystemService(Context.POWER_SERVICE);
            if (manager == null) {
                Log.i(TAG, "Thermal monitoring unavailable: PowerManager missing");
                return;
            }
            Executor executor = context.getMainExecutor();
            PowerManager.OnThermalStatusChangedListener listener = status -> {
                String state = thermalStatusName(status);
                if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                    Log.w(TAG, "Thermal status=" + state
                            + "; device throttling may affect frame pacing. No graphics settings changed.");
                } else {
                    Log.i(TAG, "Thermal status=" + state);
                }
            };
            manager.addThermalStatusListener(executor, listener);
            thermalPowerManager = manager;
            thermalStatusListener = listener;
            Log.i(TAG, "Thermal monitoring started");
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to start thermal monitoring", error);
        }
    }

    public static synchronized void stopThermalMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && thermalPowerManager != null && thermalStatusListener != null) {
            try {
                thermalPowerManager.removeThermalStatusListener(thermalStatusListener);
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to stop thermal monitoring cleanly", error);
            }
        }
        thermalPowerManager = null;
        thermalStatusListener = null;
    }

    static String thermalStatusName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "none";
            case PowerManager.THERMAL_STATUS_LIGHT: return "light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return "unknown(" + status + ")";
        }
    }
}
